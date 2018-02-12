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
package org.jetbrains.io

import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.IconLoader
import com.intellij.util.io.isWriteFromBrowserWithoutOrigin
import com.intellij.util.ui.UIUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.util.AttributeKey
import org.apache.commons.imaging.ImageFormats
import org.apache.commons.imaging.Imaging
import org.jetbrains.ide.HttpRequestHandler
import java.awt.image.BufferedImage

private val PREV_HANDLER = AttributeKey.valueOf<HttpRequestHandler>("DelegatingHttpRequestHandler.handler")

@ChannelHandler.Sharable
internal class DelegatingHttpRequestHandler : DelegatingHttpRequestHandlerBase() {
  override fun process(context: ChannelHandlerContext,
                       request: FullHttpRequest,
                       urlDecoder: QueryStringDecoder): Boolean {
    fun HttpRequestHandler.checkAndProcess(): Boolean {
      return isSupported(request) && !request.isWriteFromBrowserWithoutOrigin() && isAccessible(request) && process(urlDecoder, request, context)
    }

    val prevHandlerAttribute = context.channel().attr(PREV_HANDLER)
    val connectedHandler = prevHandlerAttribute.get()
    if (connectedHandler != null) {
      if (connectedHandler.checkAndProcess()) {
        return true
      }
      // prev cached connectedHandler is not suitable for this request, so, let's find it again
      prevHandlerAttribute.set(null)
    }

    for (handler in HttpRequestHandler.EP_NAME.extensions) {
      try {
        if (handler.checkAndProcess()) {
          prevHandlerAttribute.set(handler)
          return true
        }
      }
      catch (e: Throwable) {
        Logger.getInstance(BuiltInServer::class.java).error(e)
      }
    }

    if (urlDecoder.path() == "/favicon.ico") {
      val icon = IconLoader.findIcon(ApplicationInfoEx.getInstanceEx().smallIconUrl)
      if (icon != null) {
        val image = UIUtil.createImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
        icon.paintIcon(null, image.graphics, 0, 0)
        val icoBytes = Imaging.writeImageToBytes(image, ImageFormats.ICO, null)
        response(FileResponses.getContentType(urlDecoder.path()), Unpooled.wrappedBuffer(icoBytes))
          .addNoCache()
          .send(context.channel(), request)
        return true
      }
    }

    return false
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