// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.claude.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-claude-hooks.spec.md

import com.intellij.openapi.diagnostic.logger
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.send

private val CLAUDE_HOOK_HTTP_LOG = logger<ClaudeHookHttpRequestHandler>()

internal class ClaudeHookHttpRequestHandler : HttpRequestHandler() {
  override fun isSupported(request: FullHttpRequest): Boolean {
    return request.method() === HttpMethod.POST && checkPrefix(request.uri(), CLAUDE_HOOK_ENDPOINT_PREFIX)
  }

  override fun process(
    urlDecoder: QueryStringDecoder,
    request: FullHttpRequest,
    context: ChannelHandlerContext,
  ): Boolean {
    if (request.content().readableBytes() > CLAUDE_HOOK_MAX_REQUEST_BYTES) {
      CLAUDE_HOOK_HTTP_LOG.debug("Rejected oversized Claude hook update")
      sendJsonResponse(context = context, request = request, status = HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE)
      return true
    }

    val token = bearerToken(request)
    val status = ByteBufInputStream(request.content()).use { content ->
      when (ClaudeHookBridge.handleHookRequest(token = token, content = content)) {
        ClaudeHookRequestResult.ACCEPTED -> HttpResponseStatus.OK
        ClaudeHookRequestResult.UNAUTHORIZED -> {
          CLAUDE_HOOK_HTTP_LOG.debug("Rejected unauthorized Claude hook update")
          HttpResponseStatus.UNAUTHORIZED
        }
        ClaudeHookRequestResult.BAD_REQUEST -> {
          CLAUDE_HOOK_HTTP_LOG.debug("Rejected malformed Claude hook update")
          HttpResponseStatus.BAD_REQUEST
        }
      }
    }
    sendJsonResponse(context = context, request = request, status = status)
    return true
  }
}

private fun sendJsonResponse(
  context: ChannelHandlerContext,
  request: FullHttpRequest,
  status: HttpResponseStatus,
) {
  val content = Unpooled.wrappedBuffer(CLAUDE_HOOK_EMPTY_JSON_RESPONSE_BYTES)
  val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content)
  response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
  response.send(context.channel(), request)
}

private fun bearerToken(request: FullHttpRequest): String? {
  val authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION) ?: return null
  val prefix = "Bearer "
  if (!authorization.startsWith(prefix, ignoreCase = true)) return null
  return authorization.substring(prefix.length)
}

private const val CLAUDE_HOOK_MAX_REQUEST_BYTES: Int = 64 * 1024
private val CLAUDE_HOOK_EMPTY_JSON_RESPONSE_BYTES: ByteArray = byteArrayOf('{'.code.toByte(), '}'.code.toByte())
