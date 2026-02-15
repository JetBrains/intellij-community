// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

// @spec community/plugins/agent-workbench/spec/agent-sessions-codex-rollout-source.spec.md

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.forEachObjectField
import com.intellij.agent.workbench.codex.common.readStringOrNull
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.io.path.invariantSeparatorsPathString

private const val ROLLOUT_FILE_PREFIX = "rollout-"
private const val ROLLOUT_FILE_SUFFIX = ".jsonl"
private const val MAX_TITLE_LENGTH = 120

class CodexRolloutSessionBackend(
  private val codexHomeProvider: () -> Path = { Path.of(System.getProperty("user.home"), ".codex") },
) : CodexSessionBackend {
  private val jsonFactory = JsonFactory()

  override suspend fun listThreads(path: String, @Suppress("UNUSED_PARAMETER") openProject: Project?): List<CodexBackendThread> {
    return withContext(Dispatchers.IO) {
      val workingDirectory = resolveProjectDirectoryFromPath(path)
        ?: return@withContext emptyList()
      val cwdFilter = normalizeRootPath(workingDirectory.invariantSeparatorsPathString)
      val sessionsDir = codexHomeProvider().resolve("sessions")
      if (!Files.isDirectory(sessionsDir)) return@withContext emptyList()

      val threads = mutableListOf<CodexBackendThread>()
      try {
        Files.walk(sessionsDir).use { stream ->
          val iterator = stream.iterator()
          while (iterator.hasNext()) {
            val candidate = iterator.next()
            if (!Files.isRegularFile(candidate)) continue
            val fileName = candidate.fileName?.toString() ?: continue
            if (!isRolloutFileName(fileName)) continue
            parseRolloutFile(candidate, cwdFilter)?.let(threads::add)
          }
        }
      }
      catch (_: Throwable) {
        return@withContext emptyList()
      }

      threads.sortedByDescending { it.thread.updatedAt }
    }
  }

  private fun parseRolloutFile(path: Path, cwdFilter: String): CodexBackendThread? {
    var sessionId: String? = null
    var sessionCwd: String? = null
    var gitBranch: String? = null
    var title: String? = null
    var updatedAt = 0L
    var processing = false
    var reviewing = false
    var latestUserMessageAt = Long.MIN_VALUE
    var latestAgentMessageAt = Long.MIN_VALUE
    var pendingUserInputAt: Long? = null

    try {
      Files.newBufferedReader(path).use { reader ->
        while (true) {
          val line = reader.readLine() ?: break
          if (line.isBlank()) continue
          val event = parseEvent(line) ?: continue

          updatedAt = maxTimestamp(updatedAt, event.timestampMs)
          updatedAt = maxTimestamp(updatedAt, event.sessionTimestampMs)
          sessionId = sessionId ?: event.sessionId
          sessionCwd = sessionCwd ?: event.sessionCwd
          gitBranch = gitBranch ?: event.gitBranch

          val eventTimestamp = event.timestampMs
          when (event.topLevelType) {
            "event_msg" -> {
              when (event.payloadType) {
                "task_started" -> processing = true
                "task_complete", "turn_aborted" -> processing = false
                "user_message" -> {
                  latestUserMessageAt = maxTimestamp(latestUserMessageAt, eventTimestamp)
                  title = title ?: extractTitle(event.payloadMessage)
                  val pendingInputAt = pendingUserInputAt
                  if (pendingInputAt != null && eventTimestamp != null && eventTimestamp >= pendingInputAt) {
                    pendingUserInputAt = null
                  }
                }

                "agent_message" -> {
                  latestAgentMessageAt = maxTimestamp(latestAgentMessageAt, eventTimestamp)
                }
              }

              if (event.payloadType?.contains("requestUserInput", ignoreCase = true) == true) {
                pendingUserInputAt = maxTimestamp(pendingUserInputAt ?: Long.MIN_VALUE, eventTimestamp)
              }

              when (event.itemType) {
                "enteredReviewMode" -> reviewing = true
                "exitedReviewMode" -> reviewing = false
              }
            }

            "response_item" -> {
              if (event.payloadType == "message") {
                when (event.payloadRole) {
                  "user" -> {
                    latestUserMessageAt = maxTimestamp(latestUserMessageAt, eventTimestamp)
                    val pendingInputAt = pendingUserInputAt
                    if (pendingInputAt != null && eventTimestamp != null && eventTimestamp >= pendingInputAt) {
                      pendingUserInputAt = null
                    }
                  }

                  "assistant" -> {
                    latestAgentMessageAt = maxTimestamp(latestAgentMessageAt, eventTimestamp)
                  }
                }
              }
            }
          }
        }
      }
    }
    catch (_: Throwable) {
      return null
    }

    val normalizedCwd = normalizeRootPath(sessionCwd ?: return null)
    if (normalizedCwd != cwdFilter) return null

    val resolvedSessionId = sessionId ?: return null
    val hasUnread = latestAgentMessageAt > latestUserMessageAt
    val hasPendingUserInput = pendingUserInputAt != null
    val activity = when {
      hasPendingUserInput || hasUnread -> CodexSessionActivity.UNREAD
      reviewing -> CodexSessionActivity.REVIEWING
      processing -> CodexSessionActivity.PROCESSING
      else -> CodexSessionActivity.READY
    }

    val fallbackUpdatedAt = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
    val resolvedUpdatedAt = if (updatedAt > 0L) updatedAt else fallbackUpdatedAt
    val resolvedTitle = title ?: "Thread ${resolvedSessionId.take(8)}"

    return CodexBackendThread(
      thread = CodexThread(
        id = resolvedSessionId,
        title = resolvedTitle,
        updatedAt = resolvedUpdatedAt,
        archived = false,
        gitBranch = gitBranch,
      ),
      activity = activity,
    )
  }

  private fun parseEvent(line: String): RolloutEvent? {
    return try {
      jsonFactory.createParser(line).use { parser ->
        if (parser.nextToken() != JsonToken.START_OBJECT) return null

        var topLevelType: String? = null
        var timestampMs: Long? = null
        var payloadType: String? = null
        var payloadRole: String? = null
        var payloadMessage: String? = null
        var sessionId: String? = null
        var sessionCwd: String? = null
        var sessionTimestampMs: Long? = null
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
                    "id" -> sessionId = readStringOrNull(parser)
                    "cwd" -> sessionCwd = readStringOrNull(parser)
                    "timestamp" -> sessionTimestampMs = parseIsoTimestamp(readStringOrNull(parser))
                    "git" -> {
                      gitBranch = parseNestedStringField(parser, "branch")
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
          sessionId = sessionId,
          sessionCwd = sessionCwd,
          sessionTimestampMs = sessionTimestampMs,
          gitBranch = gitBranch,
          itemType = itemType,
        )
      }
    }
    catch (_: Throwable) {
      null
    }
  }
}

