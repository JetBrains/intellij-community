// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.common

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.agent.workbench.json.WorkbenchJsonlScanner
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.invariantSeparatorsPathString

private const val CLAUDE_PROJECTS_DIR = "projects"
private const val MAX_TITLE_LENGTH = 120

enum class ClaudeSessionActivity {
  UNREAD,
  PROCESSING,
  READY,
}

data class ClaudeSessionThread(
  @JvmField val id: String,
  @JvmField val title: String,
  @JvmField val updatedAt: Long,
  @JvmField val gitBranch: String? = null,
  @JvmField val activity: ClaudeSessionActivity = ClaudeSessionActivity.READY,
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
    val encodedDirectory = projectsRoot.resolve(encodeProjectPath(normalizedProjectPath))
    if (Files.isDirectory(encodedDirectory)) {
      matchedDirectories.add(encodedDirectory)
    }

    return matchedDirectories
  }

  fun parseJsonlFile(path: Path): ClaudeSessionThread? {
    val headState = scanJsonlEvents(path)

    if (headState.isSidechain) return null
    if (!headState.hasConversationSignal) return null
    val normalizedSessionId = headState.sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    val tailState = scanJsonlTail(path)
    val activityState: ActivityTrackingState = if (tailState.lastEventType != null) tailState else headState

    val lastEventType = activityState.lastEventType ?: headState.lastEventType
    val lastAssistantHadToolUse = if (activityState.lastEventType != null) activityState.lastAssistantHadToolUse else headState.lastAssistantHadToolUse
    val activity = deriveActivity(lastEventType, lastAssistantHadToolUse)
    val updatedAt = listOfNotNull(headState.updatedAt, tailState.updatedAt, activityState.updatedAt).maxOrNull()

    val title = resolveThreadTitle(
      firstPrompt = headState.firstPrompt,
      sessionId = normalizedSessionId,
    )
    val resolvedUpdatedAt = updatedAt
      ?: try { Files.getLastModifiedTime(path).toMillis() } catch (_: Throwable) { 0L }

    return ClaudeSessionThread(
      id = normalizedSessionId,
      title = title,
      updatedAt = resolvedUpdatedAt,
      activity = activity,
    )
  }

  private fun scanJsonlTail(path: Path): JsonlTailScanState {
    return WorkbenchJsonlScanner.scanTailLines(
      path = path,
      jsonFactory = jsonFactory,
      newState = ::JsonlTailScanState,
    ) { parser, state ->
      val lineData = parseJsonlLine(parser) ?: return@scanTailLines true
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
      if (lineData.hasConversationSignal) {
        scanState.hasConversationSignal = true
      }
      updateActivityFields(scanState, lineData)
      !(scanState.sessionId != null && scanState.hasConversationSignal && scanState.firstPrompt != null)
    }
  }

}

private fun deriveActivity(lastEventType: String?, lastAssistantHadToolUse: Boolean?): ClaudeSessionActivity {
  return when (lastEventType) {
    "progress" -> if (lastAssistantHadToolUse == false) ClaudeSessionActivity.READY else ClaudeSessionActivity.PROCESSING
    "assistant" -> if (lastAssistantHadToolUse == true) ClaudeSessionActivity.PROCESSING else ClaudeSessionActivity.READY
    else -> ClaudeSessionActivity.READY
  }
}

private fun parseJsonlLine(parser: JsonParser): ParsedJsonlLine? {
  return try {
    if (parser.currentToken != JsonToken.START_OBJECT) return null
    var sessionId: String? = null
    var isSidechain = false
    var timestampMillis: Long? = null
    var firstPrompt: String? = null
    var type: String? = null
    var messageRole: String? = null
    var messageContent: String? = null
    var messageHasToolUse = false

    forEachJsonObjectField(parser) { fieldName ->
      when (fieldName) {
        "sessionId" -> sessionId = readJsonStringOrNull(parser)
        "isSidechain" -> isSidechain = readBooleanOrFalse(parser)
        "timestamp" -> timestampMillis = parseIsoTimestamp(readJsonStringOrNull(parser))
        "type" -> type = readJsonStringOrNull(parser)
        "message" -> {
          if (parser.currentToken == JsonToken.START_OBJECT) {
            val parsedMessage = readMessageObject(parser)
            messageRole = parsedMessage.role
            messageContent = parsedMessage.contentPreview
            messageHasToolUse = parsedMessage.hasToolUse
          }
          else {
            parser.skipChildren()
          }
        }
        else -> parser.skipChildren()
      }
      true
    }
    if (type == "user" && messageRole == "user") {
      firstPrompt = sanitizeTitle(messageContent)
    }
    val hasConversationSignal = type == "user" || type == "assistant"
    val hasToolUse = type == "assistant" && messageHasToolUse
    return ParsedJsonlLine(
      sessionId = sessionId,
      isSidechain = isSidechain,
      timestampMillis = timestampMillis,
      firstPrompt = firstPrompt,
      hasConversationSignal = hasConversationSignal,
      eventType = type,
      hasToolUse = hasToolUse,
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
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "role" -> role = readJsonStringOrNull(parser)
      "content" -> {
        val result = readContentWithToolUseCheck(parser)
        contentPreview = result.first
        hasToolUse = result.second
      }
      else -> parser.skipChildren()
    }
    true
  }
  return ParsedMessageObject(role = role, contentPreview = contentPreview, hasToolUse = hasToolUse)
}

