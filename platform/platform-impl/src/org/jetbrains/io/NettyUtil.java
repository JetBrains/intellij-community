// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.NettyKt;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsConfigBuilder;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
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

  private static boolean isAsWarning(@NotNull Throwable throwable) {
    String message = throwable.getMessage();
    if (message == null) {
      return false;
    }

    if (throwable instanceof IOException) {
      return throwable instanceof BindException ||
             message.equals("An existing connection was forcibly closed by the remote host") ||
             message.equals("\u0423\u0434\u0430\u043b\u0435\u043d\u043d\u044b\u0439 \u0445\u043e\u0441\u0442 \u043f\u0440\u0438\u043d\u0443\u0434\u0438\u0442\u0435\u043b\u044c\u043d\u043e \u0440\u0430\u0437\u043e\u0440\u0432\u0430\u043b \u0441\u0443\u0449\u0435\u0441\u0442\u0432\u0443\u044e\u0449\u0435\u0435 \u043f\u043e\u0434\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u0435");
    }

    return (throwable instanceof ChannelException && message.startsWith("Failed to bind to: ")) ||
           (message.startsWith("Connection reset") || message.equals("Operation timed out") || message.equals("Connection timed out"));
  }

  @NotNull
  public static Bootstrap nioClientBootstrap() {
    return nioClientBootstrap(NettyKt.MultiThreadEventLoopGroup(2));
  }

  @NotNull
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
    pipeline.addLast("corsHandler", new CorsHandlerDoNotUseOwnLogger(CorsConfigBuilder
                                                                       .forAnyOrigin()
                                                                       .shortCircuit()
                                                                       .allowCredentials()
                                                                       .allowNullOrigin()
                                                                       .allowedRequestMethods(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.PATCH)
                                                                       .allowedRequestHeaders("origin", "accept", "authorization", "content-type", "x-ijt", "x-requested-with")
                                                                       .build()));
  }

  private static final class CorsHandlerDoNotUseOwnLogger extends CorsHandler {
    public CorsHandlerDoNotUseOwnLogger(@NotNull CorsConfig config) {
      super(config);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
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
