// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.pi.sessions

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.json.createJsonParser
import com.intellij.platform.ai.agent.json.forEachJsonObjectField
import com.intellij.platform.ai.agent.json.readJsonLongOrNull
import com.intellij.platform.ai.agent.json.readJsonStringOrNull
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionActivityEvidence
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadActivityUpdate
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.DigestUtil
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.ide.BuiltInServerManager
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import java.util.concurrent.ConcurrentHashMap

private val STATUS_LOG = logger<PiExtensionStatusBridge>()

internal object PiExtensionStatusBridge {
  private val jsonFactory = JsonFactory()
  private val sessionIdsByToken = ConcurrentHashMap<String, String>()
  private val tokensBySessionId = ConcurrentHashMap<String, MutableSet<String>>()
  private val statusUpdates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(
    extraBufferCapacity = 64,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  val updateEvents: Flow<AgentSessionSourceUpdateEvent> = statusUpdates

  fun createLaunchEnvironment(sessionId: String): Map<String, String> {
    val normalizedSessionId = sessionId.trim().takeIf { it.isNotEmpty() } ?: return emptyMap()
    val token = DigestUtil.randomToken()
    sessionIdsByToken[token] = normalizedSessionId
    tokensBySessionId.computeIfAbsent(normalizedSessionId) { ConcurrentHashMap.newKeySet() }.add(token)
    val endpoints = try {
      val port = BuiltInServerManager.getInstance().waitForStart().port
      PiExtensionLaunchEndpoints(
        statusEndpoint = "http://127.0.0.1:$port/$PI_STATUS_ENDPOINT_PREFIX",
        controlEndpoint = "ws://127.0.0.1:$port/$PI_CONTROL_ENDPOINT_PREFIX",
      )
    }
    catch (e: Exception) {
      removeToken(token)
      STATUS_LOG.warn("Failed to resolve Pi extension endpoints", e)
      return emptyMap()
    }
    return mapOf(
      PI_STATUS_ENDPOINT_ENVIRONMENT_VARIABLE to endpoints.statusEndpoint,
      PI_STATUS_TOKEN_ENVIRONMENT_VARIABLE to token,
      PI_CONTROL_WS_ENDPOINT_ENVIRONMENT_VARIABLE to endpoints.controlEndpoint,
    )
  }

  fun invalidateSession(sessionId: String) {
    val normalizedSessionId = sessionId.trim().takeIf { it.isNotEmpty() } ?: return
    val tokens = tokensBySessionId.remove(normalizedSessionId) ?: return
    for (token in tokens) {
      sessionIdsByToken.remove(token, normalizedSessionId)
    }
    PiExtensionControlBridge.invalidateSession(normalizedSessionId)
  }

  fun authenticateLaunchToken(token: String?, sessionId: String? = null): String? {
    val normalizedToken = token?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val expectedSessionId = sessionIdsByToken[normalizedToken] ?: return null
    val normalizedSessionId = sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return expectedSessionId
    return expectedSessionId.takeIf { it == normalizedSessionId }
  }

  fun rebindLaunchToken(token: String, previousSessionId: String, sessionId: String): Boolean {
    val normalizedToken = token.trim().takeIf { it.isNotEmpty() } ?: return false
    val normalizedPreviousSessionId = previousSessionId.trim().takeIf { it.isNotEmpty() } ?: return false
    val normalizedSessionId = sessionId.trim().takeIf { it.isNotEmpty() } ?: return false
    if (normalizedPreviousSessionId == normalizedSessionId) {
      return sessionIdsByToken[normalizedToken] == normalizedSessionId
    }
    if (!sessionIdsByToken.replace(normalizedToken, normalizedPreviousSessionId, normalizedSessionId)) {
      return false
    }
    tokensBySessionId[normalizedPreviousSessionId]?.let { tokens ->
      tokens.remove(normalizedToken)
      if (tokens.isEmpty()) {
        tokensBySessionId.remove(normalizedPreviousSessionId, tokens)
      }
    }
    tokensBySessionId.computeIfAbsent(normalizedSessionId) { ConcurrentHashMap.newKeySet() }.add(normalizedToken)
    return true
  }

  fun handleStatusRequest(
    token: String?,
    content: String,
    receivedAtMs: Long = System.currentTimeMillis(),
  ): PiExtensionStatusRequestResult {
    val normalizedToken = token?.trim()?.takeIf { it.isNotEmpty() } ?: return PiExtensionStatusRequestResult.UNAUTHORIZED
    val expectedSessionId = sessionIdsByToken[normalizedToken] ?: return PiExtensionStatusRequestResult.UNAUTHORIZED
    val payload = parseStatusPayload(content) ?: return PiExtensionStatusRequestResult.BAD_REQUEST
    val sessionId = payload.sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return PiExtensionStatusRequestResult.BAD_REQUEST
    if (sessionId != expectedSessionId) return PiExtensionStatusRequestResult.UNAUTHORIZED
    val updateEvent =
      createStatusUpdate(payload = payload, receivedAtMs = receivedAtMs) ?: return PiExtensionStatusRequestResult.BAD_REQUEST
    statusUpdates.tryEmit(updateEvent)
    return PiExtensionStatusRequestResult.ACCEPTED
  }

  internal fun parseStatusUpdate(content: String, receivedAtMs: Long): AgentSessionSourceUpdateEvent? {
    return parseStatusPayload(content)?.let { payload -> createStatusUpdate(payload = payload, receivedAtMs = receivedAtMs) }
  }

  private fun removeToken(token: String) {
    val sessionId = sessionIdsByToken.remove(token) ?: return
    val tokens = tokensBySessionId[sessionId] ?: return
    tokens.remove(token)
    if (tokens.isEmpty()) {
      tokensBySessionId.remove(sessionId, tokens)
    }
  }

  private fun createStatusUpdate(payload: PiStatusPayload, receivedAtMs: Long): AgentSessionSourceUpdateEvent? {
    val sessionId = payload.sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val scopedPath = payload.cwd?.let(::normalizePiProjectPath) ?: return null
    val event = payload.event?.trim()?.lowercase()?.replace('-', '_')
    if (event != null) {
      return when (event) {
        PI_SESSION_INFO_CHANGED_EVENT -> AgentSessionSourceUpdateEvent.threadsChanged(
          scopedPaths = setOf(scopedPath),
          threadIds = setOf(sessionId),
        )
        else -> null
      }
    }
    val activity = payload.activity?.let(::parsePiStatusActivity) ?: return null
    val updatedAt = payload.updatedAt ?: receivedAtMs
    return AgentSessionSourceUpdateEvent.activityChanged(
      scopedPaths = setOf(scopedPath),
      threadIds = setOf(sessionId),
      activityUpdatesByThreadId = mapOf(
        sessionId to AgentSessionThreadActivityUpdate(
          activityReport = AgentThreadActivityReport(activity),
          updatedAt = updatedAt,
          evidence = AgentSessionActivityEvidence.SEMANTIC,
        )
      ),
    )
  }

  private fun parseStatusPayload(content: String): PiStatusPayload? {
    return try {
      jsonFactory.createJsonParser(content).use { parser ->
        if (parser.nextToken() != JsonToken.START_OBJECT) return null
        readStatusPayload(parser)
      }
    }
    catch (e: Exception) {
      STATUS_LOG.debug("Failed to parse Pi status update", e)
      null
    }
  }
}

private data class PiExtensionLaunchEndpoints(
  @JvmField val statusEndpoint: String,
  @JvmField val controlEndpoint: String,
)

internal enum class PiExtensionStatusRequestResult {
  ACCEPTED,
  UNAUTHORIZED,
  BAD_REQUEST,
}

private data class PiStatusPayload(
  @JvmField val sessionId: String? = null,
  @JvmField val cwd: String? = null,
  @JvmField val event: String? = null,
  @JvmField val activity: String? = null,
  @JvmField val updatedAt: Long? = null,
)

private fun readStatusPayload(parser: JsonParser): PiStatusPayload {
  var sessionId: String? = null
  var cwd: String? = null
  var event: String? = null
  var activity: String? = null
  var updatedAt: Long? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "sessionId" -> sessionId = readJsonStringOrNull(parser)
      "cwd" -> cwd = readJsonStringOrNull(parser)
      "event" -> event = readJsonStringOrNull(parser)
      "activity" -> activity = readJsonStringOrNull(parser)
      "updatedAt" -> updatedAt = readJsonLongOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return PiStatusPayload(
    sessionId = sessionId,
    cwd = cwd,
    event = event,
    activity = activity,
    updatedAt = updatedAt,
  )
}

@Suppress("DuplicatedCode")
private fun parsePiStatusActivity(value: String): AgentThreadActivity? {
  return when (value.trim().lowercase().replace('-', '_')) {
    "ready" -> AgentThreadActivity.READY
    "processing" -> AgentThreadActivity.PROCESSING
    "reviewing" -> AgentThreadActivity.REVIEWING
    "needs_input" -> AgentThreadActivity.NEEDS_INPUT
    "done" -> AgentThreadActivity.UNREAD
    "unread" -> AgentThreadActivity.UNREAD
    else -> null
  }
}

internal const val PI_STATUS_ENDPOINT_ENVIRONMENT_VARIABLE: String = "AGENT_WORKBENCH_PI_STATUS_ENDPOINT"
internal const val PI_STATUS_TOKEN_ENVIRONMENT_VARIABLE: String = "AGENT_WORKBENCH_PI_STATUS_TOKEN"
internal const val PI_STATUS_ENDPOINT_PREFIX: String = "agent-workbench/pi/status"

private const val PI_SESSION_INFO_CHANGED_EVENT: String = "session_info_changed"
