// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.agent.workbench.codex.ide

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream

internal class CodexIdeContextIpcProtocol(
  private val contextCollector: suspend (workspaceRoot: String) -> CodexIdeContext?,
  private val jsonFactory: JsonFactory = JsonFactory(),
) {
  suspend fun handlePayload(payload: ByteArray): ByteArray? {
    val request = try {
      parseRequest(payload)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (_: Exception) {
      return errorResponse(requestId = "", error = "invalid-request")
    } ?: return null

    if (request.type != REQUEST_TYPE) {
      return null
    }

    val requestId = request.requestId?.takeIf { it.isNotBlank() }
      ?: return errorResponse(requestId = "", error = "invalid-request")
    if (request.method != IDE_CONTEXT_METHOD) {
      return errorResponse(requestId = requestId, error = "no-handler-for-request")
    }
    val version = request.version
      ?: return errorResponse(requestId = requestId, error = "invalid-request")
    if (version != 0) {
      return errorResponse(requestId = requestId, error = "request-version-mismatch")
    }

    val workspaceRoot = request.workspaceRoot
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?: return errorResponse(requestId = requestId, error = "invalid-request")
    val context = contextCollector(workspaceRoot)
      ?: return errorResponse(requestId = requestId, error = "no-client-found")

    return successResponse(requestId, context)
  }

  private fun parseRequest(payload: ByteArray): CodexIdeContextIpcRequest? {
    jsonFactory.createParser(payload).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        return null
      }

      var type: String? = null
      var requestId: String? = null
      var method: String? = null
      var version: Int? = null
      var workspaceRoot: String? = null
      readObjectFields(parser) { fieldName ->
        when (fieldName) {
          "type" -> type = readStringOrNull(parser)
          "requestId" -> requestId = readStringOrNull(parser)
          "method" -> method = readStringOrNull(parser)
          "version" -> version = readIntOrNull(parser)
          "params" -> workspaceRoot = parseWorkspaceRootParam(parser)
          else -> parser.skipChildren()
        }
      }
      return CodexIdeContextIpcRequest(
        type = type,
        requestId = requestId,
        method = method,
        version = version,
        workspaceRoot = workspaceRoot,
      )
    }
  }

  private fun parseWorkspaceRootParam(parser: JsonParser): String? {
    if (parser.currentToken != JsonToken.START_OBJECT) {
      parser.skipChildren()
      return null
    }

    var workspaceRoot: String? = null
    readObjectFields(parser) { fieldName ->
      if (fieldName == "workspaceRoot") {
        workspaceRoot = readStringOrNull(parser)
      }
      else {
        parser.skipChildren()
      }
    }
    return workspaceRoot
  }

  private fun successResponse(requestId: String, context: CodexIdeContext): ByteArray {
    return writeResponse { generator ->
      writeResponseBase(generator, requestId)
      generator.writeStringField("resultType", "success")
      generator.writeStringField("method", IDE_CONTEXT_METHOD)
      generator.writeObjectFieldStart("result")
      generator.writeStringField("type", "broadcast")
      generator.writeFieldName("ideContext")
      writeIdeContext(generator, context)
      generator.writeEndObject()
      generator.writeEndObject()
    }
  }

  private fun errorResponse(requestId: String, error: String): ByteArray {
    return writeResponse { generator ->
      writeResponseBase(generator, requestId)
      generator.writeStringField("resultType", "error")
      generator.writeStringField("error", error)
      generator.writeEndObject()
    }
  }

  private fun writeResponse(writeBody: (JsonGenerator) -> Unit): ByteArray {
    val out = ByteArrayOutputStream()
    jsonFactory.createGenerator(out).use { generator ->
      generator.writeStartObject()
      writeBody(generator)
    }
    return out.toByteArray()
  }

  private fun writeResponseBase(generator: JsonGenerator, requestId: String) {
    generator.writeStringField("type", "response")
    generator.writeStringField("requestId", requestId)
  }

  private fun writeIdeContext(generator: JsonGenerator, context: CodexIdeContext) {
    generator.writeStartObject()
    context.activeFile?.let { activeFile ->
      generator.writeFieldName("activeFile")
      writeActiveFile(generator, activeFile)
    }
    generator.writeArrayFieldStart("openTabs")
    for (tab in context.openTabs) {
      writeFileDescriptor(generator, tab)
    }
    generator.writeEndArray()
    generator.writeEndObject()
  }

  private fun writeActiveFile(generator: JsonGenerator, file: CodexIdeActiveFile) {
    generator.writeStartObject()
    writeFileDescriptorFields(generator, file.label, file.path, file.fsPath)
    generator.writeFieldName("selection")
    writeRange(generator, file.selection)
    generator.writeStringField("activeSelectionContent", file.activeSelectionContent)
    generator.writeArrayFieldStart("selections")
    for (selection in file.selections) {
      writeRange(generator, selection)
    }
    generator.writeEndArray()
    generator.writeEndObject()
  }

  private fun writeFileDescriptor(generator: JsonGenerator, file: CodexIdeFileDescriptor) {
    generator.writeStartObject()
    writeFileDescriptorFields(generator, file.label, file.path, file.fsPath)
    generator.writeEndObject()
  }

  private fun writeFileDescriptorFields(generator: JsonGenerator, label: String, path: String, fsPath: String) {
    generator.writeStringField("label", label)
    generator.writeStringField("path", path)
    generator.writeStringField("fsPath", fsPath)
  }

  private fun writeRange(generator: JsonGenerator, range: CodexIdeRange) {
    generator.writeStartObject()
    generator.writeFieldName("start")
    writePosition(generator, range.start)
    generator.writeFieldName("end")
    writePosition(generator, range.end)
    generator.writeEndObject()
  }

  private fun writePosition(generator: JsonGenerator, position: CodexIdePosition) {
    generator.writeStartObject()
    generator.writeNumberField("line", position.line)
    generator.writeNumberField("character", position.character)
    generator.writeEndObject()
  }
}

private data class CodexIdeContextIpcRequest(
  @JvmField val type: String?,
  @JvmField val requestId: String?,
  @JvmField val method: String?,
  @JvmField val version: Int?,
  @JvmField val workspaceRoot: String?,
)

private fun readObjectFields(parser: JsonParser, handleField: (String) -> Unit) {
  while (true) {
    val token = parser.nextToken() ?: return
    if (token == JsonToken.END_OBJECT) {
      return
    }
    if (token != JsonToken.FIELD_NAME) {
      parser.skipChildren()
      continue
    }
    val fieldName = parser.currentName
    parser.nextToken()
    handleField(fieldName)
  }
}

private fun readStringOrNull(parser: JsonParser): String? {
  val result = if (parser.currentToken == JsonToken.VALUE_STRING) parser.text else null
  parser.skipChildren()
  return result
}

private fun readIntOrNull(parser: JsonParser): Int? {
  val result = when (parser.currentToken) {
    JsonToken.VALUE_NUMBER_INT -> parser.intValue
    JsonToken.VALUE_STRING -> parser.text.toIntOrNull()
    else -> null
  }
  parser.skipChildren()
  return result
}

private const val REQUEST_TYPE: String = "request"
private const val IDE_CONTEXT_METHOD: String = "ide-context"
