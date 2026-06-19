// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.prompt.suggestions

import com.intellij.agent.workbench.codex.common.currentToken
import com.intellij.agent.workbench.codex.common.forEachObjectField
import com.intellij.agent.workbench.codex.common.readStringOrNull
import com.intellij.agent.workbench.json.createJsonGenerator
import com.intellij.agent.workbench.json.createJsonParser
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private data class PromptRequest(
  @JvmField val id: String,
  @JvmField val method: String,
  @JvmField val params: PromptRequestParams,
)

private data class PromptRequestParams(
  @JvmField val threadId: String? = null,
  @JvmField val turnId: String? = null,
  @JvmField val cwd: String? = null,
  @JvmField val model: String? = null,
  @JvmField val effort: String? = null,
  @JvmField val inputText: String? = null,
  @JvmField val outputSchemaPresent: Boolean = false,
)

private data class PendingPromptSuggestionTurn(
  @JvmField val threadId: String,
  @JvmField val turnId: String,
  @JvmField val sendInterruptResponse: Boolean = true,
  @JvmField val emitTerminalOnInterrupt: Boolean = true,
)

internal object CodexPromptSuggestionTestAppServer {
  private val jsonFactory = JsonFactory()
  private const val ERROR_METHOD_ENV = "CODEX_TEST_ERROR_METHOD"
  private const val ERROR_MESSAGE_ENV = "CODEX_TEST_ERROR_MESSAGE"
  private const val REQUEST_LOG_ENV = "CODEX_TEST_REQUEST_LOG"
  private const val REQUEST_PAYLOAD_LOG_ENV = "CODEX_TEST_REQUEST_PAYLOAD_LOG"
  private const val PROMPT_SUGGEST_KIND_ENV = "CODEX_TEST_PROMPT_SUGGEST_KIND"
  private const val PROMPT_SUGGEST_LIFECYCLE_ENV = "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE"
  private const val PROMPT_SUGGEST_LIFECYCLE_STATE_FILE_ENV = "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE_STATE_FILE"
  private const val PROMPT_SUGGEST_ERROR_MESSAGE_ENV = "CODEX_TEST_PROMPT_SUGGEST_ERROR_MESSAGE"

