// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
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
class PiExtensionStatusHttpRequestHandlerTest {
  @TempDir
  lateinit var tempDir: Path

  private val httpClient = HttpClient.newHttpClient()

  @Test
  fun `valid status request emits update`(@TestDisposable disposable: Disposable): Unit = runBlocking(Dispatchers.Default) {
    registerStatusHandler(disposable)
    val sessionId = "session-http-valid"
    val projectDir = tempDir.resolve("project-valid")
    val launchEnvironment = createStatusLaunchEnvironment(sessionId)
    val update = async(start = CoroutineStart.UNDISPATCHED) {
      withTimeout(5.seconds) {
        PiExtensionStatusBridge.updateEvents.first { event -> sessionId in event.activityUpdatesByThreadId }
      }
    }

    val response = postStatus(
      launchEnvironment = launchEnvironment,
      payload = statusPayload(sessionId = sessionId, cwd = projectDir.toString(), activity = "processing", updatedAt = 7_000L),
    )

    assertThat(response.statusCode()).isEqualTo(200)
    val event = update.await()
    assertThat(event.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
    assertThat(event.scopedPaths).containsExactly(checkNotNull(normalizePiProjectPath(projectDir.toString())))
    assertThat(event.threadIds).containsExactly(sessionId)
    val activityUpdate = event.activityUpdatesByThreadId[sessionId]
    assertThat(activityUpdate?.activityReport).isEqualTo(AgentThreadActivityReport(AgentThreadActivity.PROCESSING))
    assertThat(activityUpdate?.updatedAt).isEqualTo(7_000L)
  }

  @Test
  fun `valid session info request emits thread update`(@TestDisposable disposable: Disposable): Unit = runBlocking(Dispatchers.Default) {
    registerStatusHandler(disposable)
    val sessionId = "session-http-name"
    val projectDir = tempDir.resolve("project-name")
    val launchEnvironment = createStatusLaunchEnvironment(sessionId)
    val update = async(start = CoroutineStart.UNDISPATCHED) {
      withTimeout(5.seconds) {
        PiExtensionStatusBridge.updateEvents.first { event ->
          event.threadIds?.contains(sessionId) == true && event.type == AgentSessionSourceUpdate.THREADS_CHANGED
        }
      }
    }

    val response = postStatus(
      launchEnvironment = launchEnvironment,
      payload = """
        {"sessionId":"$sessionId","cwd":${projectDir.toString().jsonString()},"event":"session_info_changed","name":"Renamed"}
      """.trimIndent(),
    )

    assertThat(response.statusCode()).isEqualTo(200)
    val event = update.await()
    assertThat(event.type).isEqualTo(AgentSessionSourceUpdate.THREADS_CHANGED)
    assertThat(event.scopedPaths).containsExactly(checkNotNull(normalizePiProjectPath(projectDir.toString())))
    assertThat(event.threadIds).containsExactly(sessionId)
    assertThat(event.activityUpdatesByThreadId).isEmpty()
  }

  @Test
  fun `missing bearer token is rejected`(@TestDisposable disposable: Disposable): Unit = runBlocking(Dispatchers.Default) {
    registerStatusHandler(disposable)
    val sessionId = "session-http-missing-token"
    val projectDir = tempDir.resolve("project-missing-token")
    val launchEnvironment = createStatusLaunchEnvironment(sessionId)

    val response = assertNoStatusUpdateFor(sessionId) {
      postStatus(
        launchEnvironment = launchEnvironment,
        payload = statusPayload(sessionId = sessionId, cwd = projectDir.toString(), activity = "processing"),
        token = null,
      )
    }

    assertThat(response.statusCode()).isEqualTo(401)
  }

  @Test
  fun `wrong bearer token is rejected`(@TestDisposable disposable: Disposable): Unit = runBlocking(Dispatchers.Default) {
    registerStatusHandler(disposable)
    val sessionId = "session-http-wrong-token"
    val projectDir = tempDir.resolve("project-wrong-token")
    val launchEnvironment = createStatusLaunchEnvironment(sessionId)

    val response = assertNoStatusUpdateFor(sessionId) {
      postStatus(
        launchEnvironment = launchEnvironment,
        payload = statusPayload(sessionId = sessionId, cwd = projectDir.toString(), activity = "processing"),
        token = "wrong-token",
      )
    }

    assertThat(response.statusCode()).isEqualTo(401)
  }

  @Test
  fun `session mismatched bearer token is rejected`(@TestDisposable disposable: Disposable): Unit = runBlocking(Dispatchers.Default) {
    registerStatusHandler(disposable)
    val expectedSessionId = "session-http-bound"
    val spoofedSessionId = "session-http-spoofed"
    val projectDir = tempDir.resolve("project-session-mismatch")
    val launchEnvironment = createStatusLaunchEnvironment(expectedSessionId)

    val response = assertNoStatusUpdateFor(spoofedSessionId) {
      postStatus(
        launchEnvironment = launchEnvironment,
        payload = statusPayload(sessionId = spoofedSessionId, cwd = projectDir.toString(), activity = "processing"),
      )
    }

    assertThat(response.statusCode()).isEqualTo(401)
  }

  @Test
  fun `invalidated session token is rejected`(@TestDisposable disposable: Disposable): Unit = runBlocking(Dispatchers.Default) {
    registerStatusHandler(disposable)
    val sessionId = "session-http-invalidated"
    val projectDir = tempDir.resolve("project-invalidated")
    val launchEnvironment = createStatusLaunchEnvironment(sessionId)

    PiExtensionStatusBridge.invalidateSession(sessionId)

    val response = assertNoStatusUpdateFor(sessionId) {
      postStatus(
        launchEnvironment = launchEnvironment,
        payload = statusPayload(sessionId = sessionId, cwd = projectDir.toString(), activity = "processing"),
      )
    }

    assertThat(response.statusCode()).isEqualTo(401)
  }

  @Test
  fun `malformed payload is rejected`(@TestDisposable disposable: Disposable): Unit = runBlocking(Dispatchers.Default) {
    registerStatusHandler(disposable)
    val sessionId = "session-http-malformed"
    val launchEnvironment = createStatusLaunchEnvironment(sessionId)

    val response = assertNoStatusUpdateFor(sessionId) {
      postStatus(launchEnvironment = launchEnvironment, payload = "not json")
    }

    assertThat(response.statusCode()).isEqualTo(400)
  }

  @Test
  fun `oversized payload is rejected`(@TestDisposable disposable: Disposable): Unit = runBlocking(Dispatchers.Default) {
    registerStatusHandler(disposable)
    val sessionId = "session-http-oversized"
    val projectDir = tempDir.resolve("project-oversized")
    val launchEnvironment = createStatusLaunchEnvironment(sessionId)
    val payload = statusPayload(
      sessionId = sessionId,
      cwd = projectDir.toString(),
      activity = "processing",
      extraJson = "\"padding\":${"x".repeat(20_000).jsonString()}",
    )

    val response = assertNoStatusUpdateFor(sessionId) {
      postStatus(launchEnvironment = launchEnvironment, payload = payload)
    }

    assertThat(response.statusCode()).isEqualTo(413)
  }

  @Test
  fun `non local origin is rejected`(@TestDisposable disposable: Disposable): Unit = runBlocking(Dispatchers.Default) {
    registerStatusHandler(disposable)
    val sessionId = "session-http-remote-origin"
    val projectDir = tempDir.resolve("project-remote-origin")
    val launchEnvironment = createStatusLaunchEnvironment(sessionId)

    val response = assertNoStatusUpdateFor(sessionId) {
      postStatus(
        launchEnvironment = launchEnvironment,
        payload = statusPayload(sessionId = sessionId, cwd = projectDir.toString(), activity = "processing"),
        origin = "https://example.com",
      )
    }

    assertThat(response.statusCode()).isNotEqualTo(200)
  }

  private fun registerStatusHandler(disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(HttpRequestHandler.EP_NAME, listOf(PiExtensionStatusHttpRequestHandler()), disposable)
  }

  private fun createStatusLaunchEnvironment(sessionId: String): PiStatusLaunchEnvironment {
    val environment = PiExtensionStatusBridge.createLaunchEnvironment(sessionId)
    return PiStatusLaunchEnvironment(
      endpoint = environment.getValue(PI_STATUS_ENDPOINT_ENVIRONMENT_VARIABLE),
      token = environment.getValue(PI_STATUS_TOKEN_ENVIRONMENT_VARIABLE),
    )
  }

  private fun postStatus(
    launchEnvironment: PiStatusLaunchEnvironment,
    payload: String,
    token: String? = launchEnvironment.token,
    origin: String? = null,
  ): HttpResponse<String> {
    val builder = HttpRequest.newBuilder(URI(launchEnvironment.endpoint))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(payload))
    if (token != null) {
      builder.header("Authorization", "Bearer $token")
    }
    if (origin != null) {
      builder.header("Origin", origin)
    }
    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
  }

