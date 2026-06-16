// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.common

// @spec community/plugins/agent-workbench/spec/chat/agent-chat-structure-view.spec.md

import com.intellij.agent.workbench.common.session.AgentSessionOutlineItem
import com.intellij.agent.workbench.common.session.AgentSessionOutlineItemKind
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThreadOutline
import com.intellij.agent.workbench.common.session.agentSessionOutlinePhaseTitle
import com.intellij.agent.workbench.common.session.normalizeAgentSessionOutlinePreview
import com.intellij.agent.workbench.common.session.summarizeAgentSessionOutlineChildren
import com.intellij.agent.workbench.json.createJsonParser
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import com.intellij.agent.workbench.json.WorkbenchJsonlScanner
import com.intellij.agent.workbench.json.forEachJsonObjectField
import com.intellij.agent.workbench.json.readJsonStringOrNull
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.invariantSeparatorsPathString

private const val CLAUDE_PROJECTS_DIR = "projects"

// Claude transcript parsing only reports provider work state.
// Unread is derived later in ClaudeSessionSource from local read tracking.
enum class ClaudeSessionActivity {
  PROCESSING,
  NEEDS_INPUT,
  READY,
}

enum class ClaudeSessionTitleSource {
  EXPLICIT,
  AI_TITLE,
  FIRST_PROMPT,
  LAST_PROMPT,
  DEFAULT,
}

data class ClaudeSessionIndexEntry(
  @JvmField val sessionId: String,
  @JvmField val summary: String? = null,
  @JvmField val firstPrompt: String? = null,
  @JvmField val gitBranch: String? = null,
  @JvmField val isSidechain: Boolean = false,
)

data class ClaudeSessionThread(
  @JvmField val id: String,
  @JvmField val title: String,
  @JvmField val updatedAt: Long,
  @JvmField val projectFilesChangedAt: Long = Long.MIN_VALUE,
  @JvmField val projectFileChangeEvidence: List<ClaudeProjectFileChangeEvidence> = emptyList(),
  @JvmField val gitBranch: String? = null,
  @JvmField val activity: ClaudeSessionActivity = ClaudeSessionActivity.READY,
  @JvmField val awaitingAssistantTurn: Boolean = false,
  @JvmField val hasCustomTitle: Boolean = false,
  @JvmField val titleSource: ClaudeSessionTitleSource = ClaudeSessionTitleSource.DEFAULT,
  @JvmField val projectPath: String? = null,
)

typealias ClaudeSessionOutline = AgentSessionThreadOutline
typealias ClaudeSessionOutlineItem = AgentSessionOutlineItem
typealias ClaudeSessionOutlineItemKind = AgentSessionOutlineItemKind

data class ClaudeProjectFileChangeEvidence(
  @JvmField val timestampMillis: Long,
  @JvmField val changedProjectFilePaths: Set<String>?,
)

data class ClaudeSessionUsageFile(
  @JvmField val sessionId: String,
  @JvmField val projectPath: String? = null,
  @JvmField val usageSnapshots: List<ClaudeUsageSnapshot> = emptyList(),
)

