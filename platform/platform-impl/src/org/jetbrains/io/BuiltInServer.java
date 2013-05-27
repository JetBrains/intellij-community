/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.concurrency.Semaphore;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.util.CharsetUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.CustomPortServerManager;
import org.jetbrains.ide.PooledThreadExecutor;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jboss.netty.channel.Channels.pipeline;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class BuiltInServer implements Disposable {
  static final String START_TIME_PATH = "/startTime";

  private final ChannelGroup openChannels = new DefaultChannelGroup();

  static final Logger LOG = Logger.getInstance(BuiltInServer.class);

  @NonNls
  private static final String PROPERTY_ONLY_ANY_HOST = "rpc.onlyAnyHost";

  private final NioServerSocketChannelFactory channelFactory;

  public BuiltInServer() {
    this(1);
  }

  public BuiltInServer(int workerCount) {
    Executor pooledThreadExecutor = new PooledThreadExecutor();
    channelFactory = new NioServerSocketChannelFactory(pooledThreadExecutor, pooledThreadExecutor, workerCount);
  }

  public boolean isRunning() {
    return !openChannels.isEmpty();
  }

  public void start(int port) {
    start(port, 1, false);
  }

  public int start(int firstPort, int portsCount, boolean tryAnyPort) {
    if (isRunning()) {
      throw new IllegalStateException("server already started");
    }

    ServerBootstrap bootstrap = new ServerBootstrap(channelFactory);
    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setPipelineFactory(new ChannelPipelineFactoryImpl(new PortUnificationServerHandler(openChannels)));
    int port = bind(firstPort, portsCount, tryAnyPort, bootstrap);
    bindCustomPorts(firstPort, port, bootstrap);
    return port;
  }

  private void bindCustomPorts(int firstPort, int port, ServerBootstrap bootstrap) {
    for (CustomPortServerManager customPortServerManager : CustomPortServerManager.EP_NAME.getExtensions()) {
      try {
        int customPortServerManagerPort = customPortServerManager.getPort();
        SubServer subServer = new SubServer(customPortServerManager, bootstrap);
        Disposer.register(this, subServer);
        if (customPortServerManagerPort != firstPort && customPortServerManagerPort != port) {
          subServer.bind(customPortServerManagerPort);
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  private static boolean checkPort(final InetSocketAddress remoteAddress) {
    final ClientBootstrap bootstrap = new ClientBootstrap(new OioClientSocketChannelFactory(new PooledThreadExecutor()));
    bootstrap.setOption("child.tcpNoDelay", true);

    final AtomicBoolean result = new AtomicBoolean(false);
    final Semaphore semaphore = new Semaphore();
    semaphore.down(); // must call to down() here to ensure that down was called _before_ up()
    bootstrap.setPipeline(
      pipeline(new HttpResponseDecoder(), new HttpRequestEncoder(), new SimpleChannelUpstreamHandler() {
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

  static String getApplicationStartTime() {
    return Long.toString(ApplicationManager.getApplication().getStartTime());
  }

  // IDEA-91436 idea <121 binds to 127.0.0.1, but >=121 must be available not only from localhost
  // but if we bind only to any local port (0.0.0.0), instance of idea <121 can bind to our ports and any request to us will be intercepted
  // so, we bind to 127.0.0.1 and 0.0.0.0
  private int bind(int firstPort, int portsCount, boolean tryAnyPort, ServerBootstrap bootstrap) {
    String property = System.getProperty(PROPERTY_ONLY_ANY_HOST);
    boolean onlyAnyHost = property == null
                          ? (SystemInfo.isLinux || SystemInfo.isWindows && !SystemInfo.isWinVistaOrNewer)
                          : (property.isEmpty() || Boolean.valueOf(property));
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
          catch (UnknownHostException ignored) {
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
      LOG.info("We cannot bind to our default range, so, try to bind to any free port");
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

  private static boolean checkPortSafe(@NotNull InetSocketAddress localAddress) {
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

  @Override
  public void dispose() {
    try {
      openChannels.close().awaitUninterruptibly();
    }
    finally {
      channelFactory.releaseExternalResources();
    }
    LOG.info("web server stopped");
  }

  public static void replaceDefaultHandler(@NotNull ChannelHandlerContext context, @NotNull SimpleChannelUpstreamHandler messageChannelHandler) {
    context.getPipeline().replace(DelegatingHttpRequestHandler.class, "replacedDefaultHandler", messageChannelHandler);
  }

  private static class ChannelPipelineFactoryImpl implements ChannelPipelineFactory {
    private final ChannelHandler defaultHandler;

    public ChannelPipelineFactoryImpl(ChannelHandler defaultHandler) {
      this.defaultHandler = defaultHandler;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
      return pipeline(defaultHandler);
    }
  }
}