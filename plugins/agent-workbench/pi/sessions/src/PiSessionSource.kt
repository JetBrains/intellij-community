// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-pi.spec.md

import com.intellij.agent.workbench.json.createJsonParser
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.json.createJsonGenerator
import com.intellij.agent.workbench.json.WorkbenchJsonlScanner
import com.intellij.agent.workbench.json.forEachJsonObjectField
import com.intellij.agent.workbench.json.readJsonLongOrNull
import com.intellij.agent.workbench.json.readJsonStringOrNull
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.BaseAgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.resolveReadTrackedActivity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.merge
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence

private val LOG = logger<PiSessionSource>()

internal interface PiSessionThreadMutationBackend {
  fun renameThread(path: String, threadId: String, normalizedName: String): Boolean

  fun archiveThread(path: String, threadId: String): Boolean

  fun unarchiveThread(path: String, threadId: String): Boolean
}

internal class PiSessionStore(
  private val sessionDirResolver: (String) -> Path = ::resolveEffectivePiSessionDir,
  private val archiveStatePathResolver: (Path) -> Path = { sessionDir -> sessionDir.resolve(PI_AGENT_WORKBENCH_ARCHIVE_STATE_FILE) },
  private val jsonFactory: JsonFactory = JsonFactory(),
  private val timeProvider: () -> Long = System::currentTimeMillis,
) : PiSessionThreadMutationBackend {
  fun sessionDir(projectPath: String): Path = sessionDirResolver(projectPath)

  fun loadEntries(projectPath: String): List<PiSessionIndexEntry> {
    val normalizedProjectPath = normalizePiProjectPath(projectPath) ?: return emptyList()
    val sessionDir = sessionDirResolver(projectPath)
    val parsedEntries = loadSessionFiles(sessionDir)
      .filter { entry -> entry.normalizedProjectDir == normalizedProjectPath }
    if (parsedEntries.isEmpty()) return emptyList()

    val archiveState = loadArchiveState(archiveStatePathResolver(sessionDir))
    return parsedEntries.map { entry ->
      val archiveEntry = archiveState[PiSessionArchiveKey(normalizedProjectPath, entry.sessionId)]
      if (archiveEntry == null) {
        entry
      }
      else {
        entry.copy(archived = archiveEntry.archived, updatedAt = maxOf(entry.updatedAt, archiveEntry.updatedAt))
      }
    }
  }

  private fun loadSessionFiles(sessionDir: Path): List<PiSessionIndexEntry> {
    if (!Files.isDirectory(sessionDir)) return emptyList()
    return try {
      Files.list(sessionDir).use { stream ->
        stream.asSequence()
          .filter { path -> path.fileName.toString().endsWith(".jsonl") }
          .filter { path -> path.isRegularFile() }
          .mapNotNull(::parseSessionFile)
          .toList()
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to load Pi session directory: $sessionDir", e)
      emptyList()
    }
  }

  private fun parseSessionFile(sessionFile: Path): PiSessionIndexEntry? {
    val state = try {
      WorkbenchJsonlScanner.scanJsonObjects(
        path = sessionFile,
        jsonFactory = jsonFactory,
        newState = ::PiSessionFileState,
      ) { parser, state ->
        parseSessionObject(parser, state)
        true
      }
    }
    catch (e: Exception) {
      LOG.debug("Failed to parse Pi session file: $sessionFile", e)
      return null
    }
    val header = state.header ?: return null
    val normalizedProjectDir = normalizePiProjectPath(header.cwd) ?: return null
    val title = state.name?.normalizePiSessionTitle()
                ?: state.firstUserMessage?.normalizePiSessionTitle()
                ?: header.id
    val headerTime = parseIsoTimestamp(header.timestamp)
    val fileMtime = runCatching { Files.getLastModifiedTime(sessionFile).toMillis() }.getOrDefault(0L)
    val updatedAt = state.lastActivityAtMs ?: headerTime ?: fileMtime
    return PiSessionIndexEntry(
      sessionId = header.id,
      title = title,
      updatedAt = updatedAt,
      projectDir = header.cwd,
      normalizedProjectDir = normalizedProjectDir,
      sessionFile = sessionFile,
      leafId = state.leafId,
      entryIds = state.entryIds,
      activity = state.leafActivity,
      archived = false,
    )
  }

  private fun loadArchiveState(archiveStatePath: Path): Map<PiSessionArchiveKey, PiSessionArchiveEntry> {
    if (!Files.isRegularFile(archiveStatePath)) return emptyMap()
    return try {
      WorkbenchJsonlScanner.scanJsonObjects(
        path = archiveStatePath,
        jsonFactory = jsonFactory,
        newState = { LinkedHashMap() },
      ) { parser, state ->
        parseArchiveStateEntry(parser)?.let { (key, entry) -> state[key] = entry }
        true
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to load Pi archive state: $archiveStatePath", e)
      emptyMap()
    }
  }

  override fun renameThread(path: String, threadId: String, normalizedName: String): Boolean {
    val entry = findEntry(path, threadId) ?: return false
    return appendSessionInfo(entry, normalizedName)
  }

  override fun archiveThread(path: String, threadId: String): Boolean {
    return appendArchiveStateEntry(path, threadId, archived = true)
  }

  override fun unarchiveThread(path: String, threadId: String): Boolean {
    return appendArchiveStateEntry(path, threadId, archived = false)
  }

  private fun findEntry(path: String, threadId: String): PiSessionIndexEntry? {
    val normalizedProjectPath = normalizePiProjectPath(path) ?: return null
    return loadEntries(path).firstOrNull { entry ->
      entry.normalizedProjectDir == normalizedProjectPath && entry.sessionId == threadId
    }
  }

  private fun appendSessionInfo(entry: PiSessionIndexEntry, normalizedName: String): Boolean {
    val title = normalizedName.normalizePiSessionTitle() ?: return false
    return try {
      Files.writeString(
        entry.sessionFile,
        buildSessionInfoLine(entry, title) + "\n",
        StandardCharsets.UTF_8,
        StandardOpenOption.APPEND,
      )
      true
    }
    catch (e: Exception) {
      LOG.warn("Failed to append Pi session_info entry: ${entry.sessionFile}", e)
      false
    }
  }

  private fun appendArchiveStateEntry(path: String, threadId: String, archived: Boolean): Boolean {
    val normalizedProjectPath = normalizePiProjectPath(path) ?: return false
    val sessionDir = sessionDirResolver(path)
    val archiveStatePath = archiveStatePathResolver(sessionDir)
    return try {
      archiveStatePath.parent?.let(Files::createDirectories)
      Files.writeString(
        archiveStatePath,
        buildArchiveStateLine(projectDir = normalizedProjectPath, sessionId = threadId, archived = archived) + "\n",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
      )
      true
    }
    catch (e: Exception) {
      LOG.warn("Failed to update Pi archive state: $archiveStatePath", e)
      false
    }
  }

  private fun buildSessionInfoLine(entry: PiSessionIndexEntry, normalizedName: String): String {
    val writer = StringWriter()
    jsonFactory.createJsonGenerator(writer).use { generator ->
      generator.writeStartObject()
      generator.writeStringProperty("type", "session_info")
      generator.writeStringProperty("id", generatePiEntryId(entry.entryIds))
      if (entry.leafId == null) generator.writeNullProperty("parentId") else generator.writeStringProperty("parentId", entry.leafId)
      generator.writeStringProperty("timestamp", Instant.ofEpochMilli(timeProvider()).toString())
      generator.writeStringProperty("name", normalizedName)
      generator.writeEndObject()
    }
    return writer.toString()
  }

  private fun buildArchiveStateLine(projectDir: String, sessionId: String, archived: Boolean): String {
    val writer = StringWriter()
    jsonFactory.createJsonGenerator(writer).use { generator ->
      generator.writeStartObject()
      generator.writeStringProperty("projectDir", projectDir)
      generator.writeStringProperty("sessionId", sessionId)
      generator.writeBooleanProperty("archived", archived)
      generator.writeNumberProperty("updatedAt", timeProvider())
      generator.writeEndObject()
    }
    return writer.toString()
  }
}

internal class PiSessionSource(
  internal val sessionStore: PiSessionStore = PiSessionStore(),
  extensionStatusEvents: Flow<AgentSessionSourceUpdateEvent> = PiExtensionStatusBridge.updateEvents,
  fileWatchFallbackEnabledProvider: () -> Boolean = ::isPiFileWatchFallbackEnabled,
  sessionUpdateEventsContributorProvider: () -> List<PiSessionUpdateEventsContributor> = ::piSessionUpdateEventsContributors,
) : BaseAgentSessionSource(AgentSessionProvider.PI) {
  private val fileWatchFallbackEnabled = fileWatchFallbackEnabledProvider()
  private val watchedSessionDirectoriesLock = Any()
  private val watchedProjectPathsBySessionDir = MutableStateFlow<Map<Path, Set<String>>>(emptyMap())
  private val observedUpdatedAtByThreadId = ConcurrentHashMap<String, Long>()
  private val completedUnreadUpdatedAtByThreadId = ConcurrentHashMap<String, Long>()

  override val supportsUpdates: Boolean get() = true

  override val supportsArchivedThreads: Boolean get() = true

  override val updateEvents: Flow<AgentSessionSourceUpdateEvent> = merge(
    readStateUpdateEvents,
    extensionStatusEvents,
    createPiSessionUpdateEvents(
      watchedProjectPathsBySessionDir = watchedProjectPathsBySessionDir,
      fileWatchFallbackEnabledProvider = { fileWatchFallbackEnabled },
      sessionUpdateEventsContributorProvider = sessionUpdateEventsContributorProvider,
    ),
  )

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    rememberSessionDirectory(path)
    val entries = sessionStore.loadEntries(path)
      .filterNot(PiSessionIndexEntry::archived)
      .sortedByDescending(PiSessionIndexEntry::updatedAt)
    rememberActiveWorkingThreadRead(entries)
    val threads = entries.map { entry ->
      entry.toAgentSessionThread(
        readTracker = readTracker,
        completedUnreadUpdatedAtByThreadId = completedUnreadUpdatedAtByThreadId,
        observedUpdatedAtByThreadId = observedUpdatedAtByThreadId,
      )
    }
    rememberObservedThreadUpdates(entries)
    return threads
  }

  override suspend fun listArchivedThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    rememberSessionDirectory(path)
    return sessionStore.loadEntries(path)
      .filter(PiSessionIndexEntry::archived)
      .sortedByDescending(PiSessionIndexEntry::updatedAt)
      .map { entry -> entry.toAgentSessionThread(readTracker) }
  }

  private fun rememberActiveWorkingThreadRead(entries: Iterable<PiSessionIndexEntry>) {
    rememberActiveThreadRead(
      threads = entries,
      id = PiSessionIndexEntry::sessionId,
      updatedAt = PiSessionIndexEntry::updatedAt,
      shouldRemember = { it.activity == AgentThreadActivity.PROCESSING },
    )
  }

  private fun rememberObservedThreadUpdates(entries: Iterable<PiSessionIndexEntry>) {
    entries.forEach { entry ->
      observedUpdatedAtByThreadId.merge(entry.sessionId, entry.updatedAt, ::maxOf)
    }
  }

  private fun rememberSessionDirectory(path: String) {
    if (!fileWatchFallbackEnabled) return
    val normalizedProjectPath = normalizePiProjectPath(path) ?: return
    val sessionDir = normalizePiSessionDirectoryPath(sessionStore.sessionDir(path))

    synchronized(watchedSessionDirectoriesLock) {
      val current = watchedProjectPathsBySessionDir.value
      val currentPaths = current[sessionDir].orEmpty()
      if (normalizedProjectPath in currentPaths) return

      val updatedPaths = LinkedHashSet<String>(currentPaths.size + 1)
      updatedPaths.addAll(currentPaths)
      updatedPaths.add(normalizedProjectPath)

      val updated = LinkedHashMap<Path, Set<String>>(current.size + 1)
      updated.putAll(current)
      updated[sessionDir] = updatedPaths
      watchedProjectPathsBySessionDir.value = updated
    }
  }
}

