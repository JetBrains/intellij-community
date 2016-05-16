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

import com.google.common.net.InetAddresses;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.SystemProperties;
import com.intellij.util.net.NetUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.BootstrapUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cors.CorsConfig;
import io.netty.handler.codec.http.cors.CorsHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.resolver.HostsFileEntriesResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.IOException;
import java.net.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Strings.isNullOrEmpty;

public final class NettyUtil {
  public static final int MAX_CONTENT_LENGTH = 100 * 1024 * 1024;

  public static final int DEFAULT_CONNECT_ATTEMPT_COUNT = 20;
  public static final int MIN_START_TIME = 100;

  static {
    // IDEA-120811
    if (SystemProperties.getBooleanProperty("io.netty.random.id", true)) {
      System.setProperty("io.netty.machineId", "9e43d860");
      System.setProperty("io.netty.processId", Integer.toString(new Random().nextInt(65535)));
    }
  }

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
  public static Channel connect(@NotNull Bootstrap bootstrap, @NotNull InetSocketAddress remoteAddress, @Nullable AsyncPromise<?> promise) {
    return connect(bootstrap, remoteAddress, promise, DEFAULT_CONNECT_ATTEMPT_COUNT);
  }

  @Nullable
  public static Channel connect(@NotNull Bootstrap bootstrap, @NotNull InetSocketAddress remoteAddress, @Nullable AsyncPromise<?> promise, int maxAttemptCount) {
    return connect(bootstrap, remoteAddress, promise, maxAttemptCount, null);
  }

  @Nullable
  public static Channel connect(@NotNull Bootstrap bootstrap, @NotNull InetSocketAddress remoteAddress, @Nullable AsyncPromise<?> promise, int maxAttemptCount, @Nullable Condition<Void> stopCondition) {
    try {
      return doConnect(bootstrap, remoteAddress, promise, maxAttemptCount, stopCondition == null ? Conditions.<Void>alwaysFalse() : stopCondition);
    }
    catch (Throwable e) {
      if (promise != null) {
        promise.setError(e);
      }
      return null;
    }
  }

