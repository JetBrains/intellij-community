// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.google.common.net.InetAddresses
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.net.NetUtils
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.BootstrapUtil
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.oio.OioEventLoopGroup
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.oio.OioServerSocketChannel
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.ssl.SslHandler
import io.netty.resolver.ResolvedAddressTypes
import io.netty.util.concurrent.GenericFutureListener
import org.jetbrains.io.NettyUtil
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

inline fun Bootstrap.handler(crossinline task: (Channel) -> Unit): Bootstrap {
  handler(object : ChannelInitializer<Channel>() {
    override fun initChannel(channel: Channel) {
      task(channel)
    }
  })
  return this
}

fun serverBootstrap(group: EventLoopGroup): ServerBootstrap {
  val bootstrap = ServerBootstrap()
    .group(group)
    .channel(serverSocketChannelClass(group))
  bootstrap.childOption(ChannelOption.TCP_NODELAY, true).childOption(ChannelOption.SO_KEEPALIVE, true)
  return bootstrap
}

@Suppress("DEPRECATION")
private fun serverSocketChannelClass(eventLoopGroup: EventLoopGroup): Class<out ServerSocketChannel> {
  return when (eventLoopGroup) {
    is NioEventLoopGroup -> NioServerSocketChannel::class.java
    is OioEventLoopGroup -> OioServerSocketChannel::class.java
    //  SystemInfo.isMacOSSierra && this is KQueueEventLoopGroup -> KQueueServerSocketChannel::class.java
    else -> throw Exception("Unknown event loop group type: ${eventLoopGroup.javaClass.name}")
  }
}

inline fun ChannelFuture.addChannelListener(crossinline listener: (future: ChannelFuture) -> Unit) {
  addListener(GenericFutureListener<ChannelFuture> { listener(it) })
}

// if NIO, so, it is shared and we must not shutdown it
fun EventLoop.shutdownIfOio() {
  @Suppress("DEPRECATION")
  (parent() as? io.netty.channel.oio.OioEventLoopGroup)?.shutdownGracefully(1L, 2L, TimeUnit.NANOSECONDS)
}

// Event loop will be shut downed only if OIO
fun Channel.closeAndShutdownEventLoop() {
  val eventLoop = eventLoop()
  try {
    close().awaitUninterruptibly()
  }
  finally {
    eventLoop.shutdownIfOio()
  }
}

/**
 * Synchronously connects to remote address.
 */
@JvmOverloads
@RequiresBackgroundThread
fun Bootstrap.connectRetrying(remoteAddress: InetSocketAddress,
                              maxAttemptCount: Int = NettyUtil.DEFAULT_CONNECT_ATTEMPT_COUNT,
                              stopCondition: Condition<Void>? = null): ConnectToChannelResult {
  try {
    return doConnect(this, remoteAddress, maxAttemptCount, stopCondition ?: Conditions.alwaysFalse<Void>())
  }
  catch (e: Throwable) {
    return ConnectToChannelResult(e)
  }
}

private fun doConnect(bootstrap: Bootstrap,
                       remoteAddress: InetSocketAddress,
                       maxAttemptCount: Int,
                       stopCondition: Condition<Void>): ConnectToChannelResult {
  var attemptCount = 0
  @Suppress("DEPRECATION")
  if (bootstrap.config().group() !is io.netty.channel.oio.OioEventLoopGroup) {
    return connectNio(bootstrap, remoteAddress, maxAttemptCount, stopCondition, attemptCount)
  }

  bootstrap.validate()

  while (true) {
    try {
      @Suppress("DEPRECATION")
      val channel = io.netty.channel.socket.oio.OioSocketChannel(Socket(remoteAddress.address, remoteAddress.port))
      BootstrapUtil.initAndRegister(channel, bootstrap).sync()
      return ConnectToChannelResult(channel)
    }
    catch (e: IOException) {
      when {
        stopCondition.value(null) -> return ConnectToChannelResult()
        maxAttemptCount == -1 -> {
          sleep(300)?.let {
            return ConnectToChannelResult(it)
          }
          attemptCount++
        }
        ++attemptCount < maxAttemptCount -> {
          sleep(attemptCount * NettyUtil.MIN_START_TIME)?.let {
            return ConnectToChannelResult(it)
          }
        }
        else -> return ConnectToChannelResult(e)
      }
    }
  }
}

private fun connectNio(bootstrap: Bootstrap,
                       remoteAddress: InetSocketAddress,
                       maxAttemptCount: Int,
                       stopCondition: Condition<Void>,
                       _attemptCount: Int): ConnectToChannelResult {
  var attemptCount = _attemptCount
  Logger.getInstance("com.intellij.util.io.netty").debug("connectNio: ${Thread.currentThread()} #$attemptCount, max:#$maxAttemptCount to $remoteAddress")
  while (true) {
    Logger.getInstance("com.intellij.util.io.netty").debug("Connection attempt #$attemptCount to $remoteAddress")
    val future = bootstrap.connect(remoteAddress).awaitUninterruptibly()
    if (future.isSuccess) {
      if (!future.channel().isOpen) {
        Logger.getInstance("com.intellij.util.io.netty").debug("connectNio: !future.channel().isOpen")
      }
      return ConnectToChannelResult(future.channel())
    }
    else if (stopCondition.value(null)) {
      return ConnectToChannelResult()
    }
    else if (maxAttemptCount == -1) {
      sleep(300)?.let {
        return ConnectToChannelResult(it)
      }
      attemptCount++
    }
    else if (++attemptCount < maxAttemptCount) {
      sleep(attemptCount * NettyUtil.MIN_START_TIME)?.let {
        return ConnectToChannelResult(it)
      }
    }
    else {
      @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
      val cause = future.cause()
      if (cause == null) {
        return ConnectToChannelResult(IdeUtilIoBundle.message("error.message.cannot.connect.unknown.error"))
      }
      else {
        return ConnectToChannelResult(cause)
      }
    }
  }
}

