// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.junie.sessions

import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.parseAgentWorkbenchPathOrNull
import com.intellij.platform.ai.agent.json.filebacked.FileBackedSessionChangeSet
import com.intellij.platform.ai.agent.json.filebacked.createFileBackedSessionChangeFlow
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.nio.file.Path

private val UPDATE_LOG = logger<JunieSessionUpdateBackend>()

internal class JunieSessionUpdateBackend(
  private val sessionIndexStore: JunieSessionIndexStore,
  private val eventsAnalyzer: JunieSessionEventsAnalyzer,
  private val sessionsRootPathProvider: () -> Path = ::defaultJunieSessionsRootPath,
  changeSource: (() -> Flow<FileBackedSessionChangeSet>)? = null,
) {
  val sessionUpdates: Flow<AgentSessionSourceUpdateEvent> = createUpdatesFlow(
    changeSource?.invoke() ?: createWatcherUpdates()
  ).conflate()

  fun activeThreadUpdateEvents(path: String, threadId: String): Flow<AgentSessionSourceUpdateEvent> {
    val normalizedPath = normalizeJunieProjectPath(path)
    return sessionUpdates.filter { update ->
      val scopedPaths = update.scopedPaths
      val threadIds = update.threadIds
      (normalizedPath == null || scopedPaths == null || normalizedPath in scopedPaths) &&
      (threadIds == null || threadId in threadIds)
    }
  }

  internal fun buildSessionUpdate(changeSet: FileBackedSessionChangeSet): AgentSessionSourceUpdateEvent {
    if (changeSet.requiresFullRescan) {
      UPDATE_LOG.debug { "Junie sessions update is unscoped (fullRescan=true, changedPaths=${changeSet.changedPaths.size})" }
      return JUNIE_THREADS_CHANGED_UPDATE
    }

    if (changeSet.changedPaths.isEmpty()) {
      UPDATE_LOG.debug { "Junie sessions update is unscoped (refresh ping)" }
      return JUNIE_THREADS_CHANGED_UPDATE
    }

    val changedPaths = changeSet.changedPaths
    if (changedPaths.any(::isJunieIndexPath) || changedPaths.any(::isJunieArchiveStatePath)) {
      val scopedPaths = sessionIndexStore.loadEntries()
        .mapTo(LinkedHashSet()) { entry -> entry.normalizedProjectDir }
      UPDATE_LOG.debug { "Junie index update scopedPaths=${scopedPaths.size}" }
      return AgentSessionSourceUpdateEvent.threadsChanged(
        scopedPaths = scopedPaths.takeIf { it.isNotEmpty() },
      )
    }

    val eventsPaths = changedPaths.filter(::isJunieEventsPath)
    if (eventsPaths.isEmpty()) {
      UPDATE_LOG.debug { "Junie sessions update is unscoped (no event/index paths in changedPaths=${changedPaths.size})" }
      return JUNIE_THREADS_CHANGED_UPDATE
    }

    return buildEventsUpdate(eventsPaths)
  }

  private fun createUpdatesFlow(sourceUpdates: Flow<FileBackedSessionChangeSet>): Flow<AgentSessionSourceUpdateEvent> {
    return sourceUpdates.map { changeSet ->
      withContext(Dispatchers.IO) {
        buildSessionUpdate(changeSet)
      }
    }
  }

  private fun createWatcherUpdates(): Flow<FileBackedSessionChangeSet> {
    return createFileBackedSessionChangeFlow(
      logger = UPDATE_LOG,
      watcherName = "Junie sessions",
      initContext = { "sessionsRoot=${sessionsRootPathProvider()}" },
      emitInitialRefreshPing = true,
    ) { scope, onChange ->
      JunieSessionsWatcher(
        sessionsRootPathProvider = sessionsRootPathProvider,
        scope = scope,
        onChange = onChange,
      )
    }
  }

  private fun buildEventsUpdate(eventsPaths: List<Path>): AgentSessionSourceUpdateEvent {
    val entriesBySessionId = sessionIndexStore.loadEntries().associateBy(JunieSessionIndexEntry::sessionId)
    val scopedPaths = LinkedHashSet<String>()
    val threadIds = LinkedHashSet<String>()
    var mayHaveChangedProjectFiles = false
    var changedProjectFilePaths: LinkedHashSet<String>? = LinkedHashSet()

    for (eventsPath in eventsPaths) {
      val sessionId = resolveJunieSessionId(eventsPath) ?: continue
      threadIds += sessionId
      val entry = entriesBySessionId[sessionId]
      if (entry != null) {
        scopedPaths += entry.normalizedProjectDir
      }
      val analysis = eventsAnalyzer.loadAnalysis(sessionId) ?: continue
      val rawChangedProjectFilePaths = analysis.changedProjectFilePaths ?: continue
      val resolvedChangedProjectFilePaths = resolveChangedProjectFilePaths(
        paths = rawChangedProjectFilePaths,
        projectPath = entry?.normalizedProjectDir,
      )
      mayHaveChangedProjectFiles = true
      if (resolvedChangedProjectFilePaths == null) {
        changedProjectFilePaths = null
      }
      else {
        changedProjectFilePaths?.addAll(resolvedChangedProjectFilePaths)
      }
    }

    UPDATE_LOG.debug {
      "Junie events update scopedPaths=${scopedPaths.size}, threadIds=${threadIds.size}, changedProjectFiles=$mayHaveChangedProjectFiles"
    }
    return AgentSessionSourceUpdateEvent.hintsChanged(
      scopedPaths = scopedPaths.takeIf { it.isNotEmpty() },
      threadIds = threadIds.takeIf { it.isNotEmpty() },
      mayHaveChangedProjectFiles = mayHaveChangedProjectFiles,
      changedProjectFilePaths = changedProjectFilePathsForUpdate(mayHaveChangedProjectFiles, changedProjectFilePaths),
    )
  }
}

