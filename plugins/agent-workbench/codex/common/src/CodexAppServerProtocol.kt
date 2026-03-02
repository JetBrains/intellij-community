// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import java.io.Writer

private const val MAX_TITLE_LENGTH = 120

internal data class ThreadListResult(
  val threads: List<CodexThread>,
  val nextCursor: String?,
)

internal class CodexAppServerProtocol {
  private val jsonFactory = JsonFactory()

  fun writePayload(out: Writer, payloadWriter: (JsonGenerator) -> Unit) {
    val generator = jsonFactory.createGenerator(out)
    generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
    payloadWriter(generator)
    generator.close()
  }

  fun <T> parseResponse(payload: String, resultParser: (JsonParser) -> T, defaultResult: T): T {
    jsonFactory.createParser(payload).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) return defaultResult
      var result: T = defaultResult
      var hasResult = false
      var errorSeen = false
      var errorMessage: String? = null
      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "result" -> {
            result = resultParser(parser)
            hasResult = true
          }
          "error" -> {
            errorSeen = true
            errorMessage = readErrorMessage(parser)
          }
          else -> parser.skipChildren()
        }
        true
      }
      if (errorSeen) {
        throw CodexAppServerException(errorMessage ?: "Codex app-server error")
      }
      return if (hasResult) result else defaultResult
    }
  }

  fun parseMessageId(payload: String): String? {
    jsonFactory.createParser(payload).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) return null
      var id: String? = null
      forEachObjectField(parser) { fieldName ->
        if (fieldName == "id") {
          id = readStringOrNull(parser)
          return@forEachObjectField false
        }
        parser.skipChildren()
        true
      }
      return id
    }
  }

  fun parseThreadListResult(parser: JsonParser, archived: Boolean, cwdFilter: String? = null): ThreadListResult {
    if (parser.currentToken != JsonToken.START_OBJECT) {
      parser.skipChildren()
      return ThreadListResult(threads = emptyList(), nextCursor = null)
    }

    val threads = mutableListOf<CodexThread>()
    var nextCursor: String? = null
    forEachObjectField(parser) { fieldName ->
      when (fieldName) {
        "data" -> if (parser.currentToken == JsonToken.START_ARRAY) parseThreadArray(parser, archived, threads, cwdFilter) else parser.skipChildren()
        "nextCursor", "next_cursor" -> nextCursor = readStringOrNull(parser)
        else -> parser.skipChildren()
      }
      true
    }
    return ThreadListResult(threads, nextCursor)
  }

  fun parseThreadStartResult(parser: JsonParser): CodexThread {
    if (parser.currentToken != JsonToken.START_OBJECT) {
      parser.skipChildren()
      throw CodexAppServerException("Codex app-server returned invalid thread/start result")
    }

    return parseThreadFromResultObject(parser)
      ?: throw CodexAppServerException("Codex app-server returned thread/start result without thread data")
  }

}

private fun parseThreadArray(parser: JsonParser, archived: Boolean, threads: MutableList<CodexThread>, cwdFilter: String?) {
  while (true) {
    val token = parser.nextToken() ?: return
    if (token == JsonToken.END_ARRAY) return
    if (token == JsonToken.START_OBJECT) {
      parseThreadObject(parser, archived, cwdFilter)?.let(threads::add)
    }
    else {
      parser.skipChildren()
    }
  }
}

private fun parseThreadObject(parser: JsonParser, archived: Boolean, cwdFilter: String?): CodexThread? {
  val payload = parseThreadPayload(parser, allowNestedThread = false)
  if (!cwdFilter.isNullOrBlank()) {
    val normalizedCwd = payload.cwd?.let(::normalizeRootPath)
    if (normalizedCwd.isNullOrBlank() || normalizedCwd != cwdFilter) {
      return null
    }
  }
  return createCodexThread(payload = payload, archived = archived)
}

