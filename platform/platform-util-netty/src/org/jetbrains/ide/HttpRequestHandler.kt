// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet")

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

/**
 * See [Remote Communication](https://youtrack.jetbrains.com/articles/IDEA-A-63/Remote-Communication)
 */
abstract class HttpRequestHandler {
  enum class OriginCheckResult {
    ALLOW, FORBID,
    // any origin is allowed but user confirmation is required
    ASK_CONFIRMATION
  }

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
   * Write request from browser without Origin will always be blocked regardless of your implementation.
   */
  @SuppressWarnings("SpellCheckingInspection")
  open fun isAccessible(request: HttpRequest): Boolean {
    val hostName = getHostName(request)
    // If attacker.com DNS rebound to 127.0.0.1 and user open site directly - no Origin or Referrer headers.
    // So we should check Host header.
    return hostName != null && isOriginAllowed(request) != OriginCheckResult.FORBID && isLocalHost(hostName)
  }

  protected open fun isOriginAllowed(request: HttpRequest): OriginCheckResult {
    return if (request.isLocalOrigin()) OriginCheckResult.ALLOW else OriginCheckResult.FORBID
  }

  /**
   * Note that changes of [request] object in methods that overrides [isSupported] are highly undesirable. The same mutable [request] object
   * (as [FullHttpRequest] is mutable) is passed to every registered [HttpRequestHandler] until the first handler that accepts the request.
   * If one [isSupported] method changes [request] then other services in the chain are affected by this change and might function
   * improperly.
   */
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
    response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, must-revalidate") //NON-NLS
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