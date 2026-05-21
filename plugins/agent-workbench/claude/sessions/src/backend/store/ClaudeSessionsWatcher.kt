// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions.backend.store

import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEvent
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEventType
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionChangeSet
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionWatcher
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionWatcherSpec
import com.intellij.agent.workbench.json.filebacked.normalizeFileBackedSessionPath
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

private val LOG = logger<ClaudeSessionsWatcher>()
private const val CLAUDE_SESSION_INDEX_FILE = "sessions-index.json"

internal class ClaudeSessionsWatcher(
  claudeHomeProvider: () -> Path,
  scope: CoroutineScope,
  onChange: (FileBackedSessionChangeSet) -> Unit,
) : AutoCloseable {
  private val projectsRoot = normalizeFileBackedSessionPath(claudeHomeProvider().resolve("projects"))
  private val watcher = FileBackedSessionWatcher(
    logger = LOG,
    watcherName = "Claude sessions",
    spec = FileBackedSessionWatcherSpec(
      roots = listOf(projectsRoot),
      eventToChangeSet = ::eventToChangeSet,
    ),
    scope = scope,
    onChange = onChange,
    failureMessage = "Claude sessions watcher failed",
  )

  override fun close() {
    watcher.close()
  }

  internal fun eventToChangeSet(event: AgentWorkbenchWatchEvent): FileBackedSessionChangeSet? {
    val eventPath = event.path
    val rootPath = event.rootPath

    if (event.eventType == AgentWorkbenchWatchEventType.OVERFLOW) {
      val isRelevant = when {
        eventPath != null && isUnderRoot(eventPath, projectsRoot) -> true
        rootPath != null && normalizeFileBackedSessionPath(rootPath) == projectsRoot -> true
        else -> false
      }
      if (!isRelevant) return null
      return FileBackedSessionChangeSet(requiresFullRescan = true)
    }

    if (eventPath != null && isJsonlPath(eventPath, projectsRoot)) {
      return FileBackedSessionChangeSet(changedPaths = setOf(normalizeFileBackedSessionPath(eventPath)))
    }
    if (eventPath != null && isIndexPath(eventPath, projectsRoot)) {
      return FileBackedSessionChangeSet(changedPaths = setOf(normalizeFileBackedSessionPath(eventPath)))
    }

    val isProjectsEvent = when {
      eventPath != null -> isUnderRoot(eventPath, projectsRoot)
      rootPath != null -> normalizeFileBackedSessionPath(rootPath) == projectsRoot
      else -> false
    }
    if (!isProjectsEvent) return null

    val isAmbiguousDirectoryEvent = event.isDirectory || eventPath == null
    if (isAmbiguousDirectoryEvent) {
      return FileBackedSessionChangeSet(requiresFullRescan = true)
    }

    // Non-JSONL files under projects: emit refresh ping.
    return FileBackedSessionChangeSet()
  }
}

private fun isUnderRoot(path: Path, normalizedRoot: Path): Boolean {
  return normalizeFileBackedSessionPath(path).startsWith(normalizedRoot)
}

private fun isJsonlPath(path: Path, normalizedRoot: Path): Boolean {
  val fileName = path.fileName?.toString() ?: return false
  return fileName.endsWith(".jsonl") && isUnderRoot(path, normalizedRoot)
}

private fun isIndexPath(path: Path, normalizedRoot: Path): Boolean {
  val fileName = path.fileName?.toString() ?: return false
  return fileName == CLAUDE_SESSION_INDEX_FILE && isUnderRoot(path, normalizedRoot)
}
