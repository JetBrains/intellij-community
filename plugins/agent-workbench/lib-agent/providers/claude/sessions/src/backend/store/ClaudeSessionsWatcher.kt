// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions.backend.store

import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEvent
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionChangeSet
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionWatcher
import com.intellij.agent.workbench.json.filebacked.FileBackedSessionWatcherSpec
import com.intellij.agent.workbench.json.filebacked.classifyFileBackedSessionEvent
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
  private val claudeHome = normalizeFileBackedSessionPath(claudeHomeProvider())
  private val projectsRoot = normalizeFileBackedSessionPath(claudeHome.resolve("projects"))
  private val watcher = FileBackedSessionWatcher(
    logger = LOG,
    watcherName = "Claude sessions",
    spec = FileBackedSessionWatcherSpec(
      roots = listOf(claudeHome, projectsRoot),
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
    return classifyFileBackedSessionEvent(
      event = event,
      isChangedPath = { path -> isJsonlPath(path, projectsRoot) || isIndexPath(path, projectsRoot) },
      isRelevantPath = { path -> isUnderRoot(path, projectsRoot) },
      isRelevantRoot = { path -> isRelevantWatcherRoot(path, claudeHome, projectsRoot) },
    )
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

private fun isRelevantWatcherRoot(path: Path, claudeHome: Path, projectsRoot: Path): Boolean {
  val normalizedPath = normalizeFileBackedSessionPath(path)
  return normalizedPath == claudeHome || normalizedPath.startsWith(projectsRoot)
}
