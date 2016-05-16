/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.io

import com.google.common.net.InetAddresses
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Conditions
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.net.NetUtils
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.oio.OioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.oio.OioServerSocketChannel
import io.netty.channel.socket.oio.OioSocketChannel
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.ssl.SslHandler
import io.netty.resolver.HostsFileEntriesResolver
import io.netty.util.concurrent.GenericFutureListener
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.ide.PooledThreadExecutor
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

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
    .channel(if (group is NioEventLoopGroup) NioServerSocketChannel::class.java else OioServerSocketChannel::class.java)
  bootstrap.childOption(ChannelOption.TCP_NODELAY, true).childOption(ChannelOption.SO_KEEPALIVE, true)
  return bootstrap
}

fun oioClientBootstrap(): Bootstrap {
  val bootstrap = Bootstrap().group(OioEventLoopGroup(1, PooledThreadExecutor.INSTANCE)).channel(OioSocketChannel::class.java)
  bootstrap.option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.SO_KEEPALIVE, true)
  return bootstrap
}

inline fun ChannelFuture.addChannelListener(crossinline listener: (future: ChannelFuture) -> Unit) {
  addListener(GenericFutureListener<io.netty.channel.ChannelFuture> { listener(it) })
}

// if NIO, so, it is shared and we must not shutdown it
fun EventLoop.shutdownIfOio() {
  if (this is OioEventLoopGroup) {
    @Suppress("USELESS_CAST")
    (this as OioEventLoopGroup).shutdownGracefully(1L, 2L, TimeUnit.NANOSECONDS)
  }
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

@JvmOverloads
fun Bootstrap.connect(remoteAddress: InetSocketAddress, promise: AsyncPromise<*>? = null, maxAttemptCount: Int = NettyUtil.DEFAULT_CONNECT_ATTEMPT_COUNT, stopCondition: Condition<Void>? = null): Channel? {
  try {
    return NettyUtil.doConnect(this, remoteAddress, promise, maxAttemptCount, stopCondition ?: Conditions.alwaysFalse<Void>())
  }
  catch (e: Throwable) {
    promise?.setError(e)
    return null
  }
}

val Channel.uriScheme: String
  get() = if (pipeline().get(SslHandler::class.java) == null) "http" else "https"

val HttpRequest.host: String?
  get() = headers().getAsString(HttpHeaderNames.HOST)

val HttpRequest.origin: String?
  get() = headers().getAsString(HttpHeaderNames.ORIGIN)

val HttpRequest.referrer: String?
  get() = headers().getAsString(HttpHeaderNames.REFERER)

val HttpRequest.userAgent: String?
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

fun isLocalHost(host: String, onlyAnyOrLoopback: Boolean, hostsOnly: Boolean = false): Boolean {
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
      return HostsFileEntriesResolver.DEFAULT.address(host).let { it != null && it.isLocal() }
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
fun HttpRequest.isLocalOrigin(onlyAnyOrLoopback: Boolean = true, hostsOnly: Boolean = false) = parseAndCheckIsLocalHost(origin, onlyAnyOrLoopback, hostsOnly) && parseAndCheckIsLocalHost(referrer, onlyAnyOrLoopback, hostsOnly)

private fun isTrustedChromeExtension(url: Url): Boolean {
  return url.scheme == "chrome-extension" && (url.authority == "hmhgeddbohgjknpmjagkdomcpobmllji" || url.authority == "offnedcbhjldheanlbojaefbfbllddna")
}

private val Url.host: String?
  get() = authority?.let {
    val portIndex = it.indexOf(':')
    if (portIndex > 0) it.substring(0, portIndex) else it
  }

@JvmOverloads
fun parseAndCheckIsLocalHost(uri: String?, onlyAnyOrLoopback: Boolean = true, hostsOnly: Boolean = false): Boolean {
  if (uri == null) {
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

fun HttpRequest.isRegularBrowser() = userAgent?.startsWith("Mozilla/5.0") ?: false

// forbid POST requests from browser without Origin
fun HttpRequest.isWriteFromBrowserWithoutOrigin(): Boolean {
  val method = method()
  return origin.isNullOrEmpty() && isRegularBrowser() && (method == HttpMethod.POST || method == HttpMethod.PATCH || method == HttpMethod.PUT || method == HttpMethod.DELETE)
}