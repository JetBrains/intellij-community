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
private const val CLAUDE_INDEX_FILE = "sessions-index.json"
private const val CLAUDE_INDEX_VERSION = 1L
private const val MAX_JSONL_SCAN_OBJECTS = 240
private const val MAX_TITLE_LENGTH = 120

data class ClaudeSessionThread(
  val id: String,
  val title: String,
  val updatedAt: Long,
  val gitBranch: String? = null,
)

class ClaudeSessionsStore(
  private val claudeHomeProvider: () -> Path = { Path.of(System.getProperty("user.home"), ".claude") },
) {
  private val jsonFactory = JsonFactory()

  suspend fun listThreads(projectPath: String): List<ClaudeSessionThread> {
    val normalizedProjectPath = normalizePath(projectPath) ?: return emptyList()
    val projectsRoot = claudeHomeProvider().resolve(CLAUDE_PROJECTS_DIR)
    if (!Files.isDirectory(projectsRoot)) return emptyList()

    val fromIndex = LinkedHashMap<String, ClaudeSessionThread>()
    val matchedDirectories = LinkedHashSet<Path>()
    val encodedDirectory = projectsRoot.resolve(encodeProjectPath(normalizedProjectPath))
    if (Files.isDirectory(encodedDirectory)) {
      matchedDirectories.add(encodedDirectory)
    }

    Files.newDirectoryStream(projectsRoot).use { directories ->
      for (dir in directories) {
        if (!Files.isDirectory(dir)) continue
        val indexFile = dir.resolve(CLAUDE_INDEX_FILE)
        if (!Files.isRegularFile(indexFile)) continue
        val index = parseSessionsIndex(indexFile) ?: continue
        if (index.version != CLAUDE_INDEX_VERSION) continue
        val entries = index.entries.filter { entry ->
          matchesProjectPath(entry.projectPath, index.originalPath, normalizedProjectPath) && !entry.isSidechain
        }
        if (entries.isEmpty()) continue
        matchedDirectories.add(dir)
        for (entry in entries) {
          val thread = entry.toThread()
          if (thread != null) {
            fromIndex[thread.id] = thread
          }
        }
      }
    }

    val fromJsonl = LinkedHashMap<String, ClaudeSessionThread>()
    for (directory in matchedDirectories) {
      Files.newDirectoryStream(directory, "*.jsonl").use { files ->
        for (file in files) {
          if (!Files.isRegularFile(file)) continue
          val fallback = parseJsonlFallback(file, normalizedProjectPath) ?: continue
          if (fromIndex.containsKey(fallback.id)) continue
          fromJsonl[fallback.id] = fallback
        }
      }
    }

    return buildList {
      addAll(fromIndex.values)
      addAll(fromJsonl.values)
    }.sortedByDescending { it.updatedAt }
  }

  private fun parseSessionsIndex(path: Path): ClaudeSessionsIndex? {
    Files.newBufferedReader(path).use { reader ->
      jsonFactory.createParser(reader).use { parser ->
        if (parser.nextToken() != JsonToken.START_OBJECT) return null
        var version: Long? = null
        var originalPath: String? = null
        val entries = mutableListOf<ClaudeSessionsIndexEntry>()
        forEachJsonObjectField(parser) { fieldName ->
          when (fieldName) {
            "version" -> version = readJsonLongOrNull(parser)
            "originalPath" -> originalPath = readJsonStringOrNull(parser)
            "entries" -> {
              if (parser.currentToken == JsonToken.START_ARRAY) {
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
        return ClaudeSessionsIndex(version = version, originalPath = originalPath, entries = entries)
      }
    }
  }

  private fun parseJsonlFallback(path: Path, targetProjectPath: String): ClaudeSessionThread? {
    val parsed = parseJsonlMetadata(path, targetProjectPath) ?: return null
    if (parsed.isSidechain) return null

    val title = resolveThreadTitle(
      summary = null,
      firstPrompt = parsed.firstPrompt,
      sessionId = parsed.sessionId,
    )
    val updatedAt = parsed.updatedAt
      ?: Files.getLastModifiedTime(path).toMillis()

    return ClaudeSessionThread(
      id = parsed.sessionId,
      title = title,
      updatedAt = updatedAt,
    )
  }

  private fun parseJsonlMetadata(path: Path, targetProjectPath: String): ParsedJsonlMetadata? {
    val state = WorkbenchJsonlScanner.scanJsonObjects(
      path = path,
      jsonFactory = jsonFactory,
      maxObjects = MAX_JSONL_SCAN_OBJECTS,
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
      val lineTimestamp = lineData.timestampMillis
      if (lineTimestamp != null) {
        scanState.updatedAt = maxOf(scanState.updatedAt ?: 0L, lineTimestamp)
      }
      if (!lineData.cwd.isNullOrBlank()) {
        scanState.hasPathSignal = true
        val normalizedCwd = normalizePath(lineData.cwd)
        if (normalizedCwd == targetProjectPath) {
          scanState.pathMatches = true
        }
      }
      true
    }

    if (state.hasPathSignal && !state.pathMatches) return null
    if (!state.hasConversationSignal) return null
    val normalizedSessionId = state.sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return ParsedJsonlMetadata(
      sessionId = normalizedSessionId,
      firstPrompt = state.firstPrompt,
      isSidechain = state.isSidechain,
      updatedAt = state.updatedAt,
    )
  }

}

private fun parseIndexEntries(parser: JsonParser, entries: MutableList<ClaudeSessionsIndexEntry>) {
  while (true) {
    val token = parser.nextToken() ?: return
    if (token == JsonToken.END_ARRAY) return
    if (token != JsonToken.START_OBJECT) {
      parser.skipChildren()
      continue
    }
    parseIndexEntry(parser)?.let(entries::add)
  }
}

private fun parseIndexEntry(parser: JsonParser): ClaudeSessionsIndexEntry? {
  var sessionId: String? = null
  var summary: String? = null
  var firstPrompt: String? = null
  var modified: String? = null
  var fileMtime: Long? = null
  var fullPath: String? = null
  var projectPath: String? = null
  var isSidechain = false
  var gitBranch: String? = null

  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "sessionId" -> sessionId = readJsonStringOrNull(parser)
      "summary" -> summary = readJsonStringOrNull(parser)
      "firstPrompt" -> firstPrompt = readJsonStringOrNull(parser)
      "modified" -> modified = readJsonStringOrNull(parser)
      "fileMtime" -> fileMtime = readJsonLongOrNull(parser)
      "fullPath" -> fullPath = readJsonStringOrNull(parser)
      "projectPath" -> projectPath = readJsonStringOrNull(parser)
      "isSidechain" -> isSidechain = readBooleanOrFalse(parser)
      "gitBranch" -> gitBranch = readJsonStringOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }

  val normalizedSessionId = sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return ClaudeSessionsIndexEntry(
    sessionId = normalizedSessionId,
    summary = summary,
    firstPrompt = firstPrompt,
    modified = modified,
    fileMtime = fileMtime,
    fullPath = fullPath,
    projectPath = projectPath,
    isSidechain = isSidechain,
    gitBranch = gitBranch,
  )
}

private fun parseJsonlLine(parser: JsonParser): ParsedJsonlLine? {
  return try {
    if (parser.currentToken != JsonToken.START_OBJECT) return null
    var sessionId: String? = null
    var cwd: String? = null
    var isSidechain = false
    var timestampMillis: Long? = null
    var firstPrompt: String? = null
    var type: String? = null
    var messageRole: String? = null
    var messageContent: String? = null

    forEachJsonObjectField(parser) { fieldName ->
      when (fieldName) {
        "sessionId" -> sessionId = readJsonStringOrNull(parser)
        "cwd" -> cwd = readJsonStringOrNull(parser)
        "isSidechain" -> isSidechain = readBooleanOrFalse(parser)
        "timestamp" -> timestampMillis = parseIsoTimestamp(readJsonStringOrNull(parser))
        "type" -> type = readJsonStringOrNull(parser)
        "message" -> {
          if (parser.currentToken == JsonToken.START_OBJECT) {
            val parsedMessage = readMessageObject(parser)
            messageRole = parsedMessage.role
            messageContent = parsedMessage.contentPreview
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
    return ParsedJsonlLine(
      sessionId = sessionId,
      cwd = cwd,
      isSidechain = isSidechain,
      timestampMillis = timestampMillis,
      firstPrompt = firstPrompt,
      hasConversationSignal = hasConversationSignal,
    )
  }
  catch (_: Throwable) {
    null
  }
}

private fun readMessageObject(parser: JsonParser): ParsedMessageObject {
  var role: String? = null
  var contentPreview: String? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "role" -> role = readJsonStringOrNull(parser)
      "content" -> contentPreview = readContentPreview(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return ParsedMessageObject(role = role, contentPreview = contentPreview)
}

private fun readContentPreview(parser: JsonParser): String? {
  return when (parser.currentToken) {
    JsonToken.VALUE_STRING -> readJsonStringOrNull(parser)
    JsonToken.START_ARRAY -> readFirstTextFromArray(parser)
    else -> {
      parser.skipChildren()
      null
    }
  }
}

private fun readFirstTextFromArray(parser: JsonParser): String? {
  while (true) {
    val token = parser.nextToken() ?: return null
    if (token == JsonToken.END_ARRAY) return null
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
    if (itemType == "text" && !itemText.isNullOrBlank()) {
      return itemText
    }
  }
}

private fun ClaudeSessionsIndexEntry.toThread(): ClaudeSessionThread? {
  val sessionId = sessionId.trim().takeIf { it.isNotEmpty() } ?: return null
  val updatedAt = resolveUpdatedAt()
  return ClaudeSessionThread(
    id = sessionId,
    title = resolveThreadTitle(summary = summary, firstPrompt = firstPrompt, sessionId = sessionId),
    updatedAt = updatedAt,
    gitBranch = gitBranch,
  )
}

private fun ClaudeSessionsIndexEntry.resolveUpdatedAt(): Long {
  return parseIsoTimestamp(modified)
    ?: fileMtime
    ?: fullPath
      ?.let(::parsePath)
      ?.takeIf { Files.isRegularFile(it) }
      ?.let { Files.getLastModifiedTime(it).toMillis() }
    ?: 0L
}

private fun resolveThreadTitle(summary: String?, firstPrompt: String?, sessionId: String): String {
  val summaryTitle = sanitizeTitle(summary)
  if (!summaryTitle.isNullOrBlank()) return summaryTitle
  val promptTitle = sanitizeTitle(firstPrompt).takeUnless { it.equals("No prompt", ignoreCase = true) }
  if (!promptTitle.isNullOrBlank()) return promptTitle
  return "Session ${sessionId.take(8)}"
}

private fun matchesProjectPath(entryProjectPath: String?, originalPath: String?, targetPath: String): Boolean {
  val entryPath = normalizePath(entryProjectPath)
  if (entryPath != null) return entryPath == targetPath
  val indexPath = normalizePath(originalPath)
  if (indexPath != null) return indexPath == targetPath
  return false
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
  return projectPath.replace('/', '-')
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

private fun parsePath(path: String): Path? {
  return try {
    Path.of(path)
  }
  catch (_: Throwable) {
    null
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

private data class ClaudeSessionsIndex(
  val version: Long?,
  val originalPath: String?,
  val entries: List<ClaudeSessionsIndexEntry>,
)

private data class ClaudeSessionsIndexEntry(
  val sessionId: String,
  val summary: String?,
  val firstPrompt: String?,
  val modified: String?,
  val fileMtime: Long?,
  val fullPath: String?,
  val projectPath: String?,
  val isSidechain: Boolean,
  val gitBranch: String? = null,
)

private data class ParsedJsonlLine(
  val sessionId: String?,
  val cwd: String?,
  val isSidechain: Boolean,
  val timestampMillis: Long?,
  val firstPrompt: String?,
  val hasConversationSignal: Boolean,
)

private data class ParsedMessageObject(
  val role: String?,
  val contentPreview: String?,
)

private data class ParsedJsonlMetadata(
  val sessionId: String,
  val firstPrompt: String?,
  val isSidechain: Boolean,
  val updatedAt: Long?,
)

private data class JsonlMetadataScanState(
  var firstPrompt: String? = null,
  var sessionId: String? = null,
  var isSidechain: Boolean = false,
  var hasPathSignal: Boolean = false,
  var pathMatches: Boolean = false,
  var updatedAt: Long? = null,
  var hasConversationSignal: Boolean = false,
)