class ClaudeSessionsStore(
  private val claudeHomeProvider: () -> Path = { Path.of(System.getProperty("user.home"), ".claude") },
) {
  private val jsonFactory = JsonFactory()

  fun findMatchingDirectories(projectPath: String): Set<Path> {
    val normalizedProjectPath = normalizePath(projectPath) ?: return emptySet()
    val projectsRoot = claudeHomeProvider().resolve(CLAUDE_PROJECTS_DIR)
    if (!Files.isDirectory(projectsRoot)) return emptySet()

    val matchedDirectories = LinkedHashSet<Path>()
    for (candidatePath in projectPathCandidates(normalizedProjectPath)) {
      val encodedDirectory = projectsRoot.resolve(encodeProjectPath(candidatePath))
      if (Files.isDirectory(encodedDirectory)) {
        matchedDirectories.add(encodedDirectory)
      }
    }

    return matchedDirectories
  }

  fun parseJsonlFile(path: Path): ClaudeSessionThread? {
    val headState = scanJsonlEvents(path)

    if (headState.isSidechain) return null
    if (!headState.hasConversationSignal) return null
    val normalizedSessionId = headState.sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    val tailState = scanJsonlTail(path)
    val activityState: ActivityTrackingState = if (tailState.hasActivitySignal) tailState else headState
    val activity = deriveActivity(activityState.needsInput, activityState.isProcessing)
    val updatedAt = listOfNotNull(headState.updatedAt, tailState.updatedAt, activityState.updatedAt).maxOrNull()
    val projectPath = headState.projectPath ?: tailState.projectPath
    val recoveredProjectFileChangeEvidence = scanProjectFileChangeEvidenceForCompletedTools(
      path = path,
      completedToolUseIdsById = tailState.unmatchedCompletedToolUseIdsById,
    )
    val projectFileChangeEvidence = mergeProjectFileChangeEvidence(
      headState.projectFileChangeEvidence,
      tailState.projectFileChangeEvidence,
      recoveredProjectFileChangeEvidence,
    )
    val projectFilesChangedAt = maxOf(
      headState.projectFilesChangedAt,
      tailState.projectFilesChangedAt,
      projectFileChangeEvidence.maxOfOrNull { it.timestampMillis } ?: Long.MIN_VALUE,
    )

    val resolvedTitle = resolveThreadTitle(
      agentName = tailState.agentName,
      customTitle = tailState.customTitle,
      aiTitle = tailState.aiTitle,
      firstPrompt = headState.firstPrompt,
      lastPrompt = tailState.lastPrompt,
      sessionId = normalizedSessionId,
    )
    val resolvedUpdatedAt = updatedAt
                            ?: try {
                              Files.getLastModifiedTime(path).toMillis()
                            }
                            catch (_: Throwable) {
                              0L
                            }

    return ClaudeSessionThread(
      id = normalizedSessionId,
      title = resolvedTitle.title,
      updatedAt = resolvedUpdatedAt,
      projectFilesChangedAt = projectFilesChangedAt,
      projectFileChangeEvidence = projectFileChangeEvidence,
      gitBranch = headState.gitBranch,
      activity = activity,
      awaitingAssistantTurn = activityState.awaitingAssistantTurn,
      hasCustomTitle = tailState.agentName != null || tailState.customTitle != null,
      titleSource = resolvedTitle.source,
      projectPath = projectPath,
    )
  }

  fun parseOutlineJsonlFile(path: Path): ClaudeSessionOutline? {
    val state = try {
      WorkbenchJsonlScanner.scanJsonObjects(
        path = path,
        jsonFactory = jsonFactory,
        newState = ::JsonlOutlineScanState,
      ) { parser, outlineState ->
        val lineData = parseJsonlLine(parser) ?: return@scanJsonObjects true
        if (lineData.isSidechain) {
          outlineState.isSidechain = true
          return@scanJsonObjects false
        }
        if (outlineState.sessionId == null && !lineData.sessionId.isNullOrBlank()) {
          outlineState.sessionId = lineData.sessionId
        }
        if (outlineState.firstPrompt == null && !lineData.firstPrompt.isNullOrBlank()) {
          outlineState.firstPrompt = lineData.firstPrompt
        }
        if (!lineData.agentName.isNullOrBlank()) {
          outlineState.agentName = lineData.agentName
        }
        if (!lineData.customTitle.isNullOrBlank()) {
          outlineState.customTitle = lineData.customTitle
        }
        if (!lineData.aiTitle.isNullOrBlank()) {
          outlineState.aiTitle = lineData.aiTitle
        }
        if (!lineData.lastPrompt.isNullOrBlank()) {
          outlineState.lastPrompt = lineData.lastPrompt
        }
        lineData.timestampMillis?.let { timestamp -> outlineState.updatedAt = maxOf(outlineState.updatedAt ?: 0L, timestamp) }
        outlineState.record(lineData)
        true
      }
    }
    catch (_: Throwable) {
      return null
    }
    if (state.isSidechain) return null
    val sessionId = state.sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val title = resolveThreadTitle(
      agentName = state.agentName,
      customTitle = state.customTitle,
      aiTitle = state.aiTitle,
      firstPrompt = state.firstPrompt,
      lastPrompt = state.lastPrompt,
      sessionId = sessionId,
    ).title
    val updatedAt = state.updatedAt ?: runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
    return ClaudeSessionOutline(
      provider = AgentSessionProvider.CLAUDE,
      threadId = sessionId,
      title = title,
      updatedAt = updatedAt,
      items = state.buildItems(),
    )
  }

  fun parseUsageJsonlFile(path: Path): ClaudeSessionUsageFile? {
    val usageState = WorkbenchJsonlScanner.scanJsonObjects(
      path = path,
      jsonFactory = jsonFactory,
      newState = ::JsonlUsageScanState,
    ) { parser, state ->
      val lineData = parseJsonlLine(parser) ?: return@scanJsonObjects true
      if (state.sessionId == null && !lineData.sessionId.isNullOrBlank()) {
        state.sessionId = lineData.sessionId
      }
      if (state.projectPath == null && !lineData.projectPath.isNullOrBlank()) {
        state.projectPath = lineData.projectPath
      }
      lineData.assistantUsage?.let(state::recordUsage)
      true
    }

    val normalizedSessionId = usageState.sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val usageSnapshots = usageState.buildUsageSnapshots()
    if (usageSnapshots.isEmpty()) return null

    return ClaudeSessionUsageFile(
      sessionId = normalizedSessionId,
      projectPath = usageState.projectPath,
      usageSnapshots = usageSnapshots,
    )
  }

  fun parseSessionsIndex(path: Path): Map<String, ClaudeSessionIndexEntry> {
    if (!Files.isRegularFile(path)) {
      return emptyMap()
    }

    return try {
      Files.newBufferedReader(path).use { reader ->
        jsonFactory.createJsonParser(reader).use { parser ->
          if (parser.nextToken() != JsonToken.START_OBJECT) {
            emptyMap()
          }
          else {
            val entries = LinkedHashMap<String, ClaudeSessionIndexEntry>()
            forEachJsonObjectField(parser) { fieldName ->
              when (fieldName) {
                "entries" -> {
                  if (parser.currentToken() == JsonToken.START_ARRAY) {
                    parseIndexEntries(parser, entries)
                  }
                  else {
                    parser.skipChildren()
                  }
                }

                else -> parser.skipChildren()
              }
              true
            }
            entries
          }
        }
      }
    }
    catch (_: Throwable) {
      emptyMap()
    }
  }

  private fun scanJsonlTail(path: Path): JsonlTailScanState {
    return WorkbenchJsonlScanner.scanTailLines(
      path = path,
      jsonFactory = jsonFactory,
      newState = ::JsonlTailScanState,
    ) { parser, state ->
      val lineData = parseJsonlLine(parser) ?: return@scanTailLines true
      if (!lineData.agentName.isNullOrBlank()) {
        state.agentName = lineData.agentName
      }
      if (!lineData.customTitle.isNullOrBlank()) {
        state.customTitle = lineData.customTitle
      }
      if (!lineData.aiTitle.isNullOrBlank()) {
        state.aiTitle = lineData.aiTitle
      }
      if (!lineData.lastPrompt.isNullOrBlank()) {
        state.lastPrompt = lineData.lastPrompt
      }
      if (state.projectPath == null && !lineData.projectPath.isNullOrBlank()) {
        state.projectPath = lineData.projectPath
      }
      updateActivityFields(state, lineData)
      updateProjectFileChangeFields(state, lineData)
      true
    }
  }

  private fun scanJsonlEvents(path: Path): JsonlMetadataScanState {
    return WorkbenchJsonlScanner.scanJsonObjects(
      path = path,
      jsonFactory = jsonFactory,
      newState = ::JsonlMetadataScanState,
    ) { parser, scanState ->
      val lineData = parseJsonlLine(parser) ?: return@scanJsonObjects true
      if (lineData.isSidechain) {
        scanState.isSidechain = true
        return@scanJsonObjects false
      }
      if (scanState.sessionId == null && !lineData.sessionId.isNullOrBlank()) {
        scanState.sessionId = lineData.sessionId
      }
      if (scanState.firstPrompt == null && !lineData.firstPrompt.isNullOrBlank()) {
        scanState.firstPrompt = lineData.firstPrompt
      }
      if (scanState.gitBranch == null && !lineData.gitBranch.isNullOrBlank()) {
        scanState.gitBranch = lineData.gitBranch
      }
      if (scanState.projectPath == null && !lineData.projectPath.isNullOrBlank()) {
        scanState.projectPath = lineData.projectPath
      }
      if (lineData.hasConversationSignal) {
        scanState.hasConversationSignal = true
      }
      updateActivityFields(scanState, lineData)
      updateProjectFileChangeFields(scanState, lineData)
      // Title metadata lives near the tail; early-exit once the head metadata is settled.
      !(scanState.sessionId != null && scanState.hasConversationSignal && scanState.firstPrompt != null)
    }
  }

  private fun scanProjectFileChangeEvidenceForCompletedTools(
    path: Path,
    completedToolUseIdsById: Map<String, Long>,
  ): List<ClaudeProjectFileChangeEvidence> {
    if (completedToolUseIdsById.isEmpty()) {
      return emptyList()
    }
    val unresolvedCompletedToolUseIds = LinkedHashMap(completedToolUseIdsById)
    val projectFileChangeEvidence = ArrayList<ClaudeProjectFileChangeEvidence>()
    try {
      WorkbenchJsonlScanner.scanJsonObjects(
        path = path,
        jsonFactory = jsonFactory,
        newState = {},
      ) { parser, _ ->
        val lineData = parseJsonlLine(parser) ?: return@scanJsonObjects true
        for ((toolUseId, changedProjectFilePaths) in lineData.projectMutatingToolUsesById) {
          val completedAt = unresolvedCompletedToolUseIds[toolUseId] ?: continue
          val toolUseAt = lineData.timestampMillis
          if (toolUseAt == null || completedAt == Long.MIN_VALUE || toolUseAt <= completedAt) {
            recordProjectFileChangeEvidence(
              target = projectFileChangeEvidence,
              timestampMillis = completedAt,
              changedProjectFilePaths = changedProjectFilePaths,
            )
            unresolvedCompletedToolUseIds.remove(toolUseId)
          }
        }
        unresolvedCompletedToolUseIds.isNotEmpty()
      }
    }
    catch (_: Throwable) {
      return emptyList()
    }
    return projectFileChangeEvidence
  }

}