private fun createCodexThread(payload: ThreadPayload, archived: Boolean): CodexThread? {
  val threadId = payload.id ?: return null
  val updatedAtValue = normalizeTimestamp(
    payload.updatedAt
      ?: payload.updatedAtAlt
      ?: payload.createdAt
      ?: payload.createdAtAlt
      ?: 0L
  )
  val previewValue = payload.preview ?: payload.title ?: payload.name ?: payload.summary
  val threadTitle = previewValue?.let { trimTitle(it) }?.takeIf { it.isNotBlank() } ?: "Thread ${threadId.take(8)}"
  return CodexThread(
    id = threadId, title = threadTitle, updatedAt = updatedAtValue, archived = archived,
    gitBranch = payload.gitBranch,
    cwd = payload.cwd?.let(::normalizeRootPath),
    sourceKind = payload.sourceKind,
    parentThreadId = payload.parentThreadId,
    agentNickname = payload.agentNickname,
    agentRole = payload.agentRole,
    statusKind = payload.statusKind,
    activeFlags = payload.activeFlags,
  )
}

private fun parseThreadFromResultObject(parser: JsonParser): CodexThread? {
  val payload = parseThreadPayload(parser, allowNestedThread = true)
  if (payload.nestedThread != null) return payload.nestedThread
  return createCodexThread(payload = payload, archived = false)
}

private data class ThreadPayload(
  val id: String?,
  val updatedAt: Long?,
  val updatedAtAlt: Long?,
  val createdAt: Long?,
  val createdAtAlt: Long?,
  val preview: String?,
  val title: String?,
  val name: String?,
  val summary: String?,
  val cwd: String?,
  val nestedThread: CodexThread?,
  val gitBranch: String?,
  val sourceKind: CodexThreadSourceKind,
  val parentThreadId: String?,
  val agentNickname: String?,
  val agentRole: String?,
  val statusKind: CodexThreadStatusKind,
  val activeFlags: List<CodexThreadActiveFlag>,
)

private data class ParsedThreadSource(
  val sourceKind: CodexThreadSourceKind,
  val parentThreadId: String?,
)

private data class ParsedThreadStatus(
  val statusKind: CodexThreadStatusKind,
  val activeFlags: List<CodexThreadActiveFlag>,
)

@Suppress("DuplicatedCode")
private fun parseThreadPayload(parser: JsonParser, allowNestedThread: Boolean): ThreadPayload {
  var id: String? = null
  var updatedAt: Long? = null
  var updatedAtAlt: Long? = null
  var createdAt: Long? = null
  var createdAtAlt: Long? = null
  var preview: String? = null
  var title: String? = null
  var name: String? = null
  var summary: String? = null
  var cwd: String? = null
  var nestedThread: CodexThread? = null
  var gitBranch: String? = null
  var sourceKind: CodexThreadSourceKind = CodexThreadSourceKind.UNKNOWN
  var parentThreadId: String? = null
  var agentNickname: String? = null
  var agentRole: String? = null
  var statusKind: CodexThreadStatusKind = CodexThreadStatusKind.UNKNOWN
  var activeFlags: List<CodexThreadActiveFlag> = emptyList()

  forEachObjectField(parser) { fieldName ->
    when (fieldName) {
      "thread", "data" -> {
        if (allowNestedThread && parser.currentToken == JsonToken.START_OBJECT) {
          nestedThread = parseThreadObject(parser, archived = false, cwdFilter = null)
        }
        else {
          parser.skipChildren()
        }
      }
      "id" -> id = readStringOrNull(parser)
      "updatedAt" -> updatedAt = readLongOrNull(parser)
      "updated_at" -> updatedAtAlt = readLongOrNull(parser)
      "createdAt" -> createdAt = readLongOrNull(parser)
      "created_at" -> createdAtAlt = readLongOrNull(parser)
      "preview" -> preview = readStringOrNull(parser)
      "title" -> title = readStringOrNull(parser)
      "name" -> name = readStringOrNull(parser)
      "summary" -> summary = readStringOrNull(parser)
      "cwd" -> cwd = readStringOrNull(parser)
      "gitBranch", "git_branch" -> gitBranch = readStringOrNull(parser)
      "gitInfo", "git_info" -> gitBranch = parseGitBranch(parser) ?: gitBranch
      "source" -> {
        val parsedSource = parseThreadSource(parser)
        sourceKind = parsedSource.sourceKind
        parentThreadId = parsedSource.parentThreadId
      }
      "agentNickname", "agent_nickname" -> agentNickname = readStringOrNull(parser)
      "agentRole", "agent_role" -> agentRole = readStringOrNull(parser)
      "status" -> {
        val parsedStatus = parseThreadStatus(parser)
        statusKind = parsedStatus.statusKind
        activeFlags = parsedStatus.activeFlags
      }
      else -> parser.skipChildren()
    }
    true
  }

  return ThreadPayload(
    id = id,
    updatedAt = updatedAt,
    updatedAtAlt = updatedAtAlt,
    createdAt = createdAt,
    createdAtAlt = createdAtAlt,
    preview = preview,
    title = title,
    name = name,
    summary = summary,
    cwd = cwd,
    nestedThread = nestedThread,
    gitBranch = gitBranch,
    sourceKind = sourceKind,
    parentThreadId = parentThreadId,
    agentNickname = agentNickname,
    agentRole = agentRole,
    statusKind = statusKind,
    activeFlags = activeFlags,
  )
}

