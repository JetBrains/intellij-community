// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder

abstract class HelpProcessingRequestBase : HelpRequestHandlerBase() {
  abstract override val prefix: String

  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {

    val num: String? = urlDecoder.parameters()["num"]?.get(0)

    val dataToSend = getProcessedData(urlDecoder.parameters()["q"]?.get(0)!!,
                                      if (num != null) Integer.valueOf(num) else 100).toByteArray(Charsets.UTF_8)

    sendData(dataToSend, "data.json", request, context.channel(), request.headers())
    return true
  }

  abstract fun getProcessedData(query: String, maxHits: Int): String
}