private fun parseIndexEntries(parser: JsonParser, entries: MutableMap<String, ClaudeSessionIndexEntry>) {
  while (true) {
    val token = parser.nextToken() ?: return
    if (token == JsonToken.END_ARRAY) {
      return
    }
    if (token != JsonToken.START_OBJECT) {
      parser.skipChildren()
      continue
    }
    parseIndexEntry(parser)?.let { entry ->
      entries[entry.sessionId] = entry
    }
  }
}

private fun parseIndexEntry(parser: JsonParser): ClaudeSessionIndexEntry? {
  var sessionId: String? = null
  var summary: String? = null
  var firstPrompt: String? = null
  var gitBranch: String? = null
  var isSidechain = false

  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "sessionId" -> sessionId = readJsonStringOrNull(parser)
      "summary" -> summary = readJsonStringOrNull(parser)
      "firstPrompt" -> firstPrompt = readJsonStringOrNull(parser)
      "gitBranch" -> gitBranch = readJsonStringOrNull(parser)
      "isSidechain" -> isSidechain = readBooleanOrFalse(parser)
      else -> parser.skipChildren()
    }
    true
  }

  if (isSidechain) {
    return null
  }

  val normalizedSessionId = sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val normalizedEntry = ClaudeSessionIndexEntry(
    sessionId = normalizedSessionId,
    summary = normalizeClaudeTitleCandidate(summary),
    firstPrompt = normalizeClaudeTitleCandidate(firstPrompt),
    gitBranch = normalizeNonBlank(gitBranch),
    isSidechain = false,
  )
  if (normalizedEntry.summary == null && normalizedEntry.firstPrompt == null && normalizedEntry.gitBranch == null) {
    return null
  }
  return normalizedEntry
}

private fun deriveActivity(needsInput: Boolean, isProcessing: Boolean): ClaudeSessionActivity {
  return when {
    needsInput -> ClaudeSessionActivity.NEEDS_INPUT
    isProcessing -> ClaudeSessionActivity.PROCESSING
    else -> ClaudeSessionActivity.READY
  }
}

private fun parseJsonlLine(parser: JsonParser): ParsedJsonlLine? {
  return try {
    if (parser.currentToken() != JsonToken.START_OBJECT) return null
    var sessionId: String? = null
    var requestId: String? = null
    var isSidechain = false
    var timestampMillis: Long? = null
    var firstPrompt: String? = null
    var agentName: String? = null
    var customTitle: String? = null
    var aiTitle: String? = null
    var lastPrompt: String? = null
    var type: String? = null
    var projectPath: String? = null
    var gitBranch: String? = null
    var messageRole: String? = null
    var messageContent: String? = null
    var messageHasToolUse = false
    var messageNeedsInputToolUse = false
    var messageHasToolResult = false
    var messageProjectMutatingToolUsesById: Map<String, Set<String>?> = emptyMap()
    var messageCompletedToolUseIds: Set<String> = emptySet()
    var messageStopReason: String? = null
    var messageHasStopReason = false
    var messageModelId: String? = null
    var messageId: String? = null
    var messageUsage: ParsedClaudeUsage? = null
    var hasBackgroundTaskId = false

    forEachJsonObjectField(parser) { fieldName ->
      when (fieldName) {
        "sessionId" -> sessionId = readJsonStringOrNull(parser)
        "requestId" -> requestId = readJsonStringOrNull(parser)
        "isSidechain" -> isSidechain = readBooleanOrFalse(parser)
        "timestamp" -> timestampMillis = parseIsoTimestamp(readJsonStringOrNull(parser))
        "type" -> type = readJsonStringOrNull(parser)
        "cwd" -> projectPath = normalizePath(readJsonStringOrNull(parser))
        "agentName" -> agentName = readJsonStringOrNull(parser)
        "customTitle" -> customTitle = readJsonStringOrNull(parser)
        "aiTitle" -> aiTitle = readJsonStringOrNull(parser)
        "lastPrompt" -> lastPrompt = readJsonStringOrNull(parser)
        "gitBranch" -> gitBranch = readJsonStringOrNull(parser)
        "message" -> {
          if (parser.currentToken() == JsonToken.START_OBJECT) {
            val parsedMessage = readMessageObject(parser)
            messageRole = parsedMessage.role
            messageContent = parsedMessage.contentPreview
            messageHasToolUse = parsedMessage.hasToolUse
            messageNeedsInputToolUse = parsedMessage.needsInputToolUse
            messageHasToolResult = parsedMessage.hasToolResult
            messageProjectMutatingToolUsesById = parsedMessage.projectMutatingToolUsesById
            messageCompletedToolUseIds = parsedMessage.completedToolUseIds
            messageStopReason = parsedMessage.stopReason
            messageHasStopReason = parsedMessage.hasStopReason
            messageModelId = parsedMessage.modelId
            messageId = parsedMessage.messageId
            messageUsage = parsedMessage.usage
          }
          else {
            parser.skipChildren()
          }
        }
        "toolUseResult" -> hasBackgroundTaskId = readToolUseResultObject(parser)
        else -> parser.skipChildren()
      }
      true
    }
    val isTaskNotification = type == "user" && messageRole == "user" && messageContent?.contains("<task-notification>") == true
    val activityEvent = when (type) {
      "user" -> when {
        messageRole != "user" -> ClaudeActivityEvent.OTHER
        messageHasToolResult || hasBackgroundTaskId || isTaskNotification -> ClaudeActivityEvent.TOOL_CONTINUATION
        else -> ClaudeActivityEvent.USER_PROMPT
      }
      "assistant" -> when {
        messageRole != "assistant" -> ClaudeActivityEvent.OTHER
        messageNeedsInputToolUse -> ClaudeActivityEvent.ASSISTANT_NEEDS_INPUT
        messageHasToolUse -> ClaudeActivityEvent.ASSISTANT_IN_PROGRESS
        messageHasStopReason -> activityEventForAssistantStopReason(messageStopReason)
        else -> ClaudeActivityEvent.ASSISTANT_TERMINAL
      }
      "progress" -> ClaudeActivityEvent.PROGRESS
      "queue-operation" -> ClaudeActivityEvent.QUEUE_OPERATION
      else -> ClaudeActivityEvent.OTHER
    }
    if (activityEvent == ClaudeActivityEvent.USER_PROMPT) {
      firstPrompt = normalizeClaudeTitleCandidate(messageContent)
    }
    val assistantUsage = if (type == "assistant" && messageRole == "assistant") {
      messageUsage?.let { usage ->
        ClaudeAssistantUsage(
          dedupeKey = normalizeNonBlank(requestId) ?: normalizeNonBlank(messageId),
          modelId = normalizeNonBlank(messageModelId),
          inputTokens = usage.inputTokens,
          outputTokens = usage.outputTokens,
          cacheReadTokens = usage.cacheReadTokens,
          cacheWriteTokens = usage.cacheWriteTokens,
          cacheWrite5mTokens = usage.cacheWrite5mTokens,
          cacheWrite1hTokens = usage.cacheWrite1hTokens,
        )
      }
    }
    else {
      null
    }
    val hasConversationSignal = type == "user" || type == "assistant"
    return ParsedJsonlLine(
      sessionId = sessionId,
      isSidechain = isSidechain,
      timestampMillis = timestampMillis,
      firstPrompt = firstPrompt,
      agentName = normalizeClaudeTitleCandidate(agentName),
      customTitle = if (type == "custom-title") normalizeClaudeTitleCandidate(customTitle) else null,
      aiTitle = if (type == "ai-title") normalizeClaudeTitleCandidate(aiTitle) else null,
      lastPrompt = if (type == "last-prompt") normalizeClaudeTitleCandidate(lastPrompt) else null,
      hasConversationSignal = hasConversationSignal,
      projectPath = projectPath,
      gitBranch = normalizeNonBlank(gitBranch),
      activityEvent = activityEvent,
      assistantUsage = assistantUsage,
      projectMutatingToolUsesById = messageProjectMutatingToolUsesById,
      completedToolUseIds = messageCompletedToolUseIds,
      messageRole = messageRole,
      messageContent = messageContent,
      messageHasToolUse = messageHasToolUse,
      messageNeedsInputToolUse = messageNeedsInputToolUse,
      messageHasToolResult = messageHasToolResult,
    )
  }
  catch (_: Throwable) {
    null
  }
}

