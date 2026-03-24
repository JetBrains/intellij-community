// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.rollout

import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEvent
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEventType
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionChangeSet
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionWatcher
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionWatcherSpec
import com.intellij.agent.workbench.json.filebacked.normalizeFileBackedSessionPath
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

private val LOG = logger<CodexRolloutSessionsWatcher>()

internal class CodexRolloutSessionsWatcher(
  codexHomeProvider: () -> Path,
  scope: CoroutineScope,
  onRolloutChange: (FileBackedSessionChangeSet) -> Unit,
) : AutoCloseable {
  private val codexHome = normalizeFileBackedSessionPath(codexHomeProvider())
  private val sessionsRoot = normalizeFileBackedSessionPath(codexHome.resolve("sessions"))
  private val watcher = FileBackedSessionWatcher(
    logger = LOG,
    watcherName = "Codex rollout",
    spec = FileBackedSessionWatcherSpec(
      roots = listOf(codexHome, sessionsRoot),
      eventToChangeSet = ::eventToChangeSet,
    ),
    scope = scope,
    onChange = onRolloutChange,
    failureMessage = "Rollout watcher failed",
  )

  override fun close() {
    watcher.close()
  }

  internal fun eventToChangeSet(event: AgentWorkbenchWatchEvent): FileBackedSessionChangeSet? {
    val eventPath = event.path
    val rootPath = event.rootPath
    if (event.eventType == AgentWorkbenchWatchEventType.OVERFLOW) {
      val isRelevantOverflow = when {
        eventPath != null && isSessionsPath(eventPath) -> true
        rootPath != null && isRelevantWatcherRoot(rootPath) -> true
        else -> false
      }
      if (!isRelevantOverflow) return null
      return FileBackedSessionChangeSet(requiresFullRescan = true)
    }

    if (eventPath != null && isRolloutPath(eventPath)) {
      return FileBackedSessionChangeSet(changedPaths = setOf(normalizeFileBackedSessionPath(eventPath)))
    }

    // Prefer eventPath when present so codex-home root events outside sessions
    // (for example ~/.codex/config.toml) do not trigger session refresh.
    val isSessionsEvent = when {
      eventPath != null -> isSessionsPath(eventPath)
      rootPath != null -> isRelevantWatcherRoot(rootPath)
      else -> false
    }
    if (!isSessionsEvent) return null

    // Directory-level and path-less events are ambiguous, so force reparse.
    val isAmbiguousDirectoryEvent = event.isDirectory || eventPath == null
    if (isAmbiguousDirectoryEvent) {
      return FileBackedSessionChangeSet(requiresFullRescan = true)
    }

    // For non-rollout files under sessions (e.g. temp files used by atomic rewrite),
    // emit a refresh ping. The index will re-scan file stats without forcing full reparse.
    return FileBackedSessionChangeSet()
  }

  private fun isSessionsPath(path: Path): Boolean {
    val normalizedPath = normalizeFileBackedSessionPath(path)
    return normalizedPath.startsWith(sessionsRoot)
  }

  private fun isRolloutPath(path: Path): Boolean {
    val fileName = path.fileName?.toString() ?: return false
    return isSessionsPath(path) && isRolloutFileName(fileName)
  }

  private fun isRelevantWatcherRoot(path: Path): Boolean {
    val normalizedPath = normalizeFileBackedSessionPath(path)
    return normalizedPath == codexHome || normalizedPath == sessionsRoot || isSessionsPath(normalizedPath)
  }
}
