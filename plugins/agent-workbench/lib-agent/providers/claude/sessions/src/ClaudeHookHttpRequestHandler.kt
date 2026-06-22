// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.claude.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-claude-hooks.spec.md

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
      HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE.sendPlainText(context.channel(), request)
      return true
    }

    val token = bearerToken(request)
    val content = request.content().toString(StandardCharsets.UTF_8)
    val status = when (ClaudeHookBridge.handleHookRequest(token = token, content = content)) {
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

private const val CLAUDE_HOOK_MAX_REQUEST_BYTES: Int = 64 * 1024
