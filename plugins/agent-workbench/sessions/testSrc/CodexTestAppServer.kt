// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.agent.workbench.codex.common.forEachObjectField
import com.intellij.agent.workbench.codex.common.readLongOrNull
import com.intellij.agent.workbench.codex.common.readStringOrNull
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private data class ThreadEntry(
  @JvmField val id: String,
  @JvmField val title: String?,
  @JvmField val preview: String?,
  @JvmField var name: String?,
  @JvmField val summary: String?,
  @JvmField val cwd: String?,
  @JvmField val sourceKind: String,
  @JvmField val sourceAsString: Boolean,
  @JvmField val sourceSubAgentFieldName: String,
  @JvmField val parentThreadId: String?,
  @JvmField val agentNickname: String?,
  @JvmField val agentRole: String?,
  @JvmField val statusType: String,
  @JvmField val statusActiveFlagsFieldName: String,
  @JvmField val activeFlags: List<String>,
  @JvmField val readTurns: List<TurnEntry>,
  @JvmField val gitBranch: String?,
  @JvmField var updatedAt: Long?,
  @JvmField var updatedAtField: String?,
  @JvmField val createdAt: Long?,
  @JvmField val createdAtField: String?,
  @JvmField var archived: Boolean,
)

private data class TurnEntry(
  @JvmField val statusType: String,
  @JvmField val statusAsObject: Boolean,
  @JvmField val itemTypes: List<String>,
)

private data class Request(
  @JvmField val id: String,
  @JvmField val method: String,
  @JvmField val params: RequestParams,
)

private data class RequestParams(
  @JvmField val id: String? = null,
  @JvmField val turnId: String? = null,
  @JvmField val name: String? = null,
  @JvmField val archived: Boolean? = null,
  @JvmField val cursor: String? = null,
  @JvmField val limit: Int? = null,
  @JvmField val includeTurns: Boolean? = null,
  @JvmField val cwd: String? = null,
  @JvmField val sourceKinds: Set<String>? = null,
  @JvmField val model: String? = null,
  @JvmField val effort: String? = null,
  @JvmField val approvalPolicy: String? = null,
  @JvmField val sandbox: String? = null,
  @JvmField val ephemeral: Boolean? = null,
  @JvmField val inputText: String? = null,
  @JvmField val outputSchemaPresent: Boolean = false,
)

private data class PendingPromptSuggestionTurn(
  @JvmField val threadId: String,
  @JvmField val turnId: String,
  @JvmField val sendInterruptResponse: Boolean = true,
  @JvmField val emitTerminalOnInterrupt: Boolean = true,
)

private val DEFAULT_THREAD_LIST_SOURCE_KINDS = linkedSetOf("cli", "vscode", "appServer")
private const val TEST_MODEL_ENV = "CODEX_MODEL"
private const val TEST_REASONING_EFFORT_ENV = "CODEX_REASONING_EFFORT"
private const val DEFAULT_TEST_MODEL = "gpt-4o-mini"
private const val DEFAULT_TEST_REASONING_EFFORT = "low"

internal object CodexTestAppServer {
  private val jsonFactory = JsonFactory()
  private const val CWD_MARKER_ENV = "CODEX_TEST_CWD_MARKER"
  private const val ERROR_METHOD_ENV = "CODEX_TEST_ERROR_METHOD"
  private const val ERROR_MESSAGE_ENV = "CODEX_TEST_ERROR_MESSAGE"
  private const val REQUEST_LOG_ENV = "CODEX_TEST_REQUEST_LOG"
  private const val REQUEST_PAYLOAD_LOG_ENV = "CODEX_TEST_REQUEST_PAYLOAD_LOG"
  private const val PROMPT_SUGGEST_KIND_ENV = "CODEX_TEST_PROMPT_SUGGEST_KIND"
  private const val PROMPT_SUGGEST_LIFECYCLE_ENV = "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE"
  private const val PROMPT_SUGGEST_LIFECYCLE_STATE_FILE_ENV = "CODEX_TEST_PROMPT_SUGGEST_LIFECYCLE_STATE_FILE"
  private const val PROMPT_SUGGEST_ERROR_MESSAGE_ENV = "CODEX_TEST_PROMPT_SUGGEST_ERROR_MESSAGE"
  private const val NOTIFY_METHOD_ENV = "CODEX_TEST_NOTIFY_METHOD"
  private const val NOTIFY_ON_METHOD_ENV = "CODEX_TEST_NOTIFY_ON_METHOD"
  private const val NOTIFY_THREAD_ID_ENV = "CODEX_TEST_NOTIFY_THREAD_ID"
  private const val NOTIFY_THREAD_ID_STYLE_ENV = "CODEX_TEST_NOTIFY_THREAD_ID_STYLE"
  private const val NOTIFY_ID_ENV = "CODEX_TEST_NOTIFY_ID"

