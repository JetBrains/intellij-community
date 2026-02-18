// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.filewatch

import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryChangeListener
import io.methvin.watcher.DirectoryWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

enum class AgentWorkbenchWatchEventType {
  CREATE,
  MODIFY,
  DELETE,
  OVERFLOW,
}

data class AgentWorkbenchWatchEvent(
  val eventType: AgentWorkbenchWatchEventType,
  val path: Path?,
  val rootPath: Path?,
  val isDirectory: Boolean,
  val count: Int,
)

class AgentWorkbenchDirectoryWatcher(
  roots: Collection<Path>,
  scope: CoroutineScope,
  private val onWatchEvent: (AgentWorkbenchWatchEvent) -> Unit,
  private val onFailure: (Throwable) -> Unit = {},
) : AutoCloseable {
  private val running = AtomicBoolean(true)
  private val directoryWatcher: DirectoryWatcher?
  private val watcherJob: Job?

  init {
    directoryWatcher = createDirectoryWatcher(roots)
    watcherJob = if (directoryWatcher != null) {
      scope.launch(Dispatchers.IO) {
        runWatchLoop(directoryWatcher)
      }
    }
    else {
      null
    }
  }

  val isActive: Boolean
    get() = directoryWatcher != null

  override fun close() {
    if (!running.compareAndSet(true, false)) return
    directoryWatcher?.let { watcher ->
      runCatching {
        watcher.close()
      }.onFailure { t ->
        onFailure(t)
      }
    }
    watcherJob?.cancel()
  }

  private fun createDirectoryWatcher(roots: Collection<Path>): DirectoryWatcher? {
    val watchRoots = LinkedHashSet<Path>()
    for (root in roots) {
      val normalizedPath = normalizeWatchPath(root)
      if (Files.isDirectory(normalizedPath)) {
        watchRoots.add(normalizedPath)
      }
    }
    if (watchRoots.isEmpty()) {
      return null
    }

    return DirectoryWatcher.builder()
      .paths(ArrayList(watchRoots))
      .listener(object : DirectoryChangeListener {
        override fun onEvent(event: DirectoryChangeEvent) {
          onWatchEvent(event.toAgentWorkbenchWatchEvent())
        }

        override fun onException(exception: Exception) {
          onFailure(exception)
        }
      })
      .build()
  }

  private fun runWatchLoop(watcher: DirectoryWatcher) {
    try {
      watcher.watch()
    }
    catch (t: Throwable) {
      if (running.get()) {
        onFailure(t)
      }
    }
  }
}

private fun DirectoryChangeEvent.toAgentWorkbenchWatchEvent(): AgentWorkbenchWatchEvent {
  return AgentWorkbenchWatchEvent(
    eventType = when (eventType()) {
      DirectoryChangeEvent.EventType.CREATE -> AgentWorkbenchWatchEventType.CREATE
      DirectoryChangeEvent.EventType.MODIFY -> AgentWorkbenchWatchEventType.MODIFY
      DirectoryChangeEvent.EventType.DELETE -> AgentWorkbenchWatchEventType.DELETE
      DirectoryChangeEvent.EventType.OVERFLOW -> AgentWorkbenchWatchEventType.OVERFLOW
    },
    path = path(),
    rootPath = rootPath(),
    isDirectory = isDirectory,
    count = count(),
  )
}

private fun normalizeWatchPath(path: Path): Path {
  return runCatching {
    path.toAbsolutePath().normalize()
  }.getOrElse {
    path.normalize()
  }
}