private fun normalizePiSessionDirectoryPath(path: Path): Path {
  return runCatching {
    path.toAbsolutePath().normalize()
  }.getOrElse {
    path.normalize()
  }
}

internal data class PiSessionIndexEntry(
  val sessionId: String,
  val title: String,
  val updatedAt: Long,
  val projectDir: String,
  val normalizedProjectDir: String,
  val sessionFile: Path,
  val leafId: String?,
  val entryIds: Set<String>,
  val activity: AgentThreadActivity?,
  val archived: Boolean,
)

private data class PiSessionHeader(
  val id: String,
  val timestamp: String,
  val cwd: String,
)

private data class PiSessionFileState(
  var header: PiSessionHeader? = null,
  var firstUserMessage: String? = null,
  var name: String? = null,
  var lastActivityAtMs: Long? = null,
  var leafId: String? = null,
  var leafActivity: AgentThreadActivity? = null,
  val entryIds: LinkedHashSet<String> = LinkedHashSet(),
)

private data class PiSessionArchiveKey(
  val normalizedProjectDir: String,
  val sessionId: String,
)

private data class PiSessionArchiveEntry(
  val archived: Boolean,
  val updatedAt: Long,
)

internal fun resolveEffectivePiSessionDir(
  projectPath: String,
  environmentProvider: (String) -> String? = System::getenv,
  homeDirProvider: () -> String = { System.getProperty("user.home") ?: "." },
): Path {
  val homeDir = homeDirProvider()
  val envSessionDir = environmentProvider(PI_SESSION_DIR_ENV)?.trim()?.takeIf { it.isNotEmpty() }
  if (envSessionDir != null) return Path.of(expandPiTildePath(envSessionDir, homeDir))

  val agentDir = environmentProvider(PI_AGENT_DIR_ENV)?.trim()?.takeIf { it.isNotEmpty() }
                   ?.let { expandPiTildePath(it, homeDir) }
                 ?: Path.of(homeDir, PI_CONFIG_DIR, "agent").toString()
  val settingsSessionDir = readPiSettingsSessionDir(Path.of(agentDir, "settings.json"))
  val projectSettingsSessionDir = normalizePiProjectPath(projectPath)?.let { normalizedProjectPath ->
    readPiSettingsSessionDir(Path.of(normalizedProjectPath, PI_CONFIG_DIR, "settings.json"))
  }
  val configuredSessionDir = projectSettingsSessionDir ?: settingsSessionDir
  if (configuredSessionDir != null) return Path.of(expandPiTildePath(configuredSessionDir, homeDir))

  return Path.of(agentDir, "sessions", encodePiSessionCwd(projectPath))
}

