// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.common

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
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
  READY,
}

data class ClaudeSessionThread(
  @JvmField val id: String,
  @JvmField val title: String,
  @JvmField val updatedAt: Long,
  @JvmField val gitBranch: String? = null,
  @JvmField val activity: ClaudeSessionActivity = ClaudeSessionActivity.READY,
  @JvmField val hasCustomTitle: Boolean = false,
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
    val activity = deriveActivity(activityState.isProcessing)
    val updatedAt = listOfNotNull(headState.updatedAt, tailState.updatedAt, activityState.updatedAt).maxOrNull()

    val title = resolveThreadTitle(
      agentName = tailState.agentName,
      customTitle = tailState.customTitle,
      firstPrompt = headState.firstPrompt,
      sessionId = normalizedSessionId,
    )
    val resolvedUpdatedAt = updatedAt
      ?: try { Files.getLastModifiedTime(path).toMillis() } catch (_: Throwable) { 0L }

    return ClaudeSessionThread(
      id = normalizedSessionId,
      title = title,
      updatedAt = resolvedUpdatedAt,
      gitBranch = headState.gitBranch,
      activity = activity,
      hasCustomTitle = tailState.agentName != null || tailState.customTitle != null,
    )
  }

  fun parseSessionsIndex(path: Path): Map<String, String> {
    if (!Files.isRegularFile(path)) {
      return emptyMap()
    }

    return try {
      Files.newBufferedReader(path).use { reader ->
        jsonFactory.createParser(reader).use { parser ->
          if (parser.nextToken() != JsonToken.START_OBJECT) {
            emptyMap()
          }

          else {
            val summaries = LinkedHashMap<String, String>()
            forEachJsonObjectField(parser) { fieldName ->
              when (fieldName) {
                "entries" -> {
                  if (parser.currentToken == JsonToken.START_ARRAY) {
                    parseIndexEntries(parser, summaries)
                  }
                  else {
                    parser.skipChildren()
                  }
                }

                else -> parser.skipChildren()
              }
              true
            }
            summaries
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
      updateActivityFields(state, lineData)
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
      if (lineData.hasConversationSignal) {
        scanState.hasConversationSignal = true
      }
      updateActivityFields(scanState, lineData)
      // customTitle lives near the tail; early-exit once the head metadata is settled.
      !(scanState.sessionId != null && scanState.hasConversationSignal && scanState.firstPrompt != null)
    }
  }

}

private fun parseIndexEntries(parser: JsonParser, summaries: MutableMap<String, String>) {
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
      summaries.put(entry.sessionId, entry.summary)
    }
  }
}

private fun parseIndexEntry(parser: JsonParser): IndexedClaudeSummary? {
  var sessionId: String? = null
  var summary: String? = null
  var isSidechain = false

  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "sessionId" -> sessionId = readJsonStringOrNull(parser)
      "summary" -> summary = readJsonStringOrNull(parser)
      "isSidechain" -> isSidechain = readBooleanOrFalse(parser)
      else -> parser.skipChildren()
    }
    true
  }

  if (isSidechain) {
    return null
  }

  val normalizedSessionId = sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val normalizedSummary = normalizeClaudeStoredThreadTitle(summary)
    ?.takeUnless { it.equals("No prompt", ignoreCase = true) }
    ?: return null
  return IndexedClaudeSummary(sessionId = normalizedSessionId, summary = normalizedSummary)
}

private fun deriveActivity(isProcessing: Boolean): ClaudeSessionActivity {
  return if (isProcessing) ClaudeSessionActivity.PROCESSING else ClaudeSessionActivity.READY
}

