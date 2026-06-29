// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.pi.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-pi.spec.md

import com.intellij.platform.ai.agent.json.createJsonParser
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.AgentThreadActivityReport
import com.intellij.platform.ai.agent.core.normalizeAgentSessionProjectPath
import com.intellij.platform.ai.agent.core.normalizeAgentSessionTitle
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineItemKind
import com.intellij.platform.ai.agent.core.session.AgentSessionCost
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.core.session.AgentSessionThreadOutline
import com.intellij.platform.ai.agent.core.session.AgentSessionOutlineTreeRecord
import com.intellij.platform.ai.agent.core.session.agentSessionOutlinePhaseTitle
import com.intellij.platform.ai.agent.core.session.buildAgentSessionArchivedThreadTitle
import com.intellij.platform.ai.agent.core.session.buildAgentSessionOutlineTree
import com.intellij.platform.ai.agent.core.session.isAgentSessionArchivedThreadTitle
import com.intellij.platform.ai.agent.core.session.normalizeAgentSessionOutlinePreview
import com.intellij.platform.ai.agent.core.session.resolveAgentSessionArchivedTitleState
import com.intellij.platform.ai.agent.json.createJsonGenerator
import com.intellij.platform.ai.agent.json.WorkbenchJsonlScanner
import com.intellij.platform.ai.agent.json.forEachJsonObjectField
import com.intellij.platform.ai.agent.json.readJsonLongOrNull
import com.intellij.platform.ai.agent.json.readJsonStringOrNull
import com.intellij.platform.ai.agent.sessions.core.cost.AgentSessionUsageCostCalculators
import com.intellij.platform.ai.agent.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.platform.ai.agent.sessions.core.cost.aggregateAgentSessionUsageCost
import com.intellij.platform.ai.agent.sessions.core.normalizeConcreteAgentSessionThreadId
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionActiveThreadUpdateSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionArchivedSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionCostSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionOutlineForkResult
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadOutlineForkSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadOutlineNavigationSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadOutlineSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionUpdateSource
import com.intellij.platform.ai.agent.sessions.core.providers.BaseAgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.resolveReadTrackedActivity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import java.math.BigDecimal
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
  private val jsonFactory: JsonFactory = JsonFactory(),
  private val timeProvider: () -> Long = System::currentTimeMillis,
) : PiSessionThreadMutationBackend {
  fun sessionDir(projectPath: String): Path = sessionDirResolver(projectPath)

  fun loadEntries(projectPath: String): List<PiSessionIndexEntry> {
    val normalizedProjectPath = normalizePiProjectPath(projectPath) ?: return emptyList()
    val sessionDir = sessionDirResolver(projectPath)
    val parsedEntries = loadSessionFiles(sessionDir)
      .filter { entry -> entry.normalizedProjectDir == normalizedProjectPath }
    return parsedEntries
  }

  fun loadOutline(projectPath: String, threadId: String): AgentSessionThreadOutline? {
    val entry = findEntry(projectPath, threadId) ?: return null
    return parseSessionOutline(entry)
  }

  fun loadUsageSnapshots(projectPath: String, threadIds: Set<String>): Map<String, List<AgentSessionUsageSnapshot>> {
    if (threadIds.isEmpty()) return emptyMap()
    val normalizedProjectPath = normalizePiProjectPath(projectPath) ?: return emptyMap()
    val result = LinkedHashMap<String, List<AgentSessionUsageSnapshot>>()
    loadEntries(projectPath).asSequence()
      .filter { entry -> entry.normalizedProjectDir == normalizedProjectPath && entry.sessionId in threadIds }
      .forEach { entry ->
        result[entry.sessionId] = parseSessionUsageSnapshots(entry)
      }
    return result
  }

  fun canForkThreadFromOutlineItem(path: String, threadId: String, itemId: String): Boolean {
    val entry = findEntry(path, threadId) ?: return false
    return resolveForkBranch(entry.sessionFile, itemId) != null
  }

  fun forkThreadFromOutlineItem(path: String, threadId: String, itemId: String): PiSessionIndexEntry? {
    val entry = findEntry(path, threadId) ?: return null
    val branch = resolveForkBranch(entry.sessionFile, itemId) ?: return null
    val forkedSessionFile = writeForkedSessionFile(entry, branch) ?: return null
    return parseSessionFile(forkedSessionFile)
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
    val titleState = resolveAgentSessionArchivedTitleState(title, header.id)
    val headerTime = parseIsoTimestamp(header.timestamp)
    val fileMtime = runCatching { Files.getLastModifiedTime(sessionFile).toMillis() }.getOrDefault(0L)
    val updatedAt = state.lastActivityAtMs ?: headerTime ?: fileMtime
    return PiSessionIndexEntry(
      sessionId = header.id,
      title = titleState.title,
      updatedAt = updatedAt,
      projectDir = header.cwd,
      normalizedProjectDir = normalizedProjectDir,
      sessionFile = sessionFile,
      leafId = state.leafId,
      entryIds = state.entryIds,
      activity = state.leafActivity,
      archived = titleState.archived,
    )
  }

  private fun parseSessionOutline(entry: PiSessionIndexEntry): AgentSessionThreadOutline? {
    val state = try {
      WorkbenchJsonlScanner.scanJsonObjects(
        path = entry.sessionFile,
        jsonFactory = jsonFactory,
        newState = ::PiSessionOutlineState,
      ) { parser, state ->
        parseSessionOutlineObject(parser, state)
        true
      }
    }
    catch (e: Exception) {
      LOG.debug("Failed to parse Pi session outline: ${entry.sessionFile}", e)
      return null
    }
    val header = state.header ?: return null
    if (header.id != entry.sessionId) {
      return null
    }
    val title = state.name?.normalizePiSessionTitle()
                ?: state.firstUserMessage?.normalizePiSessionTitle()
                ?: header.id
    val titleState = resolveAgentSessionArchivedTitleState(title, header.id)
    val headerTime = parseIsoTimestamp(header.timestamp)
    val fileMtime = runCatching { Files.getLastModifiedTime(entry.sessionFile).toMillis() }.getOrDefault(0L)
    val updatedAt = state.updatedAtMs ?: headerTime ?: fileMtime
    return AgentSessionThreadOutline(
      provider = PI_AGENT_SESSION_PROVIDER,
      threadId = header.id,
      title = titleState.title,
      updatedAt = updatedAt,
      items = buildAgentSessionOutlineTree(state.records),
    )
  }

  private fun parseSessionUsageSnapshots(entry: PiSessionIndexEntry): List<AgentSessionUsageSnapshot> {
    return try {
      WorkbenchJsonlScanner.scanJsonObjects(
        path = entry.sessionFile,
        jsonFactory = jsonFactory,
        newState = { ArrayList() },
      ) { parser, snapshots ->
        parsePiUsageSnapshot(parser)?.let(snapshots::add)
        true
      }
    }
    catch (e: Exception) {
      LOG.debug("Failed to parse Pi session usage: ${entry.sessionFile}", e)
      emptyList()
    }
  }

  private fun resolveForkBranch(sessionFile: Path, itemId: String): List<PiForkRawEntry>? {
    val targetId = itemId.trim().takeIf { it.isNotEmpty() } ?: return null
    val entriesById = LinkedHashMap<String, PiForkRawEntry>()
    try {
      Files.newBufferedReader(sessionFile, StandardCharsets.UTF_8).use { reader ->
        while (true) {
          val line = reader.readLine() ?: break
          val entry = parseForkRawEntry(line.trim()) ?: continue
          entriesById[entry.id] = entry
        }
      }
    }
    catch (e: Exception) {
      LOG.debug("Failed to parse Pi session fork entries: $sessionFile", e)
      return null
    }

    val branch = ArrayList<PiForkRawEntry>()
    val seenIds = HashSet<String>()
    var currentId: String? = targetId
    while (currentId != null) {
      if (!seenIds.add(currentId)) {
        return null
      }
      val entry = entriesById[currentId] ?: return null
      branch += entry
      currentId = entry.parentId
    }
    branch.reverse()
    return branch
  }

  private fun parseForkRawEntry(line: String): PiForkRawEntry? {
    if (line.isEmpty()) return null
    return try {
      jsonFactory.createJsonParser(line).use { parser ->
        if (parser.nextToken() != JsonToken.START_OBJECT) return null
        var type: String? = null
        var id: String? = null
        var parentId: String? = null
        forEachJsonObjectField(parser) { fieldName ->
          when (fieldName) {
            "type" -> type = readJsonStringOrNull(parser)
            "id" -> id = readJsonStringOrNull(parser)
            "parentId" -> parentId = readJsonStringOrNull(parser)
            else -> parser.skipChildren()
          }
          true
        }
        if (type == "session") return null
        PiForkRawEntry(
          id = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
          parentId = parentId?.trim()?.takeIf { it.isNotEmpty() },
          line = line,
        )
      }
    }
    catch (_: Exception) {
      null
    }
  }

  private fun writeForkedSessionFile(entry: PiSessionIndexEntry, branch: List<PiForkRawEntry>): Path? {
    val sessionId = UUID.randomUUID().toString()
    val timestamp = Instant.ofEpochMilli(timeProvider()).toString()
    val sessionDir = entry.sessionFile.parent ?: sessionDirResolver(entry.projectDir)
    val sessionFile = sessionDir.resolve("${timestamp.toPiSessionFileTimestamp()}_$sessionId.jsonl")
    return try {
      Files.createDirectories(sessionDir)
      val lines = ArrayList<String>(branch.size + 1)
      lines += buildForkedSessionHeaderLine(
        sessionId = sessionId,
        timestamp = timestamp,
        cwd = entry.projectDir,
        parentSession = entry.sessionFile.toString(),
      )
      branch.mapTo(lines, PiForkRawEntry::line)
      Files.writeString(
        sessionFile,
        lines.joinToString(separator = "\n", postfix = "\n"),
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE,
      )
      sessionFile
    }
    catch (e: Exception) {
      LOG.warn("Failed to create Pi forked session: $sessionFile", e)
      null
    }
  }

  private fun buildForkedSessionHeaderLine(sessionId: String, timestamp: String, cwd: String, parentSession: String): String {
    val writer = StringWriter()
    jsonFactory.createJsonGenerator(writer).use { generator ->
      generator.writeStartObject()
      generator.writeStringProperty("type", "session")
      generator.writeNumberProperty("version", PI_CURRENT_SESSION_VERSION)
      generator.writeStringProperty("id", sessionId)
      generator.writeStringProperty("timestamp", timestamp)
      generator.writeStringProperty("cwd", cwd)
      generator.writeStringProperty("parentSession", parentSession)
      generator.writeEndObject()
    }
    return writer.toString()
  }

  override fun renameThread(path: String, threadId: String, normalizedName: String): Boolean {
    val entry = findEntry(path, threadId) ?: return false
    val storedName = if (entry.archived) buildAgentSessionArchivedThreadTitle(normalizedName, entry.sessionId) else normalizedName
    return appendSessionInfo(entry, storedName)
  }

  override fun archiveThread(path: String, threadId: String): Boolean {
    val entry = findEntry(path, threadId) ?: return false
    return appendSessionInfo(entry, buildAgentSessionArchivedThreadTitle(entry.title, entry.sessionId))
  }

  override fun unarchiveThread(path: String, threadId: String): Boolean {
    val entry = findEntry(path, threadId) ?: return false
    return appendSessionInfo(entry, resolveAgentSessionArchivedTitleState(entry.title, entry.sessionId).title)
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

}

internal class PiSessionSource(
  internal val sessionStore: PiSessionStore = PiSessionStore(),
  extensionStatusEvents: Flow<AgentSessionSourceUpdateEvent> = PiExtensionStatusBridge.updateEvents,
  fileWatchFallbackEnabledProvider: () -> Boolean = ::isPiFileWatchFallbackEnabled,
  sessionUpdateEventsContributorProvider: () -> List<PiSessionUpdateEventsContributor> = ::piSessionUpdateEventsContributors,
  private val calculateCost: (AgentSessionUsageSnapshot) -> AgentSessionCost = AgentSessionUsageCostCalculators::calculateCost,
) : BaseAgentSessionSource(PI_AGENT_SESSION_PROVIDER),
    AgentSessionUpdateSource,
    AgentSessionActiveThreadUpdateSource,
    AgentSessionArchivedSource,
    AgentSessionCostSource,
    AgentSessionThreadOutlineSource,
    AgentSessionThreadOutlineNavigationSource,
    AgentSessionThreadOutlineForkSource {
  private val fileWatchFallbackEnabled = fileWatchFallbackEnabledProvider()
  private val watchedSessionDirectoriesLock = Any()
  private val watchedProjectPathsBySessionDir = MutableStateFlow<Map<Path, Set<String>>>(emptyMap())
  private val observedUpdatedAtByThreadId = ConcurrentHashMap<String, Long>()
  private val completedUnreadUpdatedAtByThreadId = ConcurrentHashMap<String, Long>()

  override val updateEvents: Flow<AgentSessionSourceUpdateEvent> = merge(
    readStateUpdateEvents,
    extensionStatusEvents,
    PiExtensionControlBridge.updateEvents,
    createPiSessionUpdateEvents(
      watchedProjectPathsBySessionDir = watchedProjectPathsBySessionDir,
      fileWatchFallbackEnabledProvider = { fileWatchFallbackEnabled },
      sessionUpdateEventsContributorProvider = sessionUpdateEventsContributorProvider,
    ),
  )

  override fun activeThreadUpdateEvents(path: String, threadId: String): Flow<AgentSessionSourceUpdateEvent> {
    val normalizedPath = normalizePiProjectPath(path) ?: return emptyFlow()
    val normalizedThreadId = threadId.trim().takeIf { it.isNotEmpty() } ?: return emptyFlow()
    val matchingUpdates = updateEvents.filter { updateEvent -> updateEvent.matchesActivePiThread(normalizedPath, normalizedThreadId) }
    val currentControlUpdate = PiExtensionControlBridge.currentThreadUpdateEvent(path = normalizedPath, threadId = normalizedThreadId)
    return if (currentControlUpdate == null) matchingUpdates else merge(flowOf(currentControlUpdate), matchingUpdates)
  }

  override suspend fun loadThreads(path: String, openProject: Project?): List<AgentSessionThread> {
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

  override suspend fun loadThreadCosts(path: String, threads: List<AgentSessionThread>): Map<String, AgentSessionCost?> {
    if (threads.isEmpty()) {
      return emptyMap()
    }

    val requestedThreadIds = threads.asSequence()
      .map(AgentSessionThread::id)
      .mapNotNull(::normalizeConcreteAgentSessionThreadId)
      .toCollection(LinkedHashSet())
    if (requestedThreadIds.isEmpty()) {
      return emptyMap()
    }

    val usageSnapshotsByThreadId = sessionStore.loadUsageSnapshots(path, requestedThreadIds)
    return requestedThreadIds.associateWith { threadId ->
      usageSnapshotsByThreadId[threadId]?.aggregateAgentSessionUsageCost(calculateCost)
    }
  }

  override suspend fun loadThreadOutline(path: String, threadId: String, subAgentId: String?): AgentSessionThreadOutline? {
    rememberSessionDirectory(path)
    return sessionStore.loadOutline(path, threadId)
  }

  override fun canNavigateThreadOutlineItem(
    path: String,
    threadId: String,
    itemId: String,
    subAgentId: String?,
    tabKey: String?,
  ): Boolean {
    return subAgentId == null && PiExtensionControlBridge.canNavigateThreadOutlineItem(path = path, threadId = threadId, itemId = itemId)
  }

  override suspend fun navigateThreadOutlineItem(
    path: String,
    threadId: String,
    itemId: String,
    subAgentId: String?,
    tabKey: String?,
  ): Boolean {
    return subAgentId == null && PiExtensionControlBridge.navigateThreadOutlineItem(path = path, threadId = threadId, itemId = itemId)
  }

  override fun canForkThreadFromOutlineItem(
    path: String,
    threadId: String,
    itemId: String,
    subAgentId: String?,
    tabKey: String?,
  ): Boolean {
    return subAgentId == null &&
           (PiExtensionControlBridge.canForkThreadFromOutlineItem(path = path, threadId = threadId, itemId = itemId) ||
            sessionStore.canForkThreadFromOutlineItem(path = path, threadId = threadId, itemId = itemId))
  }

  override suspend fun forkThreadFromOutlineItem(
    project: Project,
    path: String,
    threadId: String,
    itemId: String,
    subAgentId: String?,
    tabKey: String?,
  ): AgentSessionOutlineForkResult? {
    if (subAgentId != null) return null
    if (PiExtensionControlBridge.canForkThreadFromOutlineItem(path = path, threadId = threadId, itemId = itemId)) {
      val liveThread = PiExtensionControlBridge.forkThreadFromOutlineItem(path = path, threadId = threadId, itemId = itemId)
      if (liveThread != null) {
        return AgentSessionOutlineForkResult(thread = liveThread)
      }
    }
    val forkedEntry = sessionStore.forkThreadFromOutlineItem(path = path, threadId = threadId, itemId = itemId) ?: return null
    return AgentSessionOutlineForkResult(thread = forkedEntry.toAgentSessionThread(readTracker))
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

private fun AgentSessionSourceUpdateEvent.matchesActivePiThread(projectPath: String, threadId: String): Boolean {
  val scopedPaths = scopedPaths
  if (scopedPaths != null && projectPath !in scopedPaths) {
    return false
  }
  val threadIds = threadIds
  return threadIds == null || threadId in threadIds
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

private data class PiForkRawEntry(
  val id: String,
  val parentId: String?,
  val line: String,
)

private data class PiSessionOutlineState(
  var header: PiSessionHeader? = null,
  var firstUserMessage: String? = null,
  var name: String? = null,
  var updatedAtMs: Long? = null,
  var nextGeneratedRecordIndex: Int = 0,
  val recordIds: HashSet<String> = HashSet(),
  val records: MutableList<AgentSessionOutlineTreeRecord> = ArrayList(),
) {
  fun noteActivity(timestampMs: Long?) {
    if (timestampMs != null) {
      updatedAtMs = maxOf(updatedAtMs ?: 0L, timestampMs)
    }
  }

  fun addRecord(
    id: String?,
    parentId: String?,
    kind: AgentSessionOutlineItemKind,
    title: String,
    preview: String?,
    timestampMs: Long?,
    visible: Boolean = true,
  ): String {
    val baseId = id?.trim()?.takeIf { it.isNotEmpty() } ?: "outline-${nextGeneratedRecordIndex++}"
    val recordId = uniqueRecordId(baseId)
    records += AgentSessionOutlineTreeRecord(
      id = recordId,
      parentId = normalizePiOutlineParentId(parentId, kind, visible),
      kind = kind,
      title = title,
      preview = preview,
      timestampMs = timestampMs,
      visible = visible,
    )
    return recordId
  }

  fun addHiddenRecord(id: String?, parentId: String?, timestampMs: Long?) {
    if (!id.isNullOrBlank()) {
      addRecord(
        id = id,
        parentId = parentId,
        kind = AgentSessionOutlineItemKind.METADATA,
        title = "Metadata",
        preview = null,
        timestampMs = timestampMs,
        visible = false,
      )
    }
  }

  private fun uniqueRecordId(baseId: String): String {
    if (recordIds.add(baseId)) {
      return baseId
    }
    var index = 2
    while (true) {
      val candidate = "$baseId-$index"
      if (recordIds.add(candidate)) {
        return candidate
      }
      index++
    }
  }
}

private fun normalizePiOutlineParentId(parentId: String?, kind: AgentSessionOutlineItemKind, visible: Boolean): String? {
  val normalizedParentId = parentId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return if (visible && kind.isPiOutlineTimelineRoot()) null else normalizedParentId
}

private fun AgentSessionOutlineItemKind.isPiOutlineTimelineRoot(): Boolean {
  return when (this) {
    AgentSessionOutlineItemKind.USER_PROMPT,
    AgentSessionOutlineItemKind.ASSISTANT_RESPONSE,
    AgentSessionOutlineItemKind.AGENT_WORK,
    AgentSessionOutlineItemKind.PLAN,
    AgentSessionOutlineItemKind.APPROVAL_REQUEST,
    AgentSessionOutlineItemKind.INPUT_REQUEST,
    AgentSessionOutlineItemKind.SUMMARY,
    AgentSessionOutlineItemKind.METADATA,
      -> true
    AgentSessionOutlineItemKind.TOOL_CALL,
    AgentSessionOutlineItemKind.TOOL_RESULT,
      -> false
  }
}

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
    state.header = parsePiSessionHeader(id = id, timestamp = timestamp, cwd = cwd) ?: return
    return
  }

  val entryId = id?.trim()?.takeIf { it.isNotEmpty() }
  val preserveLeaf = normalizedType == "session_info" && shouldPreserveLeafForPiSessionInfo(state.name, sessionInfoName)
  if (entryId != null) {
    state.entryIds += entryId
  }
  if (entryId != null && !preserveLeaf) {
    state.leafId = entryId
    state.leafActivity = null
  }
  else if (entryId == null && parentId != null && !preserveLeaf) {
    state.leafId = parentId
    state.leafActivity = null
  }

  when (normalizedType) {
    "session_info" -> {
      state.name = sessionInfoName?.trim()?.takeIf { it.isNotEmpty() }
    }
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

private fun parsePiUsageSnapshot(parser: JsonParser): AgentSessionUsageSnapshot? {
  var type: String? = null
  var usageMessage: PiUsageMessage? = null

  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "type" -> type = readJsonStringOrNull(parser)
      "message" -> usageMessage = parsePiUsageMessage(parser)
      else -> parser.skipChildren()
    }
    true
  }

  val normalizedType = type?.trim()
  if (normalizedType != null && normalizedType != "message") return null
  val message = usageMessage ?: return null
  if (message.role != "assistant") return null
  val usage = message.usage?.withTotalTokensFallback() ?: return null
  if (!usage.hasTokens()) return null
  return AgentSessionUsageSnapshot(
    modelId = message.model?.let { model -> PI_COST_MODEL_PREFIX + model },
    inputTokens = usage.inputTokens,
    outputTokens = usage.outputTokens,
    cacheReadTokens = usage.cacheReadTokens,
    cacheWriteTokens = usage.cacheWriteTokens,
    requestCount = 1,
    nativeExactCostUsd = usage.nativeExactCostUsdForProvider(message.provider),
  )
}

private fun parsePiUsageMessage(parser: JsonParser): PiUsageMessage? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var role: String? = null
  var provider: String? = null
  var model: String? = null
  var usage: PiUsageTokens? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "role" -> role = readJsonStringOrNull(parser)?.trim()?.takeIf { it.isNotEmpty() }
      "provider" -> provider = readJsonStringOrNull(parser)?.trim()?.takeIf { it.isNotEmpty() }
      "model" -> model = readJsonStringOrNull(parser)?.trim()?.takeIf { it.isNotEmpty() }
      "usage" -> usage = parsePiUsageTokens(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return PiUsageMessage(role = role, provider = provider, model = model, usage = usage)
}

private fun parsePiUsageTokens(parser: JsonParser): PiUsageTokens? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var inputTokens = 0L
  var outputTokens = 0L
  var cacheReadTokens = 0L
  var cacheWriteTokens = 0L
  var totalTokens = 0L
  var nativeExactCostUsd: BigDecimal? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "input" -> inputTokens = readJsonNonNegativeLongOrZero(parser)
      "output" -> outputTokens = readJsonNonNegativeLongOrZero(parser)
      "cacheRead" -> cacheReadTokens = readJsonNonNegativeLongOrZero(parser)
      "cacheWrite" -> cacheWriteTokens = readJsonNonNegativeLongOrZero(parser)
      "totalTokens" -> totalTokens = readJsonNonNegativeLongOrZero(parser)
      "cost" -> nativeExactCostUsd = parsePiUsageCostTotal(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return PiUsageTokens(
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    cacheReadTokens = cacheReadTokens,
    cacheWriteTokens = cacheWriteTokens,
    totalTokens = totalTokens,
    nativeExactCostUsd = nativeExactCostUsd,
  )
}

private fun parsePiUsageCostTotal(parser: JsonParser): BigDecimal? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }

  var total: BigDecimal? = null
  forEachJsonObjectField(parser) { fieldName ->
    if (fieldName == "total") {
      total = readJsonBigDecimalOrNull(parser)
    }
    else {
      parser.skipChildren()
    }
    true
  }
  return total
}

private fun shouldPreserveLeafForPiSessionInfo(previousName: String?, nextName: String?): Boolean {
  val normalizedNextName = nextName?.trim()?.takeIf { it.isNotEmpty() } ?: return false
  return isAgentSessionArchivedThreadTitle(normalizedNextName) || previousName?.let(::isAgentSessionArchivedThreadTitle) == true
}

private fun parseSessionOutlineObject(parser: JsonParser, state: PiSessionOutlineState) {
  var type: String? = null
  var id: String? = null
  var timestamp: String? = null
  var cwd: String? = null
  var parentId: String? = null
  var sessionInfoName: String? = null
  var targetId: String? = null
  var customType: String? = null
  var content: String? = null
  var summary: String? = null
  var message: PiOutlineMessage? = null

  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "type" -> type = readJsonStringOrNull(parser)
      "id" -> id = readJsonStringOrNull(parser)
      "timestamp" -> timestamp = readJsonStringOrNull(parser)
      "cwd" -> cwd = readJsonStringOrNull(parser)
      "parentId" -> parentId = readJsonStringOrNull(parser)
      "targetId" -> targetId = readJsonStringOrNull(parser)
      "name" -> sessionInfoName = readJsonStringOrNull(parser)
      "customType" -> customType = readJsonStringOrNull(parser)
      "content", "text" -> content = readPiOutlineTextValue(parser) ?: content
      "summary" -> summary = readPiOutlineTextValue(parser) ?: summary
      "message" -> message = parsePiOutlineMessage(parser)
      else -> parser.skipChildren()
    }
    true
  }

  val normalizedType = type ?: return
  val entryTimestampMs = timestamp?.let(::parseIsoTimestamp)
  if (normalizedType == "session") {
    state.header = parsePiSessionHeader(id = id, timestamp = timestamp, cwd = cwd) ?: return
    return
  }

  when (normalizedType) {
    "session_info" -> {
      state.name = sessionInfoName?.trim()?.takeIf { it.isNotEmpty() }
      state.addHiddenRecord(id = id, parentId = parentId, timestampMs = entryTimestampMs)
    }
    "leaf" -> state.addHiddenRecord(id = id, parentId = targetId ?: parentId, timestampMs = entryTimestampMs)
    "label", "custom", "model_change", "thinking_level_change" -> {
      state.addHiddenRecord(id = id, parentId = parentId, timestampMs = entryTimestampMs)
    }
    "message" -> recordPiOutlineMessage(
      state = state,
      id = id,
      parentId = parentId,
      entryTimestampMs = entryTimestampMs,
      message = message,
    )
    "compaction" -> {
      state.noteActivity(entryTimestampMs)
      state.addRecord(
        id = id,
        parentId = parentId,
        kind = AgentSessionOutlineItemKind.SUMMARY,
        title = "Compaction",
        preview = summary ?: content,
        timestampMs = entryTimestampMs,
      )
    }
    "branch_summary" -> {
      state.noteActivity(entryTimestampMs)
      state.addRecord(
        id = id,
        parentId = parentId,
        kind = AgentSessionOutlineItemKind.SUMMARY,
        title = "Branch summary",
        preview = summary ?: content,
        timestampMs = entryTimestampMs,
      )
    }
    "custom_message" -> {
      val preview = content?.takeIf { it.isNotBlank() }
      if (preview == null) {
        state.addHiddenRecord(id = id, parentId = parentId, timestampMs = entryTimestampMs)
      }
      else {
        state.noteActivity(entryTimestampMs)
        state.addRecord(
          id = id,
          parentId = parentId,
          kind = AgentSessionOutlineItemKind.METADATA,
          title = customType?.normalizePiSessionTitle() ?: "Metadata",
          preview = preview,
          timestampMs = entryTimestampMs,
        )
      }
    }
    else -> state.addHiddenRecord(id = id, parentId = parentId, timestampMs = entryTimestampMs)
  }
}

