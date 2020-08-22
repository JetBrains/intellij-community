// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.PlatformUtils
import com.intellij.util.io.serverBootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.util.concurrent.FastThreadLocalThread
import io.netty.util.internal.logging.InternalLoggerFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

class BuiltInServer private constructor(val eventLoopGroup: EventLoopGroup, val port: Int, private val channelRegistrar: ChannelRegistrar) : Disposable {
  val isRunning: Boolean
    get() = !channelRegistrar.isEmpty

  companion object {
    init {
      // IDEA-120811
      if (System.getProperty("io.netty.random.id", "true")!!.toBoolean()) {
        System.setProperty("io.netty.machineId", "28:f0:76:ff:fe:16:65:0e")
        System.setProperty("io.netty.processId", Random().nextInt(65535).toString())
      }

      System.setProperty("io.netty.serviceThreadPrefix", "Netty ")

      // https://youtrack.jetbrains.com/issue/IDEA-208908
      setSystemPropertyIfNotConfigured("io.netty.allocator.numDirectArenas", "1")
      setSystemPropertyIfNotConfigured("io.netty.allocator.numHeapArenas", "1")
      setSystemPropertyIfNotConfigured("io.netty.allocator.useCacheForAllThreads", "false")
      setSystemPropertyIfNotConfigured("io.netty.allocator.cacheTrimIntervalMillis", "600000")

      val logger = IdeaNettyLogger()
      InternalLoggerFactory.setDefaultFactory(object : InternalLoggerFactory() {
        override fun newInstance(name: String) = logger
      })
    }

    private fun setSystemPropertyIfNotConfigured(name: String, @Suppress("SameParameterValue") value: String) {
      if (System.getProperty(name) == null) {
        System.setProperty(name, value)
      }
    }

    @JvmStatic
    fun start(firstPort: Int, portsCount: Int, handler: (Supplier<ChannelHandler>)? = null): BuiltInServer {
      val eventLoopGroup = multiThreadEventLoopGroup(if (PlatformUtils.isIdeaCommunity()) 2 else 3, BuiltInServerThreadFactory())
      return start(eventLoopGroup, true, firstPort, portsCount, tryAnyPort = false, handler = handler)
    }

    fun start(eventLoopGroup: EventLoopGroup,
              isEventLoopGroupOwner: Boolean,
              firstPort: Int,
              portsCount: Int,
              tryAnyPort: Boolean,
              handler: (Supplier<ChannelHandler>)? = null): BuiltInServer {
      val channelRegistrar = ChannelRegistrar()
      val bootstrap = serverBootstrap(eventLoopGroup)
      configureChildHandler(bootstrap, channelRegistrar, handler)
      val port = bind(firstPort, portsCount, tryAnyPort, bootstrap, channelRegistrar, isEventLoopGroupOwner)
      return BuiltInServer(eventLoopGroup, port, channelRegistrar)
    }

    @JvmStatic
    fun configureChildHandler(bootstrap: ServerBootstrap, channelRegistrar: ChannelRegistrar, channelHandler: (Supplier<ChannelHandler>)?) {
      val portUnificationServerHandler = if (channelHandler == null) PortUnificationServerHandler() else null
      bootstrap.childHandler(object : ChannelInitializer<Channel>(), ChannelHandler {
        override fun initChannel(channel: Channel) {
          channel.pipeline().addLast(channelRegistrar, channelHandler?.get() ?: portUnificationServerHandler)
        }
      })
    }

    private fun bind(firstPort: Int,
                     portsCount: Int,
                     tryAnyPort: Boolean,
                     bootstrap: ServerBootstrap,
                     channelRegistrar: ChannelRegistrar,
                     isEventLoopGroupOwner: Boolean): Int {
      val address = InetAddress.getLoopbackAddress()
      val maxPort = (firstPort + portsCount) - 1
      for (port in firstPort..maxPort) {
        // some antiviral software detect viruses by the fact of accessing these ports so we should not touch them to appear innocent
        if (port == 6953 || port == 6969 || port == 6970) {
          continue
        }

        val future = bootstrap.bind(address, port).awaitUninterruptibly()
        if (future.isSuccess) {
          channelRegistrar.setServerChannel(future.channel(), isEventLoopGroupOwner)
          return port
        }
        else if (!tryAnyPort && port == maxPort) {
          throw future.cause()
        }
      }

      logger<BuiltInServer>().info("Cannot bind to our default range, so, try to bind to any free port")
      val future = bootstrap.bind(address, 0).awaitUninterruptibly()
      if (future.isSuccess) {
        channelRegistrar.setServerChannel(future.channel(), isEventLoopGroupOwner)
        return (future.channel().localAddress() as InetSocketAddress).port
      }
      throw future.cause()
    }

    @JvmStatic
    fun replaceDefaultHandler(context: ChannelHandlerContext, channelHandler: ChannelHandler) {
      context.pipeline().replace(DelegatingHttpRequestHandler::class.java, "replacedDefaultHandler", channelHandler)
    }
  }

  override fun dispose() {
    channelRegistrar.close()
    logger<BuiltInServer>().info("web server stopped")
  }
}

private class BuiltInServerThreadFactory : ThreadFactory {
  private val counter = AtomicInteger()

  override fun newThread(r: Runnable): Thread {
    return FastThreadLocalThread(r, "Netty Builtin Server " + counter.incrementAndGet())
  }
}

private fun multiThreadEventLoopGroup(workerCount: Int, threadFactory: ThreadFactory): MultithreadEventLoopGroup {
//  if (SystemInfo.isMacOSSierra && SystemProperties.getBooleanProperty("native.net.io", false)) {
//    try {
//      return KQueueEventLoopGroup(workerCount, threadFactory)
//    }
//    catch (e: Throwable) {
//      logger<BuiltInServer>().warn("Cannot use native event loop group", e)
//    }
//  }

  return NioEventLoopGroup(workerCount, threadFactory)
}