private fun parseSessionObject(parser: JsonParser, state: PiSessionFileState) {
  var type: String? = null
  var id: String? = null
  var timestamp: String? = null
  var cwd: String? = null
  var parentId: String? = null
  var sessionInfoName: String? = null
  var messageRole: String? = null
  var messageText: String? = null
  var messageTimestamp: Long? = null
  var messageStopReason: String? = null

  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "type" -> type = readJsonStringOrNull(parser)
      "id" -> id = readJsonStringOrNull(parser)
      "timestamp" -> timestamp = readJsonStringOrNull(parser)
      "cwd" -> cwd = readJsonStringOrNull(parser)
      "parentId" -> parentId = readJsonStringOrNull(parser)
      "name" -> sessionInfoName = readJsonStringOrNull(parser)
      "message" -> {
        val parsedMessage = parsePiMessage(parser)
        messageRole = parsedMessage.role
        messageText = parsedMessage.text
        messageTimestamp = parsedMessage.timestamp
        messageStopReason = parsedMessage.stopReason
      }
      else -> parser.skipChildren()
    }
    true
  }

  val normalizedType = type ?: return
  if (normalizedType == "session") {
    val headerId = id?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val headerTimestamp = timestamp?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val headerCwd = cwd?.trim()?.takeIf { it.isNotEmpty() } ?: return
    state.header = PiSessionHeader(id = headerId, timestamp = headerTimestamp, cwd = headerCwd)
    return
  }

  val entryId = id?.trim()?.takeIf { it.isNotEmpty() }
  if (entryId != null) {
    state.entryIds += entryId
    state.leafId = entryId
    state.leafActivity = null
  }
  else if (parentId != null) {
    state.leafId = parentId
    state.leafActivity = null
  }

  when (normalizedType) {
    "session_info" -> state.name = sessionInfoName?.trim()?.takeIf { it.isNotEmpty() }
    "message" -> {
      val role = messageRole ?: return
      if (!isPiActivityMessageRole(role)) return
      val activityTime = messageTimestamp ?: timestamp?.let(::parseIsoTimestamp)
      if (activityTime != null) {
        state.lastActivityAtMs = maxOf(state.lastActivityAtMs ?: 0L, activityTime)
      }
      if (role == "user" && state.firstUserMessage == null) {
        state.firstUserMessage = messageText?.takeIf { it.isNotBlank() }
      }
      state.leafActivity = resolvePiLeafActivity(role, messageStopReason)
    }
    "custom", "custom_message" -> {
      val activityTime = timestamp?.let(::parseIsoTimestamp)
      if (activityTime != null) {
        state.lastActivityAtMs = maxOf(state.lastActivityAtMs ?: 0L, activityTime)
      }
      state.leafActivity = AgentThreadActivity.PROCESSING
    }
  }
}

