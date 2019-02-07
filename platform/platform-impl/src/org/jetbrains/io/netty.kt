// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io

import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.oio.OioEventLoopGroup
import io.netty.channel.socket.oio.OioSocketChannel
import org.jetbrains.ide.PooledThreadExecutor

// used in Go
@Deprecated(level = DeprecationLevel.ERROR, message = "Prefer to use NIO")
fun oioClientBootstrap(): Bootstrap {
  val bootstrap = Bootstrap().group(OioEventLoopGroup(1, PooledThreadExecutor.INSTANCE)).channel(OioSocketChannel::class.java)
  bootstrap.option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.SO_KEEPALIVE, true)
  return bootstrap
}