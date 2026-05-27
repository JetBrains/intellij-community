// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.junie.sessions

import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.session.AgentSessionCost
import com.intellij.platform.ai.agent.core.normalizeAgentSessionProjectPath
import com.intellij.platform.ai.agent.core.normalizeAgentSessionTitle
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.json.createJsonGenerator
import com.intellij.platform.ai.agent.json.WorkbenchJsonlScanner
import com.intellij.platform.ai.agent.json.forEachJsonObjectField
import com.intellij.platform.ai.agent.json.readJsonLongOrNull
import com.intellij.platform.ai.agent.json.readJsonStringOrNull
import com.intellij.platform.ai.agent.sessions.core.paths.resolveAgentWorkbenchProjectDirectory
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionActiveThreadUpdateSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionArchivedSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionCostSource
import com.intellij.platform.ai.agent.sessions.core.providers.BaseAgentSessionSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRebindCandidate
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshThreadSeed
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshHintsSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionRefreshSource
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceRefreshRequest
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceRefreshResult
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadActivityUpdate
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionThreadPresentationUpdate
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionUpdateSource
import com.intellij.platform.ai.agent.sessions.core.providers.resolveReadTrackedActivity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private val LOG = logger<JunieSessionSource>()

internal interface JunieSessionThreadMutationBackend {
  fun renameThread(path: String, threadId: String, normalizedName: String): Boolean

  fun archiveThread(path: String, threadId: String): Boolean

  fun unarchiveThread(path: String, threadId: String): Boolean
}

