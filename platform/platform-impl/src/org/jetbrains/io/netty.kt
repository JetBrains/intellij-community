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
package org.jetbrains.io

import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Conditions
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.oio.OioEventLoopGroup
import io.netty.channel.socket.oio.OioSocketChannel
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.ssl.SslHandler
import io.netty.util.concurrent.GenericFutureListener
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.ide.PooledThreadExecutor
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

inline fun Bootstrap.handler(crossinline task: (Channel) -> Unit): Bootstrap {
  handler(object : ChannelInitializer<Channel>() {
    override fun initChannel(channel: Channel) {
      task(channel)
    }
  })
  return this
}

fun oioClientBootstrap(): Bootstrap {
  val bootstrap = Bootstrap().group(OioEventLoopGroup(1, PooledThreadExecutor.INSTANCE)).channel(OioSocketChannel::class.java)
  bootstrap.option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.SO_KEEPALIVE, true)
  return bootstrap
}

inline fun ChannelFuture.addListener(crossinline listener: (future: ChannelFuture) -> Unit) {
  addListener(GenericFutureListener<io.netty.channel.ChannelFuture> { future -> listener(future) })
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

val HttpRequest.host: String
  get() = headers().getAsString(HttpHeaderNames.HOST)