/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.io;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.util.CharsetUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jboss.netty.channel.Channels.pipeline;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class WebServer {
  private static final String START_TIME_PATH = "/startTime";

  private final List<ChannelFutureListener> closingListeners = ContainerUtil.createEmptyCOWList();
  private final ChannelGroup openChannels = new DefaultChannelGroup("web-server");

  private static final Logger LOG = Logger.getInstance(WebServer.class);

  @NonNls
  private static final String PROPERTY_ONLY_ANY_HOST = "rpc.onlyAnyHost";

  private final Executor pooledThreadExecutor = new Executor() {
    private final Application application = ApplicationManager.getApplication();

    @Override
    public void execute(@NotNull Runnable command) {
      application.executeOnPooledThread(command);
    }
  };
  private final NioServerSocketChannelFactory channelFactory = new NioServerSocketChannelFactory(pooledThreadExecutor, pooledThreadExecutor, 1);

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
    return bind(firstPort, portsCount, tryAnyPort, bootstrap);
  }

  private boolean checkPort(final InetSocketAddress remoteAddress) {
    final ClientBootstrap bootstrap = new ClientBootstrap(new OioClientSocketChannelFactory(pooledThreadExecutor));
    bootstrap.setOption("child.tcpNoDelay", true);

    final AtomicBoolean result = new AtomicBoolean(false);
    final Semaphore semaphore = new Semaphore();
    semaphore.down(); // must call to down() here to ensure that down was called _before_ up()
    bootstrap.setPipeline(
      pipeline(new HttpResponseDecoder(), new HttpChunkAggregator(1048576), new HttpRequestEncoder(), new SimpleChannelUpstreamHandler() {
        @Override
        public void messageReceived(ChannelHandlerContext context, MessageEvent e) throws Exception {
          try {
            if (e.getMessage() instanceof HttpResponse) {
              HttpResponse response = (HttpResponse)e.getMessage();
              if (response.getStatus().equals(OK) &&
                  response.getContent().toString(CharsetUtil.US_ASCII).equals(getApplicationStartTime())) {
                LOG.info("port check: current OS must be marked as normal");
                result.set(true);
              }
            }
          }
          finally {
            semaphore.up();
          }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
          try {
            LOG.error(e.getCause());
          }
          finally {
            semaphore.up();
          }
        }
      }));

    ChannelFuture connectFuture = null;
    try {
      connectFuture = bootstrap.connect(remoteAddress);
    if (!waitComplete(connectFuture, "connect")) {
      return false;
    }
      ChannelFuture writeFuture = connectFuture.getChannel().write(new DefaultHttpRequest(HTTP_1_1, HttpMethod.GET, START_TIME_PATH));
      if (!waitComplete(writeFuture, "write")) {
        return false;
      }

      try {
        // yes, 30 seconds. I always get timeout in Linux in Parallels if I set to 2 seconds.
        // In any case all work is done in pooled thread (IDE init time isn't affected)
        if (!semaphore.waitForUnsafe(30000)) {
          LOG.info("port check: semaphore down timeout");
        }
      }
      catch (InterruptedException e) {
        LOG.info("port check: semaphore interrupted", e);
      }
    }
    finally {
      if (connectFuture != null) {
        connectFuture.getChannel().close().awaitUninterruptibly();
      }
      bootstrap.releaseExternalResources();
    }
    return result.get();
  }

  private static boolean waitComplete(ChannelFuture writeFuture, String failedMessage) {
    if (!writeFuture.awaitUninterruptibly(500) || !writeFuture.isSuccess()) {
      LOG.info("port check: " + failedMessage + ", " + writeFuture.isSuccess());
      return false;
    }
    return true;
  }

  private static String getApplicationStartTime() {
    return Long.toString(ApplicationManager.getApplication().getStartTime());
  }

  // IDEA-91436 idea <121 binds to 127.0.0.1, but >=121 must be available not only from localhost
  // but if we bind only to any local port (0.0.0.0), instance of idea <121 can bind to our ports and any request to us will be intercepted
  // so, we bind to 127.0.0.1 and 0.0.0.0
  private int bind(int firstPort, int portsCount, boolean tryAnyPort, ServerBootstrap bootstrap) {
    String property = System.getProperty(PROPERTY_ONLY_ANY_HOST);
    boolean onlyAnyHost = property == null ? (SystemInfo.isLinux || SystemInfo.isWindows && !SystemInfo.isWinVistaOrNewer) : (property.isEmpty() || Boolean.valueOf(property));
    boolean portChecked = false;
    for (int i = 0; i < portsCount; i++) {
      int port = firstPort + i;
      ChannelException channelException = null;
      try {
        openChannels.add(bootstrap.bind(new InetSocketAddress(port)));
        if (!onlyAnyHost) {
          InetSocketAddress localAddress = null;
          try {
            localAddress = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port);
            openChannels.add(bootstrap.bind(localAddress));
          }
          catch (UnknownHostException e) {
            return port;
          }
          catch (ChannelException e) {
            channelException = e;
            if (!portChecked) {
              portChecked = true;
              assert localAddress != null;
              if (checkPortSafe(localAddress)) {
                return port;
              }
            }
          }
        }
      }
      catch (ChannelException e) {
        channelException = e;
      }

      if (channelException == null) {
        return port;
      }
      else {
        if (!openChannels.isEmpty()) {
          openChannels.close();
          openChannels.clear();
        }

        if (portsCount == 1) {
          throw channelException;
        }
        else if (!tryAnyPort && i == (portsCount - 1)) {
          LOG.error(channelException);
        }
      }
    }

    if (tryAnyPort) {
      try {
        Channel channel = bootstrap.bind(new InetSocketAddress(0));
        openChannels.add(channel);
        return ((InetSocketAddress)channel.getLocalAddress()).getPort();
      }
      catch (ChannelException e) {
        LOG.error(e);
      }
    }

    return -1;
  }

  private boolean checkPortSafe(@NotNull InetSocketAddress localAddress) {
    LOG.info("We have tried to bind to 127.0.0.1 host but have got exception (" +
             SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION + "), " +
             "so, try to check - are we really need to bind to 127.0.0.1");
    try {
      return checkPort(localAddress);
    }
    catch (Throwable innerE) {
      LOG.error(innerE);
      return false;
    }
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
        HttpRequest message = (HttpRequest)e.getMessage();
        HttpResponse response;
        if (new QueryStringDecoder(message.getUri()).getPath().equals(START_TIME_PATH)) {
          response = new DefaultHttpResponse(HTTP_1_1, OK);
          response.setContent(ChannelBuffers.copiedBuffer(getApplicationStartTime(), CharsetUtil.US_ASCII));
        }
        else {
          response = new DefaultHttpResponse(HTTP_1_1, NOT_FOUND);
        }
        context.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
      }
    }
  }
}
