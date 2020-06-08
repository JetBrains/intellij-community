// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.io.getHostName
import com.intellij.util.io.isLocalHost
import com.intellij.util.io.isLocalOrigin
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.netty.handler.stream.ChunkedStream
import org.jetbrains.io.FileResponses
import org.jetbrains.io.addCommonHeaders
import org.jetbrains.io.addKeepAliveIfNeeded
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.*

abstract class HttpRequestHandler {
  companion object {
    // Your handler will be instantiated on first user request
    val EP_NAME = ExtensionPointName<HttpRequestHandler>("com.intellij.httpRequestHandler")

    @JvmStatic
    fun checkPrefix(uri: String, prefix: String): Boolean {
      if (uri.length > prefix.length && uri[0] == '/' && uri.regionMatches(1, prefix, 0, prefix.length, ignoreCase = true)) {
        if (uri.length - prefix.length == 1) {
          return true
        }
        else {
          val c = uri.get(prefix.length + 1)
          return c == '/' || c == '?'
        }
      }
      return false
    }
  }

  /**
   * Write request from browser without Origin will be always blocked regardless of your implementation.
   */
  @SuppressWarnings("SpellCheckingInspection")
  open fun isAccessible(request: HttpRequest): Boolean {
    val hostName = getHostName(request)
    // If attacker.com DNS rebound to 127.0.0.1 and user open site directly - no Origin or Referrer headers.
    // So we should check Host header.
    return hostName != null && request.isLocalOrigin() && isLocalHost(hostName)
  }

  open fun isSupported(request: FullHttpRequest): Boolean {
    return request.method() === HttpMethod.GET || request.method() === HttpMethod.HEAD
  }

  /**
   * @return true if processed successfully, false to pass processing to other handlers.
   */
  @Throws(IOException::class)
  abstract fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean

  protected fun sendData(content: ByteArray, name: String, request: FullHttpRequest, channel: Channel, extraHeaders: HttpHeaders): Boolean {
    val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, FileResponses.getContentType(name))
    response.addCommonHeaders()
    response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, must-revalidate")
    response.headers().set(HttpHeaderNames.LAST_MODIFIED, Date(Calendar.getInstance().timeInMillis))
    response.headers().add(extraHeaders)

    val keepAlive = response.addKeepAliveIfNeeded(request)
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