  @JvmStatic
  fun main(args: Array<String>) {
    val configPath = args.firstOrNull()?.let(Path::of)
      ?: error("Expected config path argument")
    val threads = loadThreads(configPath)
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
    val notifyMethod = readEnv(NOTIFY_METHOD_ENV)
    val notifyOnMethod = readEnv(NOTIFY_ON_METHOD_ENV)
    val notifyThreadId = readEnv(NOTIFY_THREAD_ID_ENV)
    val notifyThreadIdStyle = readEnv(NOTIFY_THREAD_ID_STYLE_ENV)
    val notifyId = readEnv(NOTIFY_ID_ENV)
    val pendingPromptSuggestionTurns = LinkedHashMap<String, PendingPromptSuggestionTurn>()
    readEnv(CWD_MARKER_ENV)?.let(::writeWorkingDirectoryMarker)
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
      }
      if (request == null) continue
      requestLogPath?.let { appendRequestLog(it, request.method) }
      if (errorMethod != null && request.method == errorMethod) {
        writeResponse(writer, request.id, ::writeEmptyObject, errorMessage = errorMessage ?: "Forced error")
        continue
      }
      when (request.method) {
        "initialize" -> writeResponse(writer, request.id, ::writeEmptyObject)
        "thread/start" -> {
          val startedThread = startThread(threads, request.params.cwd)
          val model = readEnv(TEST_MODEL_ENV) ?: request.params.model ?: DEFAULT_TEST_MODEL
          val reasoningEffort = readEnv(TEST_REASONING_EFFORT_ENV) ?: request.params.effort ?: DEFAULT_TEST_REASONING_EFFORT
          writeResponse(writer, request.id, resultWriter = { generator ->
            generator.writeStartObject()
            generator.writeFieldName("thread")
            writeThreadObject(generator, startedThread)
            generator.writeStringField("model", model)
            generator.writeStringField("reasoningEffort", reasoningEffort)
            generator.writeEndObject()
          })
        }
        "thread/list" -> writeResponse(writer, request.id, resultWriter = { generator ->
          val archived = request.params.archived ?: false
          writeThreadList(
            generator = generator,
            threads = threads,
            archived = archived,
            cursor = request.params.cursor,
            limit = request.params.limit,
            cwd = request.params.cwd,
            sourceKinds = request.params.sourceKinds,
          )
        })
        "thread/read" -> writeResponse(writer, request.id, resultWriter = { generator ->
          val thread = request.params.id
            ?.let { requestedId -> threads.firstOrNull { entry -> entry.id == requestedId } }
          generator.writeStartObject()
          generator.writeFieldName("thread")
          if (thread == null) {
            writeEmptyObject(generator)
          }
          else {
            writeThreadReadObject(
              generator = generator,
              thread = thread,
              includeTurns = request.params.includeTurns ?: false,
            )
          }
          generator.writeEndObject()
        })
        "thread/archive" -> {
          updateArchive(request.params.id, threads, archive = true)
          writeResponse(writer, request.id, ::writeEmptyObject)
        }
        "thread/name/set" -> {
          updateThreadName(request.params.id, request.params.name, threads)
          writeResponse(writer, request.id, ::writeEmptyObject)
        }
        "thread/unarchive" -> {
          updateArchive(request.params.id, threads, archive = false)
          writeResponse(writer, request.id, ::writeEmptyObject)
        }
        "turn/start" -> {
          val turnId = "turn-${System.currentTimeMillis()}"
          val threadId = request.params.id ?: "thread-missing"
          writeResponse(writer, request.id, resultWriter = { generator ->
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
            ?.takeIf { pendingTurn -> pendingTurn.threadId == request.params.id }
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

      maybeWriteNotification(
        writer = writer,
        request = request,
        threads = threads,
        notifyMethod = notifyMethod,
        notifyOnMethod = notifyOnMethod,
        notifyThreadId = notifyThreadId,
        notifyThreadIdStyle = notifyThreadIdStyle,
        notifyId = notifyId,
      )
    }
  }

  private fun writePromptSuggestionResult(generator: JsonGenerator, promptSuggestKind: String?) {
    when (promptSuggestKind?.lowercase()) {
      "polished" -> {
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
        generator.writeStartObject()
        generator.writeStringField("id", "tests.stabilize")
        generator.writeStringField("label", "AI: Stabilize the ParserTest coverage")
        generator.writeStringField("promptText", "Stabilize the ParserTest scenario and call out any missing assertions or cleanup.")
        generator.writeEndObject()
        generator.writeEndArray()
        generator.writeEndObject()
      }

      else -> {
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
    }
  }

  private fun writePromptSuggestionTurnNotifications(
    writer: BufferedWriter,
    threadId: String,
    turnId: String,
    promptSuggestKind: String?,
    lifecycle: String?,
    errorMessage: String?,
  ): PendingPromptSuggestionTurn? {
    writePromptSuggestionTurnStartedNotification(
      writer = writer,
      threadId = threadId,
      turnId = turnId,
    )
    writePromptSuggestionItemStartedNotification(
      writer = writer,
      threadId = threadId,
      turnId = turnId,
    )
    writePromptSuggestionOutputDeltaNotification(
      writer = writer,
      threadId = threadId,
      turnId = turnId,
    )
    writePromptSuggestionTurnStartedNotification(
      writer = writer,
      threadId = "$threadId-unrelated",
      turnId = "$turnId-unrelated",
    )
    writePromptSuggestionItemCompletedNotification(
      writer = writer,
      threadId = "$threadId-unrelated",
      turnId = "$turnId-unrelated",
      promptSuggestKind = promptSuggestKind,
    )
    return when (lifecycle?.lowercase()) {
      "interrupted" -> {
        writePromptSuggestionTurnCompletedNotification(
          writer = writer,
          threadId = threadId,
          turnId = turnId,
          status = "interrupted",
        )
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

      "wait_for_interrupt" -> PendingPromptSuggestionTurn(
        threadId = threadId,
        turnId = turnId,
      )

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
        writePromptSuggestionItemCompletedNotification(
          writer = writer,
          threadId = threadId,
          turnId = turnId,
          promptSuggestKind = promptSuggestKind,
        )
        writePromptSuggestionTurnCompletedNotification(
          writer = writer,
          threadId = threadId,
          turnId = turnId,
          status = "completed",
        )
        null
      }
    }
  }

  private fun writePromptSuggestionTurnStartedNotification(
    writer: BufferedWriter,
    threadId: String,
    turnId: String,
  ) {
    val generator = jsonFactory.createGenerator(writer)
    generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
    generator.writeStartObject()
    generator.writeStringField("method", "turn/started")
    generator.writeFieldName("params")
    generator.writeStartObject()
    generator.writeStringField("threadId", threadId)
    generator.writeFieldName("turn")
    generator.writeStartObject()
    generator.writeStringField("id", turnId)
    generator.writeStringField("status", "in_progress")
    generator.writeEndObject()
    generator.writeEndObject()
    generator.writeEndObject()
    generator.close()
    writer.newLine()
    writer.flush()
  }

  private fun writePromptSuggestionItemStartedNotification(
    writer: BufferedWriter,
    threadId: String,
    turnId: String,
  ) {
    val generator = jsonFactory.createGenerator(writer)
    generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
    generator.writeStartObject()
    generator.writeStringField("method", "item/started")
    generator.writeFieldName("params")
    generator.writeStartObject()
    generator.writeStringField("threadId", threadId)
    generator.writeStringField("turnId", turnId)
    generator.writeFieldName("item")
    generator.writeStartObject()
    generator.writeStringField("type", "agentMessage")
    generator.writeStringField("id", "item-$turnId")
    generator.writeNullField("phase")
    generator.writeEndObject()
    generator.writeEndObject()
    generator.writeEndObject()
    generator.close()
    writer.newLine()
    writer.flush()
  }

  private fun writePromptSuggestionOutputDeltaNotification(
    writer: BufferedWriter,
    threadId: String,
    turnId: String,
  ) {
    val generator = jsonFactory.createGenerator(writer)
    generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
    generator.writeStartObject()
    generator.writeStringField("method", "item/commandExecution/outputDelta")
    generator.writeFieldName("params")
    generator.writeStartObject()
    generator.writeStringField("threadId", threadId)
    generator.writeStringField("turnId", turnId)
    generator.writeStringField("delta", "ignored output")
    generator.writeEndObject()
    generator.writeEndObject()
    generator.close()
    writer.newLine()
    writer.flush()
  }

  private fun writePromptSuggestionItemCompletedNotification(
    writer: BufferedWriter,
    threadId: String,
    turnId: String,
    promptSuggestKind: String?,
  ) {
    val generator = jsonFactory.createGenerator(writer)
    generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
    generator.writeStartObject()
    generator.writeStringField("method", "item/completed")
    generator.writeFieldName("params")
    generator.writeStartObject()
    generator.writeStringField("threadId", threadId)
    generator.writeStringField("turnId", turnId)
    generator.writeFieldName("item")
    generator.writeStartObject()
    generator.writeStringField("type", "agentMessage")
    generator.writeStringField("id", "item-$turnId")
    val resultWriter = StringWriter()
    val stringGenerator = jsonFactory.createGenerator(resultWriter)
    writePromptSuggestionResult(stringGenerator, promptSuggestKind)
    stringGenerator.close()
    generator.writeStringField("text", resultWriter.toString())
    generator.writeNullField("phase")
    generator.writeEndObject()
    generator.writeEndObject()
    generator.writeEndObject()
    generator.close()
    writer.newLine()
    writer.flush()
  }

  private fun writePromptSuggestionTurnCompletedNotification(
    writer: BufferedWriter,
    threadId: String,
    turnId: String,
    status: String,
    errorMessage: String? = null,
  ) {
    val generator = jsonFactory.createGenerator(writer)
    generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
    generator.writeStartObject()
    generator.writeStringField("method", "turn/completed")
    generator.writeFieldName("params")
    generator.writeStartObject()
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
    generator.writeEndObject()
    generator.writeEndObject()
    generator.close()
    writer.newLine()
    writer.flush()
  }

  private fun maybeWriteNotification(
    writer: BufferedWriter,
    request: Request,
    threads: List<ThreadEntry>,
    notifyMethod: String?,
    notifyOnMethod: String?,
    notifyThreadId: String?,
    notifyThreadIdStyle: String?,
    notifyId: String?,
  ) {
    val method = notifyMethod ?: return
    val triggerMethod = notifyOnMethod?.takeIf { it.isNotBlank() }
    if (triggerMethod != null && request.method != triggerMethod) {
      return
    }

    val threadId = notifyThreadId ?: request.params.id
    writeNotification(
      writer = writer,
      method = method,
      threadId = threadId,
      thread = threadId?.let { requestedId -> threads.firstOrNull { entry -> entry.id == requestedId } },
      threadIdStyle = notifyThreadIdStyle,
      notificationId = notifyId,
    )
  }

  private fun parseRequest(payload: String): Request? {
    jsonFactory.createParser(payload).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) return null
      var id: String? = null
      var method: String? = null
      var paramsId: String? = null
      var paramsTurnId: String? = null
      var paramsName: String? = null
      var paramsArchived: Boolean? = null
      var paramsCursor: String? = null
      var paramsLimit: Int? = null
      var paramsIncludeTurns: Boolean? = null
      var paramsCwd: String? = null
      var paramsSourceKinds: MutableSet<String>? = null
      var paramsModel: String? = null
      var paramsEffort: String? = null
      var paramsApprovalPolicy: String? = null
      var paramsSandbox: String? = null
      var paramsEphemeral: Boolean? = null
      var paramsInputText: String? = null
      var paramsOutputSchemaPresent = false
      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "id" -> id = readStringOrNull(parser)
          "method" -> method = readStringOrNull(parser)
          "params" -> {
            if (parser.currentToken == JsonToken.START_OBJECT) {
              forEachObjectField(parser) { paramName ->
                when (paramName) {
                  "id" -> paramsId = readStringOrNull(parser)
                  "threadId" -> paramsId = readStringOrNull(parser)
                  "turnId" -> paramsTurnId = readStringOrNull(parser)
                  "name" -> paramsName = readStringOrNull(parser)
                  "archived" -> paramsArchived = readBooleanOrNull(parser)
                  "cursor" -> paramsCursor = readStringOrNull(parser)
                  "limit" -> paramsLimit = readLongOrNull(parser)?.toInt()
                  "includeTurns", "include_turns" -> paramsIncludeTurns = readBooleanOrNull(parser)
                  "cwd" -> paramsCwd = readStringOrNull(parser)
                  "approvalPolicy" -> paramsApprovalPolicy = readStringOrNull(parser)
                  "sandbox", "sandboxPolicy" -> paramsSandbox = readStringOrNull(parser)
                  "ephemeral" -> paramsEphemeral = readBooleanOrNull(parser)
                  "model" -> paramsModel = readStringOrNull(parser)
                  "effort" -> paramsEffort = readStringOrNull(parser)
                  "input" -> {
                    if (parser.currentToken == JsonToken.START_ARRAY) {
                      paramsInputText = parseInputTextArray(parser)
                    }
                    else {
                      parser.skipChildren()
                    }
                  }
                  "outputSchema" -> {
                    paramsOutputSchemaPresent = true
                    parser.skipChildren()
                  }
                  "sourceKinds", "source_kinds" -> {
                    if (parser.currentToken == JsonToken.START_ARRAY) {
                      val kinds = paramsSourceKinds ?: LinkedHashSet<String>().also { paramsSourceKinds = it }
                      parseStringArray(parser, kinds)
                    }
                    else {
                      parser.skipChildren()
                    }
                  }
                  else -> parser.skipChildren()
                }
                true
              }
            }
            else {
              parser.skipChildren()
            }
          }
          else -> parser.skipChildren()
        }
        true
      }
      val requestId = id?.takeIf { it.isNotBlank() } ?: return null
      val requestMethod = method?.takeIf { it.isNotBlank() } ?: return null
      return Request(
        requestId,
        requestMethod,
        RequestParams(
          id = paramsId,
          turnId = paramsTurnId,
          name = paramsName,
          archived = paramsArchived,
          cursor = paramsCursor,
          limit = paramsLimit,
          includeTurns = paramsIncludeTurns,
          cwd = paramsCwd,
          sourceKinds = paramsSourceKinds,
          model = paramsModel,
          effort = paramsEffort,
          approvalPolicy = paramsApprovalPolicy,
          sandbox = paramsSandbox,
          ephemeral = paramsEphemeral,
          inputText = paramsInputText,
          outputSchemaPresent = paramsOutputSchemaPresent,
        )
      )
    }
  }

  private fun parseInputTextArray(parser: JsonParser): String? {
    var inputText: String? = null
    while (true) {
      val token = parser.nextToken() ?: return inputText
      if (token == JsonToken.END_ARRAY) {
        return inputText
      }
      if (token != JsonToken.START_OBJECT) {
        parser.skipChildren()
        continue
      }

      var inputType: String? = null
      var text: String? = null
      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "type" -> inputType = readStringOrNull(parser)
          "text" -> text = readStringOrNull(parser)
          else -> parser.skipChildren()
        }
        true
      }
      if (inputType == "text" && inputText == null) {
        inputText = text
      }
    }
  }

  private fun loadThreads(path: Path): MutableList<ThreadEntry> {
    if (!Files.exists(path)) return mutableListOf()
    Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
      jsonFactory.createParser(reader).use { parser ->
        if (parser.nextToken() != JsonToken.START_OBJECT) return mutableListOf()
        val result = mutableListOf<ThreadEntry>()
        forEachObjectField(parser) { fieldName ->
          if (fieldName == "threads" && parser.currentToken == JsonToken.START_ARRAY) {
            parseThreadsArray(parser, result)
          }
          else {
            parser.skipChildren()
          }
          true
        }
        return result
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

  private fun parseThreadsArray(parser: JsonParser, result: MutableList<ThreadEntry>) {
    while (true) {
      val token = parser.nextToken() ?: return
      if (token == JsonToken.END_ARRAY) return
      if (token == JsonToken.START_OBJECT) {
        parseThreadEntry(parser)?.let(result::add)
      }
      else {
        parser.skipChildren()
      }
    }
  }

  private fun parseThreadEntry(parser: JsonParser): ThreadEntry? {
    var id: String? = null
    var title: String? = null
    var preview: String? = null
    var name: String? = null
    var summary: String? = null
    var cwd: String? = null
    var sourceKind = "cli"
    var sourceAsString = false
    var sourceSubAgentFieldName = "subAgent"
    var parentThreadId: String? = null
    var agentNickname: String? = null
    var agentRole: String? = null
    var statusType = "idle"
    var statusActiveFlagsFieldName = "activeFlags"
    val activeFlags = ArrayList<String>()
    val readTurns = ArrayList<TurnEntry>()
    var gitBranch: String? = null
    var updatedAt: Long? = null
    var updatedAtField: String? = null
    var createdAt: Long? = null
    var createdAtField: String? = null
    var archived = false
    forEachObjectField(parser) { fieldName ->
      when (fieldName) {
        "id" -> id = readStringOrNull(parser)
        "title" -> title = readStringOrNull(parser)
        "preview" -> preview = readStringOrNull(parser)
        "name" -> name = readStringOrNull(parser)
        "summary" -> summary = readStringOrNull(parser)
        "cwd" -> cwd = readStringOrNull(parser)
        "sourceKind" -> sourceKind = readStringOrNull(parser)?.takeIf { it.isNotBlank() } ?: sourceKind
        "sourceAsString" -> sourceAsString = readBooleanOrNull(parser) ?: false
        "sourceSubAgentFieldName" -> sourceSubAgentFieldName = readStringOrNull(parser)?.takeIf { it.isNotBlank() } ?: sourceSubAgentFieldName
        "parentThreadId" -> parentThreadId = readStringOrNull(parser)
        "agentNickname" -> agentNickname = readStringOrNull(parser)
        "agentRole" -> agentRole = readStringOrNull(parser)
        "statusType" -> statusType = readStringOrNull(parser)?.takeIf { it.isNotBlank() } ?: statusType
        "statusActiveFlagsFieldName" -> {
          statusActiveFlagsFieldName = readStringOrNull(parser)?.takeIf { it.isNotBlank() } ?: statusActiveFlagsFieldName
        }
        "activeFlags" -> {
          if (parser.currentToken == JsonToken.START_ARRAY) {
            parseStringArray(parser, activeFlags)
          }
          else {
            parser.skipChildren()
          }
        }
        "readTurns" -> {
          if (parser.currentToken == JsonToken.START_ARRAY) {
            parseReadTurnsArray(parser, readTurns)
          }
          else {
            parser.skipChildren()
          }
        }
        "gitBranch" -> gitBranch = readStringOrNull(parser)
        "updated_at" -> {
          updatedAt = readLongOrNull(parser)
          updatedAtField = "updated_at"
        }
        "updatedAt" -> {
          updatedAt = readLongOrNull(parser)
          updatedAtField = "updatedAt"
        }
        "created_at" -> {
          createdAt = readLongOrNull(parser)
          createdAtField = "created_at"
        }
        "createdAt" -> {
          createdAt = readLongOrNull(parser)
          createdAtField = "createdAt"
        }
        "archived" -> archived = readBooleanOrNull(parser) ?: false
        else -> parser.skipChildren()
      }
      true
    }
    val threadId = id ?: return null
    return ThreadEntry(
      id = threadId,
      title = title,
      preview = preview,
      name = name,
      summary = summary,
      cwd = cwd,
      sourceKind = sourceKind,
      sourceAsString = sourceAsString,
      sourceSubAgentFieldName = sourceSubAgentFieldName,
      parentThreadId = parentThreadId,
      agentNickname = agentNickname,
      agentRole = agentRole,
      statusType = statusType,
      statusActiveFlagsFieldName = statusActiveFlagsFieldName,
      activeFlags = activeFlags,
      readTurns = readTurns,
      gitBranch = gitBranch,
      updatedAt = updatedAt,
      updatedAtField = updatedAtField,
      createdAt = createdAt,
      createdAtField = createdAtField,
      archived = archived,
    )
  }

  private fun writeResponse(
    writer: BufferedWriter,
    id: String,
    resultWriter: (JsonGenerator) -> Unit,
    errorMessage: String? = null,
  ) {
    val generator = jsonFactory.createGenerator(writer)
    generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
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

  private fun writeNotification(
    writer: BufferedWriter,
    method: String,
    threadId: String?,
    thread: ThreadEntry?,
    threadIdStyle: String?,
    notificationId: String?,
  ) {
    val generator = jsonFactory.createGenerator(writer)
    generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
    generator.writeStartObject()
    if (notificationId != null) {
      generator.writeStringField("id", notificationId)
    }
    generator.writeStringField("method", method)
    generator.writeFieldName("params")
    generator.writeStartObject()
    when (threadIdStyle) {
      "thread_id" -> {
        if (threadId != null) {
          generator.writeStringField("thread_id", threadId)
        }
      }
      "thread", "thread_object" -> {
        generator.writeFieldName("thread")
        if (method == "thread/started" && thread != null) {
          writeThreadObject(generator, thread)
        }
        else {
          generator.writeStartObject()
          if (threadId != null) {
            generator.writeStringField("id", threadId)
          }
          generator.writeEndObject()
        }
      }
      else -> {
        if (threadId != null) {
          generator.writeStringField("threadId", threadId)
        }
      }
    }
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

private fun writeThreadList(
  generator: JsonGenerator,
  threads: List<ThreadEntry>,
  archived: Boolean,
  cursor: String?,
  limit: Int?,
  cwd: String?,
  sourceKinds: Set<String>?,
) {
  val effectiveSourceKinds = sourceKinds
    ?.filterTo(LinkedHashSet()) { it.isNotBlank() }
    ?.takeIf { it.isNotEmpty() }
    ?: DEFAULT_THREAD_LIST_SOURCE_KINDS
  val sorted = threads
    .filter { thread ->
      if (thread.archived != archived) return@filter false
      if (cwd != null && thread.cwd != cwd) return@filter false
      thread.sourceKind in effectiveSourceKinds
    }
    .sortedByDescending { it.updatedAt ?: 0L }
  val pageStart = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
  val pageLimit = (limit ?: sorted.size).coerceAtLeast(1)
  val pageItems = sorted.drop(pageStart).take(pageLimit)
  val nextOffset = pageStart + pageItems.size
  val nextCursor = if (nextOffset < sorted.size) nextOffset.toString() else null
  generator.writeStartObject()
  generator.writeFieldName("data")
  generator.writeStartArray()
  pageItems.forEach { thread ->
    writeThreadObject(generator, thread)
  }
  generator.writeEndArray()
  if (nextCursor != null) {
    generator.writeStringField("nextCursor", nextCursor)
  }
  generator.writeEndObject()
}

private fun writeThreadObject(generator: JsonGenerator, thread: ThreadEntry) {
  generator.writeStartObject()
  generator.writeStringField("id", thread.id)
  thread.title?.let { generator.writeStringField("title", it) }
  thread.preview?.let { generator.writeStringField("preview", it) }
  thread.name?.let { generator.writeStringField("name", it) }
  thread.summary?.let { generator.writeStringField("summary", it) }
  thread.cwd?.let { generator.writeStringField("cwd", it) }
  generator.writeFieldName("source")
  writeThreadSource(generator, thread)
  thread.agentNickname?.let { generator.writeStringField("agentNickname", it) }
  thread.agentRole?.let { generator.writeStringField("agentRole", it) }
  generator.writeFieldName("status")
  writeThreadStatus(generator, thread)
  thread.gitBranch?.let { gitBranch ->
    generator.writeFieldName("gitInfo")
    generator.writeStartObject()
    generator.writeNullField("sha")
    generator.writeStringField("branch", gitBranch)
    generator.writeNullField("originUrl")
    generator.writeEndObject()
  }
  thread.updatedAt?.let { updatedAt ->
    val field = thread.updatedAtField?.takeIf { it.isNotBlank() } ?: "updated_at"
    generator.writeNumberField(field, updatedAt)
  }
  thread.createdAt?.let { createdAt ->
    val field = thread.createdAtField?.takeIf { it.isNotBlank() } ?: "created_at"
    generator.writeNumberField(field, createdAt)
  }
  generator.writeEndObject()
}

private fun writeThreadSource(generator: JsonGenerator, thread: ThreadEntry) {
  if (thread.sourceAsString) {
    generator.writeString(thread.sourceKind)
    return
  }

  when (thread.sourceKind) {
    "subAgentThreadSpawn" -> {
      generator.writeStartObject()
      generator.writeFieldName(thread.sourceSubAgentFieldName)
      generator.writeStartObject()
      generator.writeFieldName("thread_spawn")
      generator.writeStartObject()
      generator.writeStringField("parent_thread_id", thread.parentThreadId ?: "missing-parent")
      generator.writeNumberField("depth", 1)
      if (thread.agentNickname == null) generator.writeNullField("agent_nickname") else generator.writeStringField("agent_nickname", thread.agentNickname)
      if (thread.agentRole == null) generator.writeNullField("agent_role") else generator.writeStringField("agent_role", thread.agentRole)
      generator.writeEndObject()
      generator.writeEndObject()
      generator.writeEndObject()
    }

    "subAgentReview" -> {
      generator.writeStartObject()
      generator.writeStringField(thread.sourceSubAgentFieldName, "review")
      generator.writeEndObject()
    }

    "subAgentCompact" -> {
      generator.writeStartObject()
      generator.writeStringField(thread.sourceSubAgentFieldName, "compact")
      generator.writeEndObject()
    }

    "subAgentOther" -> {
      generator.writeStartObject()
      generator.writeFieldName(thread.sourceSubAgentFieldName)
      generator.writeStartObject()
      generator.writeStringField("other", "custom")
      generator.writeEndObject()
      generator.writeEndObject()
    }

    "subAgent" -> {
      generator.writeStartObject()
      generator.writeStringField(thread.sourceSubAgentFieldName, "memory_consolidation")
      generator.writeEndObject()
    }

    else -> generator.writeString(thread.sourceKind)
  }
}

private fun writeThreadStatus(generator: JsonGenerator, thread: ThreadEntry) {
  generator.writeStartObject()
  generator.writeStringField("type", thread.statusType)
  if (thread.statusType.equals("active", ignoreCase = true)) {
    generator.writeFieldName(thread.statusActiveFlagsFieldName)
    generator.writeStartArray()
    thread.activeFlags.forEach(generator::writeString)
    generator.writeEndArray()
  }
  generator.writeEndObject()
}

private fun writeThreadReadObject(generator: JsonGenerator, thread: ThreadEntry, includeTurns: Boolean) {
  generator.writeStartObject()
  generator.writeStringField("id", thread.id)
  thread.updatedAt?.let { updatedAt ->
    val field = thread.updatedAtField?.takeIf { it.isNotBlank() } ?: "updated_at"
    generator.writeNumberField(field, updatedAt)
  }
  thread.createdAt?.let { createdAt ->
    val field = thread.createdAtField?.takeIf { it.isNotBlank() } ?: "created_at"
    generator.writeNumberField(field, createdAt)
  }
  generator.writeFieldName("status")
  writeThreadStatus(generator, thread)
  if (includeTurns) {
    generator.writeFieldName("turns")
    generator.writeStartArray()
    thread.readTurns.forEach { turn ->
      generator.writeStartObject()
      generator.writeFieldName("status")
      if (turn.statusAsObject) {
        generator.writeStartObject()
        generator.writeStringField("type", turn.statusType)
        generator.writeEndObject()
      }
      else {
        generator.writeString(turn.statusType)
      }
      generator.writeFieldName("items")
      generator.writeStartArray()
      turn.itemTypes.forEach { itemType ->
        generator.writeStartObject()
        generator.writeStringField("type", itemType)
        generator.writeEndObject()
      }
      generator.writeEndArray()
      generator.writeEndObject()
    }
    generator.writeEndArray()
  }
  generator.writeEndObject()
}

private fun startThread(threads: MutableList<ThreadEntry>, cwdOverride: String?): ThreadEntry {
  val now = System.currentTimeMillis()
  val id = "thread-start-$now"
  val cwd = cwdOverride ?: threads.firstOrNull { !it.cwd.isNullOrBlank() }?.cwd ?: System.getProperty("user.dir")
  val thread = ThreadEntry(
    id = id,
    title = "Thread ${id.takeLast(8)}",
    preview = "",
    name = null,
    summary = null,
    cwd = cwd,
    sourceKind = "appServer",
    sourceAsString = false,
    sourceSubAgentFieldName = "subAgent",
    parentThreadId = null,
    agentNickname = null,
    agentRole = null,
    statusType = "idle",
    statusActiveFlagsFieldName = "activeFlags",
    activeFlags = emptyList(),
    readTurns = emptyList(),
    gitBranch = null,
    updatedAt = now,
    updatedAtField = "updated_at",
    createdAt = now,
    createdAtField = "created_at",
    archived = false,
  )
  threads.add(thread)
  return thread
}

}

private fun updateArchive(id: String?, threads: MutableList<ThreadEntry>, archive: Boolean) {
  if (id == null) return
  val thread = threads.firstOrNull { it.id == id } ?: return
  thread.archived = archive
  thread.updatedAt = System.currentTimeMillis()
  if (thread.updatedAtField.isNullOrBlank()) {
    thread.updatedAtField = "updated_at"
  }
}

private fun updateThreadName(id: String?, name: String?, threads: MutableList<ThreadEntry>) {
  if (id == null || name == null) return
  val thread = threads.firstOrNull { it.id == id } ?: return
  thread.name = name
  thread.updatedAt = System.currentTimeMillis()
  if (thread.updatedAtField.isNullOrBlank()) {
    thread.updatedAtField = "updated_at"
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

private fun writeWorkingDirectoryMarker(marker: String) {
  try {
    val markerPath = Path.of(marker)
    val cwd = System.getProperty("user.dir")
    Files.writeString(markerPath, cwd, StandardCharsets.UTF_8)
  }
  catch (_: Throwable) {
  }
}

private fun parseReadTurnsArray(parser: JsonParser, target: MutableCollection<TurnEntry>) {
  while (true) {
    val token = parser.nextToken() ?: return
    if (token == JsonToken.END_ARRAY) {
      return
    }
    if (token != JsonToken.START_OBJECT) {
      parser.skipChildren()
      continue
    }

    var statusType = "completed"
    var statusAsObject = false
    val itemTypes = ArrayList<String>()
    forEachObjectField(parser) { fieldName ->
      when (fieldName) {
        "statusType" -> {
          statusType = readStringOrNull(parser)?.takeIf { it.isNotBlank() } ?: statusType
        }
        "statusAsObject" -> {
          statusAsObject = readBooleanOrNull(parser) ?: false
        }
        "itemTypes" -> {
          if (parser.currentToken == JsonToken.START_ARRAY) {
            parseStringArray(parser, itemTypes)
          }
          else {
            parser.skipChildren()
          }
        }
        else -> parser.skipChildren()
      }
      true
    }

    target.add(
      TurnEntry(
        statusType = statusType,
        statusAsObject = statusAsObject,
        itemTypes = itemTypes,
      )
    )
  }
}

private fun readBooleanOrNull(parser: JsonParser): Boolean? {
  return when (parser.currentToken) {
    JsonToken.VALUE_TRUE -> true
    JsonToken.VALUE_FALSE -> false
    JsonToken.VALUE_NUMBER_INT -> parser.intValue != 0
    JsonToken.VALUE_STRING -> parser.text.toBoolean()
    JsonToken.VALUE_NULL -> null
    else -> {
      parser.skipChildren()
      null
    }
  }
}

private fun parseStringArray(parser: JsonParser, target: MutableCollection<String>) {
  while (true) {
    val token = parser.nextToken() ?: return
    if (token == JsonToken.END_ARRAY) return
    if (token == JsonToken.VALUE_STRING) {
      parser.text
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(target::add)
    }
    else {
      parser.skipChildren()
    }
  }
}