private fun recordPiOutlineMessage(
  state: PiSessionOutlineState,
  id: String?,
  parentId: String?,
  entryTimestampMs: Long?,
  message: PiOutlineMessage?,
) {
  if (message == null) {
    state.addHiddenRecord(id = id, parentId = parentId, timestampMs = entryTimestampMs)
    return
  }
  val timestampMs = message.timestamp ?: entryTimestampMs
  state.noteActivity(timestampMs)
  when (message.role) {
    "user" -> {
      val preview = normalizeAgentSessionOutlinePreview(message.text)
      if (preview == null) {
        state.addHiddenRecord(id = id, parentId = parentId, timestampMs = timestampMs)
        return
      }
      if (state.firstUserMessage == null) {
        state.firstUserMessage = preview
      }
      state.addRecord(
        id = id,
        parentId = parentId,
        kind = AgentSessionOutlineItemKind.USER_PROMPT,
        title = "",
        preview = preview,
        timestampMs = timestampMs,
      )
    }
    "assistant" -> recordPiAssistantMessage(state, id, parentId, timestampMs, message)
    "toolResult", "bashExecution" -> state.addRecord(
      id = id,
      parentId = parentId,
      kind = AgentSessionOutlineItemKind.TOOL_RESULT,
      title = message.exitCode?.let { exitCode -> "Exit $exitCode" } ?: "Tool result",
      preview = message.text,
      timestampMs = timestampMs,
    )
    else -> state.addHiddenRecord(id = id, parentId = parentId, timestampMs = timestampMs)
  }
}

