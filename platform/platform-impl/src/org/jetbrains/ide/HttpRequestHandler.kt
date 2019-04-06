// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.io.hostName
import com.intellij.util.io.isLocalHost
import com.intellij.util.io.isLocalOrigin
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import java.io.IOException

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
    val hostName = request.hostName
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
}