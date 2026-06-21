// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.filewatch

import com.intellij.agent.workbench.filewatch.impl.DirectoryChangeEvent
import com.intellij.agent.workbench.filewatch.impl.DirectoryChangeListener
import com.intellij.agent.workbench.filewatch.impl.DirectoryWatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
  @JvmField val eventType: AgentWorkbenchWatchEventType,
  @JvmField val path: Path?,
  @JvmField val rootPath: Path?,
  @JvmField val isDirectory: Boolean,
  @JvmField val count: Int,
)

class AgentWorkbenchDirectoryWatcher private constructor(
  roots: Collection<Path>,
  scope: CoroutineScope,
  private val onWatchEvent: suspend (AgentWorkbenchWatchEvent) -> Unit,
  private val onFailure: suspend (Throwable) -> Unit = {},
  private val watchLoopFactory: (List<Path>, DirectoryChangeListener) -> AgentWorkbenchWatchLoop,
  @Suppress("unused") private val constructorMarker: Unit,
) : AutoCloseable {
  private val roots = roots.toList()
  private val running = AtomicBoolean(true)
  @Volatile
  private var watchLoop: AgentWorkbenchWatchLoop?
  private val watcherJob: Job?

  constructor(
    roots: Collection<Path>,
    scope: CoroutineScope,
    onWatchEvent: suspend (AgentWorkbenchWatchEvent) -> Unit,
    onFailure: suspend (Throwable) -> Unit = {},
  ) : this(roots, scope, onWatchEvent, onFailure, ::createDefaultWatchLoop, Unit)

  internal constructor(
    roots: Collection<Path>,
    scope: CoroutineScope,
    onWatchEvent: suspend (AgentWorkbenchWatchEvent) -> Unit,
    onFailure: suspend (Throwable) -> Unit = {},
    watchLoopFactory: (List<Path>, DirectoryChangeListener) -> AgentWorkbenchWatchLoop,
  ) : this(roots, scope, onWatchEvent, onFailure, watchLoopFactory, Unit)

  init {
    watchLoop = createWatchLoop(roots)
    watcherJob = if (watchLoop != null) {
      scope.launch(Dispatchers.IO) {
        runWatchLoops()
      }
    }
    else {
      null
    }
  }

  val isActive: Boolean
    get() = watchLoop != null && watcherJob?.isActive == true

  override fun close() {
    if (!running.compareAndSet(true, false)) return
    watcherJob?.cancel()
    watchLoop?.let { watcher ->
      runCatching {
        watcher.close()
      }
    }
  }

  @Suppress("unused")
  suspend fun closeAndJoin() {
    if (running.compareAndSet(true, false)) {
      watcherJob?.cancel()
      runCatching {
        watchLoop?.close()
      }.onFailure { t ->
        onFailure(t)
      }
    }
    watcherJob?.cancelAndJoin()
  }

  private fun createWatchLoop(roots: Collection<Path>): AgentWorkbenchWatchLoop? {
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

    val listener = object : DirectoryChangeListener {
      override suspend fun onEvent(event: DirectoryChangeEvent) {
        if (running.get()) {
          onWatchEvent(event.toAgentWorkbenchWatchEvent())
        }
      }

      override suspend fun onException(e: Exception) {
        if (running.get()) {
          onFailure(e)
        }
      }
    }
    return watchLoopFactory(ArrayList(watchRoots), listener)
  }

  private suspend fun runWatchLoops() {
    while (running.get()) {
      val watcher = watchLoop ?: return
      val shouldRestart = try {
        watcher.watch()
        false
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (t: Throwable) {
        if (running.get()) {
          onFailure(t)
          true
        }
        else {
          false
        }
      }
      finally {
        if (watchLoop === watcher) {
          watchLoop = null
        }
        runCatching {
          watcher.close()
        }.onFailure { t ->
          if (running.get()) {
            onFailure(t)
          }
        }
      }

      if (!shouldRestart || !running.get()) {
        return
      }
      watchLoop = createWatchLoop(roots)
    }
  }
}

interface AgentWorkbenchWatchLoop : AutoCloseable {
  suspend fun watch()
}

private fun createDefaultWatchLoop(paths: List<Path>, listener: DirectoryChangeListener): AgentWorkbenchWatchLoop {
  val watcher = DirectoryWatcher(paths, listener)
  return object : AgentWorkbenchWatchLoop {
    override suspend fun watch() {
      watcher.watch()
    }

    override fun close() {
      watcher.close()
    }
  }
}

private fun DirectoryChangeEvent.toAgentWorkbenchWatchEvent(): AgentWorkbenchWatchEvent {
  return AgentWorkbenchWatchEvent(
    eventType = when (eventType) {
      DirectoryChangeEvent.EventType.CREATE -> AgentWorkbenchWatchEventType.CREATE
      DirectoryChangeEvent.EventType.MODIFY -> AgentWorkbenchWatchEventType.MODIFY
      DirectoryChangeEvent.EventType.DELETE -> AgentWorkbenchWatchEventType.DELETE
      DirectoryChangeEvent.EventType.OVERFLOW -> AgentWorkbenchWatchEventType.OVERFLOW
    },
    path = path,
    rootPath = rootPath,
    isDirectory = isDirectory,
    count = count,
  )
}

private fun normalizeWatchPath(path: Path): Path {
  return runCatching {
    path.toAbsolutePath().normalize()
  }.getOrElse {
    path.normalize()
  }
}
