// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.ai.agent.codex.sessions.backend.rollout

// @spec plugins/ij-air/spec/thread-view/agent-thread-view-structure.spec.md

import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.json.JsonFactory
import com.intellij.platform.ai.agent.codex.common.CodexThread
import com.intellij.platform.ai.agent.codex.common.CodexThreadActivityProjection
import com.intellij.platform.ai.agent.codex.common.CodexThreadActivitySignal
import com.intellij.platform.ai.agent.codex.common.CodexThreadSourceKind
import com.intellij.platform.ai.agent.codex.common.CodexThreadStatusKind
import com.intellij.platform.ai.agent.codex.common.forEachObjectField
import com.intellij.platform.ai.agent.codex.common.normalizeRootPath
import com.intellij.platform.ai.agent.codex.common.readLongOrNull
import com.intellij.platform.ai.agent.codex.common.readStringOrNull
import com.intellij.platform.ai.agent.codex.sessions.backend.CodexBackendThread
import com.intellij.platform.ai.agent.codex.sessions.backend.isResponseRequired
import com.intellij.platform.ai.agent.codex.sessions.backend.toCodexSessionActivity
import com.intellij.platform.ai.agent.codex.sessions.codexUserPromptOutlineItemId
import com.intellij.platform.ai.agent.codex.sessions.CODEX_AGENT_SESSION_PROVIDER
import com.intellij.platform.ai.agent.core.session.agentSessionOutlinePhaseTitle
import com.intellij.platform.ai.agent.core.session.compactAgentSessionOutlineText
import com.intellij.platform.ai.agent.core.session.dedupeAgentSessionOutlineText
import com.intellij.platform.ai.agent.core.session.normalizeAgentSessionOutlinePreview
import com.intellij.platform.ai.agent.core.session.summarizeAgentSessionOutlineChildren
import com.intellij.platform.ai.agent.json.WorkbenchJsonlScanner
import com.intellij.platform.ai.agent.json.createJsonParser
import com.intellij.platform.ai.agent.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItem
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItemKind
import com.intellij.platform.ai.agent.core.session.AgentSessionThreadOutline
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeParseException

private const val USER_MESSAGE_BEGIN = "## My request for Codex:"
private const val ENVIRONMENT_CONTEXT_OPEN_TAG = "<environment_context>"
private const val TURN_ABORTED_OPEN_TAG = "<turn_aborted>"
private const val CODEX_EXEC_HEADER = "OpenAI Codex"
private const val CODEX_EXEC_WORKDIR_PREFIX = "workdir:"
private const val CODEX_EXEC_SESSION_ID_PREFIX = "session id:"
private const val MIN_SUB_AGENT_REPLAY_SNAPSHOTS = 3
private val THREAD_TITLE_WHITESPACE = Regex("\\s+")
private val CODEX_THREAD_ID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

private val LOG = logger<CodexRolloutParser>()

internal class CodexRolloutParser(
  private val jsonFactory: JsonFactory = JsonFactory(),
  private val openReader: (Path) -> BufferedReader = Files::newBufferedReader,
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
    val fallbackUpdatedAt = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
    val resolvedUpdatedAt = if (state.updatedAt > 0L) state.updatedAt else fallbackUpdatedAt
    val activitySnapshot = state.activityProjection.toSnapshot(
      threadId = resolvedSessionId,
      updatedAt = resolvedUpdatedAt,
      statusKind = CodexThreadStatusKind.IDLE,
      hasTurnActivity = state.hasActivityEvidence,
    )
    val activity = activitySnapshot.toCodexSessionActivity()
    val fallbackTitle = "Thread ${resolvedSessionId.take(8)}"
    val resolvedTitle = state.title ?: fallbackTitle
    val usedFallbackTitle = state.title == null
    val sourceKind = when {
      state.sourceKind != CodexThreadSourceKind.UNKNOWN -> state.sourceKind
      state.parentThreadId != null -> CodexThreadSourceKind.SUB_AGENT_THREAD_SPAWN
      else -> CodexThreadSourceKind.CLI
    }
    val summaryActivity = if (sourceKind.isSubAgentSourceKind()) null else activity

    LOG.debug {
      "Parsed rollout thread (sessionId=$resolvedSessionId, cwd=$normalizedCwd, title=$resolvedTitle, fallbackTitle=$usedFallbackTitle, updatedAt=$resolvedUpdatedAt, activity=$activity)"
    }

    return ParsedRolloutThread(
      path = path,
      normalizedCwd = normalizedCwd,
      parentThreadId = state.parentThreadId,
      projectFilesChangedAt = state.projectFilesChangedAt,
      projectFileChangeEvidence = state.projectFileChangeEvidence.sortedBy { it.timestampMillis },
      hasExplicitTitle = !usedFallbackTitle,
      spawnedExecThreadIds = state.spawnedExecThreadIds,
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
        requiresResponse = activitySnapshot.activeFlags.isResponseRequired() || activitySnapshot.hasPendingPlan,
        summaryActivity = summaryActivity,
        usageSnapshots = when (
          val usageSnapshots = collectRolloutUsageSnapshots(
            path = path,
            fallbackModelId = state.modelId,
            isSubAgentThreadSpawn = state.parentThreadId != null,
            openReader = openReader,
          )
        ) {
          null -> emptyList()
          else -> usageSnapshots.ifEmpty { listOfNotNull(state.usageSnapshot) }
        },
        hasExplicitTitle = !usedFallbackTitle,
      ),
    )
  }

  fun parseOutline(path: Path): AgentSessionThreadOutline? {
    val state = try {
      WorkbenchJsonlScanner.scanJsonObjects(
        path = path,
        jsonFactory = jsonFactory,
        newState = ::RolloutOutlineParseState,
      ) { parser, parseState ->
        val event = parseEvent(parser) ?: return@scanJsonObjects true
        reduceOutlineEvent(parseState, event)
        true
      }
    }
    catch (_: Throwable) {
      return null
    }

    val sessionId = state.sessionId ?: return null
    val fallbackUpdatedAt = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
    val title = state.title ?: "Thread ${sessionId.take(8)}"
    return AgentSessionThreadOutline(
      provider = CODEX_AGENT_SESSION_PROVIDER,
      threadId = sessionId,
      title = title,
      updatedAt = if (state.updatedAt > 0L) state.updatedAt else fallbackUpdatedAt,
      items = state.buildItems(),
    )
  }

}

private fun collectRolloutUsageSnapshots(
  path: Path,
  fallbackModelId: String?,
  isSubAgentThreadSpawn: Boolean,
  openReader: (Path) -> BufferedReader,
): List<AgentSessionUsageSnapshot>? {
  return try {
    val usageByModel = LinkedHashMap<String?, UsageTotals>()
    val modelState = CodexUsageModelState(currentModel = fallbackModelId)
    var previousTotalUsage: AgentSessionUsageSnapshot? = null
    val replaySecond = if (isSubAgentThreadSpawn) detectSubAgentReplaySecond(path, openReader) else null
    var skipReplay = replaySecond != null
    val fallbackTimestamp = runCatching { Files.getLastModifiedTime(path).toInstant().toString() }.getOrDefault("1970-01-01T00:00:00Z")

    openReader(path).useLines { lines ->
      for (line in lines) {
        val parsed = parseUsageLine(line) ?: continue
        if (parsed.isSessionUsageLine()) {
          if (parsed.isTokenCountLine()) {
            if (skipReplay && replaySecond == parsed.timestampSecond) {
              if (parsed.totalUsage != null) {
                previousTotalUsage = parsed.totalUsage
              }
              continue
            }
            if (skipReplay) {
              skipReplay = false
            }
          }
          when {
            parsed.topLevelType == "turn_context" -> {
              parsed.eventModelId?.let {
                modelState.currentModel = it
                modelState.currentModelIsFallback = false
              }
            }
            parsed.isTokenCountLine() -> {
              val usage = parsed.lastUsage ?: parsed.totalUsage?.subtract(previousTotalUsage)
              if (parsed.totalUsage != null) {
                previousTotalUsage = parsed.totalUsage
              }
              if (usage == null || usage.isZeroUsage()) {
                continue
              }

              val resolvedModelId = resolveCodexUsageModel(
                parsedModelId = parsed.eventModelId,
                timestamp = parsed.eventTimestamp ?: parsed.timestampSecond,
                modelState = modelState,
              )
              usageByModel.getOrPut(resolvedModelId) { UsageTotals(modelId = resolvedModelId) }
                .add(usage.copy(modelId = resolvedModelId))
            }
          }
        }
        else if (parsed.execUsage != null) {
          val resolvedModelId = resolveCodexUsageModel(
            parsedModelId = parsed.execModelId,
            timestamp = parsed.execTimestamp ?: parsed.eventTimestamp ?: fallbackTimestamp,
            modelState = modelState,
          )
          usageByModel.getOrPut(resolvedModelId) { UsageTotals(modelId = resolvedModelId) }
            .add(parsed.execUsage.copy(modelId = resolvedModelId))
        }
      }
    }

    usageByModel.values
      .map(UsageTotals::toSnapshot)
      .filterNot(AgentSessionUsageSnapshot::isZeroUsage)
  }
  catch (_: Throwable) {
    null
  }
}