internal class JunieSessionIndexStore(
  private val sessionIndexPathProvider: () -> Path = ::defaultJunieSessionIndexPath,
  private val jsonFactory: JsonFactory = JsonFactory(),
  private val timeProvider: () -> Long = System::currentTimeMillis,
  private val archiveStatePathProvider: () -> Path = { defaultJunieArchiveStatePath(sessionIndexPathProvider()) },
) : JunieSessionThreadMutationBackend {
  fun loadEntries(): List<JunieSessionIndexEntry> {
    val entries = loadIndexEntries(sessionIndexPathProvider())
    if (entries.isEmpty()) return emptyList()

    val archiveState = loadArchiveState(archiveStatePathProvider())
    if (archiveState.isEmpty()) return entries

    return entries.map { entry ->
      val archived = archiveState[JunieSessionArchiveKey(entry.normalizedProjectDir, entry.sessionId)] ?: return@map entry
      entry.copy(archived = archived)
    }
  }

  private fun loadIndexEntries(indexPath: Path): List<JunieSessionIndexEntry> {
    if (!Files.isRegularFile(indexPath)) return emptyList()
    return try {
      val entries = WorkbenchJsonlScanner.scanJsonObjects(
        path = indexPath,
        jsonFactory = jsonFactory,
        newState = { ArrayList<JunieSessionIndexEntry>() },
      ) { parser, state ->
        parseIndexEntry(parser)?.let(state::add)
        true
      }
      entries.latestBySessionId()
    }
    catch (e: Exception) {
      LOG.warn("Failed to load Junie session index: $indexPath", e)
      emptyList()
    }
  }

  private fun loadArchiveState(archiveStatePath: Path): Map<JunieSessionArchiveKey, Boolean> {
    if (!Files.isRegularFile(archiveStatePath)) return emptyMap()
    return try {
      WorkbenchJsonlScanner.scanJsonObjects(
        path = archiveStatePath,
        jsonFactory = jsonFactory,
        newState = { LinkedHashMap() },
      ) { parser, state ->
        parseArchiveStateEntry(parser)?.let { (key, archived) -> state[key] = archived }
        true
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to load Junie archive state: $archiveStatePath", e)
      emptyMap()
    }
  }

  override fun renameThread(path: String, threadId: String, normalizedName: String): Boolean {
    val updatedEntry = buildUpdatedEntry(path, threadId) { entry -> entry.copy(title = normalizedName) } ?: return false
    return appendIndexEntry(updatedEntry)
  }

  override fun archiveThread(path: String, threadId: String): Boolean {
    val updatedEntry = buildUpdatedEntry(path, threadId) { entry -> entry.copy(archived = true) } ?: return false
    val archiveStateUpdated = appendArchiveStateEntry(updatedEntry)
    appendIndexEntry(updatedEntry)
    return archiveStateUpdated
  }

  override fun unarchiveThread(path: String, threadId: String): Boolean {
    val updatedEntry = buildUpdatedEntry(path, threadId) { entry -> entry.copy(archived = false) } ?: return false
    val archiveStateUpdated = appendArchiveStateEntry(updatedEntry)
    appendIndexEntry(updatedEntry)
    return archiveStateUpdated
  }

  private fun buildUpdatedEntry(
    path: String,
    threadId: String,
    update: (JunieSessionIndexEntry) -> JunieSessionIndexEntry,
  ): JunieSessionIndexEntry? {
    val normalizedProjectPath = normalizeJunieProjectPath(path) ?: return null
    val currentEntry = loadEntries().firstOrNull { entry ->
      entry.sessionId == threadId && entry.normalizedProjectDir == normalizedProjectPath
    } ?: return null
    val updatedAt = maxOf(timeProvider(), currentEntry.updatedAt)
    return update(currentEntry).copy(updatedAt = updatedAt)
  }

  private fun appendIndexEntry(updatedEntry: JunieSessionIndexEntry): Boolean {
    val indexPath = sessionIndexPathProvider()
    return try {
      indexPath.parent?.let(Files::createDirectories)
      Files.writeString(
        indexPath,
        buildIndexLine(updatedEntry) + "\n",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
      )
      true
    }
    catch (e: Exception) {
      LOG.warn("Failed to update Junie session index: $indexPath", e)
      false
    }
  }

  private fun appendArchiveStateEntry(updatedEntry: JunieSessionIndexEntry): Boolean {
    val archiveStatePath = archiveStatePathProvider()
    return try {
      archiveStatePath.parent?.let(Files::createDirectories)
      Files.writeString(
        archiveStatePath,
        buildArchiveStateLine(updatedEntry) + "\n",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND,
      )
      true
    }
    catch (e: Exception) {
      LOG.warn("Failed to update Junie archive state: $archiveStatePath", e)
      false
    }
  }

  private fun buildIndexLine(entry: JunieSessionIndexEntry): String {
    val writer = StringWriter()
    jsonFactory.createJsonGenerator(writer).use { generator ->
      generator.writeStartObject()
      generator.writeStringProperty("sessionId", entry.sessionId)
      generator.writeNumberProperty("createdAt", entry.createdAt)
      generator.writeNumberProperty("updatedAt", entry.updatedAt)
      generator.writeStringProperty("projectDir", entry.projectDir)
      generator.writeStringProperty("taskName", entry.title)
      generator.writeBooleanProperty("archived", entry.archived == true)
      generator.writeEndObject()
    }
    return writer.toString()
  }

  private fun buildArchiveStateLine(entry: JunieSessionIndexEntry): String {
    val writer = StringWriter()
    jsonFactory.createJsonGenerator(writer).use { generator ->
      generator.writeStartObject()
      generator.writeStringProperty("sessionId", entry.sessionId)
      generator.writeNumberProperty("updatedAt", entry.updatedAt)
      generator.writeStringProperty("projectDir", entry.normalizedProjectDir)
      generator.writeBooleanProperty("archived", entry.archived == true)
      generator.writeEndObject()
    }
    return writer.toString()
  }
}

internal class JunieSessionSource(
  internal val sessionIndexStore: JunieSessionIndexStore = JunieSessionIndexStore(),
  private val eventsAnalyzer: JunieSessionEventsAnalyzer = JunieSessionEventsAnalyzer(),
  private val updateBackend: JunieSessionUpdateBackend = JunieSessionUpdateBackend(
    sessionIndexStore = sessionIndexStore,
    eventsAnalyzer = eventsAnalyzer,
  ),
) : BaseAgentSessionSource(provider = JUNIE_AGENT_SESSION_PROVIDER),
    AgentSessionUpdateSource,
    AgentSessionActiveThreadUpdateSource,
    AgentSessionArchivedSource,
    AgentSessionRefreshSource,
    AgentSessionRefreshHintsSource,
    AgentSessionCostSource {
  constructor(
    sessionIndexPathProvider: () -> Path,
    jsonFactory: JsonFactory = JsonFactory(),
    timeProvider: () -> Long = System::currentTimeMillis,
    archiveStatePathProvider: () -> Path = { defaultJunieArchiveStatePath(sessionIndexPathProvider()) },
  ) : this(
    sessionIndexStore = JunieSessionIndexStore(sessionIndexPathProvider, jsonFactory, timeProvider, archiveStatePathProvider),
    eventsAnalyzer = JunieSessionEventsAnalyzer(
      sessionsRootPathProvider = { sessionIndexPathProvider().parent ?: defaultJunieSessionsRootPath() },
      jsonFactory = jsonFactory,
    ),
  )

  override val updateEvents: Flow<AgentSessionSourceUpdateEvent>
    get() = merge(
      updateBackend.sessionUpdates,
      readStateUpdateEvents,
    )

  override fun activeThreadUpdateEvents(path: String, threadId: String): Flow<AgentSessionSourceUpdateEvent> {
    return updateBackend.activeThreadUpdateEvents(path = path, threadId = threadId)
  }

  override suspend fun loadThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    val normalizedProjectPath = normalizeJunieProjectPath(path) ?: return emptyList()
    val matchingEntries = sessionIndexStore.loadEntries()
      .filter { it.normalizedProjectDir == normalizedProjectPath }
      .filterNot { it.archived == true }
      .sortedByDescending(JunieSessionIndexEntry::updatedAt)
    rememberActiveThreadRead(matchingEntries, JunieSessionIndexEntry::sessionId, JunieSessionIndexEntry::updatedAt)
    return matchingEntries.map { it.toAgentSessionThread(readTracker, eventsAnalyzer.cachedAnalysis(it.sessionId)) }
  }

  override suspend fun listArchivedThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    val normalizedProjectPath = normalizeJunieProjectPath(path) ?: return emptyList()
    val matchingEntries = sessionIndexStore.loadEntries()
      .filter { it.normalizedProjectDir == normalizedProjectPath }
      .filter { it.archived == true }
      .sortedByDescending(JunieSessionIndexEntry::updatedAt)
    return matchingEntries.map { it.toAgentSessionThread(readTracker, eventsAnalyzer.cachedAnalysis(it.sessionId)) }
  }

  override suspend fun refreshThreads(request: AgentSessionSourceRefreshRequest): AgentSessionSourceRefreshResult {
    if (!request.isThreadScoped) {
      return refreshThreadsByListing(request)
    }

    val partialThreadsByPath = LinkedHashMap<String, List<AgentSessionThread>>()
    val completeThreadsByPath = LinkedHashMap<String, List<AgentSessionThread>>()
    val removedThreadIdsByPath = LinkedHashMap<String, Set<String>>()
    val failuresByPath = LinkedHashMap<String, Throwable>()
    for (path in request.paths) {
      try {
        val entries = loadEntries(path = request.sourcePathFor(path))
          .filter { entry -> entry.sessionId in request.threadIds }
        val visibleEntries = entries.filterNot { entry -> entry.archived == true }
        rememberActiveThreadRead(visibleEntries, JunieSessionIndexEntry::sessionId, JunieSessionIndexEntry::updatedAt)
        partialThreadsByPath[path] = visibleEntries.map { entry ->
          entry.toAgentSessionThread(readTracker, eventsAnalyzer.loadAnalysis(entry.sessionId))
        }
        val removedThreadIds = entries.asSequence()
          .filter { entry -> entry.archived == true }
          .map(JunieSessionIndexEntry::sessionId)
          .toCollection(LinkedHashSet())
        if (removedThreadIds.isNotEmpty()) {
          removedThreadIdsByPath[path] = removedThreadIds
        }
      }
      catch (e: Throwable) {
        if (e is CancellationException) throw e
        failuresByPath[path] = e
      }
    }

    return AgentSessionSourceRefreshResult(
      completeThreadsByPath = completeThreadsByPath,
      partialThreadsByPath = partialThreadsByPath,
      removedThreadIdsByPath = removedThreadIdsByPath,
      failuresByPath = failuresByPath,
    )
  }

  override suspend fun prefetchRefreshHints(
    paths: List<String>,
    refreshThreadSeedsByPath: Map<String, Set<AgentSessionRefreshThreadSeed>>,
  ): Map<String, AgentSessionRefreshHints> {
    if (paths.isEmpty()) return emptyMap()

    val result = LinkedHashMap<String, AgentSessionRefreshHints>(paths.size)
    for (path in paths) {
      val knownThreadIds = refreshThreadSeedsByPath[path].orEmpty().asSequence().map { seed -> seed.threadId }.toCollection(LinkedHashSet())
      val visibleEntries = loadEntries(path = path).filterNot { entry -> entry.archived == true }
      if (visibleEntries.isEmpty()) {
        continue
      }

      val rebindCandidates = ArrayList<AgentSessionRebindCandidate>()
      val activityUpdatesByThreadId = LinkedHashMap<String, AgentSessionThreadActivityUpdate>()
      val presentationUpdatesByThreadId = LinkedHashMap<String, AgentSessionThreadPresentationUpdate>()
      for (entry in visibleEntries) {
        if (entry.sessionId in knownThreadIds) {
          val thread = entry.toAgentSessionThread(readTracker, eventsAnalyzer.loadAnalysis(entry.sessionId))
          activityUpdatesByThreadId[entry.sessionId] = AgentSessionThreadActivityUpdate(
            activityReport = thread.activityReport,
            updatedAt = thread.updatedAt,
          )
          presentationUpdatesByThreadId[entry.sessionId] = AgentSessionThreadPresentationUpdate(
            title = thread.title,
            activityReport = thread.activityReport,
            updatedAt = thread.updatedAt,
          )
        }
        else {
          val thread = entry.toAgentSessionThread(readTracker, eventsAnalyzer.cachedAnalysis(entry.sessionId))
          rebindCandidates += AgentSessionRebindCandidate(
            threadId = thread.id,
            title = thread.title,
            updatedAt = thread.updatedAt,
            activity = thread.activity,
          )
        }
      }

      if (rebindCandidates.isNotEmpty() || activityUpdatesByThreadId.isNotEmpty() || presentationUpdatesByThreadId.isNotEmpty()) {
        result[path] = AgentSessionRefreshHints(
          rebindCandidates = rebindCandidates,
          activityUpdatesByThreadId = activityUpdatesByThreadId,
          presentationUpdatesByThreadId = presentationUpdatesByThreadId,
        )
      }
    }
    return result
  }

  override suspend fun loadThreadCosts(
    path: String,
    threads: List<AgentSessionThread>,
  ): Map<String, AgentSessionCost?> {
    if (threads.isEmpty()) {
      return emptyMap()
    }

    return threads.associate { thread ->
      thread.id to eventsAnalyzer.loadCost(thread.id, thread.updatedAt)
    }
  }

  private fun loadEntries(path: String): List<JunieSessionIndexEntry> {
    val normalizedProjectPath = normalizeJunieProjectPath(path) ?: return emptyList()
    return sessionIndexStore.loadEntries()
      .filter { entry -> entry.normalizedProjectDir == normalizedProjectPath }
      .sortedByDescending(JunieSessionIndexEntry::updatedAt)
  }
}