private fun parseGitBranch(parser: JsonParser): String? {
  if (parser.currentToken != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var branch: String? = null
  forEachObjectField(parser) { fieldName ->
    if (fieldName == "branch") {
      branch = readStringOrNull(parser)
    }
    else {
      parser.skipChildren()
    }
    true
  }
  return branch
}

private fun parseThreadSource(parser: JsonParser): ParsedThreadSource {
  return when (parser.currentToken) {
    JsonToken.VALUE_STRING -> ParsedThreadSource(
      sourceKind = parseSourceKind(readStringOrNull(parser)),
      parentThreadId = null,
    )
    JsonToken.START_OBJECT -> {
      var sourceKind = CodexThreadSourceKind.UNKNOWN
      var parentThreadId: String? = null
      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "subAgent", "sub_agent", "subagent" -> {
            val parsed = parseSubAgentSource(parser)
            sourceKind = parsed.sourceKind
            parentThreadId = parsed.parentThreadId
          }
          else -> parser.skipChildren()
        }
        true
      }
      ParsedThreadSource(sourceKind = sourceKind, parentThreadId = parentThreadId)
    }
    else -> {
      parser.skipChildren()
      ParsedThreadSource(sourceKind = CodexThreadSourceKind.UNKNOWN, parentThreadId = null)
    }
  }
}

private fun parseSubAgentSource(parser: JsonParser): ParsedThreadSource {
  return when (parser.currentToken) {
    JsonToken.VALUE_STRING -> {
      val value = readStringOrNull(parser)
      val sourceKind = when (value) {
        "review" -> CodexThreadSourceKind.SUB_AGENT_REVIEW
        "compact" -> CodexThreadSourceKind.SUB_AGENT_COMPACT
        else -> CodexThreadSourceKind.SUB_AGENT
      }
      ParsedThreadSource(sourceKind = sourceKind, parentThreadId = null)
    }
    JsonToken.START_OBJECT -> {
      var sourceKind = CodexThreadSourceKind.SUB_AGENT
      var parentThreadId: String? = null
      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "thread_spawn", "threadSpawn" -> {
            sourceKind = CodexThreadSourceKind.SUB_AGENT_THREAD_SPAWN
            parentThreadId = parseThreadSpawnParentId(parser)
          }
          "other" -> {
            sourceKind = CodexThreadSourceKind.SUB_AGENT_OTHER
            parser.skipChildren()
          }
          else -> parser.skipChildren()
        }
        true
      }
      ParsedThreadSource(sourceKind = sourceKind, parentThreadId = parentThreadId)
    }
    else -> {
      parser.skipChildren()
      ParsedThreadSource(sourceKind = CodexThreadSourceKind.SUB_AGENT, parentThreadId = null)
    }
  }
}

