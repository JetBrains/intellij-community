// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.junie.sessions

import com.intellij.platform.ai.agent.filewatch.AgentWorkbenchWatchEvent
import com.intellij.platform.ai.agent.json.filebacked.FileBackedSessionChangeSet
import com.intellij.platform.ai.agent.json.filebacked.FileBackedSessionWatcher
import com.intellij.platform.ai.agent.json.filebacked.FileBackedSessionWatcherSpec
import com.intellij.platform.ai.agent.json.filebacked.classifyFileBackedSessionEvent
import com.intellij.platform.ai.agent.json.filebacked.normalizeFileBackedSessionPath
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

private val WATCHER_LOG = logger<JunieSessionsWatcher>()

internal class JunieSessionsWatcher(
  sessionsRootPathProvider: () -> Path,
  scope: CoroutineScope,
  onChange: (FileBackedSessionChangeSet) -> Unit,
) : AutoCloseable {
  private val sessionsRoot = normalizeFileBackedSessionPath(sessionsRootPathProvider())
  private val junieHome = normalizeFileBackedSessionPath(sessionsRoot.parent ?: sessionsRoot)
  private val watcher = FileBackedSessionWatcher(
    logger = WATCHER_LOG,
    watcherName = "Junie sessions",
    spec = FileBackedSessionWatcherSpec(
      roots = listOf(junieHome, sessionsRoot),
      eventToChangeSet = ::eventToChangeSet,
    ),
    scope = scope,
    onChange = onChange,
    failureMessage = "Junie sessions watcher failed",
  )

  override fun close() {
    watcher.close()
  }

  internal fun eventToChangeSet(event: AgentWorkbenchWatchEvent): FileBackedSessionChangeSet? {
    return classifyFileBackedSessionEvent(
      event = event,
      isChangedPath = ::isChangedSessionPath,
      isRelevantPath = ::isSessionsPath,
      isRelevantRoot = ::isRelevantWatcherRoot,
    )
  }

  private fun isChangedSessionPath(path: Path): Boolean {
    val fileName = path.fileName?.toString() ?: return false
    return isSessionsPath(path) && fileName in JUNIE_CHANGED_SESSION_FILE_NAMES
  }

  private fun isSessionsPath(path: Path): Boolean {
    return normalizeFileBackedSessionPath(path).startsWith(sessionsRoot)
  }

  private fun isRelevantWatcherRoot(path: Path): Boolean {
    val normalizedPath = normalizeFileBackedSessionPath(path)
    return normalizedPath == junieHome || normalizedPath == sessionsRoot || normalizedPath.startsWith(sessionsRoot)
  }
}

private val JUNIE_CHANGED_SESSION_FILE_NAMES = setOf(
  JUNIE_INDEX_FILE_NAME,
  JUNIE_AGENT_WORKBENCH_ARCHIVE_STATE_FILE,
  JUNIE_EVENTS_FILE_NAME,
)
