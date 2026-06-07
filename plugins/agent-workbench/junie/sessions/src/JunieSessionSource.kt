// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.junie.sessions

import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.json.JsonFactory
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.json.createJsonGenerator
import com.intellij.agent.workbench.json.WorkbenchJsonlScanner
import com.intellij.agent.workbench.json.forEachJsonObjectField
import com.intellij.agent.workbench.json.readJsonLongOrNull
import com.intellij.agent.workbench.json.readJsonStringOrNull
import com.intellij.agent.workbench.sessions.core.providers.BaseAgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.resolveReadTrackedActivity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
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
  private val costLoader: JunieSessionCostLoader = JunieSessionCostLoader(),
) : BaseAgentSessionSource(provider = AgentSessionProvider.JUNIE) {
  constructor(
    sessionIndexPathProvider: () -> Path,
    jsonFactory: JsonFactory = JsonFactory(),
    timeProvider: () -> Long = System::currentTimeMillis,
    archiveStatePathProvider: () -> Path = { defaultJunieArchiveStatePath(sessionIndexPathProvider()) },
  ) : this(
    sessionIndexStore = JunieSessionIndexStore(sessionIndexPathProvider, jsonFactory, timeProvider, archiveStatePathProvider),
    costLoader = JunieSessionCostLoader(
      sessionsRootPathProvider = { sessionIndexPathProvider().parent ?: defaultJunieSessionsRootPath() },
      jsonFactory = jsonFactory,
    ),
  )

  override val supportsArchivedThreads: Boolean get() = true

  override suspend fun listThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    val normalizedProjectPath = normalizeJunieProjectPath(path) ?: return emptyList()
    val matchingEntries = sessionIndexStore.loadEntries()
      .filter { it.normalizedProjectDir == normalizedProjectPath }
      .filterNot { it.archived == true }
      .sortedByDescending(JunieSessionIndexEntry::updatedAt)
    rememberActiveThreadRead(matchingEntries, JunieSessionIndexEntry::sessionId, JunieSessionIndexEntry::updatedAt)
    return matchingEntries.map { it.toAgentSessionThread(readTracker) }
  }

  override suspend fun listArchivedThreads(path: String, openProject: Project?): List<AgentSessionThread> {
    val normalizedProjectPath = normalizeJunieProjectPath(path) ?: return emptyList()
    val matchingEntries = sessionIndexStore.loadEntries()
      .filter { it.normalizedProjectDir == normalizedProjectPath }
      .filter { it.archived == true }
      .sortedByDescending(JunieSessionIndexEntry::updatedAt)
    return matchingEntries.map { it.toAgentSessionThread(readTracker) }
  }

  override suspend fun loadThreadCosts(
    path: String,
    threads: List<AgentSessionThread>,
  ): Map<String, AgentSessionCost?> {
    if (threads.isEmpty()) {
      return emptyMap()
    }

    return threads.associate { thread ->
      thread.id to costLoader.loadCost(thread.id)
    }
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
  return Path.of(System.getProperty("user.home") ?: ".", ".junie", "sessions", "index.jsonl")
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

private fun JunieSessionIndexEntry.toAgentSessionThread(readTracker: Map<String, Long>): AgentSessionThread {
  return AgentSessionThread(
    id = sessionId,
    title = title,
    updatedAt = updatedAt,
    archived = archived == true,
    activity = effectiveActivity(readTracker),
    provider = AgentSessionProvider.JUNIE,
  )
}

private fun JunieSessionIndexEntry.effectiveActivity(readTracker: Map<String, Long>): AgentThreadActivity {
  return resolveReadTrackedActivity(readTracker = readTracker, threadId = sessionId, updatedAt = updatedAt)
}

private fun normalizeJunieProjectPath(path: String): String? {
  val trimmedPath = path.trim().takeIf { it.isNotEmpty() } ?: return null
  val normalizedPath = normalizeAgentWorkbenchPathOrNull(trimmedPath) ?: return null
  return normalizedPath.trimEnd('/').ifEmpty { "/" }
}

private fun String.normalizeJunieSessionTitle(): String? {
  return replace('\n', ' ')
    .replace('\r', ' ')
    .replace(THREAD_TITLE_WHITESPACE, " ")
    .trim()
    .takeIf { it.isNotEmpty() }
}

private val THREAD_TITLE_WHITESPACE = Regex("\\s+")

private const val JUNIE_AGENT_WORKBENCH_ARCHIVE_STATE_FILE = "agent-workbench-archive-state.jsonl"
