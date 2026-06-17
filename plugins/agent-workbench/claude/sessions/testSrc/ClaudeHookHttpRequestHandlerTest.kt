// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.ide.HttpRequestHandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class ClaudeHookHttpRequestHandlerTest {
  @TempDir
  lateinit var tempDir: Path

  private val httpClient = HttpClient.newHttpClient()

  @Test
  fun validHookRequestEmitsUpdate(@TestDisposable disposable: Disposable): Unit = runBlocking(Dispatchers.Default) {
    registerHookHandler(disposable)
    val sessionId = "session-http-valid"
    val projectPath = tempDir.resolve("project-valid")
    val settings = checkNotNull(createLaunchSettings(sessionId))
    val update = async(start = CoroutineStart.UNDISPATCHED) {
      withTimeout(5.seconds) {
        ClaudeHookBridge.updateEvents.first { event -> sessionId in event.activityUpdatesByThreadId }
      }
    }

    try {
      val response = postHook(
        endpoint = settings.endpoint,
        token = settings.token,
        payload = hookPayload(
          sessionId = sessionId,
          cwd = projectPath.toString(),
          hookEventName = "PreToolUse",
          toolName = "AskUserQuestion",
        ),
      )

      assertThat(response.statusCode()).isEqualTo(200)
      val event = update.await()
      assertThat(event.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
      assertThat(event.scopedPaths).containsExactly(normalizeAgentWorkbenchPath(projectPath.toString()))
      assertThat(event.threadIds).isNull()
      assertThat(event.activityUpdatesByThreadId.getValue(sessionId).activityReport)
        .isEqualTo(AgentThreadActivityReport(AgentThreadActivity.NEEDS_INPUT))
    }
    finally {
      ClaudeHookBridge.invalidateSession(sessionId)
    }
  }

  @Test
  fun missingBearerTokenIsRejected(@TestDisposable disposable: Disposable): Unit = runBlocking(Dispatchers.Default) {
    registerHookHandler(disposable)
    val sessionId = "session-http-missing-token"
    val projectPath = tempDir.resolve("project-missing-token")
    val settings = checkNotNull(createLaunchSettings(sessionId))

    try {
      val response = assertNoHookUpdateFor(sessionId) {
        postHook(
          endpoint = settings.endpoint,
          token = null,
          payload = hookPayload(
            sessionId = sessionId,
            cwd = projectPath.toString(),
            hookEventName = "PreToolUse",
            toolName = "AskUserQuestion",
          ),
        )
      }

      assertThat(response.statusCode()).isEqualTo(401)
    }
    finally {
      ClaudeHookBridge.invalidateSession(sessionId)
    }
  }

  @Test
  fun malformedPayloadIsRejected(@TestDisposable disposable: Disposable): Unit = runBlocking(Dispatchers.Default) {
    registerHookHandler(disposable)
    val sessionId = "session-http-malformed"
    val settings = checkNotNull(createLaunchSettings(sessionId))

    try {
      val response = assertNoHookUpdateFor(sessionId) {
        postHook(endpoint = settings.endpoint, token = settings.token, payload = "{")
      }

      assertThat(response.statusCode()).isEqualTo(400)
    }
    finally {
      ClaudeHookBridge.invalidateSession(sessionId)
    }
  }

  @Test
  fun oversizedPayloadIsRejected(@TestDisposable disposable: Disposable): Unit = runBlocking(Dispatchers.Default) {
    registerHookHandler(disposable)
    val sessionId = "session-http-oversized"
    val settings = checkNotNull(createLaunchSettings(sessionId))
    val payload = "{" + "\"padding\":" + "x".repeat(70_000).jsonString() + "}"

    try {
      val response = assertNoHookUpdateFor(sessionId) {
        postHook(endpoint = settings.endpoint, token = settings.token, payload = payload)
      }

      assertThat(response.statusCode()).isEqualTo(413)
    }
    finally {
      ClaudeHookBridge.invalidateSession(sessionId)
    }
  }

  private fun registerHookHandler(disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(HttpRequestHandler.EP_NAME, listOf(ClaudeHookHttpRequestHandler()), disposable)
  }

  private fun createLaunchSettings(sessionId: String): ClaudeHookLaunchSettings? {
    return ClaudeHookBridge.createLaunchSettings(
      sessionId = sessionId,
      settingsDirectoryProvider = { tempDir.resolve("hook-settings") },
    )
  }

  private fun postHook(
    endpoint: String,
    payload: String,
    token: String?,
  ): HttpResponse<String> {
    val builder = HttpRequest.newBuilder(URI(endpoint))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(payload))
    if (token != null) {
      builder.header("Authorization", "Bearer $token")
    }
    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
  }
}

private suspend fun assertNoHookUpdateFor(
  sessionId: String,
  action: () -> HttpResponse<String>,
): HttpResponse<String> = coroutineScope {
  val update = async(start = CoroutineStart.UNDISPATCHED) {
    withTimeout(200.milliseconds) {
      ClaudeHookBridge.updateEvents.first { event ->
        sessionId in event.activityUpdatesByThreadId || event.threadIds?.contains(sessionId) == true
      }
    }
  }
  val response = action()
  assertThat(runCatching { update.await() }.exceptionOrNull()).isInstanceOf(TimeoutCancellationException::class.java)
  response
}

private fun hookPayload(
  sessionId: String,
  cwd: String,
  hookEventName: String,
  toolName: String,
  toolInputJson: String = "{}",
): String {
  return """
    {
      "hook_event_name":${hookEventName.jsonString()},
      "session_id":${sessionId.jsonString()},
      "cwd":${cwd.jsonString()},
      "tool_name":${toolName.jsonString()},
      "tool_input":$toolInputJson
    }
  """.trimIndent()
}

private fun String.jsonString(): String {
  return "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
