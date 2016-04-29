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
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executor;

import static org.jboss.netty.channel.Channels.pipeline;

public class WebServer implements Disposable {
  static final String START_TIME_PATH = "/startTime";

  private final ChannelGroup openChannels = new DefaultChannelGroup("web-server");

  static final Logger LOG = Logger.getInstance(WebServer.class);

  private final NioServerSocketChannelFactory channelFactory;

  public WebServer() {
    Executor pooledThreadExecutor = new PooledThreadExecutor();
    channelFactory = new NioServerSocketChannelFactory(pooledThreadExecutor, pooledThreadExecutor, 1);
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
    return bind(firstPort, portsCount, tryAnyPort, bootstrap);
  }

  static String getApplicationStartTime() {
    return Long.toString(ApplicationManager.getApplication().getStartTime());
  }

  private int bind(int firstPort, int portsCount, boolean tryAnyPort, ServerBootstrap bootstrap) {
    for (int i = 0; i < portsCount; i++) {
      int port = firstPort + i;
      ChannelException channelException = null;
      try {
        openChannels.add(bootstrap.bind(new InetSocketAddress(InetAddress.getByName(null), port)));
      }
      catch (ChannelException e) {
        channelException = e;
      }
      catch (UnknownHostException e) {
        LOG.error(e);
        return -1;
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