private fun parseThreadSpawnParentId(parser: JsonParser): String? {
  if (parser.currentToken != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var parentThreadId: String? = null
  forEachObjectField(parser) { fieldName ->
    when (fieldName) {
      "parent_thread_id", "parentThreadId" -> parentThreadId = readStringOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return parentThreadId
}

private fun parseSourceKind(value: String?): CodexThreadSourceKind {
  val normalized = value
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.lowercase()
    ?: return CodexThreadSourceKind.UNKNOWN
  return when (normalized) {
    "cli" -> CodexThreadSourceKind.CLI
    "vscode" -> CodexThreadSourceKind.VSCODE
    "exec" -> CodexThreadSourceKind.EXEC
    "appserver", "app_server", "app-server" -> CodexThreadSourceKind.APP_SERVER
    "subagent", "sub_agent", "sub-agent" -> CodexThreadSourceKind.SUB_AGENT
    "subagentreview", "sub_agent_review", "sub-agent-review" -> CodexThreadSourceKind.SUB_AGENT_REVIEW
    "subagentcompact", "sub_agent_compact", "sub-agent-compact" -> CodexThreadSourceKind.SUB_AGENT_COMPACT
    "subagentthreadspawn", "sub_agent_thread_spawn", "sub-agent-thread-spawn" -> CodexThreadSourceKind.SUB_AGENT_THREAD_SPAWN
    "subagentother", "sub_agent_other", "sub-agent-other" -> CodexThreadSourceKind.SUB_AGENT_OTHER
    "unknown" -> CodexThreadSourceKind.UNKNOWN
    else -> CodexThreadSourceKind.UNKNOWN
  }
}

private fun parseThreadStatus(parser: JsonParser): ParsedThreadStatus {
  return when (parser.currentToken) {
    JsonToken.VALUE_STRING -> ParsedThreadStatus(
      statusKind = parseStatusKind(readStringOrNull(parser)),
      activeFlags = emptyList(),
    )
    JsonToken.START_OBJECT -> {
      var statusKind = CodexThreadStatusKind.UNKNOWN
      val activeFlags = LinkedHashSet<CodexThreadActiveFlag>()
      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "type" -> statusKind = parseStatusKind(readStringOrNull(parser))
          "activeFlags", "active_flags" -> {
            if (parser.currentToken == JsonToken.START_ARRAY) {
              parseActiveFlags(parser, activeFlags)
            }
            else {
              parser.skipChildren()
            }
          }
          else -> parser.skipChildren()
        }
        true
      }
      ParsedThreadStatus(statusKind = statusKind, activeFlags = ArrayList(activeFlags))
    }
    else -> {
      parser.skipChildren()
      ParsedThreadStatus(statusKind = CodexThreadStatusKind.UNKNOWN, activeFlags = emptyList())
    }
  }
}

private fun parseActiveFlags(parser: JsonParser, target: MutableSet<CodexThreadActiveFlag>) {
  while (true) {
    val token = parser.nextToken() ?: return
    if (token == JsonToken.END_ARRAY) return
    if (token != JsonToken.VALUE_STRING) {
      parser.skipChildren()
      continue
    }
    when (parser.text.lowercase()) {
      "waitingonapproval", "waiting_on_approval", "waiting-on-approval" -> target.add(CodexThreadActiveFlag.WAITING_ON_APPROVAL)
      "waitingonuserinput", "waiting_on_user_input", "waiting-on-user-input" -> target.add(CodexThreadActiveFlag.WAITING_ON_USER_INPUT)
    }
  }
}

private fun parseStatusKind(value: String?): CodexThreadStatusKind {
  val normalized = value
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.lowercase()
    ?: return CodexThreadStatusKind.UNKNOWN
  return when (normalized) {
    "notloaded", "not_loaded", "not-loaded" -> CodexThreadStatusKind.NOT_LOADED
    "idle" -> CodexThreadStatusKind.IDLE
    "active" -> CodexThreadStatusKind.ACTIVE
    "systemerror", "system_error", "system-error" -> CodexThreadStatusKind.SYSTEM_ERROR
    else -> CodexThreadStatusKind.UNKNOWN
  }
}

private fun normalizeTimestamp(value: Long): Long {
  if (value <= 0L) {
    return 0L
  }
  return if (value < 100_000_000_000L) value * 1000L else value
}

private fun trimTitle(value: String): String {
  val trimmed = value.trim()
  if (trimmed.length <= MAX_TITLE_LENGTH) {
    return trimmed
  }
  return trimmed.take(MAX_TITLE_LENGTH - 3).trimEnd() + "..."
}

fun normalizeRootPath(value: String): String {
  return value.replace('\\', '/').trimEnd('/')
}

private fun readErrorMessage(parser: JsonParser): String? {
  return when (parser.currentToken) {
    JsonToken.VALUE_STRING -> parser.text
    JsonToken.START_OBJECT -> {
      var message: String? = null
      forEachObjectField(parser) { fieldName ->
        if (fieldName == "message") {
          message = readStringOrNull(parser)
        }
        else {
          parser.skipChildren()
        }
        true
      }
      message
    }
    else -> {
      parser.skipChildren()
      null
    }
  }
}