private fun readMessageObject(parser: JsonParser): ParsedMessageObject {
  var role: String? = null
  var contentPreview: String? = null
  var hasToolUse = false
  var needsInputToolUse = false
  var hasToolResult = false
  var projectMutatingToolUsesById: Map<String, Set<String>?> = emptyMap()
  var completedToolUseIds: Set<String> = emptySet()
  var stopReason: String? = null
  var hasStopReason = false
  var modelId: String? = null
  var messageId: String? = null
  var usage: ParsedClaudeUsage? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "model" -> modelId = readJsonStringOrNull(parser)
      "id" -> messageId = readJsonStringOrNull(parser)
      "role" -> role = readJsonStringOrNull(parser)
      "content" -> {
        val result = readContentWithToolUseCheck(parser)
        contentPreview = result.contentPreview
        hasToolUse = result.hasToolUse
        needsInputToolUse = result.needsInputToolUse
        hasToolResult = result.hasToolResult
        projectMutatingToolUsesById = result.projectMutatingToolUsesById
        completedToolUseIds = result.completedToolUseIds
      }
      "stop_reason" -> {
        hasStopReason = true
        stopReason = readJsonStringOrNull(parser)
      }
      "usage" -> usage = readUsageObject(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return ParsedMessageObject(
    modelId = modelId,
    messageId = messageId,
    role = role,
    contentPreview = contentPreview,
    hasToolUse = hasToolUse,
    needsInputToolUse = needsInputToolUse,
    hasToolResult = hasToolResult,
    projectMutatingToolUsesById = projectMutatingToolUsesById,
    completedToolUseIds = completedToolUseIds,
    stopReason = stopReason,
    hasStopReason = hasStopReason,
    usage = usage,
  )
}

private fun readUsageObject(parser: JsonParser): ParsedClaudeUsage? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var sawUsageField = false
  var inputTokens = 0L
  var cacheWriteTokens = 0L
  var cacheWrite5mTokens = 0L
  var cacheWrite1hTokens = 0L
  var cacheReadTokens = 0L
  var outputTokens = 0L
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "input_tokens" -> {
        inputTokens = readLongOrZero(parser)
        sawUsageField = true
      }
      "cache_creation_input_tokens" -> {
        cacheWriteTokens = readLongOrZero(parser)
        sawUsageField = true
      }
      "cache_creation" -> {
        if (parser.currentToken() == JsonToken.START_OBJECT) {
          forEachJsonObjectField(parser) { nestedField ->
            when (nestedField) {
              "ephemeral_5m_input_tokens" -> {
                cacheWrite5mTokens = readLongOrZero(parser)
                sawUsageField = true
              }
              "ephemeral_1h_input_tokens" -> {
                cacheWrite1hTokens = readLongOrZero(parser)
                sawUsageField = true
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
      "cache_read_input_tokens" -> {
        cacheReadTokens = readLongOrZero(parser)
        sawUsageField = true
      }
      "output_tokens" -> {
        outputTokens = readLongOrZero(parser)
        sawUsageField = true
      }
      else -> parser.skipChildren()
    }
    true
  }

  return if (sawUsageField) {
    val resolvedCacheWrite5mTokens: Long
    val resolvedCacheWrite1hTokens: Long
    if (cacheWrite5mTokens == 0L && cacheWrite1hTokens == 0L) {
      resolvedCacheWrite5mTokens = cacheWriteTokens
      resolvedCacheWrite1hTokens = 0L
    }
    else {
      resolvedCacheWrite5mTokens = cacheWrite5mTokens
      resolvedCacheWrite1hTokens = cacheWrite1hTokens
    }
    ParsedClaudeUsage(
      inputTokens = inputTokens,
      outputTokens = outputTokens,
      cacheReadTokens = cacheReadTokens,
      cacheWriteTokens = cacheWriteTokens,
      cacheWrite5mTokens = resolvedCacheWrite5mTokens,
      cacheWrite1hTokens = resolvedCacheWrite1hTokens,
    )
  }
  else {
    null
  }
}

private fun readContentWithToolUseCheck(parser: JsonParser): ParsedMessageContent {
  return when (parser.currentToken()) {
    JsonToken.VALUE_STRING -> ParsedMessageContent(contentPreview = readJsonStringOrNull(parser))
    JsonToken.START_ARRAY -> readFirstTextAndToolUseFromArray(parser)
    else -> {
      parser.skipChildren()
      ParsedMessageContent()
    }
  }
}

private fun readFirstTextAndToolUseFromArray(parser: JsonParser): ParsedMessageContent {
  var firstText: String? = null
  var hasToolUse = false
  var needsInputToolUse = false
  var hasToolResult = false
  val projectMutatingToolUsesById = LinkedHashMap<String, Set<String>?>()
  val completedToolUseIds = LinkedHashSet<String>()
  while (true) {
    val token = parser.nextToken() ?: return ParsedMessageContent(
      contentPreview = firstText,
      hasToolUse = hasToolUse,
      needsInputToolUse = needsInputToolUse,
      hasToolResult = hasToolResult,
      projectMutatingToolUsesById = projectMutatingToolUsesById,
      completedToolUseIds = completedToolUseIds,
    )
    if (token == JsonToken.END_ARRAY) return ParsedMessageContent(
      contentPreview = firstText,
      hasToolUse = hasToolUse,
      needsInputToolUse = needsInputToolUse,
      hasToolResult = hasToolResult,
      projectMutatingToolUsesById = projectMutatingToolUsesById,
      completedToolUseIds = completedToolUseIds,
    )
    if (token != JsonToken.START_OBJECT) {
      parser.skipChildren()
      continue
    }
    var itemType: String? = null
    var itemText: String? = null
    var itemId: String? = null
    var itemName: String? = null
    var itemToolUseId: String? = null
    var itemChangedProjectFilePaths: Set<String> = emptySet()
    forEachJsonObjectField(parser) { fieldName ->
      when (fieldName) {
        "type" -> itemType = readJsonStringOrNull(parser)
        "id" -> itemId = readJsonStringOrNull(parser)
        "name" -> itemName = readJsonStringOrNull(parser)
        "tool_use_id" -> itemToolUseId = readJsonStringOrNull(parser)
        "text" -> itemText = readJsonStringOrNull(parser)
        "input" -> itemChangedProjectFilePaths = readToolInputProjectFilePaths(parser)
        else -> parser.skipChildren()
      }
      true
    }
    if (itemType == "tool_use") {
      hasToolUse = true
      if (isUserInteractionToolName(itemName)) {
        needsInputToolUse = true
      }
      if (isProjectMutatingToolName(itemName)) {
        normalizeToolUseId(itemId)?.let { toolUseId ->
          projectMutatingToolUsesById[toolUseId] = preciseProjectFilePathsForToolUse(
            toolName = itemName,
            toolInputProjectFilePaths = itemChangedProjectFilePaths,
          )
        }
      }
    }
    if (itemType == "tool_result") {
      hasToolResult = true
      normalizeToolUseId(itemToolUseId)?.let(completedToolUseIds::add)
    }
    if (firstText == null && itemType == "text" && !itemText.isNullOrBlank()) {
      firstText = itemText
    }
  }
}

private fun normalizeToolUseId(value: String?): String? {
  return value?.trim()?.takeIf { it.isNotEmpty() }
}

private fun activityEventForAssistantStopReason(stopReason: String?): ClaudeActivityEvent {
  return when (stopReason) {
    null, "tool_use", "pause_turn" -> ClaudeActivityEvent.ASSISTANT_IN_PROGRESS
    else -> ClaudeActivityEvent.ASSISTANT_TERMINAL
  }
}

private fun isUserInteractionToolName(toolName: String?): Boolean {
  return when (toolName?.trim()) {
    "AskUserQuestion", "ExitPlanMode" -> true
    else -> false
  }
}

private fun isProjectMutatingToolName(toolName: String?): Boolean {
  return when (toolName?.trim()?.lowercase()) {
    "bash", "edit", "multiedit", "write", "notebookedit" -> true
    else -> false
  }
}

private fun preciseProjectFilePathsForToolUse(toolName: String?, toolInputProjectFilePaths: Set<String>): Set<String>? {
  if (toolName?.trim()?.lowercase() == "bash") {
    return null
  }
  return toolInputProjectFilePaths.takeIf { it.isNotEmpty() }
}

private fun readToolInputProjectFilePaths(parser: JsonParser): Set<String> {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return emptySet()
  }

  val paths = LinkedHashSet<String>()
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "file_path", "notebook_path" -> readJsonStringOrNull(parser)?.trim()?.takeIf { it.isNotEmpty() }?.let(paths::add)
      else -> parser.skipChildren()
    }
    true
  }
  return paths
}

