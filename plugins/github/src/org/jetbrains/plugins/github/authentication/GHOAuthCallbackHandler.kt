// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.util.Url
import com.intellij.util.Urls.newFromEncoded
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.RestService
import org.jetbrains.io.send
import org.jetbrains.plugins.github.authentication.GHOAuthService.Companion.ERROR_URL
import org.jetbrains.plugins.github.authentication.GHOAuthService.Companion.SUCCESS_URL

private const val INVALID_REQUEST_ERROR = "Invalid Request"

internal class GHOAuthCallbackHandler : RestService() {
  override fun getServiceName(): String = SERVICE_NAME

  override fun isHostTrusted(request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean = true

  override fun execute(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): String? {
    if (!urlDecoder.isAuthorizationCodeUrl) return INVALID_REQUEST_ERROR
    val code = urlDecoder.authorizationCode ?: return INVALID_REQUEST_ERROR

    val isCodeAccepted = GHOAuthService.instance.acceptCode(code)
    sendRedirect(request, context, if (isCodeAccepted) SUCCESS_URL else ERROR_URL)
    return null
  }

  private fun sendRedirect(request: FullHttpRequest, context: ChannelHandlerContext, url: Url) {
    val headers = DefaultHttpHeaders().set(HttpHeaderNames.LOCATION, url.toExternalForm())

    HttpResponseStatus.FOUND.send(context.channel(), request, null, headers)
  }

  companion object {
    private const val SERVICE_NAME = "github/oauth"
    private val port: Int get() = BuiltInServerManager.getInstance().port

    val authorizationCodeUrl: Url get() = newFromEncoded("http://localhost:$port/$PREFIX/$SERVICE_NAME/authorization_code")

    private val QueryStringDecoder.isAuthorizationCodeUrl: Boolean get() = path() == authorizationCodeUrl.path
    private val QueryStringDecoder.authorizationCode: String? get() = parameters()["code"]?.firstOrNull()
  }
}