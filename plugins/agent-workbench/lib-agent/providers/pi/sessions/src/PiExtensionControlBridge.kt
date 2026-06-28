// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.pi.sessions

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.sessions.core.folders.AgentTaskFolder
import com.intellij.platform.ai.agent.sessions.core.folders.AgentTaskFolderService
import com.intellij.platform.ai.agent.sessions.core.folders.AgentTaskFolderThreadAssignment
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.platform.ai.agent.json.createJsonGenerator
import com.intellij.platform.ai.agent.json.createJsonParser
import com.intellij.platform.ai.agent.json.forEachJsonObjectField
import com.intellij.platform.ai.agent.json.readJsonLongOrNull
import com.intellij.platform.ai.agent.json.readJsonStringOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.io.jsonRpc.Client
import org.jetbrains.io.jsonRpc.MessageServer
import org.jetbrains.io.sendPlainText
import org.jetbrains.io.webSocket.WebSocketClient
import org.jetbrains.io.webSocket.WebSocketHandshakeHandler
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import java.io.StringWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

private val CONTROL_LOG = logger<PiExtensionControlBridge>()
private val PI_CONTROL_CONNECTION_KEY: Key<PiControlConnection> = Key.create("agent.workbench.pi.control.connection")

internal object PiExtensionControlBridge {
  private val jsonFactory = JsonFactory()
  private val connectionsBySessionId = ConcurrentHashMap<String, PiControlConnection>()
  private val controlUpdates = MutableSharedFlow<AgentSessionSourceUpdateEvent>(
    extraBufferCapacity = 64,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  val updateEvents: Flow<AgentSessionSourceUpdateEvent> = controlUpdates

  fun canNavigateThreadOutlineItem(path: String, threadId: String, itemId: String): Boolean {
    return resolveConnection(path = path, threadId = threadId, itemId = itemId)?.capabilities?.navigateTree == true
  }

  suspend fun navigateThreadOutlineItem(path: String, threadId: String, itemId: String): Boolean {
    val response = sendCommand(path = path, threadId = threadId, itemId = itemId, type = PI_CONTROL_NAVIGATE_TREE_TYPE)
    return response?.ok == true && !response.cancelled
  }

  fun canForkThreadFromOutlineItem(path: String, threadId: String, itemId: String): Boolean {
    return resolveConnection(path = path, threadId = threadId, itemId = itemId)?.capabilities?.fork == true
  }

  fun currentThreadUpdateEvent(path: String, threadId: String): AgentSessionSourceUpdateEvent? {
    val normalizedPath = normalizePiProjectPath(path) ?: return null
    val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return null
    val connection = connectionsBySessionId[normalizedThreadId]?.takeIf { it.projectPath == normalizedPath } ?: return null
    return buildControlUpdateEvent(projectPath = connection.projectPath, sessionId = connection.sessionId)
  }

  suspend fun forkThreadFromOutlineItem(path: String, threadId: String, itemId: String): AgentSessionThread? {
    val response = sendCommand(path = path, threadId = threadId, itemId = itemId, type = PI_CONTROL_FORK_FROM_ENTRY_TYPE)
    if (response?.ok != true || response.cancelled) {
      return null
    }
    return response.thread
  }

  fun invalidateSession(sessionId: String) {
    val normalizedSessionId = sessionId.trim().takeIf { it.isNotEmpty() } ?: return
    val connection = connectionsBySessionId.remove(normalizedSessionId) ?: return
    if (connection.sessionId == normalizedSessionId) {
      connection.completePending(PiControlResponse(ok = false, cancelled = true, error = "Session was invalidated", thread = null))
    }
  }

  fun handleMessage(client: Client, message: CharSequence) {
    val webSocketClient = client as? WebSocketClient ?: return
    val payload = parseControlPayload(message.toString())
    if (payload == null) {
      CONTROL_LOG.debug("Rejected malformed Pi extension control message")
      sendProtocolError(webSocketClient, requestId = null, error = "Malformed control message")
      return
    }
    when (payload.type) {
      PI_CONTROL_HELLO_TYPE -> handleHello(webSocketClient, payload)
      PI_CONTROL_SESSION_STATE_TYPE -> handleSessionState(webSocketClient, payload)
      PI_CONTROL_RESPONSE_TYPE -> handleResponse(webSocketClient, payload)
      PI_CONTROL_GET_CURRENT_TASK_FOLDER_TYPE,
      PI_CONTROL_LIST_TASK_FOLDER_THREADS_TYPE,
      PI_CONTROL_GET_TASK_FOLDER_METADATA_TYPE,
      PI_CONTROL_SET_TASK_FOLDER_METADATA_TYPE,
      PI_CONTROL_DELETE_TASK_FOLDER_METADATA_TYPE,
        -> handleTaskFolderRequest(webSocketClient, payload)
      else -> sendProtocolError(webSocketClient, requestId = payload.requestId, error = "Unsupported control message type")
    }
  }

  fun handleDisconnected(client: Client) {
    val connection = client.getUserData(PI_CONTROL_CONNECTION_KEY) ?: return
    CONTROL_LOG.info(
      "Pi control socket disconnected for session=${connection.sessionId}, projectPath=${connection.projectPath}, " +
      "capabilities=${connection.capabilities.describe()}"
    )
    unregisterConnection(connection)
    emitControlUpdate(projectPath = connection.projectPath, sessionId = connection.sessionId)
    connection.completePending(PiControlResponse(ok = false, cancelled = true, error = "Control socket disconnected", thread = null))
  }

  private fun handleHello(client: WebSocketClient, payload: PiControlPayload) {
    val token = payload.token?.trim()?.takeIf { it.isNotEmpty() }
    val sessionId = payload.sessionId?.trim()?.takeIf { it.isNotEmpty() }
    val expectedSessionId = PiExtensionStatusBridge.authenticateLaunchToken(token = token, sessionId = sessionId)
    val projectPath = payload.cwd?.let(::normalizePiProjectPath)
    if (token == null || sessionId == null || expectedSessionId == null || projectPath == null) {
      CONTROL_LOG.info("Rejected unauthorized Pi extension control hello for session=${sessionId != null}, cwd=${payload.cwd != null}")
      sendProtocolError(client, requestId = payload.requestId, error = "Unauthorized control hello")
      return
    }

    val connection = PiControlConnection(
      client = client,
      token = token,
      sessionId = sessionId,
      projectPath = projectPath,
      capabilities = payload.capabilities ?: PiControlCapabilities.EMPTY,
    )
    client.putUserData(PI_CONTROL_CONNECTION_KEY, connection)
    registerConnection(connection)
    CONTROL_LOG.info(
      "Registered Pi control socket for session=$sessionId, projectPath=$projectPath, capabilities=${connection.capabilities.describe()}"
    )
    emitControlUpdate(projectPath = projectPath, sessionId = sessionId)
    sendControlText(client, buildHelloAcknowledgement(requestId = payload.requestId, sessionId = sessionId))
  }

  private fun handleSessionState(client: WebSocketClient, payload: PiControlPayload) {
    val connection = client.getUserData(PI_CONTROL_CONNECTION_KEY) ?: return
    val sessionId = payload.sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val projectPath = payload.cwd?.let(::normalizePiProjectPath) ?: return
    val capabilities = payload.capabilities ?: connection.capabilities
    CONTROL_LOG.debug(
      "Received Pi control session state for session=$sessionId, projectPath=$projectPath, capabilities=${capabilities.describe()}"
    )
    updateConnectionState(connection, sessionId = sessionId, projectPath = projectPath, capabilities = capabilities)
  }

  private fun handleResponse(client: WebSocketClient, payload: PiControlPayload) {
    val connection = client.getUserData(PI_CONTROL_CONNECTION_KEY) ?: return
    val requestId = payload.requestId?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val pending = connection.pendingRequests.remove(requestId) ?: return
    val thread = payload.thread?.toAgentSessionThread()
    CONTROL_LOG.debug(
      "Received Pi control response for session=${connection.sessionId}, requestId=$requestId, ok=${payload.ok}, " +
      "cancelled=${payload.cancelled == true}, replacementThreadId=${thread?.id}"
    )
    if (thread != null) {
      updateConnectionState(
        connection = connection,
        sessionId = thread.id,
        projectPath = connection.projectPath,
        capabilities = connection.capabilities,
      )
    }
    pending.complete(
      PiControlResponse(
        ok = payload.ok == true,
        cancelled = payload.cancelled == true,
        error = payload.error,
        thread = thread,
      )
    )
  }

  private fun handleTaskFolderRequest(client: WebSocketClient, payload: PiControlPayload) {
    val connection = client.getUserData(PI_CONTROL_CONNECTION_KEY)
    val requestId = payload.requestId?.trim()?.takeIf { it.isNotEmpty() }
    if (connection == null || requestId == null) {
      sendProtocolError(client, requestId = requestId, error = "Task folder request requires an authenticated control connection")
      return
    }

    val service = service<AgentTaskFolderService>()
    val response = when (payload.type) {
      PI_CONTROL_GET_CURRENT_TASK_FOLDER_TYPE -> {
        val folder = service.getFolderForThread(connection.projectPath, PI_AGENT_SESSION_PROVIDER, connection.sessionId)
        buildTaskFolderResponse(requestId = requestId, folder = folder)
      }
      PI_CONTROL_LIST_TASK_FOLDER_THREADS_TYPE -> {
        val folderId = resolveTaskFolderId(service, connection, payload)
        if (folderId == null) {
          buildErrorResponse(requestId, "Task folder is not available")
        }
        else {
          buildTaskFolderAssignmentsResponse(requestId, service.listFolderThreadAssignments(connection.projectPath, folderId))
        }
      }
      PI_CONTROL_GET_TASK_FOLDER_METADATA_TYPE -> {
        val folder = resolveTaskFolder(service, connection, payload)
        if (folder == null) buildErrorResponse(requestId, "Task folder is not available") else buildTaskFolderMetadataResponse(requestId, folder.metadata)
      }
      PI_CONTROL_SET_TASK_FOLDER_METADATA_TYPE -> {
        val folderId = resolveTaskFolderId(service, connection, payload)
        val key = payload.key?.trim()?.takeIf { it.isNotEmpty() }
        val value = payload.value
        if (folderId == null || key == null || value == null) {
          buildErrorResponse(requestId, "Task folder metadata request is incomplete")
        }
        else {
          val changed = service.setMetadata(connection.projectPath, folderId, key, value)
          buildMutationResponse(requestId, changed)
        }
      }
      PI_CONTROL_DELETE_TASK_FOLDER_METADATA_TYPE -> {
        val folderId = resolveTaskFolderId(service, connection, payload)
        val key = payload.key?.trim()?.takeIf { it.isNotEmpty() }
        if (folderId == null || key == null) {
          buildErrorResponse(requestId, "Task folder metadata request is incomplete")
        }
        else {
          val changed = service.deleteMetadata(connection.projectPath, folderId, key)
          buildMutationResponse(requestId, changed)
        }
      }
      else -> buildErrorResponse(requestId, "Unsupported task folder request")
    }
    sendControlText(client, response)
  }

  private suspend fun sendCommand(path: String, threadId: String, itemId: String, type: String): PiControlResponse? {
    val connection = resolveConnection(path = path, threadId = threadId, itemId = itemId)
    if (connection == null) {
      CONTROL_LOG.debug("No Pi control socket for type=$type, path=$path, threadId=$threadId, itemIdBlank=${itemId.isBlank()}")
      return null
    }
    val requestId = UUID.randomUUID().toString()
    val pending = CompletableDeferred<PiControlResponse>()
    connection.pendingRequests[requestId] = pending
    val sent = sendControlText(
      client = connection.client,
      text = buildControlCommand(type = type, requestId = requestId, sessionId = connection.sessionId, itemId = itemId.trim()),
    )
    if (!sent) {
      connection.pendingRequests.remove(requestId, pending)
      CONTROL_LOG.debug("Failed to send Pi control command type=$type, session=${connection.sessionId}, requestId=$requestId")
      return null
    }
    CONTROL_LOG.debug("Sent Pi control command type=$type, session=${connection.sessionId}, requestId=$requestId")
    return try {
      val response = withTimeoutOrNull(PI_CONTROL_REQUEST_TIMEOUT) {
        pending.await()
      }
      if (response == null) {
        CONTROL_LOG.debug("Timed out waiting for Pi control response type=$type, session=${connection.sessionId}, requestId=$requestId")
      }
      response
    }
    finally {
      connection.pendingRequests.remove(requestId, pending)
    }
  }

  private fun resolveConnection(path: String, threadId: String, itemId: String): PiControlConnection? {
    val normalizedPath = normalizePiProjectPath(path) ?: return null
    val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return null
    if (itemId.isBlank()) {
      return null
    }
    val connection = connectionsBySessionId[normalizedThreadId] ?: return null
    return connection.takeIf { it.projectPath == normalizedPath }
  }

  private fun registerConnection(connection: PiControlConnection) {
    connectionsBySessionId[connection.sessionId] = connection
  }

  private fun unregisterConnection(connection: PiControlConnection) {
    CONTROL_LOG.info("Unregistered Pi control socket for session=${connection.sessionId}, projectPath=${connection.projectPath}")
    connectionsBySessionId.remove(connection.sessionId, connection)
  }

  private fun updateConnectionState(
    connection: PiControlConnection,
    sessionId: String,
    projectPath: String,
    capabilities: PiControlCapabilities,
  ) {
    val previousSessionId = connection.sessionId
    val previousProjectPath = connection.projectPath
    val previousCapabilities = connection.capabilities
    connection.sessionId = sessionId
    connection.projectPath = projectPath
    connection.capabilities = capabilities
    if (previousSessionId != sessionId) {
      PiExtensionStatusBridge.rebindLaunchToken(token = connection.token, previousSessionId = previousSessionId, sessionId = sessionId)
      connectionsBySessionId.remove(previousSessionId, connection)
    }
    connectionsBySessionId[sessionId] = connection
    if (previousSessionId != sessionId || previousProjectPath != projectPath || previousCapabilities != capabilities) {
      CONTROL_LOG.info(
        "Updated Pi control socket from session=$previousSessionId, projectPath=$previousProjectPath, " +
        "capabilities=${previousCapabilities.describe()} to session=$sessionId, projectPath=$projectPath, " +
        "capabilities=${capabilities.describe()}"
      )
      emitControlUpdate(projectPath = previousProjectPath, sessionId = previousSessionId)
      emitControlUpdate(projectPath = projectPath, sessionId = sessionId)
    }
  }

  private fun emitControlUpdate(projectPath: String, sessionId: String) {
    controlUpdates.tryEmit(buildControlUpdateEvent(projectPath = projectPath, sessionId = sessionId))
  }

  private fun buildControlUpdateEvent(projectPath: String, sessionId: String): AgentSessionSourceUpdateEvent {
    return AgentSessionSourceUpdateEvent.hintsChanged(
      scopedPaths = setOf(projectPath),
      threadIds = setOf(sessionId),
    )
  }

  private fun parseControlPayload(content: String): PiControlPayload? {
    return try {
      jsonFactory.createJsonParser(content).use { parser ->
        if (parser.nextToken() != JsonToken.START_OBJECT) return null
        readControlPayload(parser)
      }
    }
    catch (e: Exception) {
      CONTROL_LOG.debug("Failed to parse Pi extension control payload", e)
      null
    }
  }
}

internal class PiExtensionControlWebSocketHandler : WebSocketHandshakeHandler() {
  private val messageServer = MessageServer { client, message -> PiExtensionControlBridge.handleMessage(client, message) }

