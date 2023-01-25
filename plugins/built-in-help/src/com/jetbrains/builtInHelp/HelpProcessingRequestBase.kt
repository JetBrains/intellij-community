// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder

abstract class HelpProcessingRequestBase : HelpRequestHandlerBase() {
  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {

    val num: String? = urlDecoder.parameters()["num"]?.get(0)

    val dataToSend = getProcessedData(urlDecoder.parameters()["q"]?.get(0)!!,
                                      if (num != null) Integer.valueOf(num) else 100).toByteArray(Charsets.UTF_8)

    return true
  }

  abstract fun getProcessedData(query: String, maxHits: Int): String
}