private fun readToolUseResultObject(parser: JsonParser): Boolean {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return false
  }

  var hasBackgroundTaskId = false
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "backgroundTaskId" -> hasBackgroundTaskId = !readJsonStringOrNull(parser).isNullOrBlank()
      else -> parser.skipChildren()
    }
    true
  }
  return hasBackgroundTaskId
}

private fun resolveThreadTitle(
  agentName: String?,
  customTitle: String?,
  aiTitle: String?,
  firstPrompt: String?,
  lastPrompt: String?,
  sessionId: String,
): ResolvedClaudeThreadTitle {
  normalizeClaudeTitleCandidate(agentName)?.let { return ResolvedClaudeThreadTitle(it, ClaudeSessionTitleSource.EXPLICIT) }
  normalizeClaudeTitleCandidate(customTitle)?.let { return ResolvedClaudeThreadTitle(it, ClaudeSessionTitleSource.EXPLICIT) }
  normalizeClaudeTitleCandidate(aiTitle)?.let { return ResolvedClaudeThreadTitle(it, ClaudeSessionTitleSource.AI_TITLE) }
  normalizeClaudeTitleCandidate(firstPrompt)?.let { return ResolvedClaudeThreadTitle(it, ClaudeSessionTitleSource.FIRST_PROMPT) }
  normalizeClaudeTitleCandidate(lastPrompt)?.let { return ResolvedClaudeThreadTitle(it, ClaudeSessionTitleSource.LAST_PROMPT) }
  return ResolvedClaudeThreadTitle(defaultClaudeThreadTitle(sessionId), ClaudeSessionTitleSource.DEFAULT)
}

private fun normalizeClaudeTitleCandidate(value: String?): String? {
  val normalized = normalizeClaudeStoredThreadTitle(value) ?: return null
  return normalized.takeUnless { it.equals("No prompt", ignoreCase = true) }
}

private fun normalizeNonBlank(value: String?): String? {
  return value?.trim()?.takeIf { it.isNotEmpty() }
}

private fun outlinePhaseTitle(preview: String?): String? {
  return agentSessionOutlinePhaseTitle(preview)
}

private fun summarizeClaudeOutlineChildren(children: List<ClaudeSessionOutlineItem>): String? {
  return summarizeAgentSessionOutlineChildren(children)
}

private fun normalizeOutlinePreview(value: String?): String? {
  return normalizeAgentSessionOutlinePreview(value)
}

private fun parseIsoTimestamp(value: String?): Long? {
  val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return try {
    Instant.parse(text).toEpochMilli()
  }
  catch (_: Throwable) {
    null
  }
}

private fun encodeProjectPath(projectPath: String): String {
  return projectPath.map { char -> if (char.isLetterOrDigit()) char else '-' }.joinToString("")
}

private fun projectPathCandidates(normalizedProjectPath: String): Set<String> {
  val paths = LinkedHashSet<String>()
  paths.add(normalizedProjectPath)
  canonicalPath(normalizedProjectPath)?.let(paths::add)
  return paths
}

private fun canonicalPath(path: String): String? {
  return try {
    Path.of(path).toRealPath().invariantSeparatorsPathString
  }
  catch (_: Throwable) {
    null
  }
}

