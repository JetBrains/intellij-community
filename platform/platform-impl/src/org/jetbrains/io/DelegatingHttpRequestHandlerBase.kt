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

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder

internal abstract class DelegatingHttpRequestHandlerBase : SimpleChannelInboundHandlerAdapter<FullHttpRequest>() {
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