  override fun isSupported(request: FullHttpRequest): Boolean {
    return request.method() === HttpMethod.GET &&
           super.isSupported(request) &&
           checkPrefix(request.uri(), PI_CONTROL_ENDPOINT_PREFIX)
  }

  override fun process(
    urlDecoder: QueryStringDecoder,
    request: FullHttpRequest,
    context: ChannelHandlerContext,
  ): Boolean {
    val token = bearerToken(request)
    if (PiExtensionStatusBridge.authenticateLaunchToken(token = token) == null) {
      CONTROL_LOG.info("Rejected unauthorized Pi extension control WebSocket handshake")
      HttpResponseStatus.UNAUTHORIZED.sendPlainText(context.channel(), request)
      return true
    }
    CONTROL_LOG.info("Accepted Pi extension control WebSocket handshake")
    return super.process(urlDecoder, request, context)
  }

  override fun getMessageServer(): MessageServer = messageServer

  override fun disconnected(client: Client) {
    PiExtensionControlBridge.handleDisconnected(client)
  }
}

private class PiControlConnection(
  @JvmField val client: WebSocketClient,
  @JvmField val token: String,
  @Volatile var sessionId: String,
  @Volatile var projectPath: String,
  @Volatile var capabilities: PiControlCapabilities,
) {
  val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<PiControlResponse>>()

  fun completePending(response: PiControlResponse) {
    val pending = ArrayList(pendingRequests.values)
    pendingRequests.clear()
    pending.forEach { request -> request.complete(response) }
  }
}

private data class PiControlCapabilities(
  @JvmField val navigateTree: Boolean,
  @JvmField val fork: Boolean,
) {
  fun describe(): String = "navigateTree=$navigateTree,fork=$fork"

  companion object {
    val EMPTY: PiControlCapabilities = PiControlCapabilities(navigateTree = false, fork = false)
  }
}

private data class PiControlResponse(
  @JvmField val ok: Boolean,
  @JvmField val cancelled: Boolean,
  @JvmField val error: String?,
  @JvmField val thread: AgentSessionThread?,
)

private data class PiControlPayload(
  @JvmField val type: String? = null,
  @JvmField val requestId: String? = null,
  @JvmField val token: String? = null,
  @JvmField val sessionId: String? = null,
  @JvmField val cwd: String? = null,
  @JvmField val ok: Boolean? = null,
  @JvmField val cancelled: Boolean? = null,
  @JvmField val error: String? = null,
  @JvmField val thread: PiControlThreadPayload? = null,
  @JvmField val capabilities: PiControlCapabilities? = null,
  @JvmField val folderId: String? = null,
  @JvmField val key: String? = null,
  @JvmField val value: String? = null,
)

private data class PiControlThreadPayload(
  @JvmField val id: String? = null,
  @JvmField val title: String? = null,
  @JvmField val updatedAt: Long? = null,
  @JvmField val activity: AgentThreadActivity? = null,
) {
  fun toAgentSessionThread(): AgentSessionThread? {
    val threadId = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return AgentSessionThread(
      id = threadId,
      title = title?.trim()?.takeIf { it.isNotEmpty() } ?: threadId,
      updatedAt = updatedAt ?: System.currentTimeMillis(),
      archived = false,
      activity = activity ?: AgentThreadActivity.READY,
      provider = PI_AGENT_SESSION_PROVIDER,
    )
  }
}

private fun readControlPayload(parser: JsonParser): PiControlPayload {
  var type: String? = null
  var requestId: String? = null
  var token: String? = null
  var sessionId: String? = null
  var cwd: String? = null
  var ok: Boolean? = null
  var cancelled: Boolean? = null
  var error: String? = null
  var thread: PiControlThreadPayload? = null
  var capabilities: PiControlCapabilities? = null
  var folderId: String? = null
  var key: String? = null
  var value: String? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "type" -> type = readJsonStringOrNull(parser)
      "requestId" -> requestId = readJsonStringOrNull(parser)
      "token" -> token = readJsonStringOrNull(parser)
      "sessionId" -> sessionId = readJsonStringOrNull(parser)
      "cwd" -> cwd = readJsonStringOrNull(parser)
      "ok" -> ok = readJsonBooleanOrNull(parser)
      "cancelled" -> cancelled = readJsonBooleanOrNull(parser)
      "error" -> error = readJsonStringOrNull(parser)
      "thread" -> thread = readControlThreadPayload(parser)
      "capabilities" -> capabilities = readControlCapabilities(parser)
      "folderId" -> folderId = readJsonStringOrNull(parser)
      "key" -> key = readJsonStringOrNull(parser)
      "value" -> value = readJsonStringOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return PiControlPayload(
    type = type,
    requestId = requestId,
    token = token,
    sessionId = sessionId,
    cwd = cwd,
    ok = ok,
    cancelled = cancelled,
    error = error,
    thread = thread,
    capabilities = capabilities,
    folderId = folderId,
    key = key,
    value = value,
  )
}

private fun readControlThreadPayload(parser: JsonParser): PiControlThreadPayload? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }
  var id: String? = null
  var title: String? = null
  var updatedAt: Long? = null
  var activity: AgentThreadActivity? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "id", "sessionId" -> id = readJsonStringOrNull(parser)
      "title", "name" -> title = readJsonStringOrNull(parser)
      "updatedAt" -> updatedAt = readJsonLongOrNull(parser)
      "activity" -> activity = readPiControlActivity(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return PiControlThreadPayload(id = id, title = title, updatedAt = updatedAt, activity = activity)
}

