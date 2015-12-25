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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.BootstrapUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public final class NettyUtil {
  public static final int MAX_CONTENT_LENGTH = 100 * 1024 * 1024;

  public static final int DEFAULT_CONNECT_ATTEMPT_COUNT = 20;
  public static final int MIN_START_TIME = 100;

  public static void logAndClose(@NotNull Throwable error, @NotNull Logger log, @NotNull Channel channel) {
    // don't report about errors while connecting
    // WEB-7727
    try {
      if (error instanceof ConnectException) {
        log.debug(error);
      }
      else {
        log(error, log);
      }
    }
    finally {
      log.info("Channel will be closed due to error");
      channel.close();
    }
  }

  public static void log(@NotNull Throwable throwable, @NotNull Logger log) {
    if (isAsWarning(throwable)) {
      log.warn(throwable);
    }
    else {
      log.error(throwable);
    }
  }

  @Nullable
  static Channel doConnect(@NotNull Bootstrap bootstrap,
                           @NotNull InetSocketAddress remoteAddress,
                           @Nullable AsyncPromise<?> promise,
                           int maxAttemptCount,
                           @NotNull Condition<Void> stopCondition) throws Throwable {
    int attemptCount = 0;
    if (bootstrap.group() instanceof NioEventLoopGroup) {
      return connectNio(bootstrap, remoteAddress, promise, maxAttemptCount, stopCondition, attemptCount);
    }

    bootstrap.validate();

    Socket socket;
    while (true) {
      try {
        //noinspection IOResourceOpenedButNotSafelyClosed,SocketOpenedButNotSafelyClosed
        socket = new Socket(remoteAddress.getAddress(), remoteAddress.getPort());
        break;
      }
      catch (IOException e) {
        if (stopCondition.value(null) || (promise != null && promise.getState() != Promise.State.PENDING)) {
          return null;
        }
        else if (maxAttemptCount == -1) {
          if (sleep(promise, 300)) {
            return null;
          }
          attemptCount++;
        }
        else if (++attemptCount < maxAttemptCount) {
          if (sleep(promise, attemptCount * MIN_START_TIME)) {
            return null;
          }
        }
        else {
          if (promise != null) {
            promise.setError(e);
          }
          return null;
        }
      }
    }

    OioSocketChannel channel = new OioSocketChannel(socket);
    BootstrapUtil.initAndRegister(channel, bootstrap).sync();
    return channel;
  }

  @Nullable
  private static Channel connectNio(@NotNull Bootstrap bootstrap,
                                    @NotNull InetSocketAddress remoteAddress,
                                    @Nullable AsyncPromise<?> promise,
                                    int maxAttemptCount,
                                    @NotNull Condition<Void> stopCondition,
                                    int attemptCount) {
    while (true) {
      ChannelFuture future = bootstrap.connect(remoteAddress).awaitUninterruptibly();
      if (future.isSuccess()) {
        if (!future.channel().isOpen()) {
          continue;
        }
        return future.channel();
      }
      else if (stopCondition.value(null) || (promise != null && promise.getState() == Promise.State.REJECTED)) {
        return null;
      }
      else if (maxAttemptCount == -1) {
        if (sleep(promise, 300)) {
          return null;
        }
        attemptCount++;
      }
      else if (++attemptCount < maxAttemptCount) {
        if (sleep(promise, attemptCount * MIN_START_TIME)) {
          return null;
        }
      }
      else {
        @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
        Throwable cause = future.cause();
        if (promise != null) {
          if (cause == null) {
            promise.setError("Cannot connect: unknown error");
          }
          else {
            promise.setError(cause);
          }
        }
        return null;
      }
    }
  }

  private static boolean sleep(@Nullable AsyncPromise<?> promise, int time) {
    try {
      //noinspection BusyWait
      Thread.sleep(time);
    }
    catch (InterruptedException ignored) {
      if (promise != null) {
        promise.setError("Interrupted");
      }
      return true;
    }
    return false;
  }

  private static boolean isAsWarning(@NotNull Throwable throwable) {
    String message = throwable.getMessage();
    if (message == null) {
      return false;
    }

    return (throwable instanceof IOException && message.equals("An existing connection was forcibly closed by the remote host")) ||
           (throwable instanceof ChannelException && message.startsWith("Failed to bind to: ")) ||
           throwable instanceof BindException ||
           (message.startsWith("Connection reset") || message.equals("Operation timed out") || message.equals("Connection timed out"));
  }

  @SuppressWarnings("unused")
  @Deprecated
  @NotNull
  public static ServerBootstrap nioServerBootstrap(@NotNull EventLoopGroup eventLoopGroup) {
    ServerBootstrap bootstrap = new ServerBootstrap().group(eventLoopGroup).channel(NioServerSocketChannel.class);
    bootstrap.childOption(ChannelOption.TCP_NODELAY, true).childOption(ChannelOption.SO_KEEPALIVE, true);
    return bootstrap;
  }

  @SuppressWarnings("unused")
  @Deprecated
  @NotNull
  public static Bootstrap oioClientBootstrap() {
    return NettyKt.oioClientBootstrap();
  }

  public static Bootstrap nioClientBootstrap() {
    return nioClientBootstrap(new NioEventLoopGroup(1, PooledThreadExecutor.INSTANCE));
  }

  public static Bootstrap nioClientBootstrap(@NotNull EventLoopGroup eventLoopGroup) {
    Bootstrap bootstrap = new Bootstrap().group(eventLoopGroup).channel(NioSocketChannel.class);
    bootstrap.option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.SO_KEEPALIVE, true);
    return bootstrap;
  }

  public static void addHttpServerCodec(@NotNull ChannelPipeline pipeline) {
    pipeline.addLast("httpRequestEncoder", new HttpResponseEncoder());
    // https://jetbrains.zendesk.com/agent/tickets/68315
    pipeline.addLast("httpRequestDecoder", new HttpRequestDecoder(16 * 1024, 16 * 1024, 8192));
    pipeline.addLast("httpObjectAggregator", new HttpObjectAggregator(MAX_CONTENT_LENGTH));
    // could be added earlier if HTTPS
    if (pipeline.get(ChunkedWriteHandler.class) == null) {
      pipeline.addLast("chunkedWriteHandler", new ChunkedWriteHandler());
    }
    pipeline.addLast("corsHandler", new CorsHandlerDoNotUseOwnLogger(CorsConfig
                                                                       .withAnyOrigin()
                                                                       .allowCredentials()
                                                                       .allowNullOrigin()
                                                                       .allowedRequestMethods(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.PATCH)
                                                                       .allowedRequestHeaders("origin", "accept", "authorization", "content-type")
                                                                       .build()));
  }

  private static final class CorsHandlerDoNotUseOwnLogger extends CorsHandler {
    public CorsHandlerDoNotUseOwnLogger(@NotNull CorsConfig config) {
      super(config);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
      context.fireExceptionCaught(cause);
    }
  }

  @TestOnly
  public static void awaitQuiescenceOfGlobalEventExecutor(long timeout, @NotNull TimeUnit unit) {
    try {
      @NotNull GlobalEventExecutor executor = GlobalEventExecutor.INSTANCE;
      executor.awaitInactivity(timeout, unit);
    }
    catch (InterruptedException ignored) {

    }
    catch (IllegalStateException ignored) {
      // thread did not start
    }
  }
}
