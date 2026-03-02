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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private data class ThreadEntry(
  val id: String,
  val title: String?,
  val preview: String?,
  val name: String?,
  val summary: String?,
  val cwd: String?,
  val sourceKind: String,
  val sourceAsString: Boolean,
  val sourceSubAgentFieldName: String,
  val parentThreadId: String?,
  val agentNickname: String?,
  val agentRole: String?,
  val statusType: String,
  val statusActiveFlagsFieldName: String,
  val activeFlags: List<String>,
  val gitBranch: String?,
  var updatedAt: Long?,
  var updatedAtField: String?,
  val createdAt: Long?,
  val createdAtField: String?,
  var archived: Boolean,
)

private data class Request(
  val id: String,
  val method: String,
  val params: RequestParams,
)

private data class RequestParams(
  val id: String? = null,
  val archived: Boolean? = null,
  val cursor: String? = null,
  val limit: Int? = null,
  val cwd: String? = null,
  val sourceKinds: Set<String>? = null,
)

private val DEFAULT_THREAD_LIST_SOURCE_KINDS = linkedSetOf("cli", "vscode", "appServer")

internal object CodexTestAppServer {
  private val jsonFactory = JsonFactory()
  private const val CWD_MARKER_ENV = "CODEX_TEST_CWD_MARKER"
  private const val ERROR_METHOD_ENV = "CODEX_TEST_ERROR_METHOD"
  private const val ERROR_MESSAGE_ENV = "CODEX_TEST_ERROR_MESSAGE"
  private const val REQUEST_LOG_ENV = "CODEX_TEST_REQUEST_LOG"

  @JvmStatic
  fun main(args: Array<String>) {
    val configPath = args.firstOrNull()?.let(Path::of)
      ?: error("Expected config path argument")
    val threads = loadThreads(configPath)
    val errorMethod = readEnv(ERROR_METHOD_ENV)
    val errorMessage = readEnv(ERROR_MESSAGE_ENV)
    val requestLogPath = readEnv(REQUEST_LOG_ENV)?.let(Path::of)
    readEnv(CWD_MARKER_ENV)?.let(::writeWorkingDirectoryMarker)
    val reader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))
    val writer = BufferedWriter(OutputStreamWriter(System.out, StandardCharsets.UTF_8))
    while (true) {
      val line = reader.readLine() ?: break
      val payload = line.trim()
      if (payload.isEmpty()) continue
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
          val startedThread = startThread(threads)
          writeResponse(writer, request.id, resultWriter = { generator ->
            generator.writeStartObject()
            generator.writeFieldName("thread")
            writeThreadObject(generator, startedThread)
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
        "thread/archive" -> {
          updateArchive(request.params.id, threads, archive = true)
          writeResponse(writer, request.id, ::writeEmptyObject)
        }
        "thread/unarchive" -> {
          updateArchive(request.params.id, threads, archive = false)
          writeResponse(writer, request.id, ::writeEmptyObject)
        }
        "turn/start" -> writeResponse(writer, request.id, resultWriter = { generator ->
          generator.writeStartObject()
          generator.writeFieldName("turn")
          generator.writeStartObject()
          generator.writeStringField("id", "turn-${System.currentTimeMillis()}")
          generator.writeStringField("status", "completed")
          generator.writeEndObject()
          generator.writeEndObject()
        })
        "turn/interrupt" -> writeResponse(writer, request.id, ::writeEmptyObject)
        else -> writeResponse(writer, request.id, ::writeEmptyObject, errorMessage = "Unknown method: ${request.method}")
      }
    }
  }

  private fun parseRequest(payload: String): Request? {
    jsonFactory.createParser(payload).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) return null
      var id: String? = null
      var method: String? = null
      var paramsId: String? = null
      var paramsArchived: Boolean? = null
      var paramsCursor: String? = null
      var paramsLimit: Int? = null
      var paramsCwd: String? = null
      var paramsSourceKinds: MutableSet<String>? = null
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
                  "archived" -> paramsArchived = readBooleanOrNull(parser)
                  "cursor" -> paramsCursor = readStringOrNull(parser)
                  "limit" -> paramsLimit = readLongOrNull(parser)?.toInt()
                  "cwd" -> paramsCwd = readStringOrNull(parser)
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
          archived = paramsArchived,
          cursor = paramsCursor,
          limit = paramsLimit,
          cwd = paramsCwd,
          sourceKinds = paramsSourceKinds,
        )
      )
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

private fun startThread(threads: MutableList<ThreadEntry>): ThreadEntry {
  val now = System.currentTimeMillis()
  val id = "thread-start-$now"
  val cwd = threads.firstOrNull { !it.cwd.isNullOrBlank() }?.cwd ?: System.getProperty("user.dir")
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
