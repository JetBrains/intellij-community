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
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.ssl.SslHandler
import io.netty.util.concurrent.GenericFutureListener
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.ide.PooledThreadExecutor
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URI
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
  @Suppress("RedundantSamConstructor")
  addListener(GenericFutureListener<ChannelFuture> { listener(it) })
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

fun isLocalHost(host: String, onlyAnyOrLoopback: Boolean): Boolean {
  if (onlyAnyOrLoopback) {
    if (NetUtils.isLocalhost(host)) {
      return true
    }

    if (!InetAddresses.isInetAddress(host)) {
      return false
    }
    // if IP address, it is safe to use getByName (not affected by DNS rebinding)
  }

  try {
    val address = InetAddress.getByName(host)
    return address.isAnyLocalAddress || address.isLoopbackAddress || NetworkInterface.getByInetAddress(address) != null
  }
  catch (ignored: IOException) {
    return false
  }
}

@JvmOverloads
fun HttpRequest.isLocalOrigin(onlyAnyOrLoopback: Boolean = true) = parseAndCheckIsLocalHost(origin, onlyAnyOrLoopback) && parseAndCheckIsLocalHost(referrer, onlyAnyOrLoopback)

private fun isTrustedChromeExtension(uri: URI): Boolean {
  return uri.scheme == "chrome-extension" && (uri.host == "hmhgeddbohgjknpmjagkdomcpobmllji" || uri.host == "offnedcbhjldheanlbojaefbfbllddna")
}

@JvmOverloads
fun parseAndCheckIsLocalHost(uri: String?, onlyAnyOrLoopback: Boolean = true): Boolean {
  if (uri == null) {
    return true
  }

  try {
    val parsedUri = URI(uri)
    return isTrustedChromeExtension(parsedUri) || isLocalHost(parsedUri.host, onlyAnyOrLoopback)
  }
  catch (ignored: Exception) {
  }
  return false
}