private fun isPiActivityMessageRole(role: String): Boolean {
  return role == "user" || role == "assistant" || role == "toolResult"
}

private fun resolvePiLeafActivity(role: String, stopReason: String?): AgentThreadActivity? {
  return when (role) {
    "user", "toolResult" -> AgentThreadActivity.PROCESSING
    "assistant" -> if (stopReason?.trim() == "toolUse") AgentThreadActivity.PROCESSING else null
    else -> null
  }
}

private data class PiParsedMessage(
  val role: String?,
  val text: String?,
  val timestamp: Long?,
  val stopReason: String?,
)

private fun parsePiMessage(parser: JsonParser): PiParsedMessage {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return PiParsedMessage(role = null, text = null, timestamp = null, stopReason = null)
  }
  var role: String? = null
  var text: String? = null
  var timestamp: Long? = null
  var stopReason: String? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "role" -> role = readJsonStringOrNull(parser)
      "content" -> text = readPiMessageContent(parser)
      "timestamp" -> timestamp = readJsonLongOrNull(parser)
      "stopReason" -> stopReason = readJsonStringOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return PiParsedMessage(role = role, text = text, timestamp = timestamp, stopReason = stopReason)
}

private fun readPiMessageContent(parser: JsonParser): String? {
  return when (parser.currentToken()) {
    JsonToken.VALUE_STRING -> parser.string
    JsonToken.START_ARRAY -> {
      val chunks = ArrayList<String>()
      while (parser.nextToken() != null) {
        if (parser.currentToken() == JsonToken.END_ARRAY) break
        if (parser.currentToken() == JsonToken.START_OBJECT) {
          readPiTextBlock(parser)?.let(chunks::add)
        }
        else {
          parser.skipChildren()
        }
      }
      chunks.joinToString(" ").takeIf { it.isNotBlank() }
    }
    else -> {
      parser.skipChildren()
      null
    }
  }
}