  @JvmStatic
  fun main(args: Array<String>) {
    val errorMethod = readEnv(ERROR_METHOD_ENV)
    val errorMessage = readEnv(ERROR_MESSAGE_ENV)
    val requestLogPath = readEnv(REQUEST_LOG_ENV)?.let(Path::of)
    val requestPayloadLogPath = readEnv(REQUEST_PAYLOAD_LOG_ENV)?.let(Path::of)
    val promptSuggestKind = readEnv(PROMPT_SUGGEST_KIND_ENV)
    val promptSuggestLifecycleValues = readEnv(PROMPT_SUGGEST_LIFECYCLE_ENV)
                                         ?.split(',')
                                         ?.map(String::trim)
                                         ?.filter(String::isNotEmpty)
                                         ?.toMutableList()
                                       ?: mutableListOf()
    val promptSuggestLifecycleStateFile = readEnv(PROMPT_SUGGEST_LIFECYCLE_STATE_FILE_ENV)?.let(Path::of)
    val promptSuggestErrorMessage = readEnv(PROMPT_SUGGEST_ERROR_MESSAGE_ENV)
    val pendingPromptSuggestionTurns = LinkedHashMap<String, PendingPromptSuggestionTurn>()

    val reader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))
    val writer = BufferedWriter(OutputStreamWriter(System.out, StandardCharsets.UTF_8))
    while (true) {
      val line = reader.readLine() ?: break
      val payload = line.trim()
      if (payload.isEmpty()) continue
      requestPayloadLogPath?.let { appendRequestLog(it, payload) }
      val request = try {
        parseRequest(payload)
      }
                    catch (_: Throwable) {
                      null
                    } ?: continue
      requestLogPath?.let { appendRequestLog(it, request.method) }
      if (errorMethod != null && request.method == errorMethod) {
        writeResponse(writer, request.id, ::writeEmptyObject, errorMessage = errorMessage ?: "Forced error")
        continue
      }
      when (request.method) {
        "initialize" -> writeResponse(writer, request.id, ::writeEmptyObject)
        "thread/start" -> writeThreadStartResponse(writer, request.id, request.params.cwd)
        "turn/start" -> {
          val turnId = "turn-${System.currentTimeMillis()}"
          val threadId = request.params.threadId ?: "thread-missing"
          writeTurnStartResponse(writer, request.id, turnId)
          if (request.params.outputSchemaPresent) {
            val promptSuggestLifecycle = nextPromptSuggestionLifecycle(
              values = promptSuggestLifecycleValues,
              stateFile = promptSuggestLifecycleStateFile,
            )
            val pendingTurn = writePromptSuggestionTurnNotifications(
              writer = writer,
              threadId = threadId,
              turnId = turnId,
              promptSuggestKind = promptSuggestKind,
              lifecycle = promptSuggestLifecycle,
              errorMessage = promptSuggestErrorMessage,
            )
            if (pendingTurn != null) {
              pendingPromptSuggestionTurns[pendingTurn.turnId] = pendingTurn
            }
          }
        }
        "turn/interrupt" -> {
          val interruptedTurn = request.params.turnId
            ?.let(pendingPromptSuggestionTurns::remove)
            ?.takeIf { pendingTurn -> pendingTurn.threadId == request.params.threadId }
          if (interruptedTurn?.sendInterruptResponse != false) {
            writeResponse(writer, request.id, ::writeEmptyObject)
          }
          if (interruptedTurn?.emitTerminalOnInterrupt == true) {
            writePromptSuggestionTurnCompletedNotification(
              writer = writer,
              threadId = interruptedTurn.threadId,
              turnId = interruptedTurn.turnId,
              status = "interrupted",
            )
          }
        }
        else -> writeResponse(writer, request.id, ::writeEmptyObject, errorMessage = "Unknown method: ${request.method}")
      }
    }
  }

  private fun writeThreadStartResponse(writer: BufferedWriter, id: String, cwd: String?) {
    val threadId = "thread-start-${System.currentTimeMillis()}"
    writeResponse(writer, id, resultWriter = { generator ->
      generator.writeStartObject()
      generator.writeFieldName("thread")
      generator.writeStartObject()
      generator.writeStringField("id", threadId)
      generator.writeStringField("title", "Thread ${threadId.takeLast(8)}")
      generator.writeStringField("cwd", cwd ?: System.getProperty("user.dir"))
      generator.writeNumberField("updated_at", System.currentTimeMillis())
      generator.writeEndObject()
      generator.writeEndObject()
    })
  }

  private fun writeTurnStartResponse(writer: BufferedWriter, id: String, turnId: String) {
    writeResponse(writer, id, resultWriter = { generator ->
      generator.writeStartObject()
      generator.writeFieldName("turn")
      generator.writeStartObject()
      generator.writeStringField("id", turnId)
      generator.writeStringField("status", "inProgress")
      generator.writeFieldName("items")
      generator.writeStartArray()
      generator.writeEndArray()
      generator.writeNullField("error")
      generator.writeEndObject()
      generator.writeEndObject()
    })
  }

  private fun writePromptSuggestionTurnNotifications(
    writer: BufferedWriter,
    threadId: String,
    turnId: String,
    promptSuggestKind: String?,
    lifecycle: String?,
    errorMessage: String?,
  ): PendingPromptSuggestionTurn? {
    writePromptSuggestionTurnStartedNotification(writer, threadId, turnId)
    writePromptSuggestionItemStartedNotification(writer, threadId, turnId)
    writePromptSuggestionTurnStartedNotification(writer, "$threadId-unrelated", "$turnId-unrelated")
    writePromptSuggestionItemCompletedNotification(writer, "$threadId-unrelated", "$turnId-unrelated", promptSuggestKind)
    return when (lifecycle?.lowercase()) {
      "interrupted" -> {
        writePromptSuggestionTurnCompletedNotification(writer, threadId, turnId, status = "interrupted")
        null
      }

      "failed" -> {
        writePromptSuggestionTurnCompletedNotification(
          writer = writer,
          threadId = threadId,
          turnId = turnId,
          status = "failed",
          errorMessage = errorMessage ?: "Codex prompt suggestion turn failed",
        )
        null
      }

      "wait_for_interrupt" -> PendingPromptSuggestionTurn(threadId = threadId, turnId = turnId)
      "wait_for_interrupt_without_response" -> PendingPromptSuggestionTurn(
        threadId = threadId,
        turnId = turnId,
        sendInterruptResponse = false,
      )

      "wait_for_interrupt_without_terminal" -> PendingPromptSuggestionTurn(
        threadId = threadId,
        turnId = turnId,
        emitTerminalOnInterrupt = false,
      )

      else -> {
        writePromptSuggestionItemCompletedNotification(writer, threadId, turnId, promptSuggestKind)
        writePromptSuggestionTurnCompletedNotification(writer, threadId, turnId, status = "completed")
        null
      }
    }
  }

  private fun writePromptSuggestionTurnStartedNotification(writer: BufferedWriter, threadId: String, turnId: String) {
    writeNotification(writer, "turn/started") { generator ->
      generator.writeStringField("threadId", threadId)
      generator.writeFieldName("turn")
      generator.writeStartObject()
      generator.writeStringField("id", turnId)
      generator.writeStringField("status", "in_progress")
      generator.writeEndObject()
    }
  }

  private fun writePromptSuggestionItemStartedNotification(writer: BufferedWriter, threadId: String, turnId: String) {
    writeNotification(writer, "item/started") { generator ->
      generator.writeStringField("threadId", threadId)
      generator.writeStringField("turnId", turnId)
      generator.writeFieldName("item")
      generator.writeStartObject()
      generator.writeStringField("type", "agentMessage")
      generator.writeStringField("id", "item-$turnId")
      generator.writeNullField("phase")
      generator.writeEndObject()
    }
  }

  private fun writePromptSuggestionItemCompletedNotification(
    writer: BufferedWriter,
    threadId: String,
    turnId: String,
    promptSuggestKind: String?,
  ) {
    writeNotification(writer, "item/completed") { generator ->
      generator.writeStringField("threadId", threadId)
      generator.writeStringField("turnId", turnId)
      generator.writeFieldName("item")
      generator.writeStartObject()
      generator.writeStringField("type", "agentMessage")
      generator.writeStringField("id", "item-$turnId")
      generator.writeStringField("text", renderPromptSuggestionResult(promptSuggestKind))
      generator.writeNullField("phase")
      generator.writeEndObject()
    }
  }

  private fun writePromptSuggestionTurnCompletedNotification(
    writer: BufferedWriter,
    threadId: String,
    turnId: String,
    status: String,
    errorMessage: String? = null,
  ) {
    writeNotification(writer, "turn/completed") { generator ->
      generator.writeStringField("threadId", threadId)
      generator.writeFieldName("turn")
      generator.writeStartObject()
      generator.writeStringField("id", turnId)
      generator.writeFieldName("items")
      generator.writeStartArray()
      generator.writeEndArray()
      generator.writeStringField("status", status)
      if (errorMessage == null) {
        generator.writeNullField("error")
      }
      else {
        generator.writeFieldName("error")
        generator.writeStartObject()
        generator.writeStringField("message", errorMessage)
        generator.writeEndObject()
      }
      generator.writeEndObject()
    }
  }

  private fun renderPromptSuggestionResult(promptSuggestKind: String?): String {
    val writer = StringWriter()
    jsonFactory.createJsonGenerator(writer).use { generator ->
      when (promptSuggestKind?.lowercase()) {
        "polished" -> writePolishedPromptSuggestionResult(generator)
        else -> writeGeneratedPromptSuggestionResult(generator)
      }
    }
    return writer.toString()
  }

  private fun writeGeneratedPromptSuggestionResult(generator: JsonGenerator) {
    generator.writeStartObject()
    generator.writeStringField("kind", "generatedCandidates")
    generator.writeFieldName("candidates")
    generator.writeStartArray()
    generator.writeStartObject()
    generator.writeNullField("id")
    generator.writeStringField("label", "AI: Investigate provided context")
    generator.writeStringField("promptText", "Investigate the provided context and explain the next steps.")
    generator.writeEndObject()
    generator.writeStartObject()
    generator.writeNullField("id")
    generator.writeStringField("label", "AI: Summarize provided context")
    generator.writeStringField("promptText", "Summarize the relevant context before making changes.")
    generator.writeEndObject()
    generator.writeEndArray()
    generator.writeEndObject()
  }

  private fun writePolishedPromptSuggestionResult(generator: JsonGenerator) {
    generator.writeStartObject()
    generator.writeStringField("kind", "polishedSeeds")
    generator.writeFieldName("candidates")
    generator.writeStartArray()
    generator.writeStartObject()
    generator.writeStringField("id", "tests.fix")
    generator.writeStringField("label", "AI: Fix the ParserTest failure")
    generator.writeStringField("promptText", "Investigate ParserTest, identify the root cause, and implement the minimal fix.")
    generator.writeEndObject()
    generator.writeStartObject()
    generator.writeStringField("id", "tests.explain")
    generator.writeStringField("label", "AI: Explain the ParserTest failure")
    generator.writeStringField("promptText", "Explain why ParserTest is failing and point out the relevant code path.")
    generator.writeEndObject()
    generator.writeEndArray()
    generator.writeEndObject()
  }

  private fun writeResponse(
    writer: BufferedWriter,
    id: String,
    resultWriter: (JsonGenerator) -> Unit,
    errorMessage: String? = null,
  ) {
    val generator = jsonFactory.createJsonGenerator(writer)
    generator.writeStartObject()
    generator.writeStringField("id", id)
    if (errorMessage != null) {
      generator.writeFieldName("error")
      generator.writeStartObject()
      generator.writeStringField("message", errorMessage)
      generator.writeEndObject()
    }
    else {
      generator.writeFieldName("result")
      resultWriter(generator)
    }
    generator.writeEndObject()
    generator.close()
    writer.newLine()
    writer.flush()
  }

  private fun writeNotification(writer: BufferedWriter, method: String, paramsWriter: (JsonGenerator) -> Unit) {
    val generator = jsonFactory.createJsonGenerator(writer)
    generator.writeStartObject()
    generator.writeStringField("method", method)
    generator.writeFieldName("params")
    generator.writeStartObject()
    paramsWriter(generator)
    generator.writeEndObject()
    generator.writeEndObject()
    generator.close()
    writer.newLine()
    writer.flush()
  }

  private fun writeEmptyObject(generator: JsonGenerator) {
    generator.writeStartObject()
    generator.writeEndObject()
  }

  private fun parseRequest(payload: String): PromptRequest? {
    jsonFactory.createJsonParser(payload).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) return null
      var id: String? = null
      var method: String? = null
      var params = PromptRequestParams()
      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "id" -> id = readStringOrNull(parser)
          "method" -> method = readStringOrNull(parser)
          "params" -> params = parseRequestParams(parser)
          else -> parser.skipChildren()
        }
        true
      }
      return PromptRequest(id = id ?: return null, method = method ?: return null, params = params)
    }
  }

  private fun parseRequestParams(parser: JsonParser): PromptRequestParams {
    if (parser.currentToken != JsonToken.START_OBJECT) {
      parser.skipChildren()
      return PromptRequestParams()
    }
    var threadId: String? = null
    var turnId: String? = null
    var cwd: String? = null
    var model: String? = null
    var effort: String? = null
    var inputText: String? = null
    var outputSchemaPresent = false
    forEachObjectField(parser) { fieldName ->
      when (fieldName) {
        "threadId", "thread_id" -> threadId = readStringOrNull(parser)
        "turnId", "turn_id" -> turnId = readStringOrNull(parser)
        "cwd" -> cwd = readStringOrNull(parser)
        "model" -> model = readStringOrNull(parser)
        "effort" -> effort = readStringOrNull(parser)
        "input" -> inputText = parseInputText(parser)
        "outputSchema" -> {
          outputSchemaPresent = true
          parser.skipChildren()
        }
        else -> parser.skipChildren()
      }
      true
    }
    return PromptRequestParams(
      threadId = threadId,
      turnId = turnId,
      cwd = cwd,
      model = model,
      effort = effort,
      inputText = inputText,
      outputSchemaPresent = outputSchemaPresent,
    )
  }

  private fun parseInputText(parser: JsonParser): String? {
    if (parser.currentToken != JsonToken.START_ARRAY) {
      parser.skipChildren()
      return null
    }
    var text: String? = null
    while (true) {
      val token = parser.nextToken() ?: return text
      if (token == JsonToken.END_ARRAY) return text
      if (token != JsonToken.START_OBJECT) {
        parser.skipChildren()
        continue
      }
      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "text" -> text = readStringOrNull(parser) ?: text
          else -> parser.skipChildren()
        }
        true
      }
    }
  }

  private fun nextPromptSuggestionLifecycle(values: MutableList<String>, stateFile: Path?): String? {
    if (values.isEmpty()) {
      return null
    }
    if (stateFile == null) {
      return values.removeAt(0)
    }

    val index = readPromptSuggestionLifecycleIndex(stateFile)
    if (index >= values.size) {
      return null
    }

    writePromptSuggestionLifecycleIndex(stateFile, index + 1)
    return values[index]
  }

  private fun readPromptSuggestionLifecycleIndex(path: Path): Int {
    if (!Files.exists(path)) {
      return 0
    }
    return Files.readString(path, StandardCharsets.UTF_8)
             .trim()
             .toIntOrNull()
           ?: 0
  }

  private fun writePromptSuggestionLifecycleIndex(path: Path, index: Int) {
    path.parent?.let(Files::createDirectories)
    Files.writeString(
      path,
      index.toString(),
      StandardCharsets.UTF_8,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE,
    )
  }
}

private fun readEnv(name: String): String? {
  return System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
}

private fun appendRequestLog(path: Path, method: String) {
  try {
    path.parent?.let(Files::createDirectories)
    Files.writeString(
      path,
      "$method\n",
      StandardCharsets.UTF_8,
      StandardOpenOption.CREATE,
      StandardOpenOption.WRITE,
      StandardOpenOption.APPEND,
    )
  }
  catch (_: Throwable) {
  }
}