private fun sleep(time: Int): String? {
  try {
    //noinspection BusyWait
    Thread.sleep(time.toLong())
  }
  catch (ignored: InterruptedException) {
    return IdeUtilIoBundle.message("error.message.interrupted")
  }

  return null
}

val Channel.uriScheme: String
  @NlsSafe
  get() = if (pipeline().get(SslHandler::class.java) == null) "http" else "https"

val HttpRequest.host: String?
  get() = headers().getAsString(HttpHeaderNames.HOST)

fun getHostName(httpRequest: HttpRequest): String? {
  val hostAndPort = httpRequest.headers().getAsString(HttpHeaderNames.HOST)?.ifBlank { null } ?: return null
  val portIndex = hostAndPort.lastIndexOf(':')
  return if (portIndex > 0) hostAndPort.substring(0, portIndex).ifBlank { null } else hostAndPort
}

val HttpRequest.origin: String?
  get() = headers().getAsString(HttpHeaderNames.ORIGIN)

val HttpRequest.referrer: String?
  get() = headers().getAsString(HttpHeaderNames.REFERER)

val HttpRequest.userAgent: String?
  @NlsSafe
  get() = headers().getAsString(HttpHeaderNames.USER_AGENT)

inline fun <T> ByteBuf.releaseIfError(task: () -> T): T {
  try {
    return task()
  }
  catch (e: Exception) {
    try {
      release()
    }
    finally {
      throw e
    }
  }
}

fun isLocalHost(host: String, onlyAnyOrLoopback: Boolean = true, hostsOnly: Boolean = false): Boolean {
  if (NetUtils.isLocalhost(host)) {
    return true
  }

  // if IP address, it is safe to use getByName (not affected by DNS rebinding)
  if (onlyAnyOrLoopback && !InetAddresses.isInetAddress(host)) {
    return false
  }

  fun InetAddress.isLocal() = isAnyLocalAddress || isLoopbackAddress || NetworkInterface.getByInetAddress(this) != null

  try {
    val address = InetAddress.getByName(host)
    if (!address.isLocal()) {
      return false
    }
    // be aware - on windows hosts file doesn't contain localhost
    // hosts can contain remote addresses, so, we check it
    if (hostsOnly && !InetAddresses.isInetAddress(host)) {
      return io.netty.resolver.HostsFileEntriesResolver.DEFAULT.address(host, ResolvedAddressTypes.IPV4_PREFERRED).let { it != null && it.isLocal() }
    }
    else {
      return true
    }
  }
  catch (ignored: IOException) {
    return false
  }
}

@JvmOverloads
fun HttpRequest.isLocalOrigin(onlyAnyOrLoopback: Boolean = true, hostsOnly: Boolean = false): Boolean {
  return parseAndCheckIsLocalHost(origin, onlyAnyOrLoopback, hostsOnly) && parseAndCheckIsLocalHost(referrer, onlyAnyOrLoopback, hostsOnly)
}

@Suppress("SpellCheckingInspection")
private fun isTrustedChromeExtension(@NlsSafe url: Url): Boolean {
  return url.scheme == "chrome-extension" &&
         (url.authority == "hmhgeddbohgjknpmjagkdomcpobmllji" ||
          url.authority == "offnedcbhjldheanlbojaefbfbllddna" ||
          System.getProperty("idea.trusted.chrome.extension.id")?.equals(url.authority) ?: false
         )
}

private val Url.host: String?
  get() = authority?.let {
    val portIndex = it.indexOf(':')
    if (portIndex > 0) it.substring(0, portIndex) else it
  }

@JvmOverloads
fun parseAndCheckIsLocalHost(@NlsSafe uri: String?, onlyAnyOrLoopback: Boolean = true, hostsOnly: Boolean = false): Boolean {
  if (uri == null || uri == "about:blank") {
    return true
  }

  try {
    val parsedUri = Urls.parse(uri, false) ?: return false
    val host = parsedUri.host
    return host != null && (isTrustedChromeExtension(parsedUri) || isLocalHost(host, onlyAnyOrLoopback, hostsOnly))
  }
  catch (ignored: Exception) {
  }
  return false
}

fun HttpRequest.isRegularBrowser(): Boolean = userAgent?.startsWith("Mozilla/5.0") ?: false

// forbid POST requests from browser without Origin
fun HttpRequest.isWriteFromBrowserWithoutOrigin(): Boolean {
  val method = method()
  return origin.isNullOrEmpty() && isRegularBrowser() && (method == HttpMethod.POST || method == HttpMethod.PATCH || method == HttpMethod.PUT || method == HttpMethod.DELETE)
}

fun ByteBuf.readUtf8(): String = toString(Charsets.UTF_8)

class ConnectToChannelResult {
  val channel: Channel?
  private val message: String?
  private val throwable: Throwable?

  constructor(channel: Channel? = null) : this(channel, null, null)

  constructor(message: String): this(null, message, null)

  constructor(error: Throwable) : this(null, null, error)

  private constructor(channel: Channel?,  message: String?, throwable: Throwable?) {
    this.channel = channel
    this.message = message
    this.throwable = throwable
  }

  fun handleError(consumer: Consumer<String>) : ConnectToChannelResult {
    if (message != null) {
      consumer.accept(message)
    }
    return this
  }

  fun handleThrowable(consumer: Consumer<Throwable>) : ConnectToChannelResult {
    if (throwable != null) {
      consumer.accept(throwable)
    }
    return this
  }
}