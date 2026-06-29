// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.pi.sessions

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.json.createJsonGenerator
import com.intellij.platform.ai.agent.json.createJsonParser
import com.intellij.platform.ai.agent.json.forEachJsonObjectField
import com.intellij.platform.ai.agent.json.readJsonLongOrNull
import com.intellij.platform.ai.agent.json.readJsonStringOrNull
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import org.jetbrains.annotations.ApiStatus
import java.io.StringWriter

internal enum class PiControlMessageType(@JvmField val wireName: String) {
  HELLO("hello"),
  SESSION_STATE("sessionState"),
  RESPONSE("response"),
  NAVIGATE_TREE("navigateTree"),
  FORK_FROM_ENTRY("forkFromEntry");

  companion object {
    private val byWireName: Map<String, PiControlMessageType> = entries.associateBy(PiControlMessageType::wireName)

    fun fromWireName(wireName: String?): PiControlMessageType? {
      return wireName?.trim()?.let(byWireName::get)
    }
  }
}

@ApiStatus.Internal
data class PiControlSessionContext(
  @JvmField val projectPath: String,
  @JvmField val sessionId: String,
)

@ApiStatus.Internal
data class PiControlExtensionRequest(
  @JvmField val operation: String? = null,
  @JvmField val arguments: PiControlRequestArguments? = null,
)

internal data class PiControlCapabilities(
  @JvmField val navigateTree: Boolean,
  @JvmField val fork: Boolean,
) {
  fun describe(): String = "navigateTree=$navigateTree,fork=$fork"

  companion object {
    val EMPTY: PiControlCapabilities = PiControlCapabilities(navigateTree = false, fork = false)
  }
}

internal data class PiControlResponse(
  @JvmField val ok: Boolean,
  @JvmField val cancelled: Boolean,
  @JvmField val error: String?,
  @JvmField val thread: AgentSessionThread?,
)

internal data class PiControlPayload(
  @JvmField val type: PiControlMessageType? = null,
  @JvmField val typeName: String? = null,
  @JvmField val requestId: String? = null,
  @JvmField val token: String? = null,
  @JvmField val sessionId: String? = null,
  @JvmField val cwd: String? = null,
  @JvmField val ok: Boolean? = null,
  @JvmField val cancelled: Boolean? = null,
  @JvmField val error: String? = null,
  @JvmField val thread: PiControlThreadPayload? = null,
  @JvmField val capabilities: PiControlCapabilities? = null,
  @JvmField val operation: String? = null,
  @JvmField val arguments: PiControlRequestArguments? = null,
)

@ApiStatus.Internal
data class PiControlRequestArguments(
  @JvmField val folderId: String? = null,
  @JvmField val name: String? = null,
  @JvmField val key: String? = null,
  @JvmField val value: String? = null,
  @JvmField val includeDone: Boolean? = null,
  @JvmField val metadata: Map<String, String>? = null,
)

internal data class PiControlThreadPayload(
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
      activityReport = AgentThreadActivityReport(activity ?: AgentThreadActivity.READY),
      provider = PI_AGENT_SESSION_PROVIDER,
    )
  }
}

internal fun parsePiControlPayload(content: String): PiControlPayload? {
  return try {
    PI_CONTROL_JSON_FACTORY.createJsonParser(content).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) return null
      readControlPayload(parser)
    }
  }
  catch (_: Exception) {
    null
  }
}

internal fun buildPiControlCommand(type: PiControlMessageType, requestId: String, sessionId: String, itemId: String): String {
  return buildPiControlJsonObject { generator ->
    generator.writeStringProperty("type", type.wireName)
    generator.writeStringProperty("requestId", requestId)
    generator.writeStringProperty("sessionId", sessionId)
    generator.writeStringProperty("entryId", itemId)
    if (type == PiControlMessageType.FORK_FROM_ENTRY) {
      generator.writeStringProperty("position", "at")
    }
  }
}

internal fun buildPiControlHelloAcknowledgement(requestId: String?, sessionId: String): String {
  return buildPiControlJsonObject { generator ->
    generator.writeStringProperty("type", PiControlMessageType.HELLO.wireName)
    requestId?.let { generator.writeStringProperty("requestId", it) }
    generator.writeBooleanProperty("ok", true)
    generator.writeStringProperty("sessionId", sessionId)
  }
}