private fun parseJsonlLine(parser: JsonParser): ParsedJsonlLine? {
  return try {
    if (parser.currentToken != JsonToken.START_OBJECT) return null
    var sessionId: String? = null
    var isSidechain = false
    var timestampMillis: Long? = null
    var firstPrompt: String? = null
    var agentName: String? = null
    var customTitle: String? = null
    var type: String? = null
    var gitBranch: String? = null
    var messageRole: String? = null
    var messageContent: String? = null
    var messageHasToolUse = false
    var messageHasToolResult = false
    var messageStopReason: String? = null
    var messageHasStopReason = false
    var hasBackgroundTaskId = false

    forEachJsonObjectField(parser) { fieldName ->
      when (fieldName) {
        "sessionId" -> sessionId = readJsonStringOrNull(parser)
        "isSidechain" -> isSidechain = readBooleanOrFalse(parser)
        "timestamp" -> timestampMillis = parseIsoTimestamp(readJsonStringOrNull(parser))
        "type" -> type = readJsonStringOrNull(parser)
        "agentName" -> agentName = readJsonStringOrNull(parser)
        "customTitle" -> customTitle = readJsonStringOrNull(parser)
        "gitBranch" -> gitBranch = readJsonStringOrNull(parser)
        "message" -> {
          if (parser.currentToken == JsonToken.START_OBJECT) {
            val parsedMessage = readMessageObject(parser)
            messageRole = parsedMessage.role
            messageContent = parsedMessage.contentPreview
            messageHasToolUse = parsedMessage.hasToolUse
            messageHasToolResult = parsedMessage.hasToolResult
            messageStopReason = parsedMessage.stopReason
            messageHasStopReason = parsedMessage.hasStopReason
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
        messageHasToolUse -> ClaudeActivityEvent.ASSISTANT_IN_PROGRESS
        messageHasStopReason -> if (messageStopReason == "end_turn") ClaudeActivityEvent.ASSISTANT_TERMINAL else ClaudeActivityEvent.ASSISTANT_IN_PROGRESS
        else -> ClaudeActivityEvent.ASSISTANT_TERMINAL
      }
      "progress" -> ClaudeActivityEvent.PROGRESS
      "queue-operation" -> ClaudeActivityEvent.QUEUE_OPERATION
      else -> ClaudeActivityEvent.OTHER
    }
    if (activityEvent == ClaudeActivityEvent.USER_PROMPT) {
      firstPrompt = normalizeClaudeStoredThreadTitle(messageContent)
    }
    val hasConversationSignal = type == "user" || type == "assistant"
    return ParsedJsonlLine(
      sessionId = sessionId,
      isSidechain = isSidechain,
      timestampMillis = timestampMillis,
      firstPrompt = firstPrompt,
      agentName = normalizeClaudeStoredThreadTitle(agentName),
      customTitle = if (type == "custom-title") normalizeClaudeStoredThreadTitle(customTitle) else null,
      hasConversationSignal = hasConversationSignal,
      gitBranch = gitBranch,
      activityEvent = activityEvent,
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
  var hasToolResult = false
  var stopReason: String? = null
  var hasStopReason = false
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "role" -> role = readJsonStringOrNull(parser)
      "content" -> {
        val result = readContentWithToolUseCheck(parser)
        contentPreview = result.contentPreview
        hasToolUse = result.hasToolUse
        hasToolResult = result.hasToolResult
      }
      "stop_reason" -> {
        hasStopReason = true
        stopReason = readJsonStringOrNull(parser)
      }
      else -> parser.skipChildren()
    }
    true
  }
  return ParsedMessageObject(
    role = role,
    contentPreview = contentPreview,
    hasToolUse = hasToolUse,
    hasToolResult = hasToolResult,
    stopReason = stopReason,
    hasStopReason = hasStopReason,
  )
}

private fun readContentWithToolUseCheck(parser: JsonParser): ParsedMessageContent {
  return when (parser.currentToken) {
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
  var hasToolResult = false
  while (true) {
    val token = parser.nextToken() ?: return ParsedMessageContent(firstText, hasToolUse, hasToolResult)
    if (token == JsonToken.END_ARRAY) return ParsedMessageContent(firstText, hasToolUse, hasToolResult)
    if (token != JsonToken.START_OBJECT) {
      parser.skipChildren()
      continue
    }
    var itemType: String? = null
    var itemText: String? = null
    forEachJsonObjectField(parser) { fieldName ->
      when (fieldName) {
        "type" -> itemType = readJsonStringOrNull(parser)
        "text" -> itemText = readJsonStringOrNull(parser)
        else -> parser.skipChildren()
      }
      true
    }
    if (itemType == "tool_use") {
      hasToolUse = true
    }
    if (itemType == "tool_result") {
      hasToolResult = true
    }
    if (firstText == null && itemType == "text" && !itemText.isNullOrBlank()) {
      firstText = itemText
    }
  }
}

private fun readToolUseResultObject(parser: JsonParser): Boolean {
  if (parser.currentToken != JsonToken.START_OBJECT) {
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

private fun resolveThreadTitle(agentName: String?, customTitle: String?, firstPrompt: String?, sessionId: String): String {
  val storedAgentName = normalizeClaudeStoredThreadTitle(agentName)
    .takeUnless { it.equals("No prompt", ignoreCase = true) }
  if (!storedAgentName.isNullOrBlank()) return storedAgentName
  val storedCustomTitle = normalizeClaudeStoredThreadTitle(customTitle)
    .takeUnless { it.equals("No prompt", ignoreCase = true) }
  if (!storedCustomTitle.isNullOrBlank()) return storedCustomTitle
  val promptTitle = normalizeClaudeStoredThreadTitle(firstPrompt).takeUnless { it.equals("No prompt", ignoreCase = true) }
  if (!promptTitle.isNullOrBlank()) return promptTitle
  return defaultClaudeThreadTitle(sessionId)
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
  return when (parser.currentToken) {
    JsonToken.VALUE_TRUE -> true
    JsonToken.VALUE_FALSE -> false
    JsonToken.VALUE_NUMBER_INT -> parser.intValue != 0
    JsonToken.VALUE_STRING -> parser.text.equals("true", ignoreCase = true)
    else -> {
      parser.skipChildren()
      false
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
  @JvmField val hasConversationSignal: Boolean,
  @JvmField val gitBranch: String?,
  @JvmField val activityEvent: ClaudeActivityEvent,
)

private data class ParsedMessageObject(
  @JvmField val role: String?,
  @JvmField val contentPreview: String?,
  @JvmField val hasToolUse: Boolean = false,
  @JvmField val hasToolResult: Boolean = false,
  @JvmField val stopReason: String? = null,
  @JvmField val hasStopReason: Boolean = false,
)

private data class ParsedMessageContent(
  @JvmField val contentPreview: String? = null,
  @JvmField val hasToolUse: Boolean = false,
  @JvmField val hasToolResult: Boolean = false,
)

private data class IndexedClaudeSummary(
  @JvmField val sessionId: String,
  @JvmField val summary: String,
)

private enum class ClaudeActivityEvent {
  USER_PROMPT,
  ASSISTANT_IN_PROGRESS,
  ASSISTANT_TERMINAL,
  TOOL_CONTINUATION,
  PROGRESS,
  QUEUE_OPERATION,
  OTHER,
}

private interface ActivityTrackingState {
  var hasActivitySignal: Boolean
  var awaitingAssistantTurn: Boolean
  var isProcessing: Boolean
  var updatedAt: Long?
}

private fun updateActivityFields(state: ActivityTrackingState, lineData: ParsedJsonlLine) {
  if (lineData.activityEvent != ClaudeActivityEvent.OTHER) {
    state.hasActivitySignal = true
  }
  when (lineData.activityEvent) {
    ClaudeActivityEvent.USER_PROMPT -> {
      state.awaitingAssistantTurn = true
      state.isProcessing = false
    }
    ClaudeActivityEvent.ASSISTANT_IN_PROGRESS,
    ClaudeActivityEvent.TOOL_CONTINUATION -> {
      state.awaitingAssistantTurn = true
      state.isProcessing = true
    }
    ClaudeActivityEvent.ASSISTANT_TERMINAL -> {
      state.awaitingAssistantTurn = false
      state.isProcessing = false
    }
    ClaudeActivityEvent.PROGRESS,
    ClaudeActivityEvent.QUEUE_OPERATION -> {
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

private data class JsonlTailScanState(
  @JvmField var agentName: String? = null,
  @JvmField var customTitle: String? = null,
  override var hasActivitySignal: Boolean = false,
  override var awaitingAssistantTurn: Boolean = false,
  override var isProcessing: Boolean = false,
  override var updatedAt: Long? = null,
) : ActivityTrackingState

private data class JsonlMetadataScanState(
  @JvmField var firstPrompt: String? = null,
  @JvmField var sessionId: String? = null,
  @JvmField var gitBranch: String? = null,
  @JvmField var isSidechain: Boolean = false,
  override var updatedAt: Long? = null,
  @JvmField var hasConversationSignal: Boolean = false,
  override var hasActivitySignal: Boolean = false,
  override var awaitingAssistantTurn: Boolean = false,
  override var isProcessing: Boolean = false,
) : ActivityTrackingState