internal data class JunieSessionIndexEntry(
  val sessionId: String,
  val title: String,
  val createdAt: Long,
  val updatedAt: Long,
  val projectDir: String,
  val normalizedProjectDir: String,
  val archived: Boolean?,
)

private data class JunieSessionArchiveKey(
  val normalizedProjectDir: String,
  val sessionId: String,
)

private fun defaultJunieSessionIndexPath(): Path {
  return Path.of(System.getProperty("user.home") ?: ".", ".junie", "sessions", JUNIE_INDEX_FILE_NAME)
}

private fun defaultJunieArchiveStatePath(sessionIndexPath: Path): Path {
  return sessionIndexPath.parent?.resolve(JUNIE_AGENT_WORKBENCH_ARCHIVE_STATE_FILE)
         ?: sessionIndexPath.resolveSibling(JUNIE_AGENT_WORKBENCH_ARCHIVE_STATE_FILE)
}

private fun parseIndexEntry(parser: JsonParser): JunieSessionIndexEntry? {
  var sessionId: String? = null
  var createdAt: Long? = null
  var updatedAt: Long? = null
  var projectDir: String? = null
  var taskName: String? = null
  var archived: Boolean? = null

  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "sessionId" -> sessionId = readJsonStringOrNull(parser)
      "createdAt" -> createdAt = readJsonLongOrNull(parser)
      "updatedAt" -> updatedAt = readJsonLongOrNull(parser)
      "projectDir" -> projectDir = readJsonStringOrNull(parser)
      "taskName" -> taskName = readJsonStringOrNull(parser)
      "archived" -> archived = readJsonBooleanOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }

  val normalizedSessionId = sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val resolvedProjectDir = projectDir?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val normalizedProjectDir = normalizeJunieProjectPath(resolvedProjectDir) ?: return null
  val resolvedUpdatedAt = updatedAt ?: createdAt ?: 0L
  val resolvedCreatedAt = createdAt ?: resolvedUpdatedAt
  val resolvedTitle = taskName?.normalizeJunieSessionTitle() ?: normalizedSessionId
  return JunieSessionIndexEntry(
    sessionId = normalizedSessionId,
    title = resolvedTitle,
    createdAt = resolvedCreatedAt,
    updatedAt = resolvedUpdatedAt,
    projectDir = resolvedProjectDir,
    normalizedProjectDir = normalizedProjectDir,
    archived = archived,
  )
}