@ApiStatus.Internal
fun buildPiControlErrorResponse(requestId: String?, error: String): String {
  return buildPiControlJsonObject { generator ->
    generator.writeStringProperty("type", PiControlMessageType.RESPONSE.wireName)
    requestId?.let { generator.writeStringProperty("requestId", it) }
    generator.writeBooleanProperty("ok", false)
    generator.writeStringProperty("error", error)
  }
}

@ApiStatus.Internal
fun buildPiControlResultResponse(requestId: String, writeResult: (tools.jackson.core.JsonGenerator) -> Unit): String {
  return buildPiControlJsonObject { generator ->
    generator.writeStringProperty("type", PiControlMessageType.RESPONSE.wireName)
    generator.writeStringProperty("requestId", requestId)
    generator.writeBooleanProperty("ok", true)
    generator.writeName("result")
    generator.writeStartObject()
    writeResult(generator)
    generator.writeEndObject()
  }
}

internal fun buildPiControlJsonObject(builder: (tools.jackson.core.JsonGenerator) -> Unit): String {
  val writer = StringWriter()
  PI_CONTROL_JSON_FACTORY.createJsonGenerator(writer).use { generator ->
    generator.writeStartObject()
    builder(generator)
    generator.writeEndObject()
  }
  return writer.toString()
}

private fun readControlPayload(parser: JsonParser): PiControlPayload {
  var type: PiControlMessageType? = null
  var typeName: String? = null
  var requestId: String? = null
  var token: String? = null
  var sessionId: String? = null
  var cwd: String? = null
  var ok: Boolean? = null
  var cancelled: Boolean? = null
  var error: String? = null
  var thread: PiControlThreadPayload? = null
  var capabilities: PiControlCapabilities? = null
  var operation: String? = null
  var arguments: PiControlRequestArguments? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "type" -> {
        val wireName = readJsonStringOrNull(parser)
        typeName = wireName
        type = PiControlMessageType.fromWireName(wireName)
      }
      "requestId" -> requestId = readJsonStringOrNull(parser)
      "token" -> token = readJsonStringOrNull(parser)
      "sessionId" -> sessionId = readJsonStringOrNull(parser)
      "cwd" -> cwd = readJsonStringOrNull(parser)
      "ok" -> ok = readJsonBooleanOrNull(parser)
      "cancelled" -> cancelled = readJsonBooleanOrNull(parser)
      "error" -> error = readJsonStringOrNull(parser)
      "thread" -> thread = readControlThreadPayload(parser)
      "capabilities" -> capabilities = readControlCapabilities(parser)
      "operation" -> operation = readJsonStringOrNull(parser)
      "arguments" -> arguments = readControlRequestArguments(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return PiControlPayload(
    type = type,
    typeName = typeName,
    requestId = requestId,
    token = token,
    sessionId = sessionId,
    cwd = cwd,
    ok = ok,
    cancelled = cancelled,
    error = error,
    thread = thread,
    capabilities = capabilities,
    operation = operation,
    arguments = arguments,
  )
}

private fun readControlRequestArguments(parser: JsonParser): PiControlRequestArguments? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }
  var folderId: String? = null
  var name: String? = null
  var key: String? = null
  var value: String? = null
  var includeDone: Boolean? = null
  var metadata: Map<String, String>? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "folderId" -> folderId = readJsonStringOrNull(parser)
      "name" -> name = readJsonStringOrNull(parser)
      "key" -> key = readJsonStringOrNull(parser)
      "value" -> value = readJsonStringOrNull(parser)
      "includeDone" -> includeDone = readJsonBooleanOrNull(parser)
      "metadata" -> metadata = readControlStringMap(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return PiControlRequestArguments(
    folderId = folderId,
    name = name,
    key = key,
    value = value,
    includeDone = includeDone,
    metadata = metadata,
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

private fun readControlStringMap(parser: JsonParser): Map<String, String>? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }
  val result = LinkedHashMap<String, String>()
  forEachJsonObjectField(parser) { fieldName ->
    when (parser.currentToken()) {
      JsonToken.VALUE_STRING -> readJsonStringOrNull(parser)?.let { value -> result[fieldName] = value }
      else -> parser.skipChildren()
    }
    true
  }
  return result
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

private val PI_CONTROL_JSON_FACTORY = JsonFactory()
