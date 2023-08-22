// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io

import com.intellij.util.io.isWriteFromBrowserWithoutOrigin
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.util.AttributeKey
import org.jetbrains.ide.HttpRequestHandler
import java.lang.ref.WeakReference

private val PREV_HANDLER = AttributeKey.valueOf<WeakReference<HttpRequestHandler>>("DelegatingHttpRequestHandler.handler")

@ChannelHandler.Sharable
internal class DelegatingHttpRequestHandler : DelegatingHttpRequestHandlerBase() {
  override fun process(context: ChannelHandlerContext,
                       request: FullHttpRequest,
                       urlDecoder: QueryStringDecoder): Boolean {
    fun HttpRequestHandler.checkAndProcess(): Boolean {
      return isSupported(request) && !request.isWriteFromBrowserWithoutOrigin() && isAccessible(request) && process(urlDecoder, request, context)
    }

    val prevHandlerAttribute = context.channel().attr(PREV_HANDLER)
    val connectedHandler = prevHandlerAttribute.get()?.get()
    if (connectedHandler != null) {
      if (connectedHandler.checkAndProcess()) {
        return true
      }
      // prev cached connectedHandler is not suitable for this request, so, let's find it again
      prevHandlerAttribute.set(null)
    }

    return HttpRequestHandler.EP_NAME.findFirstSafe { handler ->
      if (handler.checkAndProcess()) {
        prevHandlerAttribute.set(WeakReference(handler))
        true
      }
      else {
        false
      }
    } != null
  }

  @Suppress("OverridingDeprecatedMember")
  override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
    try {
      context.channel().attr(PREV_HANDLER).set(null)
    }
    finally {
      @Suppress("DEPRECATION")
      super.exceptionCaught(context, cause)
    }
  }
}