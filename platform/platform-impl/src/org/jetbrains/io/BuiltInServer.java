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

import com.intellij.ide.XmlRpcServer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.net.NetUtils;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.CustomPortServerManager;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executor;

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

    ServerBootstrap bootstrap = createServerBootstrap(channelFactory, openChannels, null);
    int port = bind(firstPort, portsCount, tryAnyPort, bootstrap);
    bindCustomPorts(firstPort, port);
    return port;
  }

  static ServerBootstrap createServerBootstrap(NioServerSocketChannelFactory channelFactory, ChannelGroup openChannels, @Nullable Map<String, Object> xmlRpcHandlers) {
    ServerBootstrap bootstrap = new ServerBootstrap(channelFactory);
    bootstrap.setOption("child.tcpNoDelay", true);
    bootstrap.setOption("child.keepAlive", true);
    if (xmlRpcHandlers == null) {
      final ChannelHandler handler = new PortUnificationServerHandler(openChannels);
      bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
        @Override
        public ChannelPipeline getPipeline() throws Exception {
          return Channels.pipeline(handler);
        }
      });
    }
    else {
      final XmlRpcDelegatingHttpRequestHandler handler = new XmlRpcDelegatingHttpRequestHandler(xmlRpcHandlers);
      bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
        @Override
        public ChannelPipeline getPipeline() throws Exception {
          return Channels.pipeline(new HttpRequestDecoder(), new HttpChunkAggregator(1048576), new HttpResponseEncoder(), handler);
        }
      });
    }
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

  private int bind(int firstPort, int portsCount, boolean tryAnyPort, ServerBootstrap bootstrap) {
    InetAddress loopbackAddress = NetUtils.getLoopbackAddress();
    for (int i = 0; i < portsCount; i++) {
      int port = firstPort + i;
      try {
        openChannels.add(bootstrap.bind(new InetSocketAddress(loopbackAddress, port)));
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
        Channel channel = bootstrap.bind(new InetSocketAddress(loopbackAddress, 0));
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

  private static final class XmlRpcDelegatingHttpRequestHandler extends DelegatingHttpRequestHandlerBase {
    private final Map<String, Object> handlers;

    public XmlRpcDelegatingHttpRequestHandler(Map<String, Object> handlers) {
      this.handlers = handlers;
    }

    @Override
    protected boolean process(ChannelHandlerContext context, HttpRequest request, QueryStringDecoder urlDecoder) throws IOException {
      return (request.getMethod() == HttpMethod.POST || request.getMethod() == HttpMethod.OPTIONS) &&
             XmlRpcServer.SERVICE.getInstance().process(urlDecoder.getPath(), request, context, handlers);
    }
  }
}