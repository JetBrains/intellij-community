// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.rollout

import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.CodexThreadActivityProjection
import com.intellij.agent.workbench.codex.common.CodexThreadActivitySignal
import com.intellij.agent.workbench.codex.common.CodexThreadSourceKind
import com.intellij.agent.workbench.codex.common.CodexThreadStatusKind
import com.intellij.agent.workbench.codex.common.forEachObjectField
import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.common.readLongOrNull
import com.intellij.agent.workbench.codex.common.readStringOrNull
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.isResponseRequired
import com.intellij.agent.workbench.codex.sessions.backend.toCodexSessionActivity
import com.intellij.agent.workbench.json.WorkbenchJsonlScanner
import com.intellij.agent.workbench.json.createJsonParser
import com.intellij.agent.workbench.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.InvalidPathException
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
        usageSnapshots = listOfNotNull(state.usageSnapshot),
        hasExplicitTitle = !usedFallbackTitle,
      ),
    )
  }

}

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
          parseState.activityProjection.apply(CodexThreadActivitySignal.ClearToolCall(callId = event.payloadCallId, turnId = event.payloadTurnId))
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
  parseState.activityProjection.apply(CodexThreadActivitySignal.ClearPendingApproval(callId = event.payloadCallId, turnId = event.payloadTurnId))
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
}

private fun reduceResponseFunctionCallOutput(parseState: RolloutParseState, event: RolloutEvent, eventTimestamp: Long?) {
  parseState.activityProjection.apply(CodexThreadActivitySignal.ClearPendingUserInput(event.payloadCallId))
  parseState.activityProjection.apply(CodexThreadActivitySignal.ClearPendingApproval(callId = event.payloadCallId, turnId = event.payloadTurnId))
  parseState.markProjectFilesChangedForCompletedFunctionCall(eventTimestamp = eventTimestamp, callId = event.payloadCallId)
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
    var payloadName: String? = null
    var payloadArguments: String? = null
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
                "name" -> payloadName = readStringOrNull(parser)
                "arguments" -> payloadArguments = readStringOrNull(parser)
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
      payloadName = payloadName,
      payloadArguments = payloadArguments,
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
  @JvmField val payloadName: String?,
  @JvmField val payloadArguments: String?,
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
  @JvmField var nextSyntheticPendingFunctionCallId: Int = 0,
)

private data class PendingFunctionCall(
  @JvmField val updatedAt: Long,
  @JvmField val turnId: String?,
  @JvmField val projectMutating: Boolean,
  @JvmField val changedProjectFilePaths: Set<String>?,
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
    )
  }
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

private fun maxTimestamp(current: Long, candidate: Long?): Long {
  if (candidate == null) return current
  return if (candidate > current) candidate else current
}
