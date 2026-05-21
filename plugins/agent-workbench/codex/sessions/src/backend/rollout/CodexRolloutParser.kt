// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.rollout

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.CodexThreadActiveFlag
import com.intellij.agent.workbench.codex.common.CodexThreadSourceKind
import com.intellij.agent.workbench.codex.common.CodexThreadStatusKind
import com.intellij.agent.workbench.codex.common.forEachObjectField
import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.common.readStringOrNull
import com.intellij.agent.workbench.codex.sessions.backend.CodexActivitySignals
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.resolveCodexSessionActivity
import com.intellij.agent.workbench.json.WorkbenchJsonlScanner
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeParseException

private const val USER_MESSAGE_BEGIN = "## My request for Codex:"
private const val ENVIRONMENT_CONTEXT_OPEN_TAG = "<environment_context>"
private const val TURN_ABORTED_OPEN_TAG = "<turn_aborted>"
private val THREAD_TITLE_WHITESPACE = Regex("\\s+")

private val LOG = logger<CodexRolloutParser>()

internal class CodexRolloutParser(
  private val jsonFactory: JsonFactory = JsonFactory(),
) {
  fun parse(path: Path): ParsedRolloutThread? {
    val state = try {
      WorkbenchJsonlScanner.scanJsonObjects(
        path = path,
        jsonFactory = jsonFactory,
        newState = ::RolloutParseState,
      ) { parser, parseState ->
        val event = parseEvent(parser) ?: return@scanJsonObjects true
        reduceEvent(parseState, event)
        true
      }
    }
    catch (_: Throwable) {
      return null
    }

    val normalizedCwd = normalizeRootPath(state.sessionCwd ?: return null)
    val resolvedSessionId = state.sessionId ?: return null
    val hasUnread = state.latestAgentMessageAt > state.latestUserMessageAt
    val hasPendingUserInput = state.pendingUserInputByCallId.isNotEmpty()
    val activity = resolveCodexSessionActivity(
      CodexActivitySignals(
        statusKind = CodexThreadStatusKind.IDLE,
        activeFlags = if (hasPendingUserInput) setOf(CodexThreadActiveFlag.WAITING_ON_USER_INPUT) else emptySet(),
        hasUnreadAssistantMessage = hasUnread,
        isReviewing = state.reviewing,
        hasInProgressTurn = state.processing,
      )
    )

    val fallbackUpdatedAt = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
    val resolvedUpdatedAt = if (state.updatedAt > 0L) state.updatedAt else fallbackUpdatedAt
    val fallbackTitle = "Thread ${resolvedSessionId.take(8)}"
    val resolvedTitle = state.title ?: fallbackTitle
    val usedFallbackTitle = state.title == null
    val sourceKind = when {
      state.sourceKind != CodexThreadSourceKind.UNKNOWN -> state.sourceKind
      state.parentThreadId != null -> CodexThreadSourceKind.SUB_AGENT_THREAD_SPAWN
      else -> CodexThreadSourceKind.CLI
    }

    LOG.debug {
      "Parsed rollout thread (sessionId=$resolvedSessionId, cwd=$normalizedCwd, title=$resolvedTitle, fallbackTitle=$usedFallbackTitle, updatedAt=$resolvedUpdatedAt, activity=$activity)"
    }

    return ParsedRolloutThread(
      normalizedCwd = normalizedCwd,
      parentThreadId = state.parentThreadId,
      thread = CodexBackendThread(
        thread = CodexThread(
          id = resolvedSessionId,
          title = resolvedTitle,
          updatedAt = resolvedUpdatedAt,
          archived = false,
          gitBranch = state.gitBranch,
          cwd = normalizedCwd,
          sourceKind = sourceKind,
          parentThreadId = state.parentThreadId,
        ),
        activity = activity,
        requiresResponse = hasPendingUserInput,
      ),
    )
  }

}