private data class CodexUsageModelState(
  @JvmField var currentModel: String?,
  @JvmField var currentModelIsFallback: Boolean = false,
)

private data class UsageTotals(
  @JvmField var modelId: String?,
  @JvmField var inputTokens: Long = 0,
  @JvmField var outputTokens: Long = 0,
  @JvmField var cacheReadTokens: Long = 0,
  @JvmField var cacheWriteTokens: Long = 0,
  @JvmField var cacheWrite5mTokens: Long = 0,
  @JvmField var cacheWrite1hTokens: Long = 0,
  @JvmField var reasoningTokens: Long = 0,
) {
  fun add(usage: AgentSessionUsageSnapshot) {
    if (modelId == null) {
      modelId = usage.modelId
    }
    inputTokens += usage.inputTokens
    outputTokens += usage.outputTokens
    cacheReadTokens += usage.cacheReadTokens
    cacheWriteTokens += usage.cacheWriteTokens
    cacheWrite5mTokens += usage.cacheWrite5mTokens
    cacheWrite1hTokens += usage.cacheWrite1hTokens
    reasoningTokens += usage.reasoningTokens
  }

  fun toSnapshot(): AgentSessionUsageSnapshot {
    return AgentSessionUsageSnapshot(
      modelId = modelId,
      inputTokens = inputTokens,
      outputTokens = outputTokens,
      cacheReadTokens = cacheReadTokens,
      cacheWriteTokens = cacheWriteTokens,
      cacheWrite5mTokens = cacheWrite5mTokens,
      cacheWrite1hTokens = cacheWrite1hTokens,
      reasoningTokens = reasoningTokens,
    )
  }
}

private fun detectSubAgentReplaySecond(path: Path, openReader: (Path) -> BufferedReader): String? {
  var firstSecond: String? = null
  var firstSecondSnapshotCount = 0
  openReader(path).useLines { lines ->
    for (line in lines) {
      val parsed = parseUsageLine(line) ?: continue
      if (!parsed.isTokenCountLine() || (parsed.lastUsage == null && parsed.totalUsage == null)) {
        continue
      }
      val timestampSecond = parsed.timestampSecond.takeIf(String::isNotEmpty) ?: continue
      val first = firstSecond
      if (first == null) {
        firstSecond = timestampSecond
        firstSecondSnapshotCount = 1
        continue
      }
      if (first == timestampSecond) {
        firstSecondSnapshotCount += 1
        continue
      }
      return first.takeIf { firstSecondSnapshotCount >= MIN_SUB_AGENT_REPLAY_SNAPSHOTS }
    }
  }
  return firstSecond?.takeIf { firstSecondSnapshotCount >= MIN_SUB_AGENT_REPLAY_SNAPSHOTS }
}

private data class ParsedUsageLine(
  @JvmField val topLevelType: String?,
  @JvmField val payloadType: String?,
  @JvmField val eventType: String?,
  @JvmField val eventTimestamp: String?,
  @JvmField val timestampSecond: String,
  @JvmField val eventModelId: String?,
  @JvmField val totalUsage: AgentSessionUsageSnapshot?,
  @JvmField val lastUsage: AgentSessionUsageSnapshot?,
  @JvmField val execModelId: String?,
  @JvmField val execTimestamp: String?,
  @JvmField val execUsage: AgentSessionUsageSnapshot?,
)

