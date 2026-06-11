// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.openapi.diagnostic.logger
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.sendPlainText
import java.nio.charset.StandardCharsets

private val HTTP_STATUS_LOG = logger<PiExtensionStatusHttpRequestHandler>()

internal class PiExtensionStatusHttpRequestHandler : HttpRequestHandler() {
  override fun isSupported(request: FullHttpRequest): Boolean {
    return request.method() === HttpMethod.POST && checkPrefix(request.uri(), PI_STATUS_ENDPOINT_PREFIX)
  }

  override fun process(
    urlDecoder: QueryStringDecoder,
    request: FullHttpRequest,
    context: ChannelHandlerContext,
  ): Boolean {
    if (request.content().readableBytes() > PI_STATUS_MAX_REQUEST_BYTES) {
      HTTP_STATUS_LOG.debug("Rejected oversized Pi extension status update")
      HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE.sendPlainText(context.channel(), request)
      return true
    }

    val token = bearerToken(request)
    val content = request.content().toString(StandardCharsets.UTF_8)
    val status = when (PiExtensionStatusBridge.handleStatusRequest(token = token, content = content)) {
      PiExtensionStatusRequestResult.ACCEPTED -> HttpResponseStatus.OK
      PiExtensionStatusRequestResult.UNAUTHORIZED -> {
        HTTP_STATUS_LOG.debug("Rejected unauthorized Pi extension status update")
        HttpResponseStatus.UNAUTHORIZED
      }
      PiExtensionStatusRequestResult.BAD_REQUEST -> {
        HTTP_STATUS_LOG.debug("Rejected malformed Pi extension status update")
        HttpResponseStatus.BAD_REQUEST
      }
    }
    status.sendPlainText(context.channel(), request)
    return true
  }
}

private fun bearerToken(request: FullHttpRequest): String? {
  val authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION) ?: return null
  val prefix = "Bearer "
  if (!authorization.startsWith(prefix, ignoreCase = true)) return null
  return authorization.substring(prefix.length)
}

private const val PI_STATUS_MAX_REQUEST_BYTES: Int = 16 * 1024
