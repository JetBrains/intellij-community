// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder

abstract class HelpProcessingRequestBase : HelpRequestHandlerBase() {
  abstract override val prefix: String

  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {

    val query = urlDecoder.parameters()["q"]

    if (query.isNullOrEmpty()) return false

    val num = try {
      Integer.parseInt(urlDecoder.parameters()["num"]?.get(0))
    }
    catch (ne: Exception) {
      100
    }

    val dataToSend = getProcessedData(query[0], num).toByteArray(Charsets.UTF_8)

    sendData(dataToSend, "data.json", request, context.channel(), request.headers())
    return true
  }

  abstract fun getProcessedData(query: String, maxHits: Int): String
}