private fun recordPiAssistantMessage(
  state: PiSessionOutlineState,
  id: String?,
  parentId: String?,
  timestampMs: Long?,
  message: PiOutlineMessage,
) {
  val normalizedText = normalizeAgentSessionOutlinePreview(message.text)
  val parentRecordId = when {
    normalizedText != null -> state.addRecord(
      id = id,
      parentId = parentId,
      kind = AgentSessionOutlineItemKind.ASSISTANT_RESPONSE,
      title = agentSessionOutlinePhaseTitle(normalizedText) ?: "Assistant response",
      preview = normalizedText,
      timestampMs = timestampMs,
    )
    message.toolCalls.isNotEmpty() -> state.addRecord(
      id = id,
      parentId = parentId,
      kind = AgentSessionOutlineItemKind.AGENT_WORK,
      title = "Agent work",
      preview = null,
      timestampMs = timestampMs,
    )
    else -> {
      state.addHiddenRecord(id = id, parentId = parentId, timestampMs = timestampMs)
      return
    }
  }
  message.toolCalls.forEachIndexed { index, toolCall ->
    state.addRecord(
      id = toolCall.id ?: "$parentRecordId-tool-$index",
      parentId = parentRecordId,
      kind = AgentSessionOutlineItemKind.TOOL_CALL,
      title = toolCall.name ?: "Tool call",
      preview = toolCall.preview,
      timestampMs = timestampMs,
    )
  }
}

