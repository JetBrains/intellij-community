/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.NotNullProducer;
import com.intellij.util.SystemProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.oio.OioEventLoopGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.util.io.NettyKt.MultiThreadEventLoopGroup;
import static com.intellij.util.io.NettyKt.serverBootstrap;

public class BuiltInServer implements Disposable {
  // Some antiviral software detect viruses by the fact of accessing these ports so we should not touch them to appear innocent.
  private static final int[] FORBIDDEN_PORTS = {6953, 6969, 6970};

  private final EventLoopGroup eventLoopGroup;
  private final int port;
  private final ChannelRegistrar channelRegistrar;

  static {
    // IDEA-120811
    if (SystemProperties.getBooleanProperty("io.netty.random.id", true)) {
      System.setProperty("io.netty.machineId", "28:f0:76:ff:fe:16:65:0e");
      System.setProperty("io.netty.processId", Integer.toString(new Random().nextInt(65535)));
      System.setProperty("io.netty.serviceThreadPrefix", "Netty ");
    }
  }

  private BuiltInServer(@NotNull EventLoopGroup eventLoopGroup, int port, @NotNull ChannelRegistrar channelRegistrar) {
    this.eventLoopGroup = eventLoopGroup;
    this.port = port;
    this.channelRegistrar = channelRegistrar;
  }

  @NotNull
  public EventLoopGroup getEventLoopGroup() {
    return eventLoopGroup;
  }

  public int getPort() {
    return port;
  }

  public boolean isRunning() {
    return !channelRegistrar.isEmpty();
  }

  @Override
  public void dispose() {
    channelRegistrar.close();
    Logger.getInstance(BuiltInServer.class).info("web server stopped");
  }

  @NotNull
  public static BuiltInServer start(int workerCount,
                                    int firstPort,
                                    int portsCount,
                                    boolean tryAnyPort,
                                    @Nullable NotNullProducer<ChannelHandler> handler) throws Exception {
    return start(MultiThreadEventLoopGroup(workerCount, new BuiltInServerThreadFactory()), true, firstPort, portsCount, tryAnyPort, handler);
  }

  @NotNull
  public static BuiltInServer startNioOrOio(int workerCount,
                                            int firstPort,
                                            int portsCount,
                                            boolean tryAnyPort,
                                            @Nullable NotNullProducer<ChannelHandler> handler) throws Exception {
    BuiltInServerThreadFactory threadFactory = new BuiltInServerThreadFactory();
    EventLoopGroup loopGroup;
    try {
      loopGroup = MultiThreadEventLoopGroup(workerCount, threadFactory);
    }
    catch (IllegalStateException e) {
      Logger.getInstance(BuiltInServer.class).warn(e);
      loopGroup = new OioEventLoopGroup(1, threadFactory);
    }
    return start(loopGroup, true, firstPort, portsCount, tryAnyPort, handler);
  }

  @NotNull
  public static BuiltInServer start(@NotNull EventLoopGroup eventLoopGroup,
                                    boolean isEventLoopGroupOwner,
                                    int firstPort,
                                    int portsCount,
                                    boolean tryAnyPort,
                                    @Nullable NotNullProducer<ChannelHandler> handler) throws Exception {
    ChannelRegistrar channelRegistrar = new ChannelRegistrar();
    ServerBootstrap bootstrap = serverBootstrap(eventLoopGroup);
    configureChildHandler(bootstrap, channelRegistrar, handler);
    int port = bind(firstPort, portsCount, tryAnyPort, bootstrap, channelRegistrar, isEventLoopGroupOwner);
    return new BuiltInServer(eventLoopGroup, port, channelRegistrar);
  }

  static void configureChildHandler(@NotNull ServerBootstrap bootstrap,
                                    @NotNull final ChannelRegistrar channelRegistrar,
                                    @Nullable final NotNullProducer<ChannelHandler> channelHandler) {
    final PortUnificationServerHandler portUnificationServerHandler = channelHandler == null ? new PortUnificationServerHandler() : null;
    bootstrap.childHandler(new ChannelInitializer() {
      @Override
      protected void initChannel(@NotNull Channel channel) throws Exception {
        channel.pipeline().addLast(channelRegistrar, channelHandler == null ? portUnificationServerHandler : channelHandler.produce());
      }
    });
  }

  private static int bind(int firstPort,
                          int portsCount,
                          boolean tryAnyPort,
                          @NotNull ServerBootstrap bootstrap,
                          @NotNull ChannelRegistrar channelRegistrar,
                          boolean isEventLoopGroupOwner) throws Exception {
    InetAddress address = InetAddress.getLoopbackAddress();

    for (int i = 0; i < portsCount; i++) {
      int port = firstPort + i;

      if (ArrayUtil.indexOf(FORBIDDEN_PORTS, i) >= 0) {
        continue;
      }

      ChannelFuture future = bootstrap.bind(address, port).awaitUninterruptibly();
      if (future.isSuccess()) {
        channelRegistrar.setServerChannel(future.channel(), isEventLoopGroupOwner);
        return port;
      }
      else if (!tryAnyPort && i == portsCount - 1) {
        ExceptionUtil.rethrowAll(future.cause());
      }
    }

    Logger.getInstance(BuiltInServer.class).info("We cannot bind to our default range, so, try to bind to any free port");
    ChannelFuture future = bootstrap.bind(address, 0).awaitUninterruptibly();
    if (future.isSuccess()) {
      channelRegistrar.setServerChannel(future.channel(), isEventLoopGroupOwner);
      return ((InetSocketAddress)future.channel().localAddress()).getPort();
    }
    ExceptionUtil.rethrowAll(future.cause());

    return -1;  // unreachable
  }

  public static void replaceDefaultHandler(@NotNull ChannelHandlerContext context, @NotNull ChannelHandler channelHandler) {
    context.pipeline().replace(DelegatingHttpRequestHandler.class, "replacedDefaultHandler", channelHandler);
  }

  private static class BuiltInServerThreadFactory implements ThreadFactory {
    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public Thread newThread(Runnable r) {
      return new Thread(r, "Netty Builtin Server " + counter.incrementAndGet());
    }
  }
}