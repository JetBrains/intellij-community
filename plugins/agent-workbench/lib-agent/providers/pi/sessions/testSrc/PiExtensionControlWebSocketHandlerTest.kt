// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.pi.sessions

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.json.createJsonParser
import com.intellij.platform.ai.agent.json.forEachJsonObjectField
import com.intellij.platform.ai.agent.json.readJsonStringOrNull
import com.intellij.agent.workbench.sessions.task.folders.AgentTaskFolderService
import com.intellij.agent.workbench.sessions.task.folders.AgentTaskFolderStatus
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
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
        assertThat(forkedThread?.activityReport?.rowActivity).isEqualTo(AgentThreadActivity.PROCESSING)
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
  fun `task folder requests are handled over authenticated websocket`(@TestDisposable disposable: Disposable): Unit =
    runBlocking(Dispatchers.Default) {
      registerControlHandler(disposable)
      val sessionId = "session-ws-task-folder"
      val projectDir = tempDir.resolve("project-task-folder")
      val folderService = service<AgentTaskFolderService>()
      val folder = requireNotNull(folderService.createFolder(projectDir.toString(), "Research", mapOf("issue" to "IJPL-248623")))
      assertThat(folderService.assignThread(projectDir.toString(), PI_AGENT_SESSION_PROVIDER, sessionId, folder.id)).isTrue()
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

        webSocket.sendText(controlTaskFolderRequest("request-current", "getCurrent"), true).join()
        val currentFolderResponse = listener.nextMessage()
        assertThat(currentFolderResponse).contains("\"requestId\":\"request-current\"")
        assertThat(currentFolderResponse).contains("\"ok\":true")
        assertThat(currentFolderResponse).contains("\"result\":")
        assertThat(currentFolderResponse).contains("\"id\":${folder.id.jsonString()}")
        assertThat(currentFolderResponse).contains("\"issue\":\"IJPL-248623\"")

        webSocket.sendText(controlTaskFolderRequest("request-folders", "listFolders"), true).join()
        val foldersResponse = listener.nextMessage()
        assertThat(foldersResponse).contains("\"folders\":")
        assertThat(foldersResponse).contains("\"name\":\"Research\"")

        webSocket.sendText(controlTaskFolderRequest("request-list", "listThreads", folderId = folder.id), true).join()
        val listResponse = listener.nextMessage()
        assertThat(listResponse).contains("\"threadId\":${sessionId.jsonString()}")
        assertThat(listResponse).contains("\"folderId\":${folder.id.jsonString()}")

        webSocket.sendText(
          controlTaskFolderRequest("request-set", "setMetadata", folderId = folder.id, key = "review", value = "backend"),
          true,
        ).join()
        val setResponse = listener.nextMessage()
        assertThat(setResponse).contains("\"changed\":true")
        assertThat(folderService.getFolder(folder.id)?.metadata).containsEntry("review", "backend")

        webSocket.sendText(controlTaskFolderRequest("request-delete-metadata", "deleteMetadata", folderId = folder.id, key = "review"),
                           true).join()
        val deleteMetadataResponse = listener.nextMessage()
        assertThat(deleteMetadataResponse).contains("\"changed\":true")
        assertThat(folderService.getFolder(folder.id)?.metadata).doesNotContainKey("review")

        webSocket.sendText(
          controlTaskFolderRequest(
            requestId = "request-duplicate-create",
            operation = "createAndAssign",
            name = "Should not be created",
            metadata = mapOf("issue" to "IJPL-000000"),
          ),
          true,
        ).join()
        val duplicateCreateResponse = listener.nextMessage()
        assertThat(duplicateCreateResponse).contains("\"created\":false")
        assertThat(folderService.getFolderForThread(projectDir.toString(), PI_AGENT_SESSION_PROVIDER, sessionId)?.id).isEqualTo(folder.id)

        webSocket.sendText(controlTaskFolderRequest("request-unassign", "unassignCurrentThread"), true).join()
        val unassignResponse = listener.nextMessage()
        assertThat(unassignResponse).contains("\"changed\":true")
        assertThat(folderService.getFolderForThread(projectDir.toString(), PI_AGENT_SESSION_PROVIDER, sessionId)).isNull()

        webSocket.sendText(
          controlTaskFolderRequest(
            requestId = "request-create",
            operation = "createAndAssign",
            name = "Follow-up task",
            metadata = mapOf("issue" to "IJPL-999999"),
          ),
          true,
        ).join()
        val createResponse = listener.nextMessage()
        val createdFolder = requireNotNull(folderService.getFolderForThread(projectDir.toString(), PI_AGENT_SESSION_PROVIDER, sessionId))
        assertThat(createResponse).contains("\"requestId\":\"request-create\"")
        assertThat(createResponse).contains("\"ok\":true")
        assertThat(createResponse).contains("\"created\":true")
        assertThat(createResponse).contains("\"assigned\":true")
        assertThat(createResponse).contains("\"id\":${createdFolder.id.jsonString()}")
        assertThat(createdFolder.name).isEqualTo("Follow-up task")
        assertThat(createdFolder.metadata).containsEntry("issue", "IJPL-999999")

        webSocket.sendText(controlTaskFolderRequest("request-rename", "rename", name = "Renamed task"), true).join()
        val renameResponse = listener.nextMessage()
        assertThat(renameResponse).contains("\"changed\":true")
        assertThat(folderService.getFolder(createdFolder.id)?.name).isEqualTo("Renamed task")

        webSocket.sendText(controlTaskFolderRequest("request-assign-existing", "assignCurrentThread", folderId = folder.id), true).join()
        val assignResponse = listener.nextMessage()
        assertThat(assignResponse).contains("\"changed\":true")
        assertThat(folderService.getFolderForThread(projectDir.toString(), PI_AGENT_SESSION_PROVIDER, sessionId)?.id).isEqualTo(folder.id)

        val otherProjectFolder = requireNotNull(folderService.createFolder(tempDir.resolve("other-project").toString(), "Other"))
        webSocket.sendText(controlTaskFolderRequest("request-cross-path", "listThreads", folderId = otherProjectFolder.id), true).join()
        val crossPathResponse = listener.nextMessage()
        assertThat(crossPathResponse).contains("\"ok\":false")
        assertThat(crossPathResponse).contains("Task folder is not available")

        val emptyFolder = requireNotNull(folderService.createFolder(projectDir.toString(), "Empty"))
        webSocket.sendText(controlTaskFolderRequest("request-mark-done", "markDone", folderId = emptyFolder.id), true).join()
        val markDoneResponse = listener.nextMessage()
        assertThat(markDoneResponse).contains("\"changed\":true")
        assertThat(markDoneResponse).contains("\"requestedCount\":0")
        assertThat(folderService.getFolder(emptyFolder.id)?.status).isEqualTo(AgentTaskFolderStatus.DONE)

        webSocket.sendText(controlTaskFolderRequest("request-delete-folder", "delete", folderId = emptyFolder.id), true).join()
        val deleteFolderResponse = listener.nextMessage()
        assertThat(deleteFolderResponse).contains("\"changed\":true")
        assertThat(folderService.getFolder(emptyFolder.id)).isNull()
      }
      finally {
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join()
      }
    }

  @Test
  fun `unsupported control message type is rejected by typed routing`(@TestDisposable disposable: Disposable): Unit =
    runBlocking(Dispatchers.Default) {
      registerControlHandler(disposable)
      val sessionId = "session-ws-unsupported"
      val projectDir = tempDir.resolve("project-unsupported")
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

        webSocket.sendText("{\"type\":\"unknownControl\",\"requestId\":\"request-unsupported\"}", true).join()
        val unsupportedResponse = listener.nextMessage()
        assertThat(unsupportedResponse).contains("\"type\":\"response\"")
        assertThat(unsupportedResponse).contains("\"requestId\":\"request-unsupported\"")
        assertThat(unsupportedResponse).contains("\"ok\":false")
        assertThat(unsupportedResponse).contains("Unsupported control message type")
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
    ExtensionTestUtil.maskExtensions(PiControlRequestHandler.EP_NAME, listOf(PiTaskFolderControlHandler()), disposable)
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

private fun controlTaskFolderRequest(
  requestId: String,
  operation: String,
  folderId: String? = null,
  name: String? = null,
  key: String? = null,
  value: String? = null,
  includeDone: Boolean? = null,
  metadata: Map<String, String>? = null,
): String {
  val arguments = mutableListOf<String>()
  folderId?.let { arguments += "\"folderId\":${it.jsonString()}" }
  name?.let { arguments += "\"name\":${it.jsonString()}" }
  key?.let { arguments += "\"key\":${it.jsonString()}" }
  value?.let { arguments += "\"value\":${it.jsonString()}" }
  includeDone?.let { arguments += "\"includeDone\":$it" }
  metadata?.let { values ->
    val metadataFields = values.entries.joinToString(",") { (metadataKey, metadataValue) ->
      "${metadataKey.jsonString()}:${metadataValue.jsonString()}"
    }
    arguments += "\"metadata\":{$metadataFields}"
  }
  val fields = mutableListOf(
    "\"type\":\"taskFolderRequest\"",
    "\"requestId\":${requestId.jsonString()}",
    "\"operation\":${operation.jsonString()}",
    "\"arguments\":{${arguments.joinToString(",")}}",
  )
  return "{${fields.joinToString(",")}}"
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
