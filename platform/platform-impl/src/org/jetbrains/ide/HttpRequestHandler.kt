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
package org.jetbrains.ide

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.io.host
import com.intellij.util.io.isLocalOrigin
import com.intellij.util.io.parseAndCheckIsLocalHost
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import java.io.IOException

abstract class HttpRequestHandler {
  companion object {
    // Your handler will be instantiated on first user request
    val EP_NAME: ExtensionPointName<HttpRequestHandler> = ExtensionPointName.create<HttpRequestHandler>("com.intellij.httpRequestHandler")

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
    val host = request.host
    // If attacker.com DNS rebound to 127.0.0.1 and user open site directly - no Origin or Referrer headers.
    // So we should check Host header.
    return host != null && request.isLocalOrigin() && parseAndCheckIsLocalHost("http://$host")
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