private fun readPiTextBlock(parser: JsonParser): String? {
  var type: String? = null
  var text: String? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "type" -> type = readJsonStringOrNull(parser)
      "text" -> text = readJsonStringOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return if (type == "text") text else null
}

private fun parseArchiveStateEntry(parser: JsonParser): Pair<PiSessionArchiveKey, PiSessionArchiveEntry>? {
  var sessionId: String? = null
  var projectDir: String? = null
  var archived: Boolean? = null
  var updatedAt: Long? = null

  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "sessionId" -> sessionId = readJsonStringOrNull(parser)
      "projectDir" -> projectDir = readJsonStringOrNull(parser)
      "archived" -> archived = readJsonBooleanOrNull(parser)
      "updatedAt" -> updatedAt = readJsonLongOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }

  val normalizedSessionId = sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val normalizedProjectDir = projectDir?.let(::normalizePiProjectPath) ?: return null
  val resolvedArchived = archived ?: return null
  return PiSessionArchiveKey(normalizedProjectDir, normalizedSessionId) to PiSessionArchiveEntry(
    archived = resolvedArchived,
    updatedAt = updatedAt ?: 0L,
  )
}

private fun readPiSettingsSessionDir(settingsPath: Path): String? {
  if (!Files.isRegularFile(settingsPath)) return null
  return try {
    Files.newInputStream(settingsPath).use { input ->
      JsonFactory().createJsonParser(input).use { parser ->
        if (parser.nextToken() != JsonToken.START_OBJECT) return null
        var sessionDir: String? = null
        forEachJsonObjectField(parser) { fieldName ->
          if (fieldName == "sessionDir") sessionDir = readJsonStringOrNull(parser) else parser.skipChildren()
          true
        }
        sessionDir?.trim()?.takeIf { it.isNotEmpty() }
      }
    }
  }
  catch (e: Exception) {
    LOG.debug("Failed to read Pi settings: $settingsPath", e)
    null
  }
}

private fun readJsonBooleanOrNull(parser: JsonParser): Boolean? {
  return when (parser.currentToken()) {
    JsonToken.VALUE_TRUE -> true
    JsonToken.VALUE_FALSE -> false
    JsonToken.VALUE_NUMBER_INT -> parser.intValue != 0
    JsonToken.VALUE_STRING -> parser.string.equals("true", ignoreCase = true)
    JsonToken.VALUE_NULL -> null
    else -> {
      parser.skipChildren()
      null
    }
  }
}