private data class RolloutEvent(
  val topLevelType: String?,
  val timestampMs: Long?,
  val payloadType: String?,
  val payloadRole: String?,
  val payloadMessage: String?,
  val sessionId: String?,
  val sessionCwd: String?,
  val sessionTimestampMs: Long?,
  val gitBranch: String?,
  val itemType: String?,
)

private fun parseIsoTimestamp(value: String?): Long? {
  val text = value?.trim().takeIf { !it.isNullOrEmpty() } ?: return null
  return try {
    Instant.parse(text).toEpochMilli()
  }
  catch (_: DateTimeParseException) {
    null
  }
}

private fun isRolloutFileName(fileName: String): Boolean {
  return fileName.startsWith(ROLLOUT_FILE_PREFIX) && fileName.endsWith(ROLLOUT_FILE_SUFFIX)
}

private fun extractTitle(message: String?): String? {
  val candidate = message
    ?.lineSequence()
    ?.map(String::trim)
    ?.firstOrNull { it.isNotEmpty() }
    ?: return null
  if (candidate.startsWith("<environment_context>")) return null
  return trimTitle(candidate.replace(Regex("\\s+"), " "))
}

private fun trimTitle(value: String): String {
  if (value.length <= MAX_TITLE_LENGTH) return value
  return value.take(MAX_TITLE_LENGTH - 3).trimEnd() + "..."
}

private fun normalizeRootPath(value: String): String {
  return value.replace('\\', '/').trimEnd('/')
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

private fun maxTimestamp(current: Long, candidate: Long?): Long {
  if (candidate == null) return current
  return if (candidate > current) candidate else current
}

