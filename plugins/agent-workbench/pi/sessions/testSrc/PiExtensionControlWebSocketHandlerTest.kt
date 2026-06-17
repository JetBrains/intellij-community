// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.json.createJsonParser
import com.intellij.agent.workbench.json.forEachJsonObjectField
import com.intellij.agent.workbench.json.readJsonStringOrNull
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.ide.HttpRequestHandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class PiExtensionControlWebSocketHandlerTest {
  @TempDir
  lateinit var tempDir: Path

  private val httpClient = HttpClient.newHttpClient()

  @Test
  fun `navigate command is delivered over authenticated websocket`(@TestDisposable disposable: Disposable): Unit =
    runBlocking(Dispatchers.Default) {
      registerControlHandler(disposable)
      val sessionId = "session-ws-navigate"
      val projectDir = tempDir.resolve("project-navigate")
      val launchEnvironment = createControlLaunchEnvironment(sessionId)
      val listener = PiControlTestWebSocketListener()
      val webSocket = connectControlSocket(launchEnvironment, listener)
      try {
        webSocket.sendText(
          controlHelloPayload(
            token = launchEnvironment.token,
            sessionId = sessionId,
            cwd = projectDir.toString(),
          ),
          true,
        ).join()
        assertThat(listener.nextMessage()).contains("\"type\":\"hello\"")
        assertThat(PiExtensionControlBridge.canNavigateThreadOutlineItem(projectDir.toString(), sessionId, "entry-1")).isTrue()

        val navigation = async {
          PiExtensionControlBridge.navigateThreadOutlineItem(projectDir.toString(), sessionId, "entry-1")
        }
        val request = listener.nextMessage()
        assertThat(request).contains("\"type\":\"navigateTree\"")
        assertThat(request).contains("\"entryId\":\"entry-1\"")
        webSocket.sendText(controlResponsePayload(requestId = request.readRequestId()), true).join()

        assertThat(navigation.await()).isTrue()
      }
      finally {
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join()
      }
    }

  @Test
  fun `fork response remaps live control connection to forked session`(@TestDisposable disposable: Disposable): Unit =
    runBlocking(Dispatchers.Default) {
      registerControlHandler(disposable)
      val sessionId = "session-ws-fork"
      val forkedSessionId = "session-ws-forked"
      val projectDir = tempDir.resolve("project-fork")
      val launchEnvironment = createControlLaunchEnvironment(sessionId)
      val listener = PiControlTestWebSocketListener()
      val webSocket = connectControlSocket(launchEnvironment, listener)
      try {
        webSocket.sendText(
          controlHelloPayload(
            token = launchEnvironment.token,
            sessionId = sessionId,
            cwd = projectDir.toString(),
          ),
          true,
        ).join()
        listener.nextMessage()

        val fork = async {
          PiExtensionControlBridge.forkThreadFromOutlineItem(projectDir.toString(), sessionId, "entry-fork")
        }
        val request = listener.nextMessage()
        assertThat(request).contains("\"type\":\"forkFromEntry\"")
        assertThat(request).contains("\"position\":\"at\"")
        webSocket.sendText(
          controlForkResponsePayload(
            requestId = request.readRequestId(),
          ),
          true,
        ).join()

        val forkedThread = fork.await()
        assertThat(forkedThread?.id).isEqualTo(forkedSessionId)
        assertThat(forkedThread?.title).isEqualTo("Forked thread")
        assertThat(forkedThread?.updatedAt).isEqualTo(9_000L)
        assertThat(forkedThread?.activity).isEqualTo(AgentThreadActivity.PROCESSING)
        assertThat(PiExtensionControlBridge.canNavigateThreadOutlineItem(projectDir.toString(), sessionId, "entry-next")).isFalse()
        assertThat(PiExtensionControlBridge.canNavigateThreadOutlineItem(projectDir.toString(), forkedSessionId, "entry-next")).isTrue()
        assertThat(PiExtensionStatusBridge.authenticateLaunchToken(launchEnvironment.token, sessionId)).isNull()
        assertThat(PiExtensionStatusBridge.authenticateLaunchToken(launchEnvironment.token, forkedSessionId)).isEqualTo(forkedSessionId)
      }
      finally {
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join()
      }
    }

  @Test
  fun `session source fork requires live fork capability`(@TestDisposable disposable: Disposable): Unit =
    runBlocking(Dispatchers.Default) {
      registerControlHandler(disposable)
      val source = PiSessionSource(sessionStore = PiSessionStore(sessionDirResolver = { tempDir.resolve("unused-sessions") }))
      val sessionId = "session-ws-no-fork"
      val projectDir = tempDir.resolve("project-no-fork")
      val launchEnvironment = createControlLaunchEnvironment(sessionId)
      val listener = PiControlTestWebSocketListener()
      val webSocket = connectControlSocket(launchEnvironment, listener)
      try {
        assertThat(source.canForkThreadFromOutlineItem(projectDir.toString(), sessionId, "entry-fork", null, null)).isFalse()
        webSocket.sendText(
          controlHelloPayload(
            token = launchEnvironment.token,
            sessionId = sessionId,
            cwd = projectDir.toString(),
            fork = false,
          ),
          true,
        ).join()
        listener.nextMessage()

        assertThat(source.canShowThreadOutlineForkAction(projectDir.toString(), sessionId, "entry-fork", null, null)).isTrue()
        assertThat(source.canForkThreadFromOutlineItem(projectDir.toString(), sessionId, "entry-fork", null, null)).isFalse()
      }
      finally {
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join()
      }
    }

  @Test
  fun `control hello exposes active thread update for structure view`(@TestDisposable disposable: Disposable): Unit =
    runBlocking(Dispatchers.Default) {
      registerControlHandler(disposable)
      val source = PiSessionSource(sessionStore = PiSessionStore(sessionDirResolver = { tempDir.resolve("unused-sessions") }))
      val sessionId = "session-ws-active-update"
      val projectDir = tempDir.resolve("project-active-update")
      val launchEnvironment = createControlLaunchEnvironment(sessionId)
      val listener = PiControlTestWebSocketListener()
      val webSocket = connectControlSocket(launchEnvironment, listener)
      try {
        webSocket.sendText(
          controlHelloPayload(
            token = launchEnvironment.token,
            sessionId = sessionId,
            cwd = projectDir.toString(),
          ),
          true,
        ).join()
        listener.nextMessage()

        val event = source.activeThreadUpdateEvents(projectDir.toString(), sessionId).first()
        assertThat(event.type).isEqualTo(AgentSessionSourceUpdate.HINTS_CHANGED)
        assertThat(event.scopedPaths).containsExactly(normalizePiProjectPath(projectDir.toString()))
        assertThat(event.threadIds).containsExactly(sessionId)
      }
      finally {
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join()
      }
    }

  @Test
  fun `session source fork delegates over connected websocket`(@TestDisposable disposable: Disposable): Unit =
    runBlocking(Dispatchers.Default) {
      registerControlHandler(disposable)
      val source = PiSessionSource(sessionStore = PiSessionStore(sessionDirResolver = { tempDir.resolve("unused-sessions") }))
      val sessionId = "session-ws-source-fork"
      val forkedSessionId = "session-ws-source-forked"
      val projectDir = tempDir.resolve("project-source-fork")
      val launchEnvironment = createControlLaunchEnvironment(sessionId)
      val listener = PiControlTestWebSocketListener()
      val webSocket = connectControlSocket(launchEnvironment, listener)
      try {
        assertThat(source.canForkThreadFromOutlineItem(projectDir.toString(), sessionId, "entry-fork", null, null)).isFalse()
        webSocket.sendText(
          controlHelloPayload(
            token = launchEnvironment.token,
            sessionId = sessionId,
            cwd = projectDir.toString(),
          ),
          true,
        ).join()
        listener.nextMessage()
        assertThat(source.canForkThreadFromOutlineItem(projectDir.toString(), sessionId, "entry-fork", null, null)).isTrue()

        val fork = async {
          source.forkThreadFromOutlineItem(
            project = ProjectManager.getInstance().defaultProject,
            path = projectDir.toString(),
            threadId = sessionId,
            itemId = "entry-fork",
            subAgentId = null,
            tabKey = "tab-1",
          )
        }
        val request = listener.nextMessage()
        assertThat(request).contains("\"type\":\"forkFromEntry\"")
        webSocket.sendText(
          controlForkResponsePayload(
            requestId = request.readRequestId(),
            forkedSessionId = forkedSessionId,
          ),
          true,
        ).join()

        val forkedThread = fork.await()?.thread
        assertThat(forkedThread?.id).isEqualTo(forkedSessionId)
        assertThat(forkedThread?.title).isEqualTo("Forked thread")
      }
      finally {
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join()
      }
      waitForCondition {
        !source.canForkThreadFromOutlineItem(projectDir.toString(), forkedSessionId, "entry-next", null, null)
      }
    }

  private fun registerControlHandler(disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(HttpRequestHandler.EP_NAME, listOf(PiExtensionControlWebSocketHandler()), disposable)
  }

  private fun createControlLaunchEnvironment(sessionId: String): PiControlLaunchEnvironment {
    val environment = PiExtensionStatusBridge.createLaunchEnvironment(sessionId)
    return PiControlLaunchEnvironment(
      endpoint = environment.getValue(PI_CONTROL_WS_ENDPOINT_ENVIRONMENT_VARIABLE),
      token = environment.getValue(PI_STATUS_TOKEN_ENVIRONMENT_VARIABLE),
    )
  }

  private fun connectControlSocket(
    launchEnvironment: PiControlLaunchEnvironment,
    listener: PiControlTestWebSocketListener,
  ): WebSocket {
    return httpClient.newWebSocketBuilder()
      .header("Authorization", "Bearer ${launchEnvironment.token}")
      .buildAsync(URI(launchEnvironment.endpoint), listener)
      .get(5, TimeUnit.SECONDS)
  }
}

