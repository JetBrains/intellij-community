// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.rollout

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.forEachObjectField
import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.common.readStringOrNull
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionActivity
import com.intellij.agent.workbench.json.WorkbenchJsonlScanner
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeParseException

private const val MAX_TITLE_LENGTH = 120
private const val USER_MESSAGE_BEGIN = "## My request for Codex:"
private const val ENVIRONMENT_CONTEXT_OPEN_TAG = "<environment_context>"
private const val TURN_ABORTED_OPEN_TAG = "<turn_aborted>"

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
    val hasPendingUserInput = state.pendingUserInputAt != null
    val activity = when {
      hasPendingUserInput || hasUnread -> CodexSessionActivity.UNREAD
      state.reviewing -> CodexSessionActivity.REVIEWING
      state.processing -> CodexSessionActivity.PROCESSING
      else -> CodexSessionActivity.READY
    }

    val fallbackUpdatedAt = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
    val resolvedUpdatedAt = if (state.updatedAt > 0L) state.updatedAt else fallbackUpdatedAt
    val fallbackTitle = "Thread ${resolvedSessionId.take(8)}"
    val resolvedTitle = state.title ?: fallbackTitle
    val usedFallbackTitle = state.title == null

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
        ),
        activity = activity,
      ),
    )
  }

}

