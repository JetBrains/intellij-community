// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder

abstract class DelegatingHttpRequestHandlerBase : SimpleChannelInboundHandlerAdapter<FullHttpRequest>() {
  override fun messageReceived(context: ChannelHandlerContext, message: FullHttpRequest) {
    Logger.getInstance(BuiltInServer::class.java).debug { "\n\nIN HTTP: $message\n\n" }

    if (!process(context, message, QueryStringDecoder(message.uri()))) {
      HttpResponseStatus.NOT_FOUND.send(context.channel(), message)
    }
  }

  protected abstract fun process(context: ChannelHandlerContext,
                                 request: FullHttpRequest,
                                 urlDecoder: QueryStringDecoder): Boolean

  override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
    NettyUtil.logAndClose(cause, Logger.getInstance(BuiltInServer::class.java), context.channel())
  }
}