private val JUNIE_THREADS_CHANGED_UPDATE = AgentSessionSourceUpdateEvent.threadsChanged()

private fun isJunieIndexPath(path: Path): Boolean {
  return path.fileName?.toString() == JUNIE_INDEX_FILE_NAME
}

private fun isJunieArchiveStatePath(path: Path): Boolean {
  return path.fileName?.toString() == JUNIE_AGENT_WORKBENCH_ARCHIVE_STATE_FILE
}

private fun isJunieEventsPath(path: Path): Boolean {
  return path.fileName?.toString() == JUNIE_EVENTS_FILE_NAME
}

private fun resolveJunieSessionId(eventsPath: Path): String? {
  if (!isJunieEventsPath(eventsPath)) return null
  return eventsPath.parent?.fileName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}

private fun changedProjectFilePathsForUpdate(
  mayHaveChangedProjectFiles: Boolean,
  changedProjectFilePaths: Set<String>?,
): Set<String>? {
  if (!mayHaveChangedProjectFiles) {
    return null
  }
  return changedProjectFilePaths?.takeIf { it.isNotEmpty() }
}

private fun resolveChangedProjectFilePaths(paths: Set<String>, projectPath: String?): Set<String>? {
  val result = LinkedHashSet<String>()
  for (path in paths) {
    val parsedPath = parseAgentWorkbenchPathOrNull(path)?.normalize() ?: return null
    val resolvedPath = if (parsedPath.isAbsolute) {
      parsedPath
    }
    else {
      val parsedProjectPath = projectPath?.let(::parseAgentWorkbenchPathOrNull)?.takeIf { it.isAbsolute } ?: return null
      parsedProjectPath.resolve(parsedPath).normalize()
    }
    result.add(normalizeAgentWorkbenchPath(resolvedPath.toString()))
  }
  return result.takeIf { it.isNotEmpty() }
}