  private suspend fun assertNoStatusUpdateFor(
    sessionId: String,
    action: () -> HttpResponse<String>,
  ): HttpResponse<String> = coroutineScope {
    val update = async(start = CoroutineStart.UNDISPATCHED) {
      withTimeout(200.milliseconds) {
        PiExtensionStatusBridge.updateEvents.first { event -> sessionId in event.activityUpdatesByThreadId }
      }
    }
    val response = action()
    assertThat(runCatching { update.await() }.exceptionOrNull()).isInstanceOf(TimeoutCancellationException::class.java)
    response
  }
}

private data class PiStatusLaunchEnvironment(
  @JvmField val endpoint: String,
  @JvmField val token: String,
)

private fun statusPayload(
  sessionId: String,
  cwd: String,
  activity: String,
  updatedAt: Long = 1_000L,
  extraJson: String? = null,
): String {
  val fields = mutableListOf(
    "\"sessionId\":${sessionId.jsonString()}",
    "\"cwd\":${cwd.jsonString()}",
    "\"activity\":${activity.jsonString()}",
    "\"updatedAt\":$updatedAt",
  )
  if (extraJson != null) {
    fields.add(extraJson)
  }
  return fields.joinToString(separator = ",", prefix = "{", postfix = "}")
}

private fun String.jsonString(): String {
  return "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
