// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.json.filebacked

import com.intellij.agent.workbench.filewatch.AgentWorkbenchDirectoryWatcher
import com.intellij.agent.workbench.filewatch.AgentWorkbenchWatchEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Files
import java.nio.file.Path

class FileBackedSessionWatcherSpec(
  val roots: Collection<Path>,
  val eventToChangeSet: (AgentWorkbenchWatchEvent) -> FileBackedSessionChangeSet?,
)

class FileBackedSessionWatcher(
  private val logger: Logger,
  private val watcherName: String,
  spec: FileBackedSessionWatcherSpec,
  scope: CoroutineScope,
  private val onChange: (FileBackedSessionChangeSet) -> Unit,
  private val failureMessage: String = "$watcherName watcher failed",
) : AutoCloseable {
  private val watcher: AgentWorkbenchDirectoryWatcher?

  init {
    val roots = spec.roots.asSequence()
      .map(::normalizeFileBackedSessionPath)
      .filter { path -> Files.isDirectory(path) }
      .distinct()
      .toList()
    logger.debug { "Registering $watcherName watcher (roots=$roots)" }

    watcher = if (roots.isEmpty()) {
      logger.debug { "No watcher roots found; $watcherName watcher will stay idle" }
      null
    }
    else {
      AgentWorkbenchDirectoryWatcher(
        roots = roots,
        scope = scope,
        onWatchEvent = { event -> handleWatchEvent(event, spec.eventToChangeSet) },
        onFailure = { t ->
          logger.warn(failureMessage, t)
        },
      )
    }

    if (watcher != null) {
      logger.debug { "Initialized directory watcher for roots=$roots" }
    }
  }

  override fun close() {
    logger.debug { "Closing $watcherName watcher" }
    watcher?.close()
  }

  private fun handleWatchEvent(
    event: AgentWorkbenchWatchEvent,
    eventToChangeSet: (AgentWorkbenchWatchEvent) -> FileBackedSessionChangeSet?,
  ) {
    val eventPath = event.path
    val rootPath = event.rootPath
    val eventType = event.eventType
    val isDirectory = event.isDirectory
    logger.debug {
      "$watcherName watcher event type=$eventType path=$eventPath root=$rootPath isDirectory=$isDirectory count=${event.count}"
    }

    val changeSet = eventToChangeSet(event) ?: return
    logger.debug {
      "$watcherName watcher detected relevant changes; notifying listeners (fullRescan=${changeSet.requiresFullRescan}, changedPaths=${changeSet.changedPaths.size})"
    }
    onChange(changeSet)
  }
}
