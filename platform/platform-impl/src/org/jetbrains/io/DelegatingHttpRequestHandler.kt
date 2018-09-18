// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io

import com.google.common.base.Strings
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.IconLoader
import com.intellij.util.io.isWriteFromBrowserWithoutOrigin
import com.intellij.util.Base64
import com.intellij.util.BuiltinWebServerAccess
import com.intellij.util.ui.UIUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.netty.util.AttributeKey
import org.apache.commons.imaging.ImageFormats
import org.apache.commons.imaging.Imaging
import org.jetbrains.ide.HttpRequestHandler
import java.awt.image.BufferedImage
import java.io.IOException

private val PREV_HANDLER = AttributeKey.valueOf<HttpRequestHandler>("DelegatingHttpRequestHandler.handler")
private val LOG = Logger.getInstance(DelegatingHttpRequestHandler::class.java)

@ChannelHandler.Sharable
internal class DelegatingHttpRequestHandler : DelegatingHttpRequestHandlerBase() {
  override fun process(context: ChannelHandlerContext,
                       request: FullHttpRequest,
                       urlDecoder: QueryStringDecoder): Boolean {
    var urlDecoder = urlDecoder

    // authenticateAndUpdateUrlIfNeeded enforces authentication of all requests of the webserver.
    // See the method's declaration for important details.
    if (!authenticateAndUpdateUrlIfNeeded(request)) {
      val product = ApplicationInfoEx.getInstance().versionName
      val headers : HttpHeaders = DefaultHttpHeaders();
      headers.add(HttpHeaderNames.WWW_AUTHENTICATE.toString(), String.format("Basic realm=\"%s\"", product))
      HttpResponseStatus.UNAUTHORIZED.send(context.channel(), request, null, headers)
      return true
    }
    // authenticateAndUpdateUrlIfNeeded might have updated the url in request.uri(), update urlDecoder accordingly.
    urlDecoder = QueryStringDecoder(request.uri())

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

    for (handler in HttpRequestHandler.EP_NAME.extensionList) {
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

  /**
   * authenticateAndUpdateUrlIfNeeded provides security to the requests by enforcing authentication.
   * Authentication can be done in two ways, both ways use a token as read from [BuiltinWebServerAccess]:
   *
   *  1. Basic authentication, with user: _token_ and password the token from above
   *  1. Url prefixing in the form of /&lt;token&gt;/original_url>
   *

   * The latter method is to support browser access as some browsers (Internet Explorer) do not support the http://user:pass@host scheme.
   * In that case we allow browser to log in with the token in the path. authenticateAndUpdateUrlIfNeeded will in that case update
   * the url in the FullHttpRequest to strip the token, so that delegated Handlers do not have to deal with the token.
   */
  private fun authenticateAndUpdateUrlIfNeeded(request: FullHttpRequest): Boolean {
    // We always allow access to favicon.ico, avoiding the browser will prompt for authentication.
    if (request.uri() == "/favicon.ico") {
      return true
    }
    var expectedToken: String? = null
    try {
      expectedToken = BuiltinWebServerAccess.getUserAuthenticationToken()
    }
    catch (e: IOException) {
      LOG.error("Unable to read user authentication token", e)
      return false
    }

    val receivedToken = getTokenFromAuthorization(request)
    if (receivedToken != null) {
      return expectedToken == receivedToken
    }
    return authenticateOnUrlAndUpdateUrl(request, expectedToken)
  }

  private fun authenticateOnUrlAndUpdateUrl(request: FullHttpRequest, expectedToken: String): Boolean {
    var uri = request.uri()
    val prefix = "/$expectedToken/"
    if (uri.startsWith(prefix)) {
      uri = uri.substring(prefix.length - 1)
      request.uri = uri
      return true
    }
    return false
  }

  private fun getTokenFromAuthorization(request: FullHttpRequest): String? {
    val authorization = request.headers().get("Authorization")
    if (Strings.isNullOrEmpty(authorization)) {
      return null
    }
    if (!authorization.startsWith("Basic ") || authorization.length < 7) {
      LOG.warn(String.format("Invalid authorization header '%s', unexpected type.", authorization))
      return null
    }
    val base64 = authorization.substring(6)
    var decoded: String? = null
    try {
      decoded = String(Base64.decode(base64))
    }
    catch (e: IllegalArgumentException) {
      LOG.warn(String.format("Invalid authorization header '%s', bad base64 encoding.", authorization))
      return null
    }

    val parts = decoded.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    if (parts.size != 2) {
      LOG.warn(String.format("Invalid authorization header '%s', bad username/password pair.", authorization))
      return null
    }
    val username = parts[0]
    val password = parts[1]
    if ("_token_" != username) {
      LOG.warn(String.format("Invalid authorization header '%s'", authorization))
      return null
    }
    return password
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