private fun readControlCapabilities(parser: JsonParser): PiControlCapabilities? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }
  var navigateTree = false
  var fork = false
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "navigateTree" -> navigateTree = readJsonBooleanOrNull(parser) == true
      "fork" -> fork = readJsonBooleanOrNull(parser) == true
      else -> parser.skipChildren()
    }
    true
  }
  return PiControlCapabilities(navigateTree = navigateTree, fork = fork)
}

@Suppress("DuplicatedCode")
private fun readJsonBooleanOrNull(parser: JsonParser): Boolean? {
  return when (parser.currentToken()) {
    JsonToken.VALUE_TRUE -> true
    JsonToken.VALUE_FALSE -> false
    JsonToken.VALUE_NUMBER_INT -> parser.intValue != 0
    JsonToken.VALUE_STRING -> parser.string.equals("true", ignoreCase = true)
    JsonToken.VALUE_NULL -> null
    else -> {
      parser.skipChildren()
      null
    }
  }
}

@Suppress("DuplicatedCode")
private fun readPiControlActivity(parser: JsonParser): AgentThreadActivity? {
  val value = readJsonStringOrNull(parser) ?: return null
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

private fun buildControlCommand(type: String, requestId: String, sessionId: String, itemId: String): String {
  return buildJsonObject { generator ->
    generator.writeStringProperty("type", type)
    generator.writeStringProperty("requestId", requestId)
    generator.writeStringProperty("sessionId", sessionId)
    generator.writeStringProperty("entryId", itemId)
    if (type == PI_CONTROL_FORK_FROM_ENTRY_TYPE) {
      generator.writeStringProperty("position", "at")
    }
  }
}

private fun buildHelloAcknowledgement(requestId: String?, sessionId: String): String {
  return buildJsonObject { generator ->
    generator.writeStringProperty("type", PI_CONTROL_HELLO_TYPE)
    requestId?.let { generator.writeStringProperty("requestId", it) }
    generator.writeBooleanProperty("ok", true)
    generator.writeStringProperty("sessionId", sessionId)
  }
}

private fun buildTaskFolderResponse(requestId: String, folder: AgentTaskFolder?): String {
  return buildJsonObject { generator ->
    generator.writeStringProperty("type", PI_CONTROL_RESPONSE_TYPE)
    generator.writeStringProperty("requestId", requestId)
    generator.writeBooleanProperty("ok", true)
    generator.writeName("folder")
    writeTaskFolder(generator, folder)
  }
}

private fun buildTaskFolderAssignmentsResponse(requestId: String, assignments: List<AgentTaskFolderThreadAssignment>): String {
  return buildJsonObject { generator ->
    generator.writeStringProperty("type", PI_CONTROL_RESPONSE_TYPE)
    generator.writeStringProperty("requestId", requestId)
    generator.writeBooleanProperty("ok", true)
    generator.writeName("threads")
    generator.writeStartArray()
    assignments.forEach { assignment -> writeTaskFolderAssignment(generator, assignment) }
    generator.writeEndArray()
  }
}

private fun buildTaskFolderMetadataResponse(requestId: String, metadata: Map<String, String>): String {
  return buildJsonObject { generator ->
    generator.writeStringProperty("type", PI_CONTROL_RESPONSE_TYPE)
    generator.writeStringProperty("requestId", requestId)
    generator.writeBooleanProperty("ok", true)
    generator.writeName("metadata")
    generator.writeStartObject()
    metadata.forEach { (key, value) -> generator.writeStringProperty(key, value) }
    generator.writeEndObject()
  }
}

private fun buildMutationResponse(requestId: String, changed: Boolean): String {
  return buildJsonObject { generator ->
    generator.writeStringProperty("type", PI_CONTROL_RESPONSE_TYPE)
    generator.writeStringProperty("requestId", requestId)
    generator.writeBooleanProperty("ok", true)
    generator.writeBooleanProperty("changed", changed)
  }
}

private fun buildErrorResponse(requestId: String, error: String): String {
  return buildJsonObject { generator ->
    generator.writeStringProperty("type", PI_CONTROL_RESPONSE_TYPE)
    generator.writeStringProperty("requestId", requestId)
    generator.writeBooleanProperty("ok", false)
    generator.writeStringProperty("error", error)
  }
}

private fun writeTaskFolder(generator: tools.jackson.core.JsonGenerator, folder: AgentTaskFolder?) {
  if (folder == null) {
    generator.writeNull()
    return
  }
  generator.writeStartObject()
  generator.writeStringProperty("path", folder.path)
  generator.writeStringProperty("id", folder.id)
  generator.writeStringProperty("name", folder.name)
  generator.writeStringProperty("status", folder.status.name)
  generator.writeName("metadata")
  generator.writeStartObject()
  folder.metadata.forEach { (key, value) -> generator.writeStringProperty(key, value) }
  generator.writeEndObject()
  generator.writeNumberProperty("createdAt", folder.createdAt)
  generator.writeNumberProperty("updatedAt", folder.updatedAt)
  generator.writeEndObject()
}

private fun writeTaskFolderAssignment(generator: tools.jackson.core.JsonGenerator, assignment: AgentTaskFolderThreadAssignment) {
  generator.writeStartObject()
  generator.writeStringProperty("path", assignment.path)
  generator.writeStringProperty("provider", assignment.provider.value)
  generator.writeStringProperty("threadId", assignment.threadId)
  generator.writeStringProperty("folderId", assignment.folderId)
  generator.writeNumberProperty("assignedAt", assignment.assignedAt)
  generator.writeEndObject()
}

private fun resolveTaskFolder(
  service: AgentTaskFolderService,
  connection: PiControlConnection,
  payload: PiControlPayload,
): AgentTaskFolder? {
  val folderId = resolveTaskFolderId(service, connection, payload) ?: return null
  return service.getFolder(connection.projectPath, folderId)
}

private fun resolveTaskFolderId(service: AgentTaskFolderService, connection: PiControlConnection, payload: PiControlPayload): String? {
  return payload.folderId?.trim()?.takeIf { it.isNotEmpty() }
         ?: service.getFolderForThread(connection.projectPath, PI_AGENT_SESSION_PROVIDER, connection.sessionId)?.id
}

private fun sendProtocolError(client: WebSocketClient, requestId: String?, error: String) {
  sendControlText(
    client = client,
    text = buildJsonObject { generator ->
      generator.writeStringProperty("type", PI_CONTROL_RESPONSE_TYPE)
      requestId?.let { generator.writeStringProperty("requestId", it) }
      generator.writeBooleanProperty("ok", false)
      generator.writeStringProperty("error", error)
    },
  )
}

private fun buildJsonObject(builder: (tools.jackson.core.JsonGenerator) -> Unit): String {
  val writer = StringWriter()
  PiExtensionControlBridgeJsonFactoryHolder.jsonFactory.createJsonGenerator(writer).use { generator ->
    generator.writeStartObject()
    builder(generator)
    generator.writeEndObject()
  }
  return writer.toString()
}

private object PiExtensionControlBridgeJsonFactoryHolder {
  val jsonFactory = JsonFactory()
}

private fun sendControlText(client: WebSocketClient, text: String): Boolean {
  return try {
    val buffer = ByteBufUtil.writeUtf8(client.byteBufAllocator, text)
    client.sendFrame(buffer, false)
    true
  }
  catch (e: Exception) {
    CONTROL_LOG.debug("Failed to send Pi extension control message", e)
    false
  }
}

private fun bearerToken(request: FullHttpRequest): String? {
  val authorization = request.headers().get(HttpHeaderNames.AUTHORIZATION) ?: return null
  val prefix = "Bearer "
  if (!authorization.startsWith(prefix, ignoreCase = true)) return null
  return authorization.substring(prefix.length)
}

internal const val PI_CONTROL_WS_ENDPOINT_ENVIRONMENT_VARIABLE: String = "AGENT_WORKBENCH_PI_CONTROL_WS_ENDPOINT"
internal const val PI_CONTROL_ENDPOINT_PREFIX: String = "agent-workbench/pi/control"

private const val PI_CONTROL_HELLO_TYPE: String = "hello"
private const val PI_CONTROL_SESSION_STATE_TYPE: String = "sessionState"
private const val PI_CONTROL_RESPONSE_TYPE: String = "response"
private const val PI_CONTROL_NAVIGATE_TREE_TYPE: String = "navigateTree"
private const val PI_CONTROL_FORK_FROM_ENTRY_TYPE: String = "forkFromEntry"
private const val PI_CONTROL_GET_CURRENT_TASK_FOLDER_TYPE: String = "getCurrentTaskFolder"
private const val PI_CONTROL_LIST_TASK_FOLDER_THREADS_TYPE: String = "listTaskFolderThreads"
private const val PI_CONTROL_GET_TASK_FOLDER_METADATA_TYPE: String = "getTaskFolderMetadata"
private const val PI_CONTROL_SET_TASK_FOLDER_METADATA_TYPE: String = "setTaskFolderMetadata"
private const val PI_CONTROL_DELETE_TASK_FOLDER_METADATA_TYPE: String = "deleteTaskFolderMetadata"
private val PI_CONTROL_REQUEST_TIMEOUT = 10.seconds