private fun reduceEvent(parseState: RolloutParseState, event: RolloutEvent) {
  parseState.updatedAt = maxTimestamp(parseState.updatedAt, event.timestampMs)
  parseState.updatedAt = maxTimestamp(parseState.updatedAt, event.sessionTimestampMs)
  parseState.sessionId = parseState.sessionId ?: event.sessionId
  parseState.sessionCwd = parseState.sessionCwd ?: event.sessionCwd
  if (parseState.sourceKind == CodexThreadSourceKind.UNKNOWN && event.sourceKind != CodexThreadSourceKind.UNKNOWN) {
    parseState.sourceKind = event.sourceKind
  }
  parseState.parentThreadId = parseState.parentThreadId ?: event.parentThreadId
  parseState.gitBranch = parseState.gitBranch ?: event.gitBranch

  val eventTimestamp = event.timestampMs
  when (event.topLevelType) {
    "event_msg" -> {
      when (event.payloadType) {
        "task_started", "turn_started" -> parseState.processing = true
        "task_complete", "turn_complete", "turn_aborted" -> parseState.processing = false
        "user_message" -> {
          parseState.latestUserMessageAt = maxTimestamp(parseState.latestUserMessageAt, eventTimestamp)
          parseState.title = parseState.title ?: extractTitle(event.payloadMessage)
          val pendingInputAt = parseState.latestPendingUserInputAt()
          if (pendingInputAt != null && eventTimestamp != null && eventTimestamp >= pendingInputAt) {
            parseState.pendingUserInputByCallId.clear()
          }
        }

        "thread_name_updated", "threadNameUpdated" -> {
          parseState.title = extractThreadName(event.payloadThreadName) ?: parseState.title
        }

        "agent_message" -> {
          parseState.latestAgentMessageAt = maxTimestamp(parseState.latestAgentMessageAt, eventTimestamp)
        }

        "request_user_input" -> {
          parseState.markPendingUserInput(eventTimestamp = eventTimestamp, callId = event.payloadCallId)
        }

        "entered_review_mode" -> parseState.reviewing = true
        "exited_review_mode" -> parseState.reviewing = false
      }
    }

    "response_item" -> {
      when (event.payloadType) {
        "message" -> {
          when (event.payloadRole) {
            "user" -> {
              parseState.latestUserMessageAt = maxTimestamp(parseState.latestUserMessageAt, eventTimestamp)
              val pendingInputAt = parseState.latestPendingUserInputAt()
              if (pendingInputAt != null && eventTimestamp != null && eventTimestamp >= pendingInputAt) {
                parseState.pendingUserInputByCallId.clear()
              }
            }

            "assistant" -> {
              parseState.latestAgentMessageAt = maxTimestamp(parseState.latestAgentMessageAt, eventTimestamp)
            }
          }
        }

        "function_call" -> {
          if (event.payloadName == "request_user_input") {
            parseState.markPendingUserInput(eventTimestamp = eventTimestamp, callId = event.payloadCallId)
          }
        }

        "function_call_output" -> {
          event.payloadCallId?.let(parseState.pendingUserInputByCallId::remove)
        }
      }
    }
  }
}

