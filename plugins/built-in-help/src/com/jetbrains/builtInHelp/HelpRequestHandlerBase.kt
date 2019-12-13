// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp

import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.handler.codec.http.*
import io.netty.handler.stream.ChunkedStream
import org.apache.commons.compress.utils.IOUtils
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.FileResponses
import org.jetbrains.io.addCommonHeaders
import org.jetbrains.io.addKeepAliveIfNeed
import java.io.ByteArrayInputStream
import java.util.*

/**
 * Created by Egor.Malyshev on 7/13/2017.
 */
abstract class HelpRequestHandlerBase : HttpRequestHandler() {
  open val MY_PREFIX: String = "/help/"

  override fun isAccessible(request: HttpRequest): Boolean {
    return super.isAccessible(request) && request.uri().startsWith(MY_PREFIX)
  }

  protected fun sendResource(resourceName: String, request: FullHttpRequest, channel: Channel, extraHeaders: HttpHeaders): Boolean {

    return sendData(IOUtils.toByteArray(HelpRequestHandlerBase::class.java.getResourceAsStream(
      (if (request.uri().contains("/img/")) "/images/" else "/topics/") + resourceName) ?: throw Exception("$resourceName not found")),
                    resourceName, request, channel, extraHeaders)
  }

  protected fun sendData(content: ByteArray, name: String, request: FullHttpRequest, channel: Channel, extraHeaders: HttpHeaders): Boolean {

    val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, FileResponses.getContentType(name))
    response.addCommonHeaders()
    response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, must-revalidate")
    response.headers().set(HttpHeaderNames.LAST_MODIFIED, Date(Calendar.getInstance().timeInMillis))
    response.headers().add(extraHeaders)

    val keepAlive = response.addKeepAliveIfNeed(request)
    if (request.method() != HttpMethod.HEAD) {
      HttpUtil.setContentLength(response, content.size.toLong())
    }

    channel.write(response)

    if (request.method() != HttpMethod.HEAD) {
      val stream = ByteArrayInputStream(content)
      channel.write(ChunkedStream(stream))
      stream.close()
    }

    val future = channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
    if (!keepAlive) {
      future.addListener(ChannelFutureListener.CLOSE)
    }
    return true
  }
}
