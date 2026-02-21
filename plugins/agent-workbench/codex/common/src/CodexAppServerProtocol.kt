// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import java.io.Writer

private const val MAX_TITLE_LENGTH = 120

internal data class ThreadListResult(
  @JvmField val threads: List<CodexThread>,
  @JvmField val nextCursor: String?,
)

internal data class CodexAppServerTurnStartResult(
  @JvmField val turnId: String,
  @JvmField val status: String? = null,
)

internal data class ParsedCodexAppServerNotification(
  @JvmField val method: String,
  @JvmField val kind: CodexAppServerNotificationKind,
  @JvmField val threadId: String? = null,
  @JvmField val startedThread: CodexAppServerStartedThread? = null,
  @JvmField val turnId: String? = null,
  @JvmField val turnStatus: String? = null,
  @JvmField val turnErrorMessage: String? = null,
  @JvmField val agentMessageText: String? = null,
) {
  fun toPublicNotification(): CodexAppServerNotification {
    return CodexAppServerNotification(
      method = method,
      kind = kind,
      threadId = threadId,
      startedThread = if (kind == CodexAppServerNotificationKind.THREAD_STARTED) startedThread else null,
    )
  }
}

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
  val threadId = resolveThreadId(payload) ?: return null
  return CodexThread(
    id = threadId, title = resolveThreadTitle(payload, threadId), updatedAt = resolveThreadUpdatedAt(payload), archived = archived,
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

private fun parseTurnFromResultObject(parser: JsonParser): CodexAppServerTurnStartResult? {
  var turnId: String? = null
  var status: String? = null
  forEachObjectField(parser) { fieldName ->
    when (fieldName) {
      "turn" -> {
        if (parser.currentToken == JsonToken.START_OBJECT) {
          val parsedTurn = parseNotificationTurnObject(parser)
          turnId = parsedTurn.turnId ?: turnId
          status = parsedTurn.turnStatus ?: status
        }
        else {
          parser.skipChildren()
        }
      }
      "id" -> turnId = readStringOrNull(parser)?.trim()?.takeIf { it.isNotEmpty() }
      "status" -> status = readStringOrNull(parser)?.trim()?.takeIf { it.isNotEmpty() }
      else -> parser.skipChildren()
    }
    true
  }
  val resolvedTurnId = turnId ?: return null
  return CodexAppServerTurnStartResult(
    turnId = resolvedTurnId,
    status = status,
  )
}

private fun parseTurnErrorMessage(parser: JsonParser): String? {
  if (parser.currentToken == JsonToken.VALUE_STRING) {
    return parser.text?.trim()?.takeIf { it.isNotEmpty() }
  }
  if (parser.currentToken != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var message: String? = null
  forEachObjectField(parser) { fieldName ->
    when (fieldName) {
      "message" -> message = readStringOrNull(parser)?.trim()?.takeIf { it.isNotEmpty() }
      else -> parser.skipChildren()
    }
    true
  }
  return message
}

private fun createStartedThread(payload: ThreadPayload): CodexAppServerStartedThread? {
  val threadId = resolveThreadId(payload) ?: return null
  val cwd = payload.cwd?.let(::normalizeRootPath)?.takeIf { it.isNotBlank() } ?: return null
  return CodexAppServerStartedThread(
    id = threadId,
    title = resolveThreadTitle(payload, threadId),
    updatedAt = resolveThreadUpdatedAt(payload),
    cwd = cwd,
    statusKind = payload.statusKind,
    activeFlags = payload.activeFlags,
  )
}

private fun resolveThreadId(payload: ThreadPayload): String? {
  return payload.id?.trim()?.takeIf { it.isNotEmpty() }
}

private fun resolveThreadUpdatedAt(payload: ThreadPayload): Long {
  return normalizeTimestamp(
    payload.updatedAt
      ?: payload.updatedAtAlt
      ?: payload.createdAt
      ?: payload.createdAtAlt
      ?: 0L
  )
}

private fun resolveThreadTitle(payload: ThreadPayload, threadId: String): String {
  val previewValue = payload.preview ?: payload.title ?: payload.name ?: payload.summary
  return previewValue?.let(::trimTitle)?.takeIf { it.isNotBlank() } ?: "Thread ${threadId.take(8)}"
}

private data class ParsedTurnsActivity(
  @JvmField val latestUserItemIndex: Long,
  @JvmField val latestAssistantItemIndex: Long,
  @JvmField val isReviewing: Boolean,
  @JvmField val hasInProgressTurn: Boolean,
)

private data class ParsedTurnItemsActivity(
  @JvmField val latestUserItemIndex: Long,
  @JvmField val latestAssistantItemIndex: Long,
  @JvmField val isReviewing: Boolean,
  @JvmField val nextItemIndex: Long,
)

private fun parseThreadActivitySnapshot(parser: JsonParser): CodexThreadActivitySnapshot? {
  if (parser.currentToken != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var id: String? = null
  var updatedAt: Long? = null
  var updatedAtAlt: Long? = null
  var createdAt: Long? = null
  var createdAtAlt: Long? = null
  var statusKind = CodexThreadStatusKind.UNKNOWN
  var activeFlags: List<CodexThreadActiveFlag> = emptyList()
  var latestUserItemIndex = Long.MIN_VALUE
  var latestAssistantItemIndex = Long.MIN_VALUE
  var isReviewing = false
  var hasInProgressTurn = false

  forEachObjectField(parser) { fieldName ->
    when (fieldName) {
      "id" -> id = readStringOrNull(parser)
      "updatedAt" -> updatedAt = readLongOrNull(parser)
      "updated_at" -> updatedAtAlt = readLongOrNull(parser)
      "createdAt" -> createdAt = readLongOrNull(parser)
      "created_at" -> createdAtAlt = readLongOrNull(parser)
      "status" -> {
        val parsedStatus = parseThreadStatus(parser)
        statusKind = parsedStatus.statusKind
        activeFlags = parsedStatus.activeFlags
      }
      "turns" -> {
        val parsedTurns = parseTurnsActivity(parser)
        latestUserItemIndex = parsedTurns.latestUserItemIndex
        latestAssistantItemIndex = parsedTurns.latestAssistantItemIndex
        isReviewing = parsedTurns.isReviewing
        hasInProgressTurn = parsedTurns.hasInProgressTurn
      }
      else -> parser.skipChildren()
    }
    true
  }

  val threadId = id ?: return null
  val resolvedUpdatedAt = normalizeTimestamp(
    updatedAt
      ?: updatedAtAlt
      ?: createdAt
      ?: createdAtAlt
      ?: 0L
  )
  val hasUnreadAssistantMessage = latestAssistantItemIndex > latestUserItemIndex
  return CodexThreadActivitySnapshot(
    threadId = threadId,
    updatedAt = resolvedUpdatedAt,
    statusKind = statusKind,
    activeFlags = activeFlags,
    hasUnreadAssistantMessage = hasUnreadAssistantMessage,
    isReviewing = isReviewing,
    hasInProgressTurn = hasInProgressTurn,
  )
}

private fun parseTurnsActivity(parser: JsonParser): ParsedTurnsActivity {
  if (parser.currentToken != JsonToken.START_ARRAY) {
    parser.skipChildren()
    return ParsedTurnsActivity(
      latestUserItemIndex = Long.MIN_VALUE,
      latestAssistantItemIndex = Long.MIN_VALUE,
      isReviewing = false,
      hasInProgressTurn = false,
    )
  }

  var latestUserItemIndex = Long.MIN_VALUE
  var latestAssistantItemIndex = Long.MIN_VALUE
  var isReviewing = false
  var hasInProgressTurn = false
  var nextItemIndex = 0L

  while (true) {
    val token = parser.nextToken() ?: break
    if (token == JsonToken.END_ARRAY) {
      break
    }
    if (token != JsonToken.START_OBJECT) {
      parser.skipChildren()
      continue
    }

    forEachObjectField(parser) { fieldName ->
      when (fieldName) {
        "status" -> {
          if (parseTurnInProgress(parser)) {
            hasInProgressTurn = true
          }
        }
        "items" -> {
          val parsedItems = parseTurnItemsActivity(
            parser = parser,
            initialReviewing = isReviewing,
            startItemIndex = nextItemIndex,
          )
          latestUserItemIndex = maxOf(latestUserItemIndex, parsedItems.latestUserItemIndex)
          latestAssistantItemIndex = maxOf(latestAssistantItemIndex, parsedItems.latestAssistantItemIndex)
          isReviewing = parsedItems.isReviewing
          nextItemIndex = parsedItems.nextItemIndex
        }
        else -> parser.skipChildren()
      }
      true
    }
  }

  return ParsedTurnsActivity(
    latestUserItemIndex = latestUserItemIndex,
    latestAssistantItemIndex = latestAssistantItemIndex,
    isReviewing = isReviewing,
    hasInProgressTurn = hasInProgressTurn,
  )
}

private fun parseTurnItemsActivity(
  parser: JsonParser,
  initialReviewing: Boolean,
  startItemIndex: Long,
): ParsedTurnItemsActivity {
  if (parser.currentToken != JsonToken.START_ARRAY) {
    parser.skipChildren()
    return ParsedTurnItemsActivity(
      latestUserItemIndex = Long.MIN_VALUE,
      latestAssistantItemIndex = Long.MIN_VALUE,
      isReviewing = initialReviewing,
      nextItemIndex = startItemIndex,
    )
  }

  var latestUserItemIndex = Long.MIN_VALUE
  var latestAssistantItemIndex = Long.MIN_VALUE
  var isReviewing = initialReviewing
  var nextItemIndex = startItemIndex

  while (true) {
    val token = parser.nextToken() ?: break
    if (token == JsonToken.END_ARRAY) {
      break
    }
    if (token != JsonToken.START_OBJECT) {
      parser.skipChildren()
      continue
    }

    var itemType: String? = null
    forEachObjectField(parser) { itemFieldName ->
      when (itemFieldName) {
        "type" -> itemType = readStringOrNull(parser)
        else -> parser.skipChildren()
      }
      true
    }

    nextItemIndex += 1
    when (normalizeToken(itemType)) {
      "usermessage" -> latestUserItemIndex = maxOf(latestUserItemIndex, nextItemIndex)
      "agentmessage" -> latestAssistantItemIndex = maxOf(latestAssistantItemIndex, nextItemIndex)
      "enteredreviewmode" -> isReviewing = true
      "exitedreviewmode" -> isReviewing = false
    }
  }

  return ParsedTurnItemsActivity(
    latestUserItemIndex = latestUserItemIndex,
    latestAssistantItemIndex = latestAssistantItemIndex,
    isReviewing = isReviewing,
    nextItemIndex = nextItemIndex,
  )
}

private fun parseTurnInProgress(parser: JsonParser): Boolean {
  return when (parser.currentToken) {
    JsonToken.VALUE_STRING -> normalizeToken(parser.text) == "inprogress"
    JsonToken.START_OBJECT -> {
      var inProgress = false
      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "type" -> {
            inProgress = normalizeToken(readStringOrNull(parser)) == "inprogress"
          }
          else -> parser.skipChildren()
        }
        true
      }
      inProgress
    }
    else -> {
      parser.skipChildren()
      false
    }
  }
}

private fun normalizeToken(value: String?): String {
  return value
    ?.trim()
    ?.lowercase()
    ?.replace("_", "")
    ?.replace("-", "")
    ?.replace(" ", "")
    .orEmpty()
}

private data class ThreadPayload(
  @JvmField val id: String?,
  @JvmField val updatedAt: Long?,
  @JvmField val updatedAtAlt: Long?,
  @JvmField val createdAt: Long?,
  @JvmField val createdAtAlt: Long?,
  @JvmField val preview: String?,
  @JvmField val title: String?,
  @JvmField val name: String?,
  @JvmField val summary: String?,
  @JvmField val cwd: String?,
  @JvmField val nestedThread: CodexThread?,
  @JvmField val gitBranch: String?,
  @JvmField val sourceKind: CodexThreadSourceKind,
  @JvmField val parentThreadId: String?,
  @JvmField val agentNickname: String?,
  @JvmField val agentRole: String?,
  @JvmField val statusKind: CodexThreadStatusKind,
  @JvmField val activeFlags: List<CodexThreadActiveFlag>,
)

private data class ParsedThreadSource(
  @JvmField val sourceKind: CodexThreadSourceKind,
  @JvmField val parentThreadId: String?,
)

private data class ParsedThreadStatus(
  @JvmField val statusKind: CodexThreadStatusKind,
  @JvmField val activeFlags: List<CodexThreadActiveFlag>,
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
