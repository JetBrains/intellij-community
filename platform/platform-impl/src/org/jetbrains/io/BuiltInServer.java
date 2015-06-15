/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.net.NetUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

public class BuiltInServer implements Disposable {
  static final Logger LOG = Logger.getInstance(BuiltInServer.class);

  private final ChannelRegistrar channelRegistrar = new ChannelRegistrar();

  final EventLoopGroup eventLoopGroup;
  private final int port;

  public boolean isRunning() {
    return !channelRegistrar.isEmpty();
  }

  private BuiltInServer(@NotNull EventLoopGroup eventLoopGroup, int port) {
    this.eventLoopGroup = eventLoopGroup;
    this.port = port;
  }

  @NotNull
  public static BuiltInServer start(int workerCount, int firstPort, int portsCount, boolean tryAnyPort) throws Throwable {
    EventLoopGroup eventLoopGroup = new NioEventLoopGroup(workerCount, PooledThreadExecutor.INSTANCE);
    ChannelRegistrar channelRegistrar = new ChannelRegistrar();
    ServerBootstrap bootstrap = NettyUtil.nioServerBootstrap(eventLoopGroup);
    configureChildHandler(bootstrap, channelRegistrar);
    return new BuiltInServer(eventLoopGroup, bind(firstPort, portsCount, tryAnyPort, bootstrap, channelRegistrar));
  }

  public int getPort() {
    return port;
  }

  static void configureChildHandler(@NotNull ServerBootstrap bootstrap, @NotNull final ChannelRegistrar channelRegistrar) {
    final PortUnificationServerHandler portUnificationServerHandler = new PortUnificationServerHandler();
    bootstrap.childHandler(new ChannelInitializer() {
      @Override
      protected void initChannel(Channel channel) throws Exception {
        channel.pipeline().addLast(channelRegistrar, portUnificationServerHandler);
      }
    });
  }

  private static int bind(int firstPort, int portsCount, boolean tryAnyPort, @NotNull ServerBootstrap bootstrap, @NotNull ChannelRegistrar channelRegistrar) throws Throwable {
    InetAddress loopbackAddress = NetUtils.getLoopbackAddress();
    for (int i = 0; i < portsCount; i++) {
      int port = firstPort + i;

      // we check if any port free too
      if (!SystemInfo.isLinux && (!SystemInfo.isWindows || SystemInfo.isWinVistaOrNewer)) {
        try {
          ServerSocket serverSocket = new ServerSocket();
          try {
            serverSocket.bind(new InetSocketAddress(port), 1);
          }
          finally {
            serverSocket.close();
          }
        }
        catch (IOException ignored) {
          continue;
        }
      }

      ChannelFuture future = bootstrap.bind(loopbackAddress, port).awaitUninterruptibly();
      if (future.isSuccess()) {
        channelRegistrar.add(future.channel());
        return port;
      }
      else if (!tryAnyPort && i == (portsCount - 1)) {
        throw future.cause();
      }
    }

    LOG.info("We cannot bind to our default range, so, try to bind to any free port");
    ChannelFuture future = bootstrap.bind(loopbackAddress, 0).awaitUninterruptibly();
    if (future.isSuccess()) {
      channelRegistrar.add(future.channel());
      return ((InetSocketAddress)future.channel().localAddress()).getPort();
    }
    else {
      throw future.cause();
    }
  }

  @Override
  public void dispose() {
    channelRegistrar.close();
    LOG.info("web server stopped");
  }

  public static void replaceDefaultHandler(@NotNull ChannelHandlerContext context, @NotNull ChannelHandler channelHandler) {
    context.pipeline().replace(DelegatingHttpRequestHandler.class, "replacedDefaultHandler", channelHandler);
  }
}