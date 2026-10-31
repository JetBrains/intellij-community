// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp

import com.jetbrains.builtInHelp.search.HelpSearch
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.annotations.NonNls
import org.jetbrains.io.send

@Suppress("unused")
class HelpSearchRequestHandler : HelpRequestHandlerBase() {
  @NonNls
  override val prefix: String = "/search/"

  override fun process(
    urlDecoder: QueryStringDecoder,
    request: FullHttpRequest,
    context: ChannelHandlerContext,
  ): Boolean {

    val query = urlDecoder.parameters()["query"]?.firstOrNull()
    val maxHitsParam = urlDecoder.parameters()["maxHits"]?.firstOrNull()
    val maxHits = maxHitsParam?.toIntOrNull() ?: if (maxHitsParam == null) 100 else 0
    if (maxHits !in 1..1000) {
      HttpResponseStatus.BAD_REQUEST.send(context.channel(), request)
      return true
    }

    HelpRequestExecutor.getInstance().execute(request, context) { retainedRequest ->
      sendData(
        HelpSearch.search(query, maxHits, getRequestLocalePath(retainedRequest)).encodeToByteArray(),
        "data.json",
        retainedRequest,
        context.channel(),
        DefaultHttpHeaders())
    }

    return true
  }
}