private fun normalizePath(path: String?): String? {
  val raw = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return try {
    Path.of(raw).invariantSeparatorsPathString
  }
  catch (_: Throwable) {
    raw.replace('\\', '/')
  }
}

private fun readBooleanOrFalse(parser: JsonParser): Boolean {
  return when (parser.currentToken()) {
    JsonToken.VALUE_TRUE -> true
    JsonToken.VALUE_FALSE -> false
    JsonToken.VALUE_NUMBER_INT -> parser.intValue != 0
    JsonToken.VALUE_STRING -> parser.string.equals("true", ignoreCase = true)
    else -> {
      parser.skipChildren()
      false
    }
  }
}

private fun readLongOrZero(parser: JsonParser): Long {
  return when (parser.currentToken()) {
    JsonToken.VALUE_NUMBER_INT,
    JsonToken.VALUE_NUMBER_FLOAT,
      -> parser.longValue
    JsonToken.VALUE_STRING -> parser.string.toLongOrNull() ?: 0L
    else -> {
      parser.skipChildren()
      0L
    }
  }
}

private data class ParsedJsonlLine(
  @JvmField val sessionId: String?,
  @JvmField val isSidechain: Boolean,
  @JvmField val timestampMillis: Long?,
  @JvmField val firstPrompt: String?,
  @JvmField val agentName: String?,
  @JvmField val customTitle: String?,
  @JvmField val aiTitle: String?,
  @JvmField val lastPrompt: String?,
  @JvmField val hasConversationSignal: Boolean,
  @JvmField val projectPath: String?,
  @JvmField val gitBranch: String?,
  @JvmField val activityEvent: ClaudeActivityEvent,
  @JvmField val assistantUsage: ClaudeAssistantUsage? = null,
  @JvmField val projectMutatingToolUsesById: Map<String, Set<String>?> = emptyMap(),
  @JvmField val completedToolUseIds: Set<String> = emptySet(),
  @JvmField val messageRole: String? = null,
  @JvmField val messageContent: String? = null,
  @JvmField val messageHasToolUse: Boolean = false,
  @JvmField val messageNeedsInputToolUse: Boolean = false,
  @JvmField val messageHasToolResult: Boolean = false,
)

private data class JsonlOutlineScanState(
  @JvmField var sessionId: String? = null,
  @JvmField var firstPrompt: String? = null,
  @JvmField var agentName: String? = null,
  @JvmField var customTitle: String? = null,
  @JvmField var aiTitle: String? = null,
  @JvmField var lastPrompt: String? = null,
  @JvmField var updatedAt: Long? = null,
  @JvmField var isSidechain: Boolean = false,
  @JvmField var nextItemIndex: Int = 0,
  @JvmField val items: MutableList<ClaudeSessionOutlineItemBuilder> = ArrayList(),
  @JvmField var currentPhase: ClaudeSessionOutlineItemBuilder? = null,
) {
  fun record(lineData: ParsedJsonlLine) {
    when (lineData.activityEvent) {
      ClaudeActivityEvent.USER_PROMPT -> {
        currentPhase = null
        addRootItem(ClaudeSessionOutlineItemKind.USER_PROMPT, "My prompt", lineData.messageContent, lineData.timestampMillis)
      }
      ClaudeActivityEvent.TOOL_CONTINUATION -> addPhaseDetail(ClaudeSessionOutlineItemKind.TOOL_RESULT,
                                                              "Tool result",
                                                              lineData.messageContent,
                                                              lineData.timestampMillis)
      ClaudeActivityEvent.ASSISTANT_NEEDS_INPUT -> {
        val phase = addAssistantResponse(lineData)
        phase.kind = ClaudeSessionOutlineItemKind.AGENT_WORK
        phase.children += newItem(ClaudeSessionOutlineItemKind.INPUT_REQUEST, "Input requested", null, lineData.timestampMillis)
      }
      ClaudeActivityEvent.ASSISTANT_IN_PROGRESS -> {
        val phase = addAssistantResponse(lineData)
        if (lineData.messageHasToolUse) {
          phase.kind = ClaudeSessionOutlineItemKind.AGENT_WORK
          phase.children += newItem(ClaudeSessionOutlineItemKind.TOOL_CALL, "Tool call", null, lineData.timestampMillis)
        }
      }
      ClaudeActivityEvent.ASSISTANT_TERMINAL -> addAssistantResponse(lineData)
      ClaudeActivityEvent.PROGRESS,
      ClaudeActivityEvent.QUEUE_OPERATION,
        -> addPhaseDetail(ClaudeSessionOutlineItemKind.AGENT_WORK, "Agent work", null, lineData.timestampMillis)
      ClaudeActivityEvent.OTHER -> Unit
    }
  }

  fun buildItems(): List<ClaudeSessionOutlineItem> = items.map(ClaudeSessionOutlineItemBuilder::build)

  private fun addAssistantResponse(lineData: ParsedJsonlLine): ClaudeSessionOutlineItemBuilder {
    if (lineData.messageRole == "assistant" || !lineData.messageContent.isNullOrBlank()) {
      val phase = newItem(
        kind = ClaudeSessionOutlineItemKind.ASSISTANT_RESPONSE,
        title = outlinePhaseTitle(lineData.messageContent) ?: "Assistant response",
        preview = lineData.messageContent,
        timestampMillis = lineData.timestampMillis,
        summarizesChildren = true,
      )
      items += phase
      currentPhase = phase
      return phase
    }
    return currentPhase ?: addRootItem(ClaudeSessionOutlineItemKind.AGENT_WORK, "Agent work", null, lineData.timestampMillis)
  }

  private fun addPhaseDetail(kind: ClaudeSessionOutlineItemKind, title: String, preview: String?, timestampMillis: Long?) {
    val item = newItem(kind, title, preview, timestampMillis)
    val phase = currentPhase
    if (phase == null) {
      items += item
      return
    }
    phase.kind = ClaudeSessionOutlineItemKind.AGENT_WORK
    phase.children += item
  }

  private fun addRootItem(
    kind: ClaudeSessionOutlineItemKind,
    title: String,
    preview: String?,
    timestampMillis: Long?,
  ): ClaudeSessionOutlineItemBuilder {
    return newItem(kind, title, preview, timestampMillis).also(items::add)
  }

  private fun newItem(
    kind: ClaudeSessionOutlineItemKind,
    title: String,
    preview: String?,
    timestampMillis: Long?,
    summarizesChildren: Boolean = false,
  ): ClaudeSessionOutlineItemBuilder {
    return ClaudeSessionOutlineItemBuilder(
      id = "outline-${nextItemIndex++}",
      kind = kind,
      title = title,
      preview = normalizeOutlinePreview(preview),
      timestampMillis = timestampMillis,
      summarizesChildren = summarizesChildren,
    )
  }
}