private fun parsePiSessionHeader(id: String?, timestamp: String?, cwd: String?): PiSessionHeader? {
  val headerId = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val headerTimestamp = timestamp?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val headerCwd = cwd?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return PiSessionHeader(id = headerId, timestamp = headerTimestamp, cwd = headerCwd)
}

private fun isPiActivityMessageRole(role: String): Boolean {
  return role == "user" || role == "assistant" || role == "toolResult" || role == "bashExecution"
}

private fun resolvePiLeafActivity(role: String, stopReason: String?): AgentThreadActivity? {
  return when (role) {
    "user", "toolResult", "bashExecution" -> AgentThreadActivity.PROCESSING
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

private data class PiUsageMessage(
  val role: String?,
  val provider: String?,
  val model: String?,
  val usage: PiUsageTokens?,
)

private data class PiUsageTokens(
  val inputTokens: Long,
  val outputTokens: Long,
  val cacheReadTokens: Long,
  val cacheWriteTokens: Long,
  val totalTokens: Long,
  val nativeExactCostUsd: BigDecimal?,
) {
  fun withTotalTokensFallback(): PiUsageTokens {
    if (outputTokens != 0L) return this
    val knownTokens = inputTokens + cacheReadTokens + cacheWriteTokens
    val missingTokens = (totalTokens - knownTokens).coerceAtLeast(0L)
    return if (missingTokens == 0L) this else copy(outputTokens = missingTokens)
  }

  fun hasTokens(): Boolean {
    return inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens > 0L
  }

  fun nativeExactCostUsdForProvider(provider: String?): BigDecimal? {
    val exactCost = nativeExactCostUsd ?: return null
    if (hasTokens() && exactCost.signum() == 0 && provider?.equals(PI_JBCENTRAL_PROVIDER_NAME, ignoreCase = true) == true) {
      return null
    }
    return exactCost
  }
}

private data class PiOutlineMessage(
  val role: String?,
  val text: String?,
  val timestamp: Long?,
  val exitCode: Long?,
  val toolCalls: List<PiOutlineToolCall>,
)

private data class PiOutlineContent(
  val text: String? = null,
  val toolCalls: List<PiOutlineToolCall> = emptyList(),
  val exitCode: Long? = null,
)

private data class PiOutlineToolCall(
  val id: String?,
  val name: String?,
  val preview: String?,
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

private fun parsePiOutlineMessage(parser: JsonParser): PiOutlineMessage? {
  if (parser.currentToken() != JsonToken.START_OBJECT) {
    parser.skipChildren()
    return null
  }
  var role: String? = null
  var content = PiOutlineContent()
  var timestamp: Long? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "role" -> role = readJsonStringOrNull(parser)
      "content" -> content = readPiOutlineContent(parser)
      "timestamp" -> timestamp = readJsonLongOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }
  return PiOutlineMessage(
    role = role,
    text = content.text,
    timestamp = timestamp,
    exitCode = content.exitCode,
    toolCalls = content.toolCalls,
  )
}

private fun readPiOutlineTextValue(parser: JsonParser): String? {
  return when (parser.currentToken()) {
    JsonToken.VALUE_STRING -> readJsonStringOrNull(parser)
    JsonToken.START_ARRAY,
    JsonToken.START_OBJECT,
      -> readPiOutlineContent(parser).text
    else -> {
      parser.skipChildren()
      null
    }
  }
}

private fun readPiOutlineContent(parser: JsonParser): PiOutlineContent {
  return when (parser.currentToken()) {
    JsonToken.VALUE_STRING -> PiOutlineContent(text = readJsonStringOrNull(parser))
    JsonToken.START_ARRAY -> readPiOutlineContentArray(parser)
    JsonToken.START_OBJECT -> readPiOutlineContentBlock(parser)
    else -> {
      parser.skipChildren()
      PiOutlineContent()
    }
  }
}

private fun readPiOutlineContentArray(parser: JsonParser): PiOutlineContent {
  val texts = ArrayList<String>()
  val toolCalls = ArrayList<PiOutlineToolCall>()
  var exitCode: Long? = null
  while (parser.nextToken() != null) {
    when (parser.currentToken()) {
      JsonToken.END_ARRAY -> break
      JsonToken.VALUE_STRING -> readJsonStringOrNull(parser)?.let(texts::add)
      JsonToken.START_OBJECT -> {
        val block = readPiOutlineContentBlock(parser)
        block.text?.let(texts::add)
        toolCalls += block.toolCalls
        exitCode = block.exitCode ?: exitCode
      }
      else -> parser.skipChildren()
    }
  }
  return PiOutlineContent(
    text = texts.joinToString(" ").takeIf { it.isNotBlank() },
    toolCalls = toolCalls,
    exitCode = exitCode,
  )
}

private fun readPiOutlineContentBlock(parser: JsonParser): PiOutlineContent {
  var type: String? = null
  var id: String? = null
  var name: String? = null
  var text: String? = null
  var contentText: String? = null
  var inputPreview: String? = null
  var exitCode: Long? = null
  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "type" -> type = readJsonStringOrNull(parser)
      "id", "toolUseId", "tool_use_id" -> id = readJsonStringOrNull(parser) ?: id
      "name" -> name = readJsonStringOrNull(parser)
      "text" -> text = readJsonStringOrNull(parser)
      "content" -> contentText = readPiOutlineTextValue(parser) ?: contentText
      "input" -> inputPreview = readPiOutlineInputPreview(parser)
      "exitCode", "exit_code" -> exitCode = readJsonLongOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }

  val normalizedType = type?.trim().orEmpty()
  if (isPiToolUseContentType(normalizedType)) {
    return PiOutlineContent(
      toolCalls = listOf(
        PiOutlineToolCall(
          id = id?.trim()?.takeIf { it.isNotEmpty() },
          name = name?.normalizePiSessionTitle(),
          preview = inputPreview,
        )
      )
    )
  }

  return PiOutlineContent(
    text = (text ?: contentText)?.takeIf { it.isNotBlank() },
    exitCode = exitCode,
  )
}

private fun readPiOutlineInputPreview(parser: JsonParser): String? {
  return when (parser.currentToken()) {
    JsonToken.VALUE_STRING -> readJsonStringOrNull(parser)
    JsonToken.START_OBJECT -> {
      var result: String? = null
      forEachJsonObjectField(parser) { fieldName ->
        when (fieldName) {
          "cmd", "command", "description", "file_path", "path" -> {
            if (result == null) {
              result = readJsonStringOrNull(parser)
            }
            else {
              parser.skipChildren()
            }
          }
          else -> parser.skipChildren()
        }
        true
      }
      result
    }
    else -> {
      parser.skipChildren()
      null
    }
  }
}

private fun isPiToolUseContentType(type: String): Boolean {
  return type == "tool_use" || type == "toolUse" || type == "tool-call" || type == "toolCall"
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

private fun readJsonNonNegativeLongOrZero(parser: JsonParser): Long {
  return readJsonLongOrNull(parser)?.takeIf { it > 0L } ?: 0L
}

private fun readJsonBigDecimalOrNull(parser: JsonParser): BigDecimal? {
  return when (parser.currentToken()) {
    JsonToken.VALUE_STRING -> parser.string?.toBigDecimalOrNull()
    JsonToken.VALUE_NUMBER_INT,
    JsonToken.VALUE_NUMBER_FLOAT,
      -> parser.numberValue?.toString()?.toBigDecimalOrNull()
    JsonToken.VALUE_NULL -> null
    else -> {
      parser.skipChildren()
      null
    }
  }
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

private fun PiSessionIndexEntry.toAgentSessionThread(readTracker: Map<String, Long>): AgentSessionThread {
  return AgentSessionThread(
    id = sessionId,
    title = title,
    updatedAt = updatedAt,
    archived = archived,
    activityReport = AgentThreadActivityReport(activity ?: resolveCompletedPiActivity(readTracker = readTracker)),
    provider = PI_AGENT_SESSION_PROVIDER,
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
    activityReport = AgentThreadActivityReport(
      activity ?: resolveCompletedPiActivity(
        readTracker = readTracker,
        completedUnreadUpdatedAtByThreadId = completedUnreadUpdatedAtByThreadId,
        observedUpdatedAtByThreadId = observedUpdatedAtByThreadId,
      )
    ),
    provider = PI_AGENT_SESSION_PROVIDER,
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
  return normalizeAgentSessionProjectPath(path)
}

private fun String.normalizePiSessionTitle(): String? {
  return normalizeAgentSessionTitle(this)
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

private fun String.toPiSessionFileTimestamp(): String {
  return replace(':', '-').replace('.', '-')
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

private val PI_LEADING_SEPARATOR = Regex("^[/\\\\]+")
private val PI_SESSION_DIR_UNSAFE_CHARS = Regex("[/\\\\:]")

private const val PI_CURRENT_SESSION_VERSION = 3
private const val PI_CONFIG_DIR = ".pi"
private const val PI_COST_MODEL_PREFIX = "[pi] "
private const val PI_AGENT_DIR_ENV = "PI_CODING_AGENT_DIR"
private const val PI_SESSION_DIR_ENV = "PI_CODING_AGENT_SESSION_DIR"