private fun reduceEvent(parseState: RolloutParseState, event: RolloutEvent) {
  parseState.updatedAt = maxTimestamp(parseState.updatedAt, event.timestampMs)
  parseState.updatedAt = maxTimestamp(parseState.updatedAt, event.sessionTimestampMs)
  parseState.sessionId = parseState.sessionId ?: event.sessionId
  parseState.sessionCwd = parseState.sessionCwd ?: event.sessionCwd
  parseState.parentThreadId = parseState.parentThreadId ?: event.parentThreadId
  parseState.gitBranch = parseState.gitBranch ?: event.gitBranch

  val eventTimestamp = event.timestampMs
  when (event.topLevelType) {
    "event_msg" -> {
      when (event.payloadType) {
        "task_started" -> parseState.processing = true
        "task_complete", "turn_aborted" -> parseState.processing = false
        "user_message" -> {
          parseState.latestUserMessageAt = maxTimestamp(parseState.latestUserMessageAt, eventTimestamp)
          parseState.title = parseState.title ?: extractTitle(event.payloadMessage)
          val pendingInputAt = parseState.pendingUserInputAt
          if (pendingInputAt != null && eventTimestamp != null && eventTimestamp >= pendingInputAt) {
            parseState.pendingUserInputAt = null
          }
        }

        "thread_name_updated", "threadNameUpdated" -> {
          parseState.title = extractThreadName(event.payloadThreadName) ?: parseState.title
        }

        "agent_message" -> {
          parseState.latestAgentMessageAt = maxTimestamp(parseState.latestAgentMessageAt, eventTimestamp)
        }
      }

      if (event.payloadType?.contains("requestUserInput", ignoreCase = true) == true) {
        parseState.pendingUserInputAt = maxTimestamp(parseState.pendingUserInputAt ?: Long.MIN_VALUE, eventTimestamp)
      }

      when (event.itemType) {
        "enteredReviewMode" -> parseState.reviewing = true
        "exitedReviewMode" -> parseState.reviewing = false
      }
    }

    "response_item" -> {
      if (event.payloadType == "message") {
        when (event.payloadRole) {
          "user" -> {
            parseState.latestUserMessageAt = maxTimestamp(parseState.latestUserMessageAt, eventTimestamp)
            val pendingInputAt = parseState.pendingUserInputAt
            if (pendingInputAt != null && eventTimestamp != null && eventTimestamp >= pendingInputAt) {
              parseState.pendingUserInputAt = null
            }
          }

          "assistant" -> {
            parseState.latestAgentMessageAt = maxTimestamp(parseState.latestAgentMessageAt, eventTimestamp)
          }
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
    var payloadThreadName: String? = null
    var sessionId: String? = null
    var sessionCwd: String? = null
    var sessionTimestampMs: Long? = null
    var parentThreadId: String? = null
    var gitBranch: String? = null
    var itemType: String? = null

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
                "thread_name", "threadName" -> payloadThreadName = readStringOrNull(parser)
                "id" -> sessionId = readStringOrNull(parser)
                "cwd" -> sessionCwd = readStringOrNull(parser)
                "timestamp" -> sessionTimestampMs = parseIsoTimestamp(readStringOrNull(parser))
                "git" -> {
                  gitBranch = parseNestedStringField(parser, "branch")
                }

                "source" -> {
                  parentThreadId = parseSubAgentParentThreadId(parser) ?: parentThreadId
                }

                "item" -> {
                  itemType = parseNestedStringField(parser, "type")
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
      payloadThreadName = payloadThreadName,
      sessionId = sessionId,
      sessionCwd = sessionCwd,
      sessionTimestampMs = sessionTimestampMs,
      parentThreadId = parentThreadId,
      gitBranch = gitBranch,
      itemType = itemType,
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
  @JvmField val payloadThreadName: String?,
  @JvmField val sessionId: String?,
  @JvmField val sessionCwd: String?,
  @JvmField val sessionTimestampMs: Long?,
  @JvmField val parentThreadId: String?,
  @JvmField val gitBranch: String?,
  @JvmField val itemType: String?,
)

private data class RolloutParseState(
  @JvmField var sessionId: String? = null,
  @JvmField var sessionCwd: String? = null,
  @JvmField var parentThreadId: String? = null,
  @JvmField var gitBranch: String? = null,
  @JvmField var title: String? = null,
  @JvmField var updatedAt: Long = 0L,
  @JvmField var processing: Boolean = false,
  @JvmField var reviewing: Boolean = false,
  @JvmField var latestUserMessageAt: Long = Long.MIN_VALUE,
  @JvmField var latestAgentMessageAt: Long = Long.MIN_VALUE,
  @JvmField var pendingUserInputAt: Long? = null,
)

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
  return trimTitle(candidate.replace(Regex("\\s+"), " "))
}

private fun extractThreadName(threadName: String?): String? {
  val candidate = threadName
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: return null
  return trimTitle(candidate.replace(Regex("\\s+"), " "))
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

private fun trimTitle(value: String): String {
  if (value.length <= MAX_TITLE_LENGTH) return value
  return value.take(MAX_TITLE_LENGTH - 3).trimEnd() + "..."
}

private fun parseNestedStringField(parser: JsonParser, fieldName: String): String? {
  if (parser.currentToken != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var result: String? = null
  forEachObjectField(parser) { nestedField ->
    if (nestedField == fieldName) {
      result = readStringOrNull(parser)
    }
    else {
      parser.skipChildren()
    }
    true
  }
  return result
}

private fun parseSubAgentParentThreadId(parser: JsonParser): String? {
  if (parser.currentToken != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var result: String? = null
  forEachObjectField(parser) { sourceField ->
    when (sourceField) {
      "subagent", "sub_agent" -> {
        result = parseNestedParentThreadId(parser) ?: result
      }

      else -> parser.skipChildren()
    }
    true
  }
  return result
}

private fun parseNestedParentThreadId(parser: JsonParser): String? {
  if (parser.currentToken != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var result: String? = null
  forEachObjectField(parser) { nestedField ->
    when (nestedField) {
      "thread_spawn", "threadSpawn" -> {
        result = parseNestedParentThreadId(parser) ?: result
      }

      "parent_thread_id", "parentThreadId" -> {
        result = readStringOrNull(parser)?.trim()?.takeIf { it.isNotEmpty() } ?: result
      }

      else -> parser.skipChildren()
    }
    true
  }
  return result
}

private fun maxTimestamp(current: Long, candidate: Long?): Long {
  if (candidate == null) return current
  return if (candidate > current) candidate else current
}