private data class ClaudeSessionOutlineItemBuilder(
  @JvmField val id: String,
  @JvmField var kind: ClaudeSessionOutlineItemKind,
  @JvmField val title: String,
  @JvmField val preview: String?,
  @JvmField val timestampMillis: Long?,
  @JvmField val summarizesChildren: Boolean = false,
  @JvmField val children: MutableList<ClaudeSessionOutlineItemBuilder> = ArrayList(),
) {
  fun build(): ClaudeSessionOutlineItem {
    val builtChildren = children.map(ClaudeSessionOutlineItemBuilder::build)
    return ClaudeSessionOutlineItem(
      id = id,
      kind = kind,
      title = title,
      preview = if (summarizesChildren && builtChildren.isNotEmpty()) summarizeClaudeOutlineChildren(builtChildren) else preview,
      timestampMs = timestampMillis,
      children = builtChildren,
    )
  }
}

private data class ParsedMessageObject(
  @JvmField val modelId: String?,
  @JvmField val messageId: String?,
  @JvmField val role: String?,
  @JvmField val contentPreview: String?,
  @JvmField val hasToolUse: Boolean = false,
  @JvmField val needsInputToolUse: Boolean = false,
  @JvmField val hasToolResult: Boolean = false,
  @JvmField val projectMutatingToolUsesById: Map<String, Set<String>?> = emptyMap(),
  @JvmField val completedToolUseIds: Set<String> = emptySet(),
  @JvmField val stopReason: String? = null,
  @JvmField val hasStopReason: Boolean = false,
  @JvmField val usage: ParsedClaudeUsage? = null,
)

private data class ParsedClaudeUsage(
  @JvmField val inputTokens: Long,
  @JvmField val outputTokens: Long,
  @JvmField val cacheReadTokens: Long,
  @JvmField val cacheWriteTokens: Long,
  @JvmField val cacheWrite5mTokens: Long,
  @JvmField val cacheWrite1hTokens: Long,
)

private data class ClaudeAssistantUsage(
  @JvmField val dedupeKey: String?,
  @JvmField val modelId: String?,
  @JvmField val inputTokens: Long,
  @JvmField val outputTokens: Long,
  @JvmField val cacheReadTokens: Long,
  @JvmField val cacheWriteTokens: Long,
  @JvmField val cacheWrite5mTokens: Long,
  @JvmField val cacheWrite1hTokens: Long,
)

private data class ParsedMessageContent(
  @JvmField val contentPreview: String? = null,
  @JvmField val hasToolUse: Boolean = false,
  @JvmField val needsInputToolUse: Boolean = false,
  @JvmField val hasToolResult: Boolean = false,
  @JvmField val projectMutatingToolUsesById: Map<String, Set<String>?> = emptyMap(),
  @JvmField val completedToolUseIds: Set<String> = emptySet(),
)

private data class ResolvedClaudeThreadTitle(
  @JvmField val title: String,
  @JvmField val source: ClaudeSessionTitleSource,
)

private enum class ClaudeActivityEvent {
  USER_PROMPT,
  ASSISTANT_IN_PROGRESS,
  ASSISTANT_NEEDS_INPUT,
  ASSISTANT_TERMINAL,
  TOOL_CONTINUATION,
  PROGRESS,
  QUEUE_OPERATION,
  OTHER,
}

private interface ActivityTrackingState {
  var hasActivitySignal: Boolean
  var awaitingAssistantTurn: Boolean
  var needsInput: Boolean
  var isProcessing: Boolean
  var updatedAt: Long?
}

private interface ProjectFileChangeTrackingState {
  var projectFilesChangedAt: Long
  val projectFileChangeEvidence: MutableList<ClaudeProjectFileChangeEvidence>
  val pendingProjectMutatingToolUsesById: LinkedHashMap<String, Set<String>?>
  val unmatchedCompletedToolUseIdsById: LinkedHashMap<String, Long>
}

private fun updateActivityFields(state: ActivityTrackingState, lineData: ParsedJsonlLine) {
  if (lineData.activityEvent != ClaudeActivityEvent.OTHER) {
    state.hasActivitySignal = true
  }
  when (lineData.activityEvent) {
    ClaudeActivityEvent.USER_PROMPT -> {
      state.awaitingAssistantTurn = true
      state.needsInput = false
      state.isProcessing = false
    }
    ClaudeActivityEvent.ASSISTANT_NEEDS_INPUT -> {
      state.awaitingAssistantTurn = false
      state.needsInput = true
      state.isProcessing = false
    }
    ClaudeActivityEvent.ASSISTANT_IN_PROGRESS,
    ClaudeActivityEvent.TOOL_CONTINUATION,
      -> {
      state.awaitingAssistantTurn = true
      state.needsInput = false
      state.isProcessing = true
    }
    ClaudeActivityEvent.ASSISTANT_TERMINAL -> {
      state.awaitingAssistantTurn = false
      state.needsInput = false
      state.isProcessing = false
    }
    ClaudeActivityEvent.PROGRESS,
    ClaudeActivityEvent.QUEUE_OPERATION,
      -> {
      state.isProcessing = state.awaitingAssistantTurn || state.isProcessing
    }
    ClaudeActivityEvent.OTHER -> {
    }
  }
  val lineTimestamp = lineData.timestampMillis
  if (lineTimestamp != null) {
    state.updatedAt = maxOf(state.updatedAt ?: 0L, lineTimestamp)
  }
}

private fun updateProjectFileChangeFields(state: ProjectFileChangeTrackingState, lineData: ParsedJsonlLine) {
  state.pendingProjectMutatingToolUsesById.putAll(lineData.projectMutatingToolUsesById)
  for (toolUseId in lineData.completedToolUseIds) {
    if (state.pendingProjectMutatingToolUsesById.containsKey(toolUseId)) {
      val changedProjectFilePaths = state.pendingProjectMutatingToolUsesById.remove(toolUseId)
      recordProjectFileChangeEvidence(
        target = state.projectFileChangeEvidence,
        timestampMillis = lineData.timestampMillis,
        changedProjectFilePaths = changedProjectFilePaths,
      )
      state.projectFilesChangedAt = maxOf(state.projectFilesChangedAt, lineData.timestampMillis ?: Long.MIN_VALUE)
    }
    else {
      state.unmatchedCompletedToolUseIdsById.merge(toolUseId, lineData.timestampMillis ?: Long.MIN_VALUE, ::maxOf)
    }
  }
}

private fun recordProjectFileChangeEvidence(
  target: MutableList<ClaudeProjectFileChangeEvidence>,
  timestampMillis: Long?,
  changedProjectFilePaths: Set<String>?,
) {
  if (timestampMillis == null || timestampMillis == Long.MIN_VALUE) {
    return
  }
  target += ClaudeProjectFileChangeEvidence(
    timestampMillis = timestampMillis,
    changedProjectFilePaths = changedProjectFilePaths,
  )
}

private fun mergeProjectFileChangeEvidence(
  vararg lists: List<ClaudeProjectFileChangeEvidence>,
): List<ClaudeProjectFileChangeEvidence> {
  if (lists.all { it.isEmpty() }) {
    return emptyList()
  }
  return lists.asSequence()
    .flatMap { it.asSequence() }
    .sortedBy { it.timestampMillis }
    .toList()
}

