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

import com.google.common.net.InetAddresses;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ActionCallback;
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
import io.netty.resolver.HostsFileEntriesResolver;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.IOException;
import java.net.*;

import static com.google.common.base.Strings.isNullOrEmpty;

public final class NettyUtil {
  public static final int DEFAULT_CONNECT_ATTEMPT_COUNT = 8;
  public static final int MIN_START_TIME = 100;

  public static void log(Throwable throwable, Logger log) {
    if (isAsWarning(throwable)) {
      log.warn(throwable);
    }
    else {
      log.error(throwable);
    }
  }

  public static Channel connectClient(Bootstrap bootstrap, InetSocketAddress remoteAddress, ActionCallback asyncResult) {
    return connect(bootstrap, remoteAddress, asyncResult, DEFAULT_CONNECT_ATTEMPT_COUNT);
  }

  @Nullable
  public static Channel connect(Bootstrap bootstrap, InetSocketAddress remoteAddress, ActionCallback asyncResult, int maxAttemptCount) {
    try {
      int attemptCount = 0;
      Socket socket;
      while (true) {
        try {
          //noinspection SocketOpenedButNotSafelyClosed
          socket = new Socket(remoteAddress.getAddress(), remoteAddress.getPort());
          break;
        }
        catch (IOException e) {
          if (++attemptCount < maxAttemptCount) {
            //noinspection BusyWait
            Thread.sleep(attemptCount * 100);
          }
          else {
            asyncResult.reject("cannot connect");
            return null;
          }
        }
      }

      OioSocketChannel channel = new OioSocketChannel(bootstrap.group().next(), socket);
      BootstrapUtil.initAndRegister(channel, bootstrap).awaitUninterruptibly();
      return channel;
    }
    catch (Throwable e) {
      asyncResult.reject("cannot connect: " + e.getMessage());
      return null;
    }
  }

  private static boolean isAsWarning(Throwable throwable) {
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
  public static void closeAndReleaseFactory(Channel channel) {
    EventLoop channelFactory = channel.eventLoop();
    try {
      channel.close().awaitUninterruptibly();
    }
    finally {
      // in our case it does nothing, we don't use ExecutorService, but we are aware of future changes
      channelFactory.shutdownGracefully();
    }
  }

  public static ServerBootstrap nioServerBootstrap(EventLoopGroup eventLoopGroup) {
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
    Bootstrap bootstrap = new Bootstrap().group(new NioEventLoopGroup(1, PooledThreadExecutor.INSTANCE)).channel(NioSocketChannel.class);
    bootstrap.option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.SO_KEEPALIVE, true);
    return bootstrap;
  }

  public static void initHttpHandlers(ChannelPipeline pipeline) {
    pipeline.addLast(new HttpRequestDecoder(), new HttpObjectAggregator(1048576 * 10), new HttpResponseEncoder());
  }


  // val HttpRequest.host: String?
  //  get() = headers().getAsString(HttpHeaderNames.HOST)
  public static String host(HttpRequest request) {
    return request.headers().get(HttpHeaders.Names.HOST);
  }

  // val HttpRequest.origin: String?
  //   get() = headers().getAsString(HttpHeaderNames.ORIGIN)
  public static String origin(HttpRequest request) {
    return request.headers().get(HttpHeaders.Names.ORIGIN);
  }

  //val HttpRequest.referrer: String?
  //  get() = headers().getAsString(HttpHeaderNames.REFERER)
  public static String referrer(HttpRequest request) {
    return request.headers().get(HttpHeaders.Names.REFERER);
  }

  //val HttpRequest.userAgent: String?
  //  get() = headers().getAsString(HttpHeaderNames.USER_AGENT)
  static String userAgent(HttpRequest request) {
    return request.headers().get(HttpHeaders.Names.USER_AGENT);
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