private fun parseArchiveStateEntry(parser: JsonParser): Pair<JunieSessionArchiveKey, Boolean>? {
  var sessionId: String? = null
  var projectDir: String? = null
  var archived: Boolean? = null

  forEachJsonObjectField(parser) { fieldName ->
    when (fieldName) {
      "sessionId" -> sessionId = readJsonStringOrNull(parser)
      "projectDir" -> projectDir = readJsonStringOrNull(parser)
      "archived" -> archived = readJsonBooleanOrNull(parser)
      else -> parser.skipChildren()
    }
    true
  }

  val normalizedSessionId = sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  val normalizedProjectDir = projectDir?.let(::normalizeJunieProjectPath) ?: return null
  val resolvedArchived = archived ?: return null
  return JunieSessionArchiveKey(normalizedProjectDir, normalizedSessionId) to resolvedArchived
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

private fun List<JunieSessionIndexEntry>.latestBySessionId(): List<JunieSessionIndexEntry> {
  val latestBySessionId = LinkedHashMap<String, JunieSessionIndexEntry>()
  val archiveStateBySessionId = LinkedHashMap<String, Boolean>()
  for (entry in this) {
    entry.archived?.let { archived -> archiveStateBySessionId[entry.sessionId] = archived }
    val previous = latestBySessionId[entry.sessionId]
    if (previous == null || entry.updatedAt >= previous.updatedAt) {
      latestBySessionId[entry.sessionId] = entry
    }
  }
  return latestBySessionId.values.map { entry ->
    entry.copy(archived = archiveStateBySessionId[entry.sessionId] ?: false)
  }
}

private fun JunieSessionIndexEntry.toAgentSessionThread(
  readTracker: Map<String, Long>,
  eventsAnalysis: JunieSessionEventsAnalysis?,
): AgentSessionThread {
  return AgentSessionThread(
    id = sessionId,
    title = title,
    updatedAt = updatedAt,
    archived = archived == true,
    activity = effectiveActivity(readTracker, eventsAnalysis),
    provider = JUNIE_AGENT_SESSION_PROVIDER,
  )
}

private fun JunieSessionIndexEntry.effectiveActivity(
  readTracker: Map<String, Long>,
  eventsAnalysis: JunieSessionEventsAnalysis?,
): AgentThreadActivity {
  if (eventsAnalysis?.activity == JunieSessionEventsActivity.PROCESSING) {
    return AgentThreadActivity.PROCESSING
  }
  return resolveReadTrackedActivity(readTracker = readTracker, threadId = sessionId, updatedAt = updatedAt)
}

internal fun normalizeJunieProjectPath(path: String): String? {
  val normalizedPath = normalizeAgentSessionProjectPath(path) ?: return null
  return resolveAgentWorkbenchProjectDirectory(identityPath = normalizedPath) ?: normalizedPath
}

private fun String.normalizeJunieSessionTitle(): String? {
  return normalizeAgentSessionTitle(this)
}

internal const val JUNIE_INDEX_FILE_NAME = "index.jsonl"
internal const val JUNIE_AGENT_WORKBENCH_ARCHIVE_STATE_FILE = "agent-workbench-archive-state.jsonl"