private fun readContentWithToolUseCheck(parser: JsonParser): Pair<String?, Boolean> {
  return when (parser.currentToken) {
    JsonToken.VALUE_STRING -> Pair(readJsonStringOrNull(parser), false)
    JsonToken.START_ARRAY -> readFirstTextAndToolUseFromArray(parser)
    else -> {
      parser.skipChildren()
      Pair(null, false)
    }
  }
}

private fun readFirstTextAndToolUseFromArray(parser: JsonParser): Pair<String?, Boolean> {
  var firstText: String? = null
  var hasToolUse = false
  while (true) {
    val token = parser.nextToken() ?: return Pair(firstText, hasToolUse)
    if (token == JsonToken.END_ARRAY) return Pair(firstText, hasToolUse)
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
    if (firstText == null && itemType == "text" && !itemText.isNullOrBlank()) {
      firstText = itemText
    }
  }
}

private fun resolveThreadTitle(firstPrompt: String?, sessionId: String): String {
  val promptTitle = sanitizeTitle(firstPrompt).takeUnless { it.equals("No prompt", ignoreCase = true) }
  if (!promptTitle.isNullOrBlank()) return promptTitle
  return "Session ${sessionId.take(8)}"
}

private fun sanitizeTitle(value: String?): String? {
  val normalized = value
    ?.replace('\n', ' ')
    ?.replace('\r', ' ')
    ?.replace(Regex("\\s+"), " ")
    ?.trim()
    ?: return null
  if (normalized.isEmpty()) return null
  return if (normalized.length <= MAX_TITLE_LENGTH) normalized else normalized.take(MAX_TITLE_LENGTH - 3).trimEnd() + "..."
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
  return projectPath.replace('/', '-').replace('.', '-')
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
  @JvmField val hasConversationSignal: Boolean,
  @JvmField val eventType: String?,
  @JvmField val hasToolUse: Boolean = false,
)

private data class ParsedMessageObject(
  @JvmField val role: String?,
  @JvmField val contentPreview: String?,
  @JvmField val hasToolUse: Boolean = false,
)

private interface ActivityTrackingState {
  var lastEventType: String?
  var lastAssistantHadToolUse: Boolean?
  var updatedAt: Long?
}

private fun updateActivityFields(state: ActivityTrackingState, lineData: ParsedJsonlLine) {
  val eventType = lineData.eventType
  if (eventType == "user" || eventType == "assistant" || eventType == "progress") {
    state.lastEventType = eventType
    when (eventType) {
      "user" -> state.lastAssistantHadToolUse = null
      "assistant" -> state.lastAssistantHadToolUse = lineData.hasToolUse
    }
  }
  val lineTimestamp = lineData.timestampMillis
  if (lineTimestamp != null) {
    state.updatedAt = maxOf(state.updatedAt ?: 0L, lineTimestamp)
  }
}

private data class JsonlTailScanState(
  override var lastEventType: String? = null,
  override var lastAssistantHadToolUse: Boolean? = null,
  override var updatedAt: Long? = null,
) : ActivityTrackingState

private data class JsonlMetadataScanState(
  @JvmField var firstPrompt: String? = null,
  @JvmField var sessionId: String? = null,
  @JvmField var isSidechain: Boolean = false,
  override var updatedAt: Long? = null,
  @JvmField var hasConversationSignal: Boolean = false,
  override var lastEventType: String? = null,
  override var lastAssistantHadToolUse: Boolean? = null,
) : ActivityTrackingState