private fun parseUsageLine(line: String): ParsedUsageLine? {
  var topLevelType: String? = null
  var eventTimestamp: String? = null
  var timestampSecond = ""
  var payloadType: String? = null
  var payloadModelId: String? = null
  var infoModelId: String? = null
  var totalUsage: AgentSessionUsageSnapshot? = null
  var lastUsage: AgentSessionUsageSnapshot? = null
  var execModelId: String? = null
  var execTimestamp: String? = null
  var execUsage: AgentSessionUsageSnapshot? = null

  try {
    JsonFactory().createParser(ObjectReadContext.empty(), line).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) return null
      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "type" -> topLevelType = readStringOrNull(parser)
          "timestamp" -> {
            val timestamp = readStringOrNull(parser)
            eventTimestamp = eventTimestamp ?: timestamp
            timestampSecond = timestamp?.take(19).orEmpty()
          }
          "created_at", "createdAt" -> eventTimestamp = eventTimestamp ?: readStringOrNull(parser)
          "model" -> execModelId = readStringOrNull(parser)
          "model_name" -> execModelId = execModelId ?: readStringOrNull(parser)
          "usage" -> execUsage = parseUsageSnapshotObject(parser)
          "result", "data", "response" -> {
            val nested = parseUsageContainer(parser)
            if (execModelId == null) {
              execModelId = nested.modelId
            }
            if (execUsage == null) {
              execUsage = nested.usage
            }
            if (execTimestamp == null) {
              execTimestamp = nested.timestamp
            }
          }
          "payload" -> {
            if (parser.currentToken() == JsonToken.START_OBJECT) {
              forEachObjectField(parser) { payloadField ->
                when (payloadField) {
                  "type" -> payloadType = readStringOrNull(parser)
                  "model" -> payloadModelId = readStringOrNull(parser)
                  "model_name" -> payloadModelId = payloadModelId ?: readStringOrNull(parser)
                  "info" -> {
                    val parsedInfo = parseTokenInfo(parser)
                    infoModelId = parsedInfo.modelId
                    totalUsage = parsedInfo.totalUsage
                    lastUsage = parsedInfo.lastUsage
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
    }
  }
  catch (_: Throwable) {
    return null
  }

  return ParsedUsageLine(
    topLevelType = topLevelType,
    payloadType = payloadType,
    eventType = payloadType ?: topLevelType,
    eventTimestamp = eventTimestamp,
    timestampSecond = timestampSecond,
    eventModelId = payloadModelId ?: infoModelId,
    totalUsage = totalUsage,
    lastUsage = lastUsage,
    execModelId = execModelId,
    execTimestamp = execTimestamp ?: eventTimestamp,
    execUsage = execUsage,
  )
}

private data class ParsedTokenInfo(
  @JvmField val modelId: String?,
  @JvmField val totalUsage: AgentSessionUsageSnapshot?,
  @JvmField val lastUsage: AgentSessionUsageSnapshot?,
)

private fun parseTokenInfo(parser: JsonParser): ParsedTokenInfo {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return ParsedTokenInfo(modelId = null, totalUsage = null, lastUsage = null)
  }

  var modelId: String? = null
  var totalUsage: AgentSessionUsageSnapshot? = null
  var lastUsage: AgentSessionUsageSnapshot? = null
  forEachObjectField(parser) { fieldName ->
    when (fieldName) {
      "model" -> modelId = readStringOrNull(parser)
      "model_name" -> modelId = modelId ?: readStringOrNull(parser)
      "total_token_usage", "total_usage", "usage" -> {
        if (totalUsage == null) {
          totalUsage = parseUsageSnapshotObject(parser)
        }
        else {
          parser.skipChildren()
        }
      }
      "last_token_usage" -> lastUsage = parseUsageSnapshotObject(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return ParsedTokenInfo(modelId = modelId, totalUsage = totalUsage, lastUsage = lastUsage)
}

private data class ParsedUsageContainer(
  @JvmField val modelId: String?,
  @JvmField val timestamp: String?,
  @JvmField val usage: AgentSessionUsageSnapshot?,
)

private fun parseUsageContainer(parser: JsonParser): ParsedUsageContainer {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return ParsedUsageContainer(modelId = null, timestamp = null, usage = null)
  }

  var modelId: String? = null
  var timestamp: String? = null
  var usage: AgentSessionUsageSnapshot? = null
  forEachObjectField(parser) { fieldName ->
    when (fieldName) {
      "model" -> modelId = readStringOrNull(parser)
      "model_name" -> modelId = modelId ?: readStringOrNull(parser)
      "timestamp", "created_at", "createdAt" -> timestamp = timestamp ?: readStringOrNull(parser)
      "usage" -> usage = parseUsageSnapshotObject(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return ParsedUsageContainer(modelId = modelId, timestamp = timestamp, usage = usage)
}

private fun parseUsageSnapshotObject(parser: JsonParser): AgentSessionUsageSnapshot? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var inputTokens = 0L
  var cachedInputTokens = 0L
  var outputTokens = 0L
  var reasoningTokens = 0L
  forEachObjectField(parser) { fieldName ->
    when (fieldName) {
      "input_tokens", "prompt_tokens", "input" -> inputTokens = readLongOrNull(parser) ?: 0L
      "cached_input_tokens", "cache_read_input_tokens", "cached_tokens" -> cachedInputTokens = readLongOrNull(parser) ?: 0L
      "output_tokens", "completion_tokens", "output" -> outputTokens = readLongOrNull(parser) ?: 0L
      "reasoning_output_tokens", "reasoning_tokens" -> reasoningTokens = readLongOrNull(parser) ?: 0L
      else -> parser.skipChildren()
    }
    true
  }

  return AgentSessionUsageSnapshot(
    modelId = null,
    inputTokens = maxOf(inputTokens - cachedInputTokens, 0L),
    outputTokens = outputTokens,
    cacheReadTokens = minOf(cachedInputTokens, inputTokens),
    reasoningTokens = reasoningTokens,
  ).takeUnless(AgentSessionUsageSnapshot::isZeroUsage)
}

private fun AgentSessionUsageSnapshot.subtract(previous: AgentSessionUsageSnapshot?): AgentSessionUsageSnapshot {
  if (previous == null) return this
  return copy(
    inputTokens = maxOf(inputTokens - previous.inputTokens, 0L),
    outputTokens = maxOf(outputTokens - previous.outputTokens, 0L),
    cacheReadTokens = maxOf(cacheReadTokens - previous.cacheReadTokens, 0L),
    cacheWriteTokens = maxOf(cacheWriteTokens - previous.cacheWriteTokens, 0L),
    cacheWrite5mTokens = maxOf(cacheWrite5mTokens - previous.cacheWrite5mTokens, 0L),
    cacheWrite1hTokens = maxOf(cacheWrite1hTokens - previous.cacheWrite1hTokens, 0L),
    reasoningTokens = maxOf(reasoningTokens - previous.reasoningTokens, 0L),
  )
}

private fun AgentSessionUsageSnapshot.isZeroUsage(): Boolean {
  return inputTokens == 0L &&
         outputTokens == 0L &&
         cacheReadTokens == 0L &&
         cacheWriteTokens == 0L &&
         cacheWrite5mTokens == 0L &&
         cacheWrite1hTokens == 0L &&
         reasoningTokens == 0L
}

private fun ParsedUsageLine.isSessionUsageLine(): Boolean {
  return topLevelType == "turn_context" || isTokenCountLine()
}

private fun ParsedUsageLine.isTokenCountLine(): Boolean {
  return topLevelType == "event_msg" && payloadType == "token_count"
}

private fun resolveCodexUsageModel(
  parsedModelId: String?,
  timestamp: String,
  modelState: CodexUsageModelState,
): String {
  if (parsedModelId != null) {
    modelState.currentModel = parsedModelId
    modelState.currentModelIsFallback = false
  }
  val modelId = parsedModelId ?: modelState.currentModel ?: run {
    modelState.currentModelIsFallback = true
    modelState.currentModel = "gpt-5"
    return "gpt-5"
  }
  return codexLogModelFallback(modelId, timestamp) ?: modelId
}

private fun codexLogModelFallback(modelId: String, timestamp: String): String? {
  if (modelId != "codex-auto-review") {
    return null
  }
  val date = codexTimestampDate(timestamp) ?: return "gpt-5"
  return CODEX_AUTO_REVIEW_FALLBACK_MODELS.firstOrNull { date >= it.releasedOn }?.model ?: "gpt-5"
}

private fun codexTimestampDate(timestamp: String): String? {
  val date = timestamp.takeIf { it.length >= 10 }?.substring(0, 10) ?: return null
  return date.takeIf { CODEX_DATE_REGEX.matches(it) }
}

private data class CodexAutoReviewFallback(
  @JvmField val releasedOn: String,
  @JvmField val model: String,
)

private val CODEX_AUTO_REVIEW_FALLBACK_MODELS = listOf(
  CodexAutoReviewFallback(releasedOn = "2026-04-23", model = "gpt-5.5"),
  CodexAutoReviewFallback(releasedOn = "2026-03-05", model = "gpt-5.4"),
  CodexAutoReviewFallback(releasedOn = "2026-02-05", model = "gpt-5.3-codex"),
  CodexAutoReviewFallback(releasedOn = "2025-12-11", model = "gpt-5.2-codex"),
  CodexAutoReviewFallback(releasedOn = "2025-11-13", model = "gpt-5.1-codex"),
  CodexAutoReviewFallback(releasedOn = "2025-09-15", model = "gpt-5-codex"),
  CodexAutoReviewFallback(releasedOn = "2025-08-07", model = "gpt-5"),
)

private val CODEX_DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

private fun CodexThreadSourceKind.isSubAgentSourceKind(): Boolean {
  return when (this) {
    CodexThreadSourceKind.SUB_AGENT,
    CodexThreadSourceKind.SUB_AGENT_REVIEW,
    CodexThreadSourceKind.SUB_AGENT_COMPACT,
    CodexThreadSourceKind.SUB_AGENT_THREAD_SPAWN,
    CodexThreadSourceKind.SUB_AGENT_OTHER,
      -> true

    CodexThreadSourceKind.CLI,
    CodexThreadSourceKind.VSCODE,
    CodexThreadSourceKind.EXEC,
    CodexThreadSourceKind.APP_SERVER,
    CodexThreadSourceKind.UNKNOWN,
      -> false
  }
}

private fun reduceEvent(parseState: RolloutParseState, event: RolloutEvent) {
  reduceSessionMetadata(parseState, event)

  val eventTimestamp = event.timestampMs
  val eventOrder = parseState.nextActivityOrder++
  reduceActivityEvent(parseState = parseState, event = event, eventOrder = eventOrder, eventTimestamp = eventTimestamp)
}

private fun reduceSessionMetadata(parseState: RolloutParseState, event: RolloutEvent) {
  parseState.updatedAt = maxTimestamp(parseState.updatedAt, event.timestampMs)
  parseState.updatedAt = maxTimestamp(parseState.updatedAt, event.sessionTimestampMs)
  parseState.sessionId = parseState.sessionId ?: event.sessionId
  parseState.sessionCwd = parseState.sessionCwd ?: event.sessionCwd
  if (parseState.sourceKind == CodexThreadSourceKind.UNKNOWN && event.sourceKind != CodexThreadSourceKind.UNKNOWN) {
    parseState.sourceKind = event.sourceKind
  }
  parseState.parentThreadId = parseState.parentThreadId ?: event.parentThreadId
  parseState.gitBranch = parseState.gitBranch ?: event.gitBranch
  parseState.modelId = event.payloadModel ?: parseState.modelId

  if (event.topLevelType != "event_msg") {
    return
  }
  when (normalizeToken(event.payloadType)) {
    "usermessage" -> parseState.title = parseState.title ?: extractTitle(event.payloadMessage)
    "threadnameupdated" -> parseState.title = extractThreadName(event.payloadThreadName) ?: parseState.title
    "tokencount" -> {
      event.payloadTokenUsage?.let { usageSnapshot ->
        parseState.usageSnapshot = if (usageSnapshot.modelId != null || parseState.modelId == null) {
          usageSnapshot
        }
        else {
          usageSnapshot.copy(modelId = parseState.modelId)
        }
      }
    }
  }
}

private fun reduceOutlineEvent(parseState: RolloutOutlineParseState, event: RolloutEvent) {
  parseState.updatedAt = maxTimestamp(parseState.updatedAt, event.timestampMs)
  parseState.updatedAt = maxTimestamp(parseState.updatedAt, event.sessionTimestampMs)
  parseState.sessionId = parseState.sessionId ?: event.sessionId
  if (event.topLevelType == "event_msg") {
    when (normalizeToken(event.payloadType)) {
      "usermessage" -> {
        val preview = stripUserMessagePrefix(event.payloadMessage.orEmpty()).trim().takeIf { it.isNotEmpty() }
        parseState.title = parseState.title ?: preview?.let(::normalizeThreadTitle)
        parseState.addUserPrompt(event, preview)
      }
      "agentmessage" -> parseState.addAssistantResponse(event, event.payloadMessage)
      "taskstarted", "turnstarted" -> parseState.noteTurnStarted(event)
      "taskcomplete", "turncomplete", "turnaborted" -> parseState.noteTurnCompleted(event)
      "requestuserinput" -> parseState.addDetailItem(event, AgentSessionOutlineItemKind.INPUT_REQUEST, "Input requested")
      "execapprovalrequest", "applypatchapprovalrequest", "requestpermissions", "elicitationrequest" -> {
        parseState.addDetailItem(event, AgentSessionOutlineItemKind.APPROVAL_REQUEST, "Approval requested")
      }
      in PROJECT_MUTATING_BEGIN_EVENT_TYPES -> parseState.addToolCall(event)
      in PROJECT_MUTATING_END_EVENT_TYPES -> parseState.addToolResult(event)
      "itemcompleted" -> {
        if (isPlanItemType(event.payloadItemType)) {
          parseState.addDetailItem(event, AgentSessionOutlineItemKind.PLAN, "Plan updated")
        }
      }
      "threadnameupdated" -> parseState.title = extractThreadName(event.payloadThreadName) ?: parseState.title
    }
    return
  }

  if (event.topLevelType == "response_item") {
    when (normalizeToken(event.payloadType)) {
      "message" -> when (event.payloadRole) {
        "user" -> {
          if (isUsefulOutlineUserPrompt(event.payloadContentPreview)) {
            parseState.title = parseState.title ?: normalizeThreadTitle(event.payloadContentPreview)
            parseState.addUserPrompt(event, event.payloadContentPreview)
          }
        }
        "assistant" -> parseState.addAssistantResponse(event, event.payloadContentPreview)
      }
      "functioncall", "customtoolcall" -> {
        when {
          normalizeToken(event.payloadName) == "requestuserinput" -> {
            parseState.addDetailItem(event, AgentSessionOutlineItemKind.INPUT_REQUEST, "Input requested")
          }
          isApprovalFunctionCall(event) -> parseState.addDetailItem(event,
                                                                    AgentSessionOutlineItemKind.APPROVAL_REQUEST,
                                                                    "Approval requested")
          else -> parseState.addToolCall(event)
        }
      }
      "functioncalloutput", "customtoolcalloutput" -> parseState.addToolResult(event)
    }
  }
}

private fun reduceActivityEvent(
  parseState: RolloutParseState,
  event: RolloutEvent,
  eventOrder: Long,
  eventTimestamp: Long?,
) {
  when (event.topLevelType) {
    "event_msg" -> {
      when (normalizeToken(event.payloadType)) {
        "taskstarted", "turnstarted" -> {
          parseState.hasActivityEvidence = true
          parseState.activityProjection.apply(CodexThreadActivitySignal.TurnStarted(order = eventOrder, turnId = event.payloadTurnId))
        }

        "taskcomplete", "turncomplete", "turnaborted" -> {
          parseState.hasActivityEvidence = true
          parseState.activityProjection.apply(CodexThreadActivitySignal.TurnCompleted(order = eventOrder, turnId = event.payloadTurnId))
          parseState.clearPendingFunctionCallsForCompletedTurn(completedTurnId = event.payloadTurnId)
        }

        "usermessage" -> {
          parseState.activityProjection.apply(CodexThreadActivitySignal.UserMessage(eventOrder))
        }

        "agentmessage" -> {
          parseState.activityProjection.apply(CodexThreadActivitySignal.AssistantMessage(eventOrder))
        }

        "mcptoolcallend" -> {
          parseState.activityProjection.apply(CodexThreadActivitySignal.ClearToolCall(callId = event.payloadCallId,
                                                                                      turnId = event.payloadTurnId))
          event.payloadCallId?.let(parseState.pendingFunctionCallByCallId::remove)
        }

        "requestuserinput" -> {
          parseState.activityProjection.apply(CodexThreadActivitySignal.PendingUserInput(order = eventOrder, callId = event.payloadCallId))
        }

        "execapprovalrequest", "applypatchapprovalrequest", "requestpermissions", "elicitationrequest" -> {
          parseState.activityProjection.apply(CodexThreadActivitySignal.PendingApproval(order = eventOrder,
                                                                                        callId = event.payloadCallId,
                                                                                        turnId = event.payloadTurnId))
        }

        in PROJECT_MUTATING_BEGIN_EVENT_TYPES -> {
          reduceProjectMutatingEventBegin(parseState, event, eventOrder, eventTimestamp)
        }

        in PROJECT_MUTATING_END_EVENT_TYPES -> {
          reduceProjectMutatingEventEnd(parseState, event, eventTimestamp)
        }

        "itemcompleted" -> {
          if (isPlanItemType(event.payloadItemType)) {
            parseState.activityProjection.apply(CodexThreadActivitySignal.Plan(order = eventOrder, turnId = event.payloadTurnId))
          }
        }

        "enteredreviewmode" -> parseState.activityProjection.apply(CodexThreadActivitySignal.ReviewModeEntered)
        "exitedreviewmode" -> parseState.activityProjection.apply(CodexThreadActivitySignal.ReviewModeExited)
      }
    }

    "response_item" -> {
      when (normalizeToken(event.payloadType)) {
        "message" -> {
          when (event.payloadRole) {
            "user" -> {
              parseState.activityProjection.apply(CodexThreadActivitySignal.UserMessage(eventOrder))
            }

            "assistant" -> {
              parseState.activityProjection.apply(CodexThreadActivitySignal.AssistantMessage(eventOrder))
            }
          }
        }

        "functioncall" -> {
          reduceResponseFunctionCall(parseState, event, eventOrder, eventTimestamp)
        }

        "functioncalloutput" -> {
          reduceResponseFunctionCallOutput(parseState, event, eventTimestamp)
        }
      }
    }
  }
}

private fun reduceProjectMutatingEventBegin(
  parseState: RolloutParseState,
  event: RolloutEvent,
  eventOrder: Long,
  eventTimestamp: Long?,
) {
  parseState.activityProjection.apply(CodexThreadActivitySignal.ClearPendingApproval(callId = event.payloadCallId,
                                                                                     turnId = event.payloadTurnId))
  parseState.markPendingFunctionCall(
    eventTimestamp = eventTimestamp,
    callId = event.payloadCallId,
    turnId = event.payloadTurnId,
    projectMutating = true,
    changedProjectFilePaths = changedProjectFilePathsForProjectMutatingEvent(event, parseState.sessionCwd),
  )
  parseState.activityProjection.apply(CodexThreadActivitySignal.ToolCallStarted(order = eventOrder,
                                                                                callId = event.payloadCallId,
                                                                                turnId = event.payloadTurnId))
}

private fun reduceProjectMutatingEventEnd(parseState: RolloutParseState, event: RolloutEvent, eventTimestamp: Long?) {
  parseState.markProjectFilesChangedForFinishedTool(
    eventTimestamp = eventTimestamp,
    callId = event.payloadCallId,
    fallbackChangedProjectFilePaths = changedProjectFilePathsForProjectMutatingEvent(event, parseState.sessionCwd),
  )
  parseState.activityProjection.apply(CodexThreadActivitySignal.ClearToolCall(callId = event.payloadCallId, turnId = event.payloadTurnId))
  parseState.clearPendingFunctionCallForFinishedTool(callId = event.payloadCallId, turnId = event.payloadTurnId)
}

private fun reduceResponseFunctionCall(
  parseState: RolloutParseState,
  event: RolloutEvent,
  eventOrder: Long,
  eventTimestamp: Long?,
) {
  if (normalizeToken(event.payloadName) == "requestuserinput") {
    parseState.activityProjection.apply(CodexThreadActivitySignal.PendingUserInput(order = eventOrder, callId = event.payloadCallId))
    return
  }

  if (isApprovalFunctionCall(event)) {
    parseState.activityProjection.apply(CodexThreadActivitySignal.PendingApproval(order = eventOrder,
                                                                                  callId = event.payloadCallId,
                                                                                  turnId = event.payloadTurnId))
    if (!isToolFunctionCall(event)) {
      return
    }
  }

  parseState.markPendingFunctionCall(
    eventTimestamp = eventTimestamp,
    callId = event.payloadCallId,
    turnId = event.payloadTurnId,
    projectMutating = isProjectMutatingFunctionCall(event),
    changedProjectFilePaths = changedProjectFilePathsForProjectMutatingFunctionCall(event, parseState.sessionCwd),
  )
  parseState.activityProjection.apply(CodexThreadActivitySignal.ToolCallStarted(order = eventOrder,
                                                                                callId = event.payloadCallId,
                                                                                turnId = event.payloadTurnId))
  if (isCodexExecFunctionCall(event)) {
    parseState.markPendingCodexExecFunctionCall(event.payloadCallId)
  }
}

private fun reduceResponseFunctionCallOutput(parseState: RolloutParseState, event: RolloutEvent, eventTimestamp: Long?) {
  parseState.activityProjection.apply(CodexThreadActivitySignal.ClearPendingUserInput(event.payloadCallId))
  parseState.activityProjection.apply(CodexThreadActivitySignal.ClearPendingApproval(callId = event.payloadCallId,
                                                                                     turnId = event.payloadTurnId))
  parseState.markProjectFilesChangedForCompletedFunctionCall(eventTimestamp = eventTimestamp, callId = event.payloadCallId)
  parseState.markSpawnedExecThreadsForCompletedFunctionCall(callId = event.payloadCallId, output = event.payloadOutput)
  parseState.activityProjection.apply(CodexThreadActivitySignal.ClearToolCall(callId = event.payloadCallId, turnId = event.payloadTurnId))
  event.payloadCallId?.let(parseState.pendingFunctionCallByCallId::remove)
}

private fun parseEvent(parser: JsonParser): RolloutEvent? {
  return try {
    if (parser.currentToken() != JsonToken.START_OBJECT) return null

    var topLevelType: String? = null
    var timestampMs: Long? = null
    var payloadType: String? = null
    var payloadRole: String? = null
    var payloadMessage: String? = null
    var payloadContentPreview: String? = null
    var payloadName: String? = null
    var payloadArguments: String? = null
    var payloadOutput: String? = null
    var payloadCallId: String? = null
    var payloadItemType: String? = null
    var payloadThreadName: String? = null
    var payloadTurnId: String? = null
    var payloadModel: String? = null
    var payloadTokenUsage: AgentSessionUsageSnapshot? = null
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
          if (parser.currentToken() == JsonToken.START_OBJECT) {
            forEachObjectField(parser) { payloadField ->
              when (payloadField) {
                "type" -> payloadType = readStringOrNull(parser)
                "role" -> payloadRole = readStringOrNull(parser)
                "message" -> payloadMessage = readStringOrNull(parser)
                "content" -> payloadContentPreview = readRolloutContentPreview(parser)
                "name" -> payloadName = readStringOrNull(parser)
                "arguments" -> payloadArguments = readStringOrNull(parser)
                "output" -> payloadOutput = readStringOrNull(parser)
                "call_id" -> payloadCallId = readStringOrNull(parser)
                "item" -> payloadItemType = parseRolloutItemType(parser)
                "thread_name", "threadName" -> payloadThreadName = readStringOrNull(parser)
                "turn_id", "turnId" -> payloadTurnId = readStringOrNull(parser)
                "model" -> payloadModel = readStringOrNull(parser)
                "info" -> payloadTokenUsage = parseTokenUsageSnapshot(parser, payloadModel)
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
      payloadContentPreview = payloadContentPreview,
      payloadName = payloadName,
      payloadArguments = payloadArguments,
      payloadOutput = payloadOutput,
      payloadCallId = payloadCallId,
      payloadItemType = payloadItemType,
      payloadThreadName = payloadThreadName,
      payloadTurnId = payloadTurnId,
      payloadModel = payloadModel,
      payloadTokenUsage = payloadTokenUsage,
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
  @JvmField val path: Path,
  @JvmField val normalizedCwd: String,
  @JvmField val parentThreadId: String?,
  @JvmField val projectFilesChangedAt: Long,
  @JvmField val projectFileChangeEvidence: List<CodexProjectFileChangeEvidence>,
  @JvmField val hasExplicitTitle: Boolean,
  @JvmField val spawnedExecThreadIds: Set<String>,
  @JvmField val thread: CodexBackendThread,
)

internal data class CodexProjectFileChangeEvidence(
  @JvmField val timestampMillis: Long,
  @JvmField val changedProjectFilePaths: Set<String>?,
)

private data class RolloutEvent(
  @JvmField val topLevelType: String?,
  @JvmField val timestampMs: Long?,
  @JvmField val payloadType: String?,
  @JvmField val payloadRole: String?,
  @JvmField val payloadMessage: String?,
  @JvmField val payloadContentPreview: String?,
  @JvmField val payloadName: String?,
  @JvmField val payloadArguments: String?,
  @JvmField val payloadOutput: String?,
  @JvmField val payloadCallId: String?,
  @JvmField val payloadItemType: String?,
  @JvmField val payloadThreadName: String?,
  @JvmField val payloadTurnId: String?,
  @JvmField val payloadModel: String?,
  @JvmField val payloadTokenUsage: AgentSessionUsageSnapshot?,
  @JvmField val sessionId: String?,
  @JvmField val sessionCwd: String?,
  @JvmField val sessionTimestampMs: Long?,
  @JvmField val sourceKind: CodexThreadSourceKind,
  @JvmField val parentThreadId: String?,
  @JvmField val gitBranch: String?,
)

private data class RolloutOutlineParseState(
  @JvmField var sessionId: String? = null,
  @JvmField var title: String? = null,
  @JvmField var updatedAt: Long = 0L,
  @JvmField var nextItemIndex: Int = 0,
  @JvmField val rootItems: MutableList<RolloutOutlineItemBuilder> = ArrayList(),
  @JvmField val turnItemsById: LinkedHashMap<String, RolloutOutlineItemBuilder> = LinkedHashMap(),
  @JvmField val currentPhaseByTurnKey: LinkedHashMap<String, RolloutOutlineItemBuilder> = LinkedHashMap(),
  @JvmField val parentByCallId: LinkedHashMap<String, RolloutOutlineItemBuilder> = LinkedHashMap(),
  @JvmField val resultByCallId: LinkedHashMap<String, RolloutOutlineItemBuilder> = LinkedHashMap(),
  @JvmField val seenConversationKeys: MutableSet<String> = HashSet(),
  @JvmField val seenToolCallKeys: MutableSet<String> = HashSet(),
  @JvmField var activeTurnId: String? = null,
  @JvmField var nextUserPromptIndex: Int = 0,
) {
  fun noteTurnStarted(event: RolloutEvent) {
    val turnId = event.payloadTurnId?.takeIf { it.isNotBlank() } ?: return
    activeTurnId = turnId
    turnFor(turnId = turnId, event = event)
  }

  fun noteTurnCompleted(event: RolloutEvent) {
    val turnId = event.payloadTurnId?.takeIf { it.isNotBlank() }
    if (turnId == null || turnId == activeTurnId) {
      activeTurnId = null
    }
  }

  fun addUserPrompt(event: RolloutEvent, preview: String?) {
    val normalizedPreview = normalizeOutlinePreview(preview) ?: return
    if (!isUsefulOutlineUserPrompt(normalizedPreview) || !seenConversationKeys.add("user:${dedupeOutlineText(normalizedPreview)}")) {
      return
    }
    val userPromptIndex = nextUserPromptIndex++
    val item = newItem(
      event = event,
      kind = AgentSessionOutlineItemKind.USER_PROMPT,
      title = "",
      preview = normalizedPreview,
      idOverride = codexUserPromptOutlineItemId(userPromptIndex),
    )
    val turn = turnFor(event)
    if (turn == null) {
      rootItems += item
    }
    else {
      turn.children += item
      currentPhaseByTurnKey.remove(turn.id)
    }
  }

  fun addAssistantResponse(event: RolloutEvent, preview: String?) {
    val normalizedPreview = normalizeOutlinePreview(preview) ?: return
    if (!seenConversationKeys.add("assistant:${dedupeOutlineText(normalizedPreview)}")) {
      return
    }
    val phase = newItem(
      event = event,
      kind = AgentSessionOutlineItemKind.ASSISTANT_RESPONSE,
      title = outlinePhaseTitle(normalizedPreview) ?: "Agent response",
      preview = normalizedPreview,
      summarizesChildren = true,
    )
    val turn = turnFor(event)
    if (turn == null) {
      rootItems += phase
      currentPhaseByTurnKey[ROOT_OUTLINE_TURN_KEY] = phase
    }
    else {
      turn.children += phase
      currentPhaseByTurnKey[turn.id] = phase
    }
  }

  fun addDetailItem(event: RolloutEvent, kind: AgentSessionOutlineItemKind, title: String, preview: String? = null) {
    val item = newItem(event = event, kind = kind, title = title, preview = preview)
    parentForDetail(event).children += item
  }

  fun addToolCall(event: RolloutEvent) {
    val key = outlineCallKey(event)
    if (key != null && !seenToolCallKeys.add(key)) {
      return
    }
    val item = newItem(
      event = event,
      kind = AgentSessionOutlineItemKind.TOOL_CALL,
      title = outlineToolTitle(event),
      preview = outlineToolCallPreview(event),
    )
    val parent = parentForDetail(event)
    parent.children += item
    event.payloadCallId?.takeIf { it.isNotBlank() }?.let { callId -> parentByCallId[callId] = parent }
  }

  fun addToolResult(event: RolloutEvent) {
    val callId = event.payloadCallId?.takeIf { it.isNotBlank() }
    val existing = callId?.let(resultByCallId::get)
    if (existing != null) {
      updateToolResult(existing, event)
      return
    }
    val item = newItem(
      event = event,
      kind = AgentSessionOutlineItemKind.TOOL_RESULT,
      title = outlineToolResultTitle(event),
      preview = event.payloadOutput,
    )
    val parent = callId?.let(parentByCallId::get) ?: parentForDetail(event)
    parent.children += item
    if (callId != null) {
      resultByCallId[callId] = item
    }
  }

  private fun updateToolResult(item: RolloutOutlineItemBuilder, event: RolloutEvent) {
    val preview = normalizeOutlinePreview(event.payloadOutput)
    if (item.preview == null && preview != null) {
      item.preview = preview
    }
    val title = outlineToolResultTitle(event)
    if (title.startsWith("Exit ") || item.title == "Tool result") {
      item.title = title
    }
  }

  private fun newItem(
    event: RolloutEvent,
    kind: AgentSessionOutlineItemKind,
    title: String,
    preview: String? = null,
    summarizesChildren: Boolean = false,
    idOverride: String? = null,
  ): RolloutOutlineItemBuilder {
    val item = RolloutOutlineItemBuilder(
      id = idOverride ?: event.payloadCallId ?: event.payloadTurnId ?: "outline-${nextItemIndex}",
      kind = kind,
      title = title,
      preview = normalizeOutlinePreview(preview),
      timestampMs = event.timestampMs,
      summarizesChildren = summarizesChildren,
    )
    nextItemIndex++
    return item
  }

  private fun turnFor(event: RolloutEvent): RolloutOutlineItemBuilder? {
    val turnId = event.payloadTurnId?.takeIf { it.isNotBlank() } ?: activeTurnId ?: return null
    return turnFor(turnId = turnId, event = event)
  }

  private fun turnFor(turnId: String, event: RolloutEvent): RolloutOutlineItemBuilder {
    val turn = turnItemsById.getOrPut(turnId) {
      RolloutOutlineItemBuilder(
        id = "turn-$turnId",
        kind = AgentSessionOutlineItemKind.AGENT_WORK,
        title = "Turn ${turnId.take(8)}",
        preview = null,
        timestampMs = event.timestampMs,
        visible = false,
      ).also(rootItems::add)
    }
    return turn
  }

  private fun parentForDetail(event: RolloutEvent): RolloutOutlineItemBuilder {
    val turn = turnFor(event)
    val turnKey = turn?.id ?: ROOT_OUTLINE_TURN_KEY
    val currentPhase = currentPhaseByTurnKey[turnKey]
    if (currentPhase != null) {
      return currentPhase
    }
    if (turn != null) {
      return newWorkPhase(event).also { work ->
        turn.children += work
        currentPhaseByTurnKey[turn.id] = work
      }
    }
    return newWorkPhase(event).also { work ->
      rootItems += work
      currentPhaseByTurnKey[ROOT_OUTLINE_TURN_KEY] = work
    }
  }

  private fun newWorkPhase(event: RolloutEvent): RolloutOutlineItemBuilder {
    return RolloutOutlineItemBuilder(
      id = "work-${nextItemIndex++}",
      kind = AgentSessionOutlineItemKind.AGENT_WORK,
      title = "Agent work",
      preview = null,
      timestampMs = event.timestampMs,
    )
  }

  fun buildItems(): List<AgentSessionOutlineItem> = rootItems.flatMap(RolloutOutlineItemBuilder::buildVisible)
}

private data class RolloutOutlineItemBuilder(
  @JvmField val id: String,
  @JvmField var kind: AgentSessionOutlineItemKind,
  @JvmField var title: String,
  @JvmField var preview: String?,
  @JvmField val timestampMs: Long?,
  @JvmField val summarizesChildren: Boolean = false,
  @JvmField val visible: Boolean = true,
  @JvmField val children: MutableList<RolloutOutlineItemBuilder> = ArrayList(),
) {
  fun buildVisible(): List<AgentSessionOutlineItem> {
    val builtChildren = children.flatMap(RolloutOutlineItemBuilder::buildVisible)
    if (!visible) {
      return builtChildren
    }
    return AgentSessionOutlineItem(
      id = id,
      kind = kind,
      title = title,
      preview = if (summarizesChildren && builtChildren.isNotEmpty()) summarizeOutlineChildren(builtChildren) else preview,
      timestampMs = timestampMs,
      children = builtChildren,
    ).let(::listOf)
  }
}

private const val ROOT_OUTLINE_TURN_KEY = "<root>"

private data class RolloutParseState(
  @JvmField var sessionId: String? = null,
  @JvmField var sessionCwd: String? = null,
  @JvmField var sourceKind: CodexThreadSourceKind = CodexThreadSourceKind.UNKNOWN,
  @JvmField var parentThreadId: String? = null,
  @JvmField var gitBranch: String? = null,
  @JvmField var title: String? = null,
  @JvmField var modelId: String? = null,
  @JvmField var updatedAt: Long = 0L,
  @JvmField var nextActivityOrder: Long = 0L,
  @JvmField var hasActivityEvidence: Boolean = false,
  @JvmField val activityProjection: CodexThreadActivityProjection = CodexThreadActivityProjection(),
  @JvmField var usageSnapshot: AgentSessionUsageSnapshot? = null,
  @JvmField var projectFilesChangedAt: Long = Long.MIN_VALUE,
  @JvmField val projectFileChangeEvidence: MutableList<CodexProjectFileChangeEvidence> = ArrayList(),
  @JvmField val pendingFunctionCallByCallId: LinkedHashMap<String, PendingFunctionCall> = LinkedHashMap(),
  @JvmField val spawnedExecThreadIds: MutableSet<String> = LinkedHashSet(),
  @JvmField var nextSyntheticPendingFunctionCallId: Int = 0,
)

private data class PendingFunctionCall(
  @JvmField val updatedAt: Long,
  @JvmField val turnId: String?,
  @JvmField val projectMutating: Boolean,
  @JvmField val changedProjectFilePaths: Set<String>?,
  @JvmField val codexExecCommand: Boolean = false,
)

private fun RolloutParseState.markPendingFunctionCall(
  eventTimestamp: Long?,
  callId: String?,
  turnId: String?,
  projectMutating: Boolean,
  changedProjectFilePaths: Set<String>?,
) {
  val resolvedTimestamp = eventTimestamp ?: updatedAt
  val resolvedCallId = callId ?: "pending-function-call-${nextSyntheticPendingFunctionCallId++}"
  val previous = pendingFunctionCallByCallId[resolvedCallId]
  if (previous == null || resolvedTimestamp >= previous.updatedAt) {
    val mergedProjectMutating = projectMutating || previous?.projectMutating == true
    val mergedChangedProjectFilePaths = when {
      !mergedProjectMutating -> null
      previous == null -> changedProjectFilePaths
      !projectMutating -> previous.changedProjectFilePaths
      else -> mergePendingChangedProjectFilePaths(previous.changedProjectFilePaths, changedProjectFilePaths)
    }
    pendingFunctionCallByCallId[resolvedCallId] = PendingFunctionCall(
      updatedAt = resolvedTimestamp,
      turnId = turnId,
      projectMutating = mergedProjectMutating,
      changedProjectFilePaths = mergedChangedProjectFilePaths,
      codexExecCommand = previous?.codexExecCommand == true,
    )
  }
}

private fun RolloutParseState.markPendingCodexExecFunctionCall(callId: String?) {
  val resolvedCallId = callId ?: return
  val pendingFunctionCall = pendingFunctionCallByCallId[resolvedCallId] ?: return
  pendingFunctionCallByCallId[resolvedCallId] = pendingFunctionCall.copy(codexExecCommand = true)
}

private fun RolloutParseState.markSpawnedExecThreadsForCompletedFunctionCall(callId: String?, output: String?) {
  val pendingFunctionCall = callId?.let(pendingFunctionCallByCallId::get) ?: return
  if (!pendingFunctionCall.codexExecCommand) {
    return
  }
  spawnedExecThreadIds.addAll(extractSpawnedCodexExecThreadIds(output))
}

private fun RolloutParseState.markProjectFilesChanged(eventTimestamp: Long?, changedProjectFilePaths: Set<String>?) {
  val resolvedTimestamp = eventTimestamp ?: updatedAt
  projectFilesChangedAt = maxTimestamp(projectFilesChangedAt, resolvedTimestamp)
  if (resolvedTimestamp != Long.MIN_VALUE) {
    projectFileChangeEvidence += CodexProjectFileChangeEvidence(
      timestampMillis = resolvedTimestamp,
      changedProjectFilePaths = changedProjectFilePaths,
    )
  }
}

private fun RolloutParseState.markProjectFilesChangedForCompletedFunctionCall(eventTimestamp: Long?, callId: String?) {
  val pendingFunctionCall = callId?.let(pendingFunctionCallByCallId::get) ?: return
  if (pendingFunctionCall.projectMutating) {
    markProjectFilesChanged(eventTimestamp, pendingFunctionCall.changedProjectFilePaths)
  }
}

private fun RolloutParseState.markProjectFilesChangedForFinishedTool(
  eventTimestamp: Long?,
  callId: String?,
  fallbackChangedProjectFilePaths: Set<String>?,
) {
  val pendingFunctionCall = callId?.let(pendingFunctionCallByCallId::get)
  if (pendingFunctionCall?.projectMutating == true) {
    markProjectFilesChanged(eventTimestamp, pendingFunctionCall.changedProjectFilePaths)
  }
  else {
    markProjectFilesChanged(eventTimestamp, fallbackChangedProjectFilePaths)
  }
}

private fun mergePendingChangedProjectFilePaths(existing: Set<String>?, incoming: Set<String>?): Set<String>? {
  if (existing == null || incoming == null) {
    return null
  }
  if (existing.isEmpty()) {
    return incoming
  }
  if (incoming.isEmpty()) {
    return existing
  }
  val merged = LinkedHashSet<String>(existing.size + incoming.size)
  merged.addAll(existing)
  merged.addAll(incoming)
  return merged
}

private fun RolloutParseState.clearPendingFunctionCallForFinishedTool(callId: String?, turnId: String?) {
  if (callId != null && pendingFunctionCallByCallId.remove(callId) != null) {
    return
  }
  if (pendingFunctionCallByCallId.size == 1) {
    pendingFunctionCallByCallId.clear()
    return
  }
  clearPendingFunctionCallsForCompletedTurn(completedTurnId = turnId)
}

private fun RolloutParseState.clearPendingFunctionCallsForCompletedTurn(completedTurnId: String?) {
  if (completedTurnId == null) {
    pendingFunctionCallByCallId.clear()
    return
  }

  val iterator = pendingFunctionCallByCallId.entries.iterator()
  while (iterator.hasNext()) {
    val (_, pendingFunctionCall) = iterator.next()
    if (pendingFunctionCall.turnId == null || pendingFunctionCall.turnId == completedTurnId) {
      iterator.remove()
    }
  }
}

private fun isApprovalFunctionCall(event: RolloutEvent): Boolean {
  return normalizeToken(event.payloadName) == "requestpermissions" || argumentsRequireEscalatedSandbox(event.payloadArguments)
}

private fun isToolFunctionCall(event: RolloutEvent): Boolean {
  return normalizeToken(event.payloadName) != "requestpermissions"
}

private fun isProjectMutatingFunctionCall(event: RolloutEvent): Boolean {
  return normalizeToken(event.payloadName) in PROJECT_MUTATING_FUNCTION_CALL_NAMES
}

private fun isCodexExecFunctionCall(event: RolloutEvent): Boolean {
  return normalizeToken(event.payloadName) == "execcommand" && readCommandText(event.payloadArguments)?.let(::isCodexExecCommand) == true
}

private fun isCodexExecCommand(command: String): Boolean {
  val text = command.trimStart()
  return text == "codex exec" ||
         text.startsWith("codex exec ") ||
         text == "/Applications/Codex.app/Contents/Resources/codex exec" ||
         text.startsWith("/Applications/Codex.app/Contents/Resources/codex exec ")
}

private fun readCommandText(arguments: String?): String? {
  val text = arguments?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return try {
    JsonFactory().createJsonParser(text).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) return@use null
      var commandText: String? = null
      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "cmd", "command" -> commandText = readStringOrNull(parser) ?: commandText
          else -> parser.skipChildren()
        }
        true
      }
      commandText
    }
  }
  catch (_: Throwable) {
    null
  }
}

private fun extractSpawnedCodexExecThreadIds(output: String?): Set<String> {
  val text = output?.takeIf { it.contains(CODEX_EXEC_HEADER) && it.contains(CODEX_EXEC_WORKDIR_PREFIX) } ?: return emptySet()
  val result = LinkedHashSet<String>()
  text.lineSequence().forEach { line ->
    val trimmedLine = line.trim()
    if (!trimmedLine.startsWith(CODEX_EXEC_SESSION_ID_PREFIX, ignoreCase = true)) {
      return@forEach
    }
    val threadId = trimmedLine.substring(CODEX_EXEC_SESSION_ID_PREFIX.length).trim()
    if (CODEX_THREAD_ID.matches(threadId)) {
      result.add(threadId)
    }
  }
  return result
}

private fun changedProjectFilePathsForProjectMutatingFunctionCall(event: RolloutEvent, cwd: String?): Set<String>? {
  return when (normalizeToken(event.payloadName)) {
    "applypatch" -> changedProjectFilePathsFromApplyPatchArguments(event.payloadArguments, cwd)
    else -> null
  }
}

private fun changedProjectFilePathsForProjectMutatingEvent(event: RolloutEvent, cwd: String?): Set<String>? {
  return when (normalizeToken(event.payloadType)) {
    "patchapplybegin", "patchapplyend" -> changedProjectFilePathsFromApplyPatchArguments(event.payloadArguments, cwd)
    else -> null
  }
}

private fun changedProjectFilePathsFromApplyPatchArguments(arguments: String?, cwd: String?): Set<String>? {
  val patchText = readApplyPatchText(arguments) ?: return null
  val paths = LinkedHashSet<String>()
  patchText.lineSequence().forEach { line ->
    when {
      line.startsWith("*** Add File: ") -> resolveChangedProjectFilePath(line.removePrefix("*** Add File: "), cwd)?.let(paths::add)
      line.startsWith("*** Update File: ") -> resolveChangedProjectFilePath(line.removePrefix("*** Update File: "), cwd)?.let(paths::add)
      line.startsWith("*** Delete File: ") -> resolveChangedProjectFilePath(line.removePrefix("*** Delete File: "), cwd)?.let(paths::add)
      line.startsWith("*** Move to: ") -> resolveChangedProjectFilePath(line.removePrefix("*** Move to: "), cwd)?.let(paths::add)
    }
  }
  return paths.takeIf { it.isNotEmpty() }
}

private fun readApplyPatchText(arguments: String?): String? {
  val text = arguments?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val parsedPatchText = try {
    JsonFactory().createJsonParser(text).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) return@use null
      var patchText: String? = null
      forEachObjectField(parser) { fieldName ->
        when (fieldName) {
          "patch", "input" -> {
            val value = readStringOrNull(parser)
            if (patchText == null && value?.contains("*** Begin Patch") == true) {
              patchText = value
            }
          }
          else -> parser.skipChildren()
        }
        true
      }
      patchText
    }
  }
  catch (_: Throwable) {
    null
  }
  if (parsedPatchText != null) {
    return parsedPatchText
  }
  return text.takeIf { "*** Begin Patch" in it }
}

private fun resolveChangedProjectFilePath(pathText: String, cwd: String?): String? {
  val trimmedPath = pathText.trim().takeIf { it.isNotEmpty() } ?: return null
  val path = pathOrNull(trimmedPath) ?: return null
  val resolvedPath = if (path.isAbsolute) {
    path
  }
  else {
    val cwdPath = pathOrNull(cwd?.takeIf { it.isNotBlank() } ?: return null) ?: return null
    cwdPath.resolve(path)
  }
  return normalizeRootPath(resolvedPath.normalize().toString())
}

private fun pathOrNull(pathText: String): Path? {
  return try {
    Path.of(pathText)
  }
  catch (_: InvalidPathException) {
    null
  }
}

// Centralized so renames in the Codex CLI event taxonomy break in one place rather than silently
// causing the project-file-change evidence path to no-op. Tokens are normalized: lowercased with
// underscores/dashes removed by normalizeToken().
private val PROJECT_MUTATING_BEGIN_EVENT_TYPES = setOf("execcommandbegin", "patchapplybegin")
private val PROJECT_MUTATING_END_EVENT_TYPES = setOf("execcommandend", "patchapplyend")
private val PROJECT_MUTATING_FUNCTION_CALL_NAMES = setOf("execcommand", "applypatch")

private fun argumentsRequireEscalatedSandbox(arguments: String?): Boolean {
  val text = arguments?.trim()?.takeIf { it.isNotEmpty() } ?: return false
  return try {
    JsonFactory().createJsonParser(text).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) return false
      forEachObjectField(parser) { fieldName ->
        if (fieldName == "sandbox_permissions") {
          return readStringOrNull(parser) == "require_escalated"
        }
        parser.skipChildren()
        true
      }
      false
    }
  }
  catch (_: Throwable) {
    false
  }
}

private fun parseTokenUsageSnapshot(parser: JsonParser, modelId: String?): AgentSessionUsageSnapshot? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var totalInputTokens: Long? = null
  var cachedInputTokens = 0L
  var outputTokens = 0L
  var reasoningOutputTokens = 0L
  forEachObjectField(parser) { fieldName ->
    when (fieldName) {
      "total_token_usage" -> {
        if (parser.currentToken() == JsonToken.START_OBJECT) {
          forEachObjectField(parser) { usageField ->
            when (usageField) {
              "input_tokens" -> totalInputTokens = readLongOrNull(parser)
              "cached_input_tokens" -> cachedInputTokens = readLongOrNull(parser) ?: 0L
              "output_tokens" -> outputTokens = readLongOrNull(parser) ?: 0L
              "reasoning_output_tokens" -> reasoningOutputTokens = readLongOrNull(parser) ?: 0L
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

  val resolvedTotalInputTokens = totalInputTokens ?: return null
  val resolvedInputTokens = maxOf(resolvedTotalInputTokens - cachedInputTokens, 0L)
  return AgentSessionUsageSnapshot(
    modelId = modelId,
    inputTokens = resolvedInputTokens,
    outputTokens = outputTokens + reasoningOutputTokens,
    cacheReadTokens = cachedInputTokens,
  )
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
  return normalized.startsWith(ENVIRONMENT_CONTEXT_OPEN_TAG) ||
         normalized.startsWith(TURN_ABORTED_OPEN_TAG) ||
         normalized.startsWith("<permissions instructions>")
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
  if (parser.currentToken() != JsonToken.START_OBJECT) {
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

private fun parseRolloutItemType(parser: JsonParser): String? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var type: String? = null
  forEachObjectField(parser) { nestedField ->
    if (nestedField == "type") {
      type = readStringOrNull(parser)
    }
    else {
      parser.skipChildren()
    }
    true
  }
  return type
}

private fun readRolloutContentPreview(parser: JsonParser): String? {
  return when (parser.currentToken()) {
    JsonToken.VALUE_STRING -> readStringOrNull(parser)
    JsonToken.START_ARRAY -> readRolloutContentArrayPreview(parser)
    else -> {
      parser.skipChildren()
      null
    }
  }
}

private fun readRolloutContentArrayPreview(parser: JsonParser): String? {
  var firstText: String? = null
  while (true) {
    val token = parser.nextToken() ?: return firstText
    if (token == JsonToken.END_ARRAY) return firstText
    if (token != JsonToken.START_OBJECT) {
      parser.skipChildren()
      continue
    }
    var itemType: String? = null
    var itemText: String? = null
    forEachObjectField(parser) { fieldName ->
      when (fieldName) {
        "type" -> itemType = readStringOrNull(parser)
        "text" -> itemText = readStringOrNull(parser)
        else -> parser.skipChildren()
      }
      true
    }
    if (firstText == null && itemType == "text" && !itemText.isNullOrBlank()) {
      firstText = itemText
    }
  }
}

private fun parseRolloutSource(parser: JsonParser): ParsedRolloutSource {
  return when (parser.currentToken()) {
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
  return when (parser.currentToken()) {
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
  if (parser.currentToken() != JsonToken.START_OBJECT) {
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

private fun isPlanItemType(value: String?): Boolean {
  return normalizeToken(value) == "plan"
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

private fun outlineToolTitle(event: RolloutEvent): String {
  readCommandText(event.payloadArguments)?.let(::compactOutlineCommand)?.let { command ->
    return command
  }
  event.payloadName?.trim()?.takeIf { it.isNotEmpty() }?.let { toolName -> return toolName }
  return when (normalizeToken(event.payloadType)) {
    "execcommandbegin" -> "exec"
    "patchapplybegin" -> "apply patch"
    else -> "Tool call"
  }
}

private fun outlineToolCallPreview(event: RolloutEvent): String? {
  if (readCommandText(event.payloadArguments) != null) {
    return null
  }
  return event.payloadArguments
}

private fun outlineToolResultTitle(event: RolloutEvent): String {
  toolResultExitCode(event.payloadOutput)?.let { exitCode -> return "Exit $exitCode" }
  return when (normalizeToken(event.payloadType)) {
    "execcommandend" -> "exec finished"
    "patchapplyend" -> "apply patch finished"
    else -> "Tool result"
  }
}

private fun outlineCallKey(event: RolloutEvent): String? {
  return event.payloadCallId?.trim()?.takeIf { it.isNotEmpty() }
}

private fun outlinePhaseTitle(preview: String?): String? {
  return agentSessionOutlinePhaseTitle(preview)
}

private fun isUsefulOutlineUserPrompt(preview: String?): Boolean {
  val normalizedPreview = normalizeOutlinePreview(preview) ?: return false
  return !isSessionPrefix(normalizedPreview)
}

private fun dedupeOutlineText(value: String): String {
  return dedupeAgentSessionOutlineText(value)
}

private fun summarizeOutlineChildren(children: List<AgentSessionOutlineItem>): String? {
  return summarizeAgentSessionOutlineChildren(children)
}

private fun compactOutlineCommand(command: String): String? {
  return compactAgentSessionOutlineText(command, maxLength = 100)
}

private fun toolResultExitCode(output: String?): String? {
  val text = output ?: return null
  return TOOL_RESULT_EXIT_CODE.find(text)?.groupValues?.getOrNull(1)
}

private fun normalizeOutlinePreview(value: String?): String? {
  return normalizeAgentSessionOutlinePreview(value)
}

private fun maxTimestamp(current: Long, candidate: Long?): Long {
  if (candidate == null) return current
  return if (candidate > current) candidate else current
}

private val TOOL_RESULT_EXIT_CODE = Regex("(?:Process exited with code|Exit code:)\\s*(-?\\d+)", RegexOption.IGNORE_CASE)