private fun parseEvent(parser: JsonParser): RolloutEvent? {
  return try {
    if (parser.currentToken != JsonToken.START_OBJECT) return null

    var topLevelType: String? = null
    var timestampMs: Long? = null
    var payloadType: String? = null
    var payloadRole: String? = null
    var payloadMessage: String? = null
    var payloadName: String? = null
    var payloadCallId: String? = null
    var payloadThreadName: String? = null
    var sessionId: String? = null
    var sessionCwd: String? = null
    var sessionTimestampMs: Long? = null
    var sourceKind = CodexThreadSourceKind.UNKNOWN
    var parentThreadId: String? = null
    var gitBranch: String? = null

    forEachObjectField(parser) { fieldName ->
      when (fieldName) {
        "timestamp" -> timestampMs = parseIsoTimestamp(readStringOrNull(parser))
        "type" -> topLevelType = readStringOrNull(parser)
        "payload" -> {
          if (parser.currentToken == JsonToken.START_OBJECT) {
            forEachObjectField(parser) { payloadField ->
              when (payloadField) {
                "type" -> payloadType = readStringOrNull(parser)
                "role" -> payloadRole = readStringOrNull(parser)
                "message" -> payloadMessage = readStringOrNull(parser)
                "name" -> payloadName = readStringOrNull(parser)
                "call_id" -> payloadCallId = readStringOrNull(parser)
                "thread_name", "threadName" -> payloadThreadName = readStringOrNull(parser)
                "id" -> sessionId = readStringOrNull(parser)
                "cwd" -> sessionCwd = readStringOrNull(parser)
                "timestamp" -> sessionTimestampMs = parseIsoTimestamp(readStringOrNull(parser))
                "git" -> {
                  gitBranch = parseBranchField(parser)
                }

                "source" -> {
                  val parsedSource = parseRolloutSource(parser)
                  if (parsedSource.sourceKind != CodexThreadSourceKind.UNKNOWN) {
                    sourceKind = parsedSource.sourceKind
                  }
                  parentThreadId = parsedSource.parentThreadId ?: parentThreadId
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

    RolloutEvent(
      topLevelType = topLevelType,
      timestampMs = timestampMs,
      payloadType = payloadType,
      payloadRole = payloadRole,
      payloadMessage = payloadMessage,
      payloadName = payloadName,
      payloadCallId = payloadCallId,
      payloadThreadName = payloadThreadName,
      sessionId = sessionId,
      sessionCwd = sessionCwd,
      sessionTimestampMs = sessionTimestampMs,
      sourceKind = sourceKind,
      parentThreadId = parentThreadId,
      gitBranch = gitBranch,
    )
  }
  catch (_: Throwable) {
    null
  }
}

internal data class ParsedRolloutThread(
  @JvmField val normalizedCwd: String,
  @JvmField val parentThreadId: String?,
  @JvmField val thread: CodexBackendThread,
)

private data class RolloutEvent(
  @JvmField val topLevelType: String?,
  @JvmField val timestampMs: Long?,
  @JvmField val payloadType: String?,
  @JvmField val payloadRole: String?,
  @JvmField val payloadMessage: String?,
  @JvmField val payloadName: String?,
  @JvmField val payloadCallId: String?,
  @JvmField val payloadThreadName: String?,
  @JvmField val sessionId: String?,
  @JvmField val sessionCwd: String?,
  @JvmField val sessionTimestampMs: Long?,
  @JvmField val sourceKind: CodexThreadSourceKind,
  @JvmField val parentThreadId: String?,
  @JvmField val gitBranch: String?,
)

private data class RolloutParseState(
  @JvmField var sessionId: String? = null,
  @JvmField var sessionCwd: String? = null,
  @JvmField var sourceKind: CodexThreadSourceKind = CodexThreadSourceKind.UNKNOWN,
  @JvmField var parentThreadId: String? = null,
  @JvmField var gitBranch: String? = null,
  @JvmField var title: String? = null,
  @JvmField var updatedAt: Long = 0L,
  @JvmField var processing: Boolean = false,
  @JvmField var reviewing: Boolean = false,
  @JvmField var latestUserMessageAt: Long = Long.MIN_VALUE,
  @JvmField var latestAgentMessageAt: Long = Long.MIN_VALUE,
  @JvmField val pendingUserInputByCallId: LinkedHashMap<String, Long> = LinkedHashMap(),
  @JvmField var nextSyntheticPendingUserInputId: Int = 0,
)

private fun RolloutParseState.latestPendingUserInputAt(): Long? {
  return pendingUserInputByCallId.values.maxOrNull()
}

private fun RolloutParseState.markPendingUserInput(eventTimestamp: Long?, callId: String?) {
  val resolvedTimestamp = eventTimestamp ?: updatedAt
  val resolvedCallId = callId ?: "pending-user-input-${nextSyntheticPendingUserInputId++}"
  pendingUserInputByCallId.merge(resolvedCallId, resolvedTimestamp, ::maxOf)
}

private fun parseIsoTimestamp(value: String?): Long? {
  val text = value?.trim().takeIf { !it.isNullOrEmpty() } ?: return null
  try {
    return Instant.parse(text).toEpochMilli()
  }
  catch (_: DateTimeParseException) {
    return null
  }
}

private fun extractTitle(message: String?): String? {
  val candidate = stripUserMessagePrefix(message ?: return null)
    .lineSequence()
    .map(String::trim)
    .firstOrNull { it.isNotEmpty() }
    ?: return null
  if (isSessionPrefix(candidate)) return null
  return normalizeThreadTitle(candidate)
}

private fun extractThreadName(threadName: String?): String? {
  return normalizeThreadTitle(threadName)
}

private fun stripUserMessagePrefix(text: String): String {
  val markerIndex = text.indexOf(USER_MESSAGE_BEGIN)
  return if (markerIndex >= 0) {
    text.substring(markerIndex + USER_MESSAGE_BEGIN.length).trim()
  }
  else {
    text.trim()
  }
}

private fun isSessionPrefix(text: String): Boolean {
  val normalized = text.trimStart().lowercase()
  return normalized.startsWith(ENVIRONMENT_CONTEXT_OPEN_TAG) || normalized.startsWith(TURN_ABORTED_OPEN_TAG)
}

private fun normalizeThreadTitle(value: String?): String? {
  return value
    ?.replace('\n', ' ')
    ?.replace('\r', ' ')
    ?.replace(THREAD_TITLE_WHITESPACE, " ")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
}

private fun parseBranchField(parser: JsonParser): String? {
  if (parser.currentToken != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var result: String? = null
  forEachObjectField(parser) { nestedField ->
    if (nestedField == "branch") {
      result = readStringOrNull(parser)
    }
    else {
      parser.skipChildren()
    }
    true
  }
  return result
}

private fun parseRolloutSource(parser: JsonParser): ParsedRolloutSource {
  return when (parser.currentToken) {
    JsonToken.VALUE_STRING -> ParsedRolloutSource(
      sourceKind = parseRolloutSourceKind(readStringOrNull(parser)),
      parentThreadId = null,
    )
    JsonToken.START_OBJECT -> {
      var sourceKind = CodexThreadSourceKind.UNKNOWN
      var parentThreadId: String? = null
      forEachObjectField(parser) { sourceField ->
        when (sourceField) {
          "subAgent", "sub_agent", "subagent" -> {
            val parsed = parseRolloutSubAgentSource(parser)
            sourceKind = parsed.sourceKind
            parentThreadId = parsed.parentThreadId
          }

          else -> {
            val parsedSourceKind = parseRolloutSourceKind(sourceField)
            if (parsedSourceKind != CodexThreadSourceKind.UNKNOWN) {
              sourceKind = parsedSourceKind
            }
            parser.skipChildren()
          }
        }
        true
      }
      ParsedRolloutSource(sourceKind = sourceKind, parentThreadId = parentThreadId)
    }

    else -> {
      parser.skipChildren()
      ParsedRolloutSource(sourceKind = CodexThreadSourceKind.UNKNOWN, parentThreadId = null)
    }
  }
}

private fun parseRolloutSubAgentSource(parser: JsonParser): ParsedRolloutSource {
  return when (parser.currentToken) {
    JsonToken.VALUE_STRING -> {
      val value = readStringOrNull(parser)
      val sourceKind = when (value?.trim()?.lowercase()) {
        "review" -> CodexThreadSourceKind.SUB_AGENT_REVIEW
        "compact" -> CodexThreadSourceKind.SUB_AGENT_COMPACT
        "other" -> CodexThreadSourceKind.SUB_AGENT_OTHER
        else -> CodexThreadSourceKind.SUB_AGENT
      }
      ParsedRolloutSource(sourceKind = sourceKind, parentThreadId = null)
    }

    JsonToken.START_OBJECT -> {
      var sourceKind = CodexThreadSourceKind.SUB_AGENT
      var parentThreadId: String? = null
      forEachObjectField(parser) { nestedField ->
        when (nestedField) {
          "thread_spawn", "threadSpawn" -> {
            sourceKind = CodexThreadSourceKind.SUB_AGENT_THREAD_SPAWN
            parentThreadId = parseThreadSpawnParentId(parser)
          }

          "review" -> {
            sourceKind = CodexThreadSourceKind.SUB_AGENT_REVIEW
            parser.skipChildren()
          }

          "compact" -> {
            sourceKind = CodexThreadSourceKind.SUB_AGENT_COMPACT
            parser.skipChildren()
          }

          "other" -> {
            sourceKind = CodexThreadSourceKind.SUB_AGENT_OTHER
            parser.skipChildren()
          }

          else -> parser.skipChildren()
        }
        true
      }
      ParsedRolloutSource(sourceKind = sourceKind, parentThreadId = parentThreadId)
    }

    else -> {
      parser.skipChildren()
      ParsedRolloutSource(sourceKind = CodexThreadSourceKind.SUB_AGENT, parentThreadId = null)
    }
  }
}

private fun parseThreadSpawnParentId(parser: JsonParser): String? {
  if (parser.currentToken != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var result: String? = null
  forEachObjectField(parser) { nestedField ->
    when (nestedField) {
      "parent_thread_id", "parentThreadId" -> {
        result = readStringOrNull(parser)?.trim()?.takeIf { it.isNotEmpty() } ?: result
      }

      else -> parser.skipChildren()
    }
    true
  }
  return result
}

@Suppress("DuplicatedCode")
private fun parseRolloutSourceKind(value: String?): CodexThreadSourceKind {
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

private data class ParsedRolloutSource(
  val sourceKind: CodexThreadSourceKind,
  val parentThreadId: String?,
)

private fun maxTimestamp(current: Long, candidate: Long?): Long {
  if (candidate == null) return current
  return if (candidate > current) candidate else current
}