private fun PiSessionIndexEntry.toAgentSessionThread(readTracker: Map<String, Long>): AgentSessionThread {
  return AgentSessionThread(
    id = sessionId,
    title = title,
    updatedAt = updatedAt,
    archived = archived,
    activity = activity ?: resolveCompletedPiActivity(readTracker = readTracker),
    provider = AgentSessionProvider.PI,
  )
}

private fun PiSessionIndexEntry.toAgentSessionThread(
  readTracker: Map<String, Long>,
  completedUnreadUpdatedAtByThreadId: MutableMap<String, Long>,
  observedUpdatedAtByThreadId: Map<String, Long>,
): AgentSessionThread {
  return AgentSessionThread(
    id = sessionId,
    title = title,
    updatedAt = updatedAt,
    archived = archived,
    activity = activity ?: resolveCompletedPiActivity(
      readTracker = readTracker,
      completedUnreadUpdatedAtByThreadId = completedUnreadUpdatedAtByThreadId,
      observedUpdatedAtByThreadId = observedUpdatedAtByThreadId,
    ),
    provider = AgentSessionProvider.PI,
  )
}

private fun PiSessionIndexEntry.resolveCompletedPiActivity(
  readTracker: Map<String, Long>,
  completedUnreadUpdatedAtByThreadId: MutableMap<String, Long>? = null,
  observedUpdatedAtByThreadId: Map<String, Long> = emptyMap(),
): AgentThreadActivity {
  val lastSeenAt = readTracker[sessionId]
  if (lastSeenAt != null) {
    return resolveReadTrackedActivity(readTracker = readTracker, threadId = sessionId, updatedAt = updatedAt)
  }

  if (completedUnreadUpdatedAtByThreadId?.get(sessionId) == updatedAt) {
    return AgentThreadActivity.UNREAD
  }

  val observedUpdatedAt = observedUpdatedAtByThreadId[sessionId] ?: return AgentThreadActivity.READY
  return if (updatedAt > observedUpdatedAt) {
    completedUnreadUpdatedAtByThreadId?.put(sessionId, updatedAt)
    AgentThreadActivity.UNREAD
  }
  else {
    AgentThreadActivity.READY
  }
}

internal fun normalizePiProjectPath(path: String): String? {
  val trimmedPath = path.trim().takeIf { it.isNotEmpty() } ?: return null
  val normalizedPath = normalizeAgentWorkbenchPathOrNull(trimmedPath) ?: return null
  return normalizedPath.trimEnd('/').ifEmpty { "/" }
}

private fun String.normalizePiSessionTitle(): String? {
  return replace('\n', ' ')
    .replace('\r', ' ')
    .replace(PI_THREAD_TITLE_WHITESPACE, " ")
    .trim()
    .takeIf { it.isNotEmpty() }
}

private fun parseIsoTimestamp(timestamp: String): Long? {
  return try {
    Instant.parse(timestamp).toEpochMilli()
  }
  catch (_: DateTimeParseException) {
    null
  }
}

private fun encodePiSessionCwd(projectPath: String): String {
  val normalizedPath = normalizePiProjectPath(projectPath) ?: projectPath
  return "--" + normalizedPath
    .replace(PI_LEADING_SEPARATOR, "")
    .replace(PI_SESSION_DIR_UNSAFE_CHARS, "-") + "--"
}

private fun expandPiTildePath(path: String, homeDir: String): String {
  return when {
    path == "~" -> homeDir
    path.startsWith("~/") -> Path.of(homeDir, path.substring(2)).toString()
    path.startsWith("~\\") -> Path.of(homeDir, path.substring(2)).toString()
    else -> path
  }
}

private fun generatePiEntryId(existingIds: Set<String>): String {
  repeat(100) {
    val id = UUID.randomUUID().toString().take(8)
    if (id !in existingIds) return id
  }
  return UUID.randomUUID().toString()
}

private val PI_THREAD_TITLE_WHITESPACE = Regex("\\s+")
private val PI_LEADING_SEPARATOR = Regex("^[/\\\\]+")
private val PI_SESSION_DIR_UNSAFE_CHARS = Regex("[/\\\\:]")

private const val PI_AGENT_WORKBENCH_ARCHIVE_STATE_FILE = "agent-workbench-archive-state.jsonl"
private const val PI_CONFIG_DIR = ".pi"
private const val PI_AGENT_DIR_ENV = "PI_CODING_AGENT_DIR"
private const val PI_SESSION_DIR_ENV = "PI_CODING_AGENT_SESSION_DIR"
