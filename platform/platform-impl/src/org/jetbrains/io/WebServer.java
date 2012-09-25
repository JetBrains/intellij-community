package org.jetbrains.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.*;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.jboss.netty.channel.Channels.pipeline;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class WebServer {
  private final NioServerSocketChannelFactory channelFactory;
  private final List<ChannelFutureListener> closingListeners = ContainerUtil.createEmptyCOWList();
  private final ChannelGroup openChannels = new DefaultChannelGroup("web-server");

  private static final Logger LOG = Logger.getInstance(WebServer.class);

  public WebServer() {
    ExecutorService executor = Executors.newCachedThreadPool();
    channelFactory = new NioServerSocketChannelFactory(executor, executor);
  }

  public boolean isRunning() {
    return !openChannels.isEmpty();
  }

  public void start(int port, Consumer<ChannelPipeline>... pipelineConsumers) {
    start(port, new Computable.PredefinedValueComputable<Consumer<ChannelPipeline>[]>(pipelineConsumers));
  }

  public void start(int port, int portsCount, Consumer<ChannelPipeline>... pipelineConsumers) {
    start(port, portsCount, false, new Computable.PredefinedValueComputable<Consumer<ChannelPipeline>[]>(pipelineConsumers));
  }

  public void start(int port, Computable<Consumer<ChannelPipeline>[]> pipelineConsumers) {
    start(port, 1, false, pipelineConsumers);
  }

  public int start(int firstPort, int portsCount, boolean tryAnyPort, Computable<Consumer<ChannelPipeline>[]> pipelineConsumers) {
    if (isRunning()) {
      throw new IllegalStateException("server already started");
    }

    ServerBootstrap bootstrap = new ServerBootstrap(channelFactory);
    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setPipelineFactory(new ChannelPipelineFactoryImpl(pipelineConsumers, new DefaultHandler(openChannels)));

    for (int i = 0, n = tryAnyPort ? portsCount : portsCount + 1; i < n; i++) {
      int port = i == portsCount ? 0 : firstPort + i;
      try {
        openChannels.add(bootstrap.bind(new InetSocketAddress(port)));
        return port;
      }
      catch (ChannelException e) {
        if (portsCount == 1) {
          throw e;
        }
        else if (i == (n - 1)) {
          LOG.error(e);
        }
      }
    }

    return -1;
  }

  public void stop() {
    try {
      for (ChannelFutureListener listener : closingListeners) {
        try {
          listener.operationComplete(null);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
    finally {
      try {
        openChannels.close().awaitUninterruptibly();
      }
      finally {
        channelFactory.releaseExternalResources();
      }
    }
  }

  public void addClosingListener(ChannelFutureListener listener) {
    closingListeners.add(listener);
  }

  public Runnable createShutdownTask() {
    return new Runnable() {
      @Override
      public void run() {
        if (isRunning()) {
          stop();
        }
      }
    };
  }

  public void addShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread(createShutdownTask()));
  }

  public static void removePluggableHandlers(ChannelPipeline pipeline) {
    for (String name : pipeline.getNames()) {
      if (name.startsWith("pluggable_")) {
        pipeline.remove(name);
      }
    }
  }

  private static class ChannelPipelineFactoryImpl implements ChannelPipelineFactory {
    private final Computable<Consumer<ChannelPipeline>[]> pipelineConsumers;
    private final DefaultHandler defaultHandler;

    public ChannelPipelineFactoryImpl(Computable<Consumer<ChannelPipeline>[]> pipelineConsumers, DefaultHandler defaultHandler) {
      this.pipelineConsumers = pipelineConsumers;
      this.defaultHandler = defaultHandler;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
      ChannelPipeline pipeline = pipeline(new HttpRequestDecoder(), new HttpChunkAggregator(1048576), new HttpResponseEncoder());
      for (Consumer<ChannelPipeline> consumer : pipelineConsumers.compute()) {
        try {
          consumer.consume(pipeline);
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
      pipeline.addLast("defaultHandler", defaultHandler);
      return pipeline;
    }
  }

  @ChannelHandler.Sharable
  private static class DefaultHandler extends SimpleChannelUpstreamHandler {
    private final ChannelGroup openChannels;

    public DefaultHandler(ChannelGroup openChannels) {
      this.openChannels = openChannels;
    }

    @Override
    public void channelOpen(ChannelHandlerContext context, ChannelStateEvent e) {
      openChannels.add(e.getChannel());
    }

    @Override
    public void messageReceived(ChannelHandlerContext context, MessageEvent e) throws Exception {
      if (e.getMessage() instanceof HttpRequest) {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, NOT_FOUND);
        context.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
      }
    }
  }
}
