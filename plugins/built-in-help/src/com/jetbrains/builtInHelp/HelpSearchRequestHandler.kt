// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp

import com.jetbrains.builtInHelp.search.HelpSearch
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.annotations.NonNls

@Suppress("unused")
class HelpSearchRequestHandler : HelpRequestHandlerBase() {
  @NonNls
  override val prefix: String = "/search/"
  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {

    val query = urlDecoder.parameters()["query"]?.get(0)
    val maxHitsParam = urlDecoder.parameters()["maxHits"]?.get(0)

    sendData(HelpSearch.search(query, if (maxHitsParam != null) Integer.parseInt(maxHitsParam) else 100).encodeToByteArray(),
             "data.json",
             request,
             context.channel(),
             request.headers())

    return true
  }
}