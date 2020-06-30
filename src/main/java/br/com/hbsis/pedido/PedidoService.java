package br.com.hbsis.pedido;

import br.com.hbsis.RequestLoggingInterceptor;
import br.com.hbsis.fornecedor.Fornecedor;
import br.com.hbsis.fornecedor.FornecedorService;

import br.com.hbsis.funcionario.Funcionario;
import br.com.hbsis.funcionario.FuncionarioService;
import br.com.hbsis.itens.Item;
import br.com.hbsis.itens.ItensDTO;
import br.com.hbsis.itens.InvoiceDTO;
import br.com.hbsis.periodoVendas.PeriodoVendas;
import br.com.hbsis.periodoVendas.PeriodoVendasService;
import br.com.hbsis.produtos.Produtos;
import br.com.hbsis.produtos.ProdutosService;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;

@Service
public class PedidoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PedidoService.class);

    private final IPedidoRepository iPedidoRepository;
    private final FornecedorService fornecedorService;
    private final ProdutosService produtosService;
    private final PeriodoVendasService periodoVendasService;
    private final FuncionarioService funcionarioService;
    private final JavaMailSender javaMailSender;


    public PedidoService(IPedidoRepository iPedidoRepository, FornecedorService fornecedorService, ProdutosService produtosService, PeriodoVendasService periodoVendasService, FuncionarioService funcionarioService, JavaMailSender javaMailSender) {
        this.iPedidoRepository = iPedidoRepository;
        this.fornecedorService = fornecedorService;
        this.produtosService = produtosService;
        this.periodoVendasService = periodoVendasService;
        this.funcionarioService = funcionarioService;
        this.javaMailSender = javaMailSender;
    }

    public PedidoDTO save(PedidoDTO pedidoDTO) {
        this.validate(pedidoDTO);


        LOGGER.info("Salvando pedidos");
        LOGGER.debug("Pedidos: {}", pedidoDTO);

        Pedido pedido = new Pedido();

        Fornecedor fornecedorxx = fornecedorService.findByFornecedorId(pedidoDTO.getIdFornecedor());
        Funcionario funcionarioxx = funcionarioService.findByIdFuncionario(pedidoDTO.getIdFuncionario());

        pedido.setDataDeCriacao(pedidoDTO.getDataDeCriacao());
        pedido.setStatus(pedidoDTO.getStatus());
        pedido.setPedidoFornecedor(fornecedorxx);
        pedido.setPedidoFuncionario(funcionarioxx);
        pedido.setUid(arrumaUid(pedidoDTO));
        pedido.setItens(convercaoItens(pedidoDTO.getItens(), pedido));

        pedido = this.iPedidoRepository.save(pedido);

        List<Item> a = pedido.getItens();

        this.validaAPI(InvoiceDTO.of(pedido, a));   

        return PedidoDTO.of(pedido);
    }


    public Long arrumaUid(PedidoDTO pedidoDTO) {

        //return new Random().nextInt(100000));

        Pedido pedidoMax = iPedidoRepository.pedidoWhitIdMax();

        if(String.valueOf(pedidoMax.getUid()).isEmpty()){
           String um = "1";
            pedidoDTO.setuUid(Long.parseLong(um));

        }else if (pedidoMax.getUid() >= 1){
            pedidoDTO.setuUid(pedidoMax.getUid() + 1);
        }

        return pedidoDTO.getUid();

    }

    private List<Item> convercaoItens(List<ItensDTO> itensDTOS, Pedido pedido) {
        List<Item> itens = new ArrayList<>();
        for (ItensDTO itensDTO : itensDTOS) {
            Item itens1 = new Item();
            itens1.setIdPedido(pedido);
            itens1.setQuantidade(itensDTO.getQuantidadeProduto());
            itens1.setIdProduto(produtosService.findById(itensDTO.getIdProduto()));
            itens.add(itens1);
        }
        return itens;
    }


    private void validaAPI(InvoiceDTO invoiceDTO) {
        RestTemplate template = new RestTemplate();
        template.setInterceptors(Collections.singletonList(new RequestLoggingInterceptor()));
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "f5a00866-1b67-11ea-978f-2e728ce88125");
//        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<InvoiceDTO> httpEntity = new HttpEntity<>(invoiceDTO, headers);
        ResponseEntity<InvoiceDTO> response = template.exchange("http://10.2.54.25:9999/api/invoice", HttpMethod.POST, httpEntity, InvoiceDTO.class);
    }


    private void validate(PedidoDTO pedidoDTO) {
        LOGGER.info("Validando produtos");

        if (pedidoDTO == null) {
            throw new IllegalArgumentException("PedidoDTO  não deve ser nulo");
        }
        if (pedidoDTO.getIdFornecedor() == null) {
            throw new IllegalArgumentException("O fornecedor do pedido não pode ser nulo");
        }

        if (pedidoDTO.getIdFuncionario() == null) {
            throw new IllegalArgumentException("O funcionario do pedido não pode ser nulo");
        }

        if (pedidoDTO.getItens() == null) {
            throw new IllegalArgumentException("Os ítens do pedido não podem ser nulos");
        }

        if (StringUtils.isEmpty(pedidoDTO.getStatus())) {
            throw new IllegalArgumentException("O nome do pedido não pode ser nulo");
        }
        if (pedidoDTO.getDataDeCriacao() == null) {
            throw new IllegalArgumentException("A data de criação do pedido não pode ser nulo");
        }


        for (ItensDTO item : pedidoDTO.getItens()) {

            Produtos produtosComprados = produtosService.findById(item.getIdProduto());// pega o produto do pedido

            PeriodoVendas periodoVendasAtual = periodoVendasService.findAllByFornecedor(pedidoDTO.getIdFornecedor());

            Long idFornecedor = produtosComprados.getLinhaCategoria().getCategoriaLinha().getFornecedorCategoria().getId();

            if (!pedidoDTO.getIdFornecedor().equals(idFornecedor)) {
                throw new IllegalArgumentException("O id do fornecedor do produto e do pedido são diferentes");
            }

            if (!periodoVendasAtual.getDataInicial().isAfter(pedidoDTO.getDataDeCriacao()) && !periodoVendasAtual.getDataInicial().isBefore(pedidoDTO.getDataDeCriacao())) {
                throw new IllegalArgumentException("Não tem periodo de venda vigente");
            }
        }

    }


    public Pedido findById(Long id) {
        Optional<Pedido> idPedido = this.iPedidoRepository.findById(id);
        return idPedido.get();

    }


    public PedidoDTO statusCancela(Long id) {
        Optional<Pedido> pedidoExistenteChato = this.iPedidoRepository.findById(id);

        if (pedidoExistenteChato.isPresent()) {
            Pedido pedidoChatoPacasete = pedidoExistenteChato.get();
            PeriodoVendas periodoVendasAtual = periodoVendasService.findAllByFornecedor(pedidoChatoPacasete.getPedidoFornecedor().getId());

            if (pedidoChatoPacasete.getStatus().contains("Ativo")) {
                if (!periodoVendasAtual.getDataInicial().isAfter(LocalDate.now()) && !periodoVendasAtual.getDataInicial().isBefore(LocalDate.now())) {
                    throw new IllegalArgumentException("Não tem periodo de venda vigente");
                }
                pedidoChatoPacasete.setStatus("Cancelado");// setta o pedido como cancelado

            } else if (pedidoChatoPacasete.getStatus().contains("Cancelado") || pedidoChatoPacasete.getStatus().contains("Recebido")) {
                throw new IllegalArgumentException("não da pra cancelar pedido q não esta ativo");
            }
            pedidoChatoPacasete = this.iPedidoRepository.save(pedidoChatoPacasete);
            return PedidoDTO.of(pedidoChatoPacasete);
        }
        throw new IllegalArgumentException("não tem o que atualizar");
    }


    public PedidoDTO statusRetira(Long id) {
        Optional<Pedido> pedidoExistenteChato = this.iPedidoRepository.findById(id);

        if (pedidoExistenteChato.isPresent()) {
            Pedido pedidoChatoPacasete = pedidoExistenteChato.get();
            PeriodoVendas periodoVendasAtual = periodoVendasService.findAllByFornecedor(pedidoChatoPacasete.getPedidoFornecedor().getId());//pega o periodo de vendas do pedido

            if (pedidoChatoPacasete.getStatus().contains("Ativo")) {
                if (!periodoVendasAtual.getDataInicial().isAfter(LocalDate.now()) && !periodoVendasAtual.getDataInicial().isBefore(LocalDate.now())) {
                    throw new IllegalArgumentException("Não tem periodo de venda vigente");
                }

                pedidoChatoPacasete.setStatus("Retirado");// setta o pedido como retirado

            } else if (pedidoChatoPacasete.getStatus().contains("Cancelado") || pedidoChatoPacasete.getStatus().contains("Recebido")) {
                throw new IllegalArgumentException("não da pra cancelar pedido q não esta ativo");
            }
            pedidoChatoPacasete = this.iPedidoRepository.save(pedidoChatoPacasete);
            return PedidoDTO.of(pedidoChatoPacasete);
        }
        throw new IllegalArgumentException("não há o que atualizar");
    }

    public PedidoDTO attLadrao(PedidoDTO pedidoDTO, Long id) {
        Optional<Pedido> pedidoExistenteChato = this.iPedidoRepository.findById(id);


        if (pedidoExistenteChato.isPresent()) {
            Pedido pedidoChatoPacasete = pedidoExistenteChato.get();
            PeriodoVendas periodoVendasAtual = periodoVendasService.findAllByFornecedor(pedidoChatoPacasete.getPedidoFornecedor().getId());//pega o periodo de vendas do pedido

            if (pedidoChatoPacasete.getStatus().contains("Ativo")) {
                if (!periodoVendasAtual.getDataInicial().isAfter(LocalDate.now()) && !periodoVendasAtual.getDataInicial().isBefore(LocalDate.now())) {
                    throw new IllegalArgumentException("Não tem periodo de venda vigente");
                }
                Fornecedor fornecedorzz = this.fornecedorService.findByFornecedorId(pedidoDTO.getIdFornecedor());

                pedidoChatoPacasete.setStatus(pedidoDTO.getStatus());
                pedidoChatoPacasete.setDataDeCriacao(pedidoDTO.getDataDeCriacao());
                pedidoChatoPacasete.setItens(convercaoItens(pedidoDTO.getItens(), pedidoChatoPacasete));
                pedidoChatoPacasete.setPedidoFornecedor(fornecedorzz);

            } else if (pedidoChatoPacasete.getStatus().contains("Cancelado") || pedidoChatoPacasete.getStatus().contains("Recebido")) {
                throw new IllegalArgumentException("não da pra cancelar pedido q n ta ativo");
            }
            pedidoChatoPacasete = this.iPedidoRepository.save(pedidoChatoPacasete);
            return PedidoDTO.of(pedidoChatoPacasete);
        }
        throw new IllegalArgumentException("não tem oq updatar");
    }



    public void simpleMailMessage(Funcionario funcionario) {

        SimpleMailMessage sendSimpleMessage = new SimpleMailMessage();

        sendSimpleMessage.setSubject("Compra no site do Paulinho");
        sendSimpleMessage.setText("seu pedido foi aprovado e está em separação");
        sendSimpleMessage.setTo(funcionario.getEmail());
        sendSimpleMessage.setFrom("amrheintiago@gmail.com");

        try {
            javaMailSender.send(sendSimpleMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public PedidoDTO update(PedidoDTO pedidoDTO, Long id) {
        Optional<Pedido> pedidoExistente = this.iPedidoRepository.findById(id);

        if (pedidoExistente.isPresent()) {
            Pedido pedidoReal = pedidoExistente.get();

            LOGGER.info("Atualizando produtos.... id:[{}]", pedidoReal.getId());
            LOGGER.debug("Payload: {}", pedidoDTO);
            LOGGER.debug("Produtos existente: {}", pedidoReal);

            Fornecedor fornecedorzz = this.fornecedorService.findByFornecedorId(pedidoDTO.getIdFornecedor());

            pedidoReal.setStatus(pedidoDTO.getStatus());
            pedidoReal.setDataDeCriacao(pedidoDTO.getDataDeCriacao());
            pedidoReal.setItens(convercaoItens(pedidoDTO.getItens(), pedidoReal));
            pedidoReal.setPedidoFornecedor(fornecedorzz);

            pedidoReal = this.iPedidoRepository.save(pedidoReal);
            return PedidoDTO.of(pedidoReal);
        }
        throw new IllegalArgumentException("não tem oq updatar");
    }


    public void delete(Long id) {
        LOGGER.info("Executando delete para linha de ID: [{}]", id);

        this.iPedidoRepository.deleteById(id);
    }


    public void pegaLadrao(Long idFuncionario) {

        List<Pedido> express = iPedidoRepository.findAll();
        for (Pedido pedido : express) {

            if (pedido.getPedidoFuncionario().getId().equals(idFuncionario)) {
                if (pedido.getStatus().contains("Ativo") || pedido.getStatus().contains("Cancelado"))
                    System.out.println(pedido.toString());

            } else {
                throw new IllegalArgumentException("O idFuncionario q vc busca n se encontra, tente novamente cadastrando ele");

            }
        }
    }
}
