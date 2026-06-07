// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.ide

import com.intellij.agent.workbench.json.createJsonGenerator
import com.intellij.agent.workbench.json.createJsonParser
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
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
    jsonFactory.createJsonParser(payload).use { parser ->
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
    if (parser.currentToken() != JsonToken.START_OBJECT) {
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
      generator.writeStringProperty("resultType", "success")
      generator.writeStringProperty("method", IDE_CONTEXT_METHOD)
      generator.writeObjectPropertyStart("result")
      generator.writeStringProperty("type", "broadcast")
      generator.writeName("ideContext")
      writeIdeContext(generator, context)
      generator.writeEndObject()
      generator.writeEndObject()
    }
  }

  private fun errorResponse(requestId: String, error: String): ByteArray {
    return writeResponse { generator ->
      writeResponseBase(generator, requestId)
      generator.writeStringProperty("resultType", "error")
      generator.writeStringProperty("error", error)
      generator.writeEndObject()
    }
  }

  private fun writeResponse(writeBody: (JsonGenerator) -> Unit): ByteArray {
    val out = ByteArrayOutputStream()
    jsonFactory.createJsonGenerator(out).use { generator ->
      generator.writeStartObject()
      writeBody(generator)
    }
    return out.toByteArray()
  }

  private fun writeResponseBase(generator: JsonGenerator, requestId: String) {
    generator.writeStringProperty("type", "response")
    generator.writeStringProperty("requestId", requestId)
  }

  private fun writeIdeContext(generator: JsonGenerator, context: CodexIdeContext) {
    generator.writeStartObject()
    context.activeFile?.let { activeFile ->
      generator.writeName("activeFile")
      writeActiveFile(generator, activeFile)
    }
    generator.writeArrayPropertyStart("openTabs")
    for (tab in context.openTabs) {
      writeFileDescriptor(generator, tab)
    }
    generator.writeEndArray()
    generator.writeEndObject()
  }

  private fun writeActiveFile(generator: JsonGenerator, file: CodexIdeActiveFile) {
    generator.writeStartObject()
    writeFileDescriptorFields(generator, file.label, file.path, file.fsPath)
    generator.writeName("selection")
    writeRange(generator, file.selection)
    generator.writeStringProperty("activeSelectionContent", file.activeSelectionContent)
    generator.writeArrayPropertyStart("selections")
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
    generator.writeStringProperty("label", label)
    generator.writeStringProperty("path", path)
    generator.writeStringProperty("fsPath", fsPath)
  }

  private fun writeRange(generator: JsonGenerator, range: CodexIdeRange) {
    generator.writeStartObject()
    generator.writeName("start")
    writePosition(generator, range.start)
    generator.writeName("end")
    writePosition(generator, range.end)
    generator.writeEndObject()
  }

  private fun writePosition(generator: JsonGenerator, position: CodexIdePosition) {
    generator.writeStartObject()
    generator.writeNumberProperty("line", position.line)
    generator.writeNumberProperty("character", position.character)
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
    if (token != JsonToken.PROPERTY_NAME) {
      parser.skipChildren()
      continue
    }
    val fieldName = parser.currentName()
    parser.nextToken()
    handleField(fieldName)
  }
}

private fun readStringOrNull(parser: JsonParser): String? {
  val result = if (parser.currentToken() == JsonToken.VALUE_STRING) parser.string else null
  parser.skipChildren()
  return result
}

private fun readIntOrNull(parser: JsonParser): Int? {
  val result = when (parser.currentToken()) {
    JsonToken.VALUE_NUMBER_INT -> parser.intValue
    JsonToken.VALUE_STRING -> parser.string.toIntOrNull()
    else -> null
  }
  parser.skipChildren()
  return result
}

private const val REQUEST_TYPE: String = "request"
private const val IDE_CONTEXT_METHOD: String = "ide-context"
