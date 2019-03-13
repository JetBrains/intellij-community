// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ArrayUtil
import com.intellij.util.ExceptionUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.SystemProperties
import com.intellij.util.io.MultiThreadEventLoopGroup
import com.intellij.util.io.serverBootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

// Some antiviral software detect viruses by the fact of accessing these ports so we should not touch them to appear innocent.
private val FORBIDDEN_PORTS = intArrayOf(6953, 6969, 6970)

class BuiltInServer private constructor(val eventLoopGroup: EventLoopGroup, val port: Int, private val channelRegistrar: ChannelRegistrar) : Disposable {
  val isRunning: Boolean
    get() = !channelRegistrar.isEmpty

  companion object {
    init {
      // IDEA-120811
      if (SystemProperties.getBooleanProperty("io.netty.random.id", true)) {
        System.setProperty("io.netty.machineId", "28:f0:76:ff:fe:16:65:0e")
        System.setProperty("io.netty.processId", Integer.toString(Random().nextInt(65535)))
        System.setProperty("io.netty.serviceThreadPrefix", "Netty ")

        // https://youtrack.jetbrains.com/issue/IDEA-208908
        val numArenas = if (PlatformUtils.isIdeaCommunity()) "1" else "2"
        System.setProperty("io.netty.allocator.numDirectArenas", numArenas)
        System.setProperty("io.netty.allocator.numHeapArenas", numArenas)
      }
    }

    @Throws(Exception::class)
    fun start(workerCount: Int, firstPort: Int, portsCount: Int, tryAnyPort: Boolean = false, handler: (() -> ChannelHandler)? = null): BuiltInServer {
      return start(MultiThreadEventLoopGroup(workerCount, BuiltInServerThreadFactory()), true, firstPort, portsCount, tryAnyPort, handler)
    }

    @Throws(Exception::class)
    @JvmStatic
    fun startNioOrOio(workerCount: Int, firstPort: Int, portsCount: Int, tryAnyPort: Boolean, handler: (() -> ChannelHandler)?): BuiltInServer {
      val threadFactory = BuiltInServerThreadFactory()
      val loopGroup: EventLoopGroup = try {
        MultiThreadEventLoopGroup(workerCount, threadFactory)
      }
      catch (e: IllegalStateException) {
        logger<BuiltInServer>().warn(e)
        @Suppress("DEPRECATION")
        io.netty.channel.oio.OioEventLoopGroup(1, threadFactory)
      }

      return start(loopGroup, true, firstPort, portsCount, tryAnyPort, handler)
    }

    @Throws(Exception::class)
    fun start(eventLoopGroup: EventLoopGroup,
              isEventLoopGroupOwner: Boolean,
              firstPort: Int,
              portsCount: Int,
              tryAnyPort: Boolean,
              handler: (() -> ChannelHandler)?): BuiltInServer {
      val channelRegistrar = ChannelRegistrar()
      val bootstrap = serverBootstrap(eventLoopGroup)
      configureChildHandler(bootstrap, channelRegistrar, handler)
      val port = bind(firstPort, portsCount, tryAnyPort, bootstrap, channelRegistrar, isEventLoopGroupOwner)
      return BuiltInServer(eventLoopGroup, port, channelRegistrar)
    }

    @JvmStatic
    fun configureChildHandler(bootstrap: ServerBootstrap, channelRegistrar: ChannelRegistrar, channelHandler: (() -> ChannelHandler)?) {
      val portUnificationServerHandler = if (channelHandler == null) PortUnificationServerHandler() else null
      bootstrap.childHandler(object : ChannelInitializer<Channel>(), ChannelHandler {
        override fun initChannel(channel: Channel) {
          channel.pipeline().addLast(channelRegistrar, channelHandler?.invoke() ?: portUnificationServerHandler)
        }
      })
    }

    @Throws(Exception::class)
    private fun bind(firstPort: Int,
                     portsCount: Int,
                     tryAnyPort: Boolean,
                     bootstrap: ServerBootstrap,
                     channelRegistrar: ChannelRegistrar,
                     isEventLoopGroupOwner: Boolean): Int {
      val address = InetAddress.getLoopbackAddress()

      for (i in 0 until portsCount) {
        val port = firstPort + i

        if (ArrayUtil.indexOf(FORBIDDEN_PORTS, i) >= 0) {
          continue
        }

        val future = bootstrap.bind(address, port).awaitUninterruptibly()
        if (future.isSuccess) {
          channelRegistrar.setServerChannel(future.channel(), isEventLoopGroupOwner)
          return port
        }
        else if (!tryAnyPort && i == portsCount - 1) {
          ExceptionUtil.rethrowAll(future.cause())
        }
      }

      logger<BuiltInServer>().info("We cannot bind to our default range, so, try to bind to any free port")
      val future = bootstrap.bind(address, 0).awaitUninterruptibly()
      if (future.isSuccess) {
        channelRegistrar.setServerChannel(future.channel(), isEventLoopGroupOwner)
        return (future.channel().localAddress() as InetSocketAddress).port
      }
      ExceptionUtil.rethrowAll(future.cause())
      // unreachable
      return -1
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
    return Thread(r, "Netty Builtin Server " + counter.incrementAndGet())
  }
}