// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions.filewatch

import com.intellij.agent.workbench.filewatch.AgentWorkbenchDirectoryWatcher
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEvent
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEventType
import com.intellij.agent.workbench.pi.sessions.PiSessionUpdateEventsContributor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import java.nio.file.Path

private val LOG = logger<PiSessionFileWatchUpdateEventsContributor>()

internal class PiSessionFileWatchUpdateEventsContributor(
  private val sessionWatchEventsFactory: (Set<Path>) -> Flow<AgentWorkbenchWatchEvent> = { roots -> createPiSessionWatchEvents(roots) },
) : PiSessionUpdateEventsContributor {
  override fun createUpdateEvents(
    watchedProjectPathsBySessionDir: StateFlow<Map<Path, Set<String>>>,
  ): Flow<AgentSessionSourceUpdateEvent> {
    return watchPiSessionUpdates(watchedProjectPathsBySessionDir, sessionWatchEventsFactory)
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun watchPiSessionUpdates(
  watchedProjectPathsBySessionDir: StateFlow<Map<Path, Set<String>>>,
  sessionWatchEventsFactory: (Set<Path>) -> Flow<AgentWorkbenchWatchEvent>,
): Flow<AgentSessionSourceUpdateEvent> {
  return watchedProjectPathsBySessionDir.flatMapLatest { projectPathsBySessionDir ->
    if (projectPathsBySessionDir.isEmpty()) {
      emptyFlow()
    }
    else {
      val normalizedProjectPathsBySessionDir = projectPathsBySessionDir.mapKeys { (sessionDir, _) -> normalizePiWatchPath(sessionDir) }
      sessionWatchEventsFactory(createPiSessionWatchRoots(normalizedProjectPathsBySessionDir.keys))
        .mapNotNull { event -> createPiSessionSourceUpdateEventForWatchEvent(event, normalizedProjectPathsBySessionDir) }
    }
  }
}

private fun createPiSessionWatchRoots(sessionDirs: Collection<Path>): Set<Path> {
  val roots = LinkedHashSet<Path>()
  for (sessionDir in sessionDirs) {
    roots.add(sessionDir)
    sessionDir.parent?.let(roots::add)
  }
  return roots
}

private fun createPiSessionWatchEvents(sessionDirs: Set<Path>): Flow<AgentWorkbenchWatchEvent> {
  if (sessionDirs.isEmpty()) return emptyFlow()
  return callbackFlow {
    val watcher = AgentWorkbenchDirectoryWatcher(
      roots = sessionDirs,
      scope = this,
      onWatchEvent = { event -> trySend(event).isSuccess },
      onFailure = { t -> LOG.warn("Pi session directory watcher failed", t) },
    )
    awaitClose { watcher.close() }
  }
}

internal fun createPiSessionSourceUpdateEventForWatchEvent(
  event: AgentWorkbenchWatchEvent,
  projectPathsBySessionDir: Map<Path, Set<String>>,
): AgentSessionSourceUpdateEvent? {
  val rootPath = event.rootPath?.let(::normalizePiWatchPath) ?: return null
  val eventPath = event.path?.let(::normalizePiWatchPath)
  val scopedPaths = collectScopedPathsForPiWatchEvent(
    rootPath = rootPath,
    eventPath = eventPath,
    isOverflow = event.eventType == AgentWorkbenchWatchEventType.OVERFLOW,
    projectPathsBySessionDir = projectPathsBySessionDir,
  ).takeIf { it.isNotEmpty() } ?: return null
  if (event.eventType == AgentWorkbenchWatchEventType.OVERFLOW) {
    return AgentSessionSourceUpdateEvent(
      type = AgentSessionSourceUpdate.THREADS_CHANGED,
      scopedPaths = scopedPaths,
    )
  }
  if (!isRelevantPiSessionWatchEvent(event, eventPath, projectPathsBySessionDir.keys)) return null
  return AgentSessionSourceUpdateEvent(
    type = AgentSessionSourceUpdate.THREADS_CHANGED,
    scopedPaths = scopedPaths,
  )
}

private fun collectScopedPathsForPiWatchEvent(
  rootPath: Path,
  eventPath: Path?,
  isOverflow: Boolean,
  projectPathsBySessionDir: Map<Path, Set<String>>,
): Set<String> {
  val scopedPaths = LinkedHashSet<String>()
  for ((sessionDir, paths) in projectPathsBySessionDir) {
    val matches = if (isOverflow) {
      rootPath == sessionDir || sessionDir.startsWith(rootPath)
    }
    else {
      eventPath != null && (eventPath == sessionDir || eventPath.startsWith(sessionDir))
    }
    if (matches) {
      scopedPaths.addAll(paths)
    }
  }
  return scopedPaths
}

private fun isRelevantPiSessionWatchEvent(event: AgentWorkbenchWatchEvent, eventPath: Path?, sessionDirs: Set<Path>): Boolean {
  if (eventPath == null) return false
  if (event.isDirectory) {
    return eventPath in sessionDirs
  }
  return isPiSessionWatchFile(eventPath)
}

private fun isPiSessionWatchFile(path: Path?): Boolean {
  val fileName = path?.fileName?.toString() ?: return false
  return fileName.endsWith(".jsonl")
}

private fun normalizePiWatchPath(path: Path): Path {
  return runCatching {
    path.toAbsolutePath().normalize()
  }.getOrElse {
    path.normalize()
  }
}