private data class JsonlTailScanState(
  @JvmField var agentName: String? = null,
  @JvmField var customTitle: String? = null,
  @JvmField var aiTitle: String? = null,
  @JvmField var lastPrompt: String? = null,
  @JvmField var projectPath: String? = null,
  override var hasActivitySignal: Boolean = false,
  override var awaitingAssistantTurn: Boolean = false,
  override var needsInput: Boolean = false,
  override var isProcessing: Boolean = false,
  override var updatedAt: Long? = null,
  override var projectFilesChangedAt: Long = Long.MIN_VALUE,
  override val projectFileChangeEvidence: MutableList<ClaudeProjectFileChangeEvidence> = ArrayList(),
  override val pendingProjectMutatingToolUsesById: LinkedHashMap<String, Set<String>?> = LinkedHashMap(),
  override val unmatchedCompletedToolUseIdsById: LinkedHashMap<String, Long> = LinkedHashMap(),
) : ActivityTrackingState, ProjectFileChangeTrackingState

private data class JsonlMetadataScanState(
  @JvmField var firstPrompt: String? = null,
  @JvmField var sessionId: String? = null,
  @JvmField var gitBranch: String? = null,
  @JvmField var projectPath: String? = null,
  @JvmField var isSidechain: Boolean = false,
  override var updatedAt: Long? = null,
  @JvmField var hasConversationSignal: Boolean = false,
  override var hasActivitySignal: Boolean = false,
  override var awaitingAssistantTurn: Boolean = false,
  override var needsInput: Boolean = false,
  override var isProcessing: Boolean = false,
  override var projectFilesChangedAt: Long = Long.MIN_VALUE,
  override val projectFileChangeEvidence: MutableList<ClaudeProjectFileChangeEvidence> = ArrayList(),
  override val pendingProjectMutatingToolUsesById: LinkedHashMap<String, Set<String>?> = LinkedHashMap(),
  override val unmatchedCompletedToolUseIdsById: LinkedHashMap<String, Long> = LinkedHashMap(),
) : ActivityTrackingState, ProjectFileChangeTrackingState

private data class JsonlUsageScanState(
  @JvmField var sessionId: String? = null,
  @JvmField var projectPath: String? = null,
) {
  private val dedupedUsageByKey = LinkedHashMap<String, ClaudeAssistantUsage>()
  private val anonymousUsage = ArrayList<ClaudeAssistantUsage>()

  fun recordUsage(usage: ClaudeAssistantUsage) {
    val dedupeKey = usage.dedupeKey
    if (dedupeKey == null) {
      anonymousUsage += usage
      return
    }

    dedupedUsageByKey.merge(dedupeKey, usage, ::mergeAssistantUsage)
  }

  fun buildUsageSnapshots(): List<ClaudeUsageSnapshot> {
    if (dedupedUsageByKey.isEmpty() && anonymousUsage.isEmpty()) return emptyList()

    val usageByModelId = LinkedHashMap<String?, UsageByModelAccumulator>()
    for (usage in dedupedUsageByKey.values) {
      usageByModelId.accumulate(usage)
    }
    for (usage in anonymousUsage) {
      usageByModelId.accumulate(usage)
    }
    return usageByModelId.values.map(UsageByModelAccumulator::toSnapshot)
  }
}

private data class UsageByModelAccumulator(
  @JvmField var modelId: String?,
  @JvmField var inputTokens: Long = 0,
  @JvmField var outputTokens: Long = 0,
  @JvmField var cacheReadTokens: Long = 0,
  @JvmField var cacheWriteTokens: Long = 0,
  @JvmField var cacheWrite5mTokens: Long = 0,
  @JvmField var cacheWrite1hTokens: Long = 0,
  @JvmField var requestCount: Long = 0,
) {
  fun add(usage: ClaudeAssistantUsage) {
    if (modelId == null) {
      modelId = usage.modelId
    }
    inputTokens += usage.inputTokens
    outputTokens += usage.outputTokens
    cacheReadTokens += usage.cacheReadTokens
    cacheWriteTokens += usage.cacheWriteTokens
    cacheWrite5mTokens += usage.cacheWrite5mTokens
    cacheWrite1hTokens += usage.cacheWrite1hTokens
    requestCount += 1
  }

  fun toSnapshot(): ClaudeUsageSnapshot {
    return ClaudeUsageSnapshot(
      modelId = modelId,
      inputTokens = inputTokens,
      outputTokens = outputTokens,
      cacheReadTokens = cacheReadTokens,
      cacheWriteTokens = cacheWriteTokens,
      cacheWrite5mTokens = cacheWrite5mTokens,
      cacheWrite1hTokens = cacheWrite1hTokens,
      requestCount = requestCount,
    )
  }
}

data class ClaudeUsageSnapshot(
  @JvmField val modelId: String?,
  @JvmField val inputTokens: Long = 0,
  @JvmField val outputTokens: Long = 0,
  @JvmField val cacheReadTokens: Long = 0,
  @JvmField val cacheWriteTokens: Long = 0,
  @JvmField val cacheWrite5mTokens: Long = 0,
  @JvmField val cacheWrite1hTokens: Long = 0,
  @JvmField val requestCount: Long = 0,
)

private fun mergeAssistantUsage(left: ClaudeAssistantUsage, right: ClaudeAssistantUsage): ClaudeAssistantUsage {
  return ClaudeAssistantUsage(
    dedupeKey = left.dedupeKey ?: right.dedupeKey,
    modelId = left.modelId ?: right.modelId,
    inputTokens = maxOf(left.inputTokens, right.inputTokens),
    outputTokens = maxOf(left.outputTokens, right.outputTokens),
    cacheReadTokens = maxOf(left.cacheReadTokens, right.cacheReadTokens),
    cacheWriteTokens = maxOf(left.cacheWriteTokens, right.cacheWriteTokens),
    cacheWrite5mTokens = maxOf(left.cacheWrite5mTokens, right.cacheWrite5mTokens),
    cacheWrite1hTokens = maxOf(left.cacheWrite1hTokens, right.cacheWrite1hTokens),
  )
}

private fun MutableMap<String?, UsageByModelAccumulator>.accumulate(usage: ClaudeAssistantUsage) {
  if (usage.isIgnorableZeroTokenUsage()) return
  getOrPut(usage.modelId) { UsageByModelAccumulator(modelId = usage.modelId) }.add(usage)
}

private fun ClaudeAssistantUsage.isIgnorableZeroTokenUsage(): Boolean {
  val hasNoTokens = inputTokens == 0L &&
                    outputTokens == 0L &&
                    cacheReadTokens == 0L &&
                    cacheWriteTokens == 0L &&
                    cacheWrite5mTokens == 0L &&
                    cacheWrite1hTokens == 0L
  if (!hasNoTokens) return false

  return when (modelId?.trim()?.lowercase()) {
    null, "", "synthetic", "<synthetic>" -> true
    else -> false
  }
}