  @Nullable
  private static Channel doConnect(@NotNull Bootstrap bootstrap,
                                   @NotNull InetSocketAddress remoteAddress,
                                   @Nullable AsyncPromise<?> promise,
                                   int maxAttemptCount,
                                   @NotNull Condition<Void> stopCondition) throws Throwable {
    int attemptCount = 0;

    if (bootstrap.group() instanceof NioEventLoopGroup) {
      while (true) {
        ChannelFuture future = bootstrap.connect(remoteAddress).awaitUninterruptibly();
        if (future.isSuccess()) {
          return future.channel();
        }
        else if (stopCondition.value(null) || (promise != null && promise.getState() == Promise.State.REJECTED)) {
          return null;
        }
        else if (maxAttemptCount == -1) {
          //noinspection BusyWait
          Thread.sleep(300);
          attemptCount++;
        }
        else if (++attemptCount < maxAttemptCount) {
          //noinspection BusyWait
          Thread.sleep(attemptCount * MIN_START_TIME);
        }
        else {
          @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
          Throwable cause = future.cause();
          if (promise != null) {
            //noinspection ThrowableResultOfMethodCallIgnored
            promise.setError(cause == null ? Promise.createError("Cannot connect: unknown error") : cause);
          }
          return null;
        }
      }
    }

    Socket socket;
    while (true) {
      try {
        //noinspection SocketOpenedButNotSafelyClosed
        socket = new Socket(remoteAddress.getAddress(), remoteAddress.getPort());
        break;
      }
      catch (IOException e) {
        if (stopCondition.value(null) || (promise != null && promise.getState() == Promise.State.REJECTED)) {
          return null;
        }
        else if (maxAttemptCount == -1) {
          //noinspection BusyWait
          Thread.sleep(300);
          attemptCount++;
        }
        else if (++attemptCount < maxAttemptCount) {
          //noinspection BusyWait
          Thread.sleep(attemptCount * MIN_START_TIME);
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

  // applicable only in case of ClientBootstrap&OioClientSocketChannelFactory
  public static void closeAndReleaseFactory(@NotNull Channel channel) {
    EventLoop eventLoop = channel.eventLoop();
    try {
      channel.close().awaitUninterruptibly();
    }
    finally {
      // in our case it does nothing, we don't use ExecutorService, but we are aware of future changes
      eventLoop.shutdownGracefully(1, 2, TimeUnit.NANOSECONDS);
    }
  }

  public static ServerBootstrap nioServerBootstrap(@NotNull EventLoopGroup eventLoopGroup) {
    ServerBootstrap bootstrap = new ServerBootstrap().group(eventLoopGroup).channel(NioServerSocketChannel.class);
    bootstrap.childOption(ChannelOption.TCP_NODELAY, true).childOption(ChannelOption.SO_KEEPALIVE, true);
    return bootstrap;
  }

  public static Bootstrap oioClientBootstrap() {
    Bootstrap bootstrap = new Bootstrap().group(new OioEventLoopGroup(1, PooledThreadExecutor.INSTANCE)).channel(OioSocketChannel.class);
    bootstrap.option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.SO_KEEPALIVE, true);
    return bootstrap;
  }

  @SuppressWarnings("UnusedDeclaration")
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
    pipeline.addLast("httpRequestDecoder", new HttpRequestDecoder());
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

  // val HttpRequest.host: String?
  //  get() = headers().getAsString(HttpHeaderNames.HOST)
  public static String host(HttpRequest request) {
    return request.headers().get(HttpHeaderNames.HOST);
  }

  // val HttpRequest.origin: String?
  //   get() = headers().getAsString(HttpHeaderNames.ORIGIN)
  public static String origin(HttpRequest request) {
    return request.headers().get(HttpHeaderNames.ORIGIN);
  }

  //val HttpRequest.referrer: String?
  //  get() = headers().getAsString(HttpHeaderNames.REFERER)
  public static String referrer(HttpRequest request) {
    return request.headers().get(HttpHeaderNames.REFERER);
  }

  //val HttpRequest.userAgent: String?
  //  get() = headers().getAsString(HttpHeaderNames.USER_AGENT)
  static String userAgent(HttpRequest request) {
    return request.headers().get(HttpHeaderNames.USER_AGENT);
  }

  // fun InetAddress.isLocal() = isAnyLocalAddress || isLoopbackAddress || NetworkInterface.getByInetAddress(this) != null
  private static boolean isLocal(InetAddress address) throws SocketException {
    return address.isAnyLocalAddress() || address.isLoopbackAddress() || NetworkInterface.getByInetAddress(address) != null;
  }

  // fun isLocalHost(host: String, onlyAnyOrLoopback: Boolean, hostsOnly: Boolean = false): Boolean {
  //  if (NetUtils.isLocalhost(host)) {
  //    return true
  //  }
  //
  //  // if IP address, it is safe to use getByName (not affected by DNS rebinding)
  //  if (onlyAnyOrLoopback && !InetAddresses.isInetAddress(host)) {
  //    return false
  //  }
  //
  //  fun InetAddress.isLocal() = isAnyLocalAddress || isLoopbackAddress || NetworkInterface.getByInetAddress(this) != null
  //
  //    try {
  //    val address = InetAddress.getByName(host)
  //    if (!address.isLocal()) {
  //      return false
  //    }
  //    // be aware - on windows hosts file doesn't contain localhost
  //    // hosts can contain remote addresses, so, we check it
  //    if (hostsOnly && !InetAddresses.isInetAddress(host)) {
  //      return HostsFileEntriesResolver.DEFAULT.address(host).let { it != null && it.isLocal() }
  //    }
  //    else {
  //      return true
  //    }
  //  }
  //  catch (ignored: IOException) {
  //    return false
  //  }
  //}
  public static boolean isLocalHost(String host, boolean onlyAnyOrLoopback) {
    return isLocalHost(host, onlyAnyOrLoopback, false);
  }

  static boolean isLocalHost(String host, boolean onlyAnyOrLoopback, boolean hostsOnly) {
    if (NetUtils.isLocalhost(host)) {
      return true;
    }

    // if IP address, it is safe to use getByName (not affected by DNS rebinding)
    if (onlyAnyOrLoopback && !InetAddresses.isInetAddress(host)) {
      return false;
    }

    try {
      InetAddress address = InetAddress.getByName(host);
      if (!isLocal(address)) {
        return false;
      }
      // be aware - on windows hosts file doesn't contain localhost
      // hosts can contain remote addresses, so, we check it
      if (hostsOnly && !InetAddresses.isInetAddress(host)) {
        InetAddress addressFromHosts = HostsFileEntriesResolver.DEFAULT.address(host);
        return addressFromHosts != null && isLocal(addressFromHosts);
      }
      else {
        return true;
      }
    }
    catch (IOException ignored) {
      return false;
    }
  }

  // @JvmOverloads
  // fun HttpRequest.isLocalOrigin(onlyAnyOrLoopback: Boolean = true, hostsOnly: Boolean = false) =
  //   parseAndCheckIsLocalHost(origin, onlyAnyOrLoopback, hostsOnly) && parseAndCheckIsLocalHost(referrer, onlyAnyOrLoopback, hostsOnly)
  public static boolean isLocalOrigin(HttpRequest request) {
    return isLocalOrigin(request, true, false);
  }

  public static boolean isLocalOrigin(HttpRequest request, boolean onlyAnyOrLoopback, boolean hostsOnly) {
    return parseAndCheckIsLocalHost(origin(request), onlyAnyOrLoopback, hostsOnly) &&
           parseAndCheckIsLocalHost(referrer(request), onlyAnyOrLoopback, hostsOnly);
  }

  //private fun isTrustedChromeExtension(uri: URI): Boolean {
  //  return uri.scheme == "chrome-extension" && (uri.host == "hmhgeddbohgjknpmjagkdomcpobmllji" || uri.host == "offnedcbhjldheanlbojaefbfbllddna")
  //}
  private static boolean isTrustedChromeExtension(URI uri){
    return "chrome-extension".equals(uri.getScheme()) && ("hmhgeddbohgjknpmjagkdomcpobmllji".equals(uri.getHost()) || "offnedcbhjldheanlbojaefbfbllddna".equals(uri.getHost()));
  }

  //@JvmOverloads
  //fun parseAndCheckIsLocalHost(uri: String?, onlyAnyOrLoopback: Boolean = true, hostsOnly: Boolean = false): Boolean {
  //  if (uri == null) {
  //    return true
  //  }
  //
  //  try {
  //    val parsedUri = URI(uri)
  //    return isTrustedChromeExtension(parsedUri) || isLocalHost(parsedUri.host, onlyAnyOrLoopback, hostsOnly)
  //  }
  //  catch (ignored: Exception) {
  //  }
  //  return false
  //}

  public static boolean parseAndCheckIsLocalHost(String uri) {
    return parseAndCheckIsLocalHost(uri, true, false);
  }

  static boolean parseAndCheckIsLocalHost(String uri, boolean onlyAnyOrLoopback, boolean hostsOnly) {
    if (uri == null) {
      return true;
    }

    try {
      URI parsedUri = new URI(uri);
      return isTrustedChromeExtension(parsedUri) || isLocalHost(parsedUri.getHost(), onlyAnyOrLoopback, hostsOnly);
    }
    catch (Exception ignored) {
    }
    return false;
  }

  // forbid POST requests from browser without Origin
  //fun HttpRequest.isWriteFromBrowserWithoutOrigin(): Boolean {
  //  val userAgent = userAgent ?: return false
  //  val method = method()
  //  return origin.isNullOrEmpty() && isRegularBrowser() && (method == HttpMethod.POST || method == HttpMethod.PATCH || method == HttpMethod.PUT || method == HttpMethod.DELETE)
  //}
  static boolean isWriteFromBrowserWithoutOrigin(HttpRequest request) {
    if (request == null) return false;

    HttpMethod method = request.getMethod();
    return isNullOrEmpty(origin(request)) &&
           isRegularBrowser(request) && (method == HttpMethod.POST || method == HttpMethod.PATCH || method == HttpMethod.PUT || method == HttpMethod.DELETE);
  }

  // fun HttpRequest.isRegularBrowser() = userAgent?.startsWith("Mozilla/5.0") ?: false
  public static boolean isRegularBrowser(HttpRequest request) {
    String userAgent = userAgent(request);
    if (request == null) return false;
    return userAgent.startsWith("Mozilla/5.0");
  }
}