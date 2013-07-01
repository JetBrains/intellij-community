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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.CustomPortServerManager;
import org.jetbrains.ide.PooledThreadExecutor;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executor;

import static org.jboss.netty.channel.Channels.pipeline;

public class BuiltInServer implements Disposable {
  private final ChannelGroup openChannels = new DefaultChannelGroup();

  static final Logger LOG = Logger.getInstance(BuiltInServer.class);

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

    ServerBootstrap bootstrap = createServerBootstrap(channelFactory, openChannels);
    int port = bind(firstPort, portsCount, tryAnyPort, bootstrap);
    bindCustomPorts(firstPort, port);
    return port;
  }

  static ServerBootstrap createServerBootstrap(NioServerSocketChannelFactory channelFactory, ChannelGroup openChannels) {
    ServerBootstrap bootstrap = new ServerBootstrap(channelFactory);
    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);
    bootstrap.setPipelineFactory(new ChannelPipelineFactoryImpl(new PortUnificationServerHandler(openChannels)));
    return bootstrap;
  }

  private void bindCustomPorts(int firstPort, int port) {
    for (CustomPortServerManager customPortServerManager : CustomPortServerManager.EP_NAME.getExtensions()) {
      try {
        int customPortServerManagerPort = customPortServerManager.getPort();
        SubServer subServer = new SubServer(customPortServerManager, channelFactory);
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

  // IDEA-91436 idea <121 binds to 127.0.0.1, but >=121 must be available not only from localhost
  // but if we bind only to any local port (0.0.0.0), instance of idea <121 can bind to our ports and any request to us will be intercepted
  // so, we bind to 127.0.0.1 and 0.0.0.0
  private int bind(int firstPort, int portsCount, boolean tryAnyPort, ServerBootstrap bootstrap) {
    InetAddress localAddress;
    try {
      localAddress = InetAddress.getByName("127.0.0.1");
    }
    catch (UnknownHostException e) {
      LOG.error(e);
      return -1;
    }

    for (int i = 0; i < portsCount; i++) {
      int port = firstPort + i;
      try {
        openChannels.add(bootstrap.bind(new InetSocketAddress(localAddress, port)));
        return port;
      }
      catch (ChannelException e) {
        if (!openChannels.isEmpty()) {
          openChannels.close();
          openChannels.clear();
        }

        if (portsCount == 1) {
          throw e;
        }
        else if (!tryAnyPort && i == (portsCount - 1)) {
          LOG.error(e);
        }
      }
    }

    if (tryAnyPort) {
      LOG.info("We cannot bind to our default range, so, try to bind to any free port");
      try {
        Channel channel = bootstrap.bind(new InetSocketAddress(localAddress, 0));
        openChannels.add(channel);
        return ((InetSocketAddress)channel.getLocalAddress()).getPort();
      }
      catch (ChannelException e) {
        LOG.error(e);
      }
    }

    return -1;
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