private data class PiControlLaunchEnvironment(
  @JvmField val endpoint: String,
  @JvmField val token: String,
)

private class PiControlTestWebSocketListener : WebSocket.Listener {
  private val messages = LinkedBlockingQueue<String>()
  private val currentMessage = StringBuilder()

  override fun onOpen(webSocket: WebSocket) {
    webSocket.request(1)
  }

  override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
    currentMessage.append(data)
    if (last) {
      messages.add(currentMessage.toString())
      currentMessage.setLength(0)
    }
    webSocket.request(1)
    return CompletableFuture.completedFuture(null)
  }

  fun nextMessage(): String {
    return checkNotNull(messages.poll(5, TimeUnit.SECONDS)) { "Timed out waiting for Pi control WebSocket message" }
  }
}

private fun controlHelloPayload(
  token: String,
  sessionId: String,
  cwd: String,
  navigateTree: Boolean = true,
  fork: Boolean = true,
): String {
  return """
    {"type":"hello","token":${token.jsonString()},"sessionId":${sessionId.jsonString()},"cwd":${cwd.jsonString()},"capabilities":{"navigateTree":$navigateTree,"fork":$fork}}
  """.trimIndent()
}

private fun controlResponsePayload(requestId: String): String {
  return "{" +
         "\"type\":\"response\"," +
         "\"requestId\":${requestId.jsonString()}," +
         "\"ok\":true" +
         "}"
}

private fun controlForkResponsePayload(requestId: String, forkedSessionId: String = "session-ws-forked"): String {
  return "{" +
         "\"type\":\"response\"," +
         "\"requestId\":${requestId.jsonString()}," +
         "\"ok\":true," +
         "\"thread\":{" +
         "\"id\":${forkedSessionId.jsonString()}," +
         "\"title\":\"Forked thread\"," +
         "\"updatedAt\":9000," +
         "\"activity\":\"processing\"" +
         "}" +
         "}"
}

private fun String.readRequestId(): String {
  JsonFactory().createJsonParser(this).use { parser ->
    check(parser.nextToken() == JsonToken.START_OBJECT)
    var requestId: String? = null
    forEachJsonObjectField(parser) { fieldName ->
      if (fieldName == "requestId") {
        requestId = readJsonStringOrNull(parser)
      }
      else {
        parser.skipChildren()
      }
      true
    }
    return checkNotNull(requestId)
  }
}

private fun String.jsonString(): String {
  return "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

private fun waitForCondition(timeoutMs: Long = 5_000, condition: () -> Boolean) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    if (condition()) {
      return
    }
    Thread.sleep(20)
  }
  throw AssertionError("Condition was not satisfied within ${timeoutMs}ms")
}
