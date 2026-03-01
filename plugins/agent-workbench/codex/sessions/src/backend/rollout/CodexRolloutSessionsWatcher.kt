// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend.rollout

import com.intellij.agent.workbench.filewatch.AgentWorkbenchDirectoryWatcher
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEvent
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEventType
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<CodexRolloutSessionsWatcher>()

internal data class CodexRolloutChangeSet(
  // Known rollout files that must be reparsed regardless of file stat heuristics.
  val changedRolloutPaths: Set<Path> = emptySet(),

  // Overflow/ambiguous events where file-level attribution is not reliable.
  val requiresFullRescan: Boolean = false,

  // `changedRolloutPaths.isEmpty() && !requiresFullRescan` means "refresh ping":
  // re-run stat-based scan without forcing full reparse.
)

internal class CodexRolloutSessionsWatcher(
  private val codexHomeProvider: () -> Path,
  scope: CoroutineScope,
  private val onRolloutChange: (CodexRolloutChangeSet) -> Unit,
) : AutoCloseable {
  private val sessionsRoot: Path
    get() = codexHomeProvider().resolve("sessions")
  private val watcher: AgentWorkbenchDirectoryWatcher?

  init {
    val codexHome = normalizeWatchPath(codexHomeProvider())
    val sessions = normalizeWatchPath(sessionsRoot)
    LOG.debug { "Registering initial watcher paths (codexHome=$codexHome, sessionsRoot=$sessions)" }

    val roots = LinkedHashSet<Path>()
    if (Files.isDirectory(codexHome)) {
      roots.add(codexHome)
    }
    if (Files.isDirectory(sessions)) {
      roots.add(sessions)
    }
    watcher = if (roots.isEmpty()) {
      LOG.debug { "No watcher roots found; rollout updates watcher will stay idle" }
      null
    }
    else {
      AgentWorkbenchDirectoryWatcher(
        roots = roots,
        scope = scope,
        onWatchEvent = ::handleWatchEvent,
        onFailure = { t ->
          LOG.warn("Rollout watcher failed", t)
        },
      )
    }

    if (watcher != null) {
      LOG.debug { "Initialized directory watcher for roots=$roots" }
    }
  }

  override fun close() {
    LOG.debug { "Closing Codex rollout sessions watcher" }
    watcher?.close()
  }

  private fun handleWatchEvent(event: AgentWorkbenchWatchEvent) {
    val eventPath = event.path
    val rootPath = event.rootPath
    val eventType = event.eventType
    val isDirectory = event.isDirectory
    val isRolloutFile = eventPath?.let(::isRolloutPath) == true
    LOG.debug {
      "Rollout watcher event type=$eventType path=$eventPath root=$rootPath isDirectory=$isDirectory count=${event.count} (isRolloutFile=$isRolloutFile)"
    }

    val changeSet = eventToChangeSet(event) ?: return

    LOG.debug {
      "Rollout watcher detected relevant changes; notifying listeners (fullRescan=${changeSet.requiresFullRescan}, changedPaths=${changeSet.changedRolloutPaths.size})"
    }
    onRolloutChange(changeSet)
  }

  internal fun eventToChangeSet(event: AgentWorkbenchWatchEvent): CodexRolloutChangeSet? {
    val eventPath = event.path
    val rootPath = event.rootPath
    if (event.eventType == AgentWorkbenchWatchEventType.OVERFLOW) {
      val isRelevantOverflow = when {
        eventPath != null && isSessionsPath(eventPath) -> true
        rootPath != null && isRelevantWatcherRoot(rootPath) -> true
        else -> false
      }
      if (!isRelevantOverflow) return null
      return CodexRolloutChangeSet(requiresFullRescan = true)
    }

    if (eventPath != null && isRolloutPath(eventPath)) {
      return CodexRolloutChangeSet(changedRolloutPaths = setOf(normalizeWatchPath(eventPath)))
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
      return CodexRolloutChangeSet(requiresFullRescan = true)
    }

    // For non-rollout files under sessions (e.g. temp files used by atomic rewrite),
    // emit a refresh ping. The index will re-scan file stats without forcing full reparse.
    return CodexRolloutChangeSet()
  }

  private fun isSessionsPath(path: Path): Boolean {
    val normalizedPath = normalizeWatchPath(path)
    return normalizedPath.startsWith(normalizedSessionsRootPath())
  }

  private fun isRolloutPath(path: Path): Boolean {
    val fileName = path.fileName?.toString() ?: return false
    return isSessionsPath(path) && isRolloutFileName(fileName)
  }

  private fun isRelevantWatcherRoot(path: Path): Boolean {
    val normalizedPath = normalizeWatchPath(path)
    return normalizedPath == normalizeWatchPath(codexHomeProvider()) || normalizedPath == normalizedSessionsRootPath() || isSessionsPath(normalizedPath)
  }

  private fun normalizedSessionsRootPath(): Path {
    return normalizeWatchPath(sessionsRoot)
  }

}

private fun normalizeWatchPath(path: Path): Path {
  return runCatching {
    path.toAbsolutePath().normalize()
  }.getOrElse {
    path.normalize()
  }
}
