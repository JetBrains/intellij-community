// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions.backend.store

import com.intellij.agent.workbench.filewatch.AgentWorkbenchDirectoryWatcher
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEvent
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEventType
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<ClaudeSessionsWatcher>()

internal data class ClaudeChangeSet(
  // Known JSONL files that must be reparsed regardless of file stat heuristics.
  @JvmField val changedJsonlPaths: Set<Path> = emptySet(),

  // Overflow/ambiguous events where file-level attribution is not reliable.
  @JvmField val requiresFullRescan: Boolean = false,

  // `changedJsonlPaths.isEmpty() && !requiresFullRescan` means "refresh ping":
  // re-run stat-based scan without forcing full reparse.
)

internal class ClaudeSessionsWatcher(
  private val claudeHomeProvider: () -> Path,
  scope: CoroutineScope,
  private val onChange: (ClaudeChangeSet) -> Unit,
) : AutoCloseable {
  private val projectsRoot: Path
    get() = claudeHomeProvider().resolve("projects")
  private val watcher: AgentWorkbenchDirectoryWatcher?

  init {
    val root = normalizeFilePath(projectsRoot)
    LOG.debug { "Registering Claude sessions watcher (projectsRoot=$root)" }

    watcher = if (Files.isDirectory(root)) {
      AgentWorkbenchDirectoryWatcher(
        roots = listOf(root),
        scope = scope,
        onWatchEvent = ::handleWatchEvent,
        onFailure = { t ->
          LOG.warn("Claude sessions watcher failed", t)
        },
      )
    }
    else {
      LOG.debug { "No watcher root found; Claude sessions watcher will stay idle" }
      null
    }

    if (watcher != null) {
      LOG.debug { "Initialized directory watcher for root=$root" }
    }
  }

  override fun close() {
    LOG.debug { "Closing Claude sessions watcher" }
    watcher?.close()
  }

  private fun handleWatchEvent(event: AgentWorkbenchWatchEvent) {
    val eventPath = event.path
    val eventType = event.eventType
    val isDirectory = event.isDirectory
    LOG.debug {
      "Claude watcher event type=$eventType path=$eventPath isDirectory=$isDirectory count=${event.count}"
    }

    val changeSet = eventToChangeSet(event) ?: return

    LOG.debug {
      "Claude watcher detected relevant changes; notifying listeners (fullRescan=${changeSet.requiresFullRescan}, changedPaths=${changeSet.changedJsonlPaths.size})"
    }
    onChange(changeSet)
  }

  internal fun eventToChangeSet(event: AgentWorkbenchWatchEvent): ClaudeChangeSet? {
    val eventPath = event.path
    val rootPath = event.rootPath
    val normalizedRoot = normalizeFilePath(projectsRoot)

    if (event.eventType == AgentWorkbenchWatchEventType.OVERFLOW) {
      val isRelevant = when {
        eventPath != null && isUnderRoot(eventPath, normalizedRoot) -> true
        rootPath != null && normalizeFilePath(rootPath) == normalizedRoot -> true
        else -> false
      }
      if (!isRelevant) return null
      return ClaudeChangeSet(requiresFullRescan = true)
    }

    if (eventPath != null && isJsonlPath(eventPath, normalizedRoot)) {
      return ClaudeChangeSet(changedJsonlPaths = setOf(normalizeFilePath(eventPath)))
    }

    val isProjectsEvent = when {
      eventPath != null -> isUnderRoot(eventPath, normalizedRoot)
      rootPath != null -> normalizeFilePath(rootPath) == normalizedRoot
      else -> false
    }
    if (!isProjectsEvent) return null

    val isAmbiguousDirectoryEvent = event.isDirectory || eventPath == null
    if (isAmbiguousDirectoryEvent) {
      return ClaudeChangeSet(requiresFullRescan = true)
    }

    // Non-JSONL files under projects: emit refresh ping.
    return ClaudeChangeSet()
  }
}

private fun isUnderRoot(path: Path, normalizedRoot: Path): Boolean {
  return normalizeFilePath(path).startsWith(normalizedRoot)
}

private fun isJsonlPath(path: Path, normalizedRoot: Path): Boolean {
  val fileName = path.fileName?.toString() ?: return false
  return fileName.endsWith(".jsonl") && isUnderRoot(path, normalizedRoot)
}
