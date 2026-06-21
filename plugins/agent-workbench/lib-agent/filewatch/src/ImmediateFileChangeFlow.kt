// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.filewatch

import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.KEvent
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.K_EVENT_ADD
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.K_EVENT_CLEAR
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.K_EVENT_FILTER_VNODE
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.K_NOTE_DELETE
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.K_NOTE_EXTEND
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.K_NOTE_RENAME
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.K_NOTE_REVOKE
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.K_NOTE_WRITE
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.K_QUEUE_OPEN_EVENT_ONLY
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.macKQueueApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import java.nio.file.Files
import java.nio.file.Path

fun agentWorkbenchImmediateFileChangeFlow(paths: Collection<Path>): Flow<Path> {
  if (!isMacOS() || paths.isEmpty()) {
    return emptyFlow()
  }
  val normalizedPaths = paths.asSequence()
    .map(::normalizeImmediateWatchPath)
    .distinct()
    .toList()
  if (normalizedPaths.isEmpty()) {
    return emptyFlow()
  }

  return callbackFlow {
    val watcher = MacOSXImmediateFileChangeWatcher.create(
      paths = normalizedPaths,
      onModify = { path -> trySend(path).isSuccess },
      onEmpty = { close() },
    )
    if (watcher == null) {
      close()
      return@callbackFlow
    }
    awaitClose {
      watcher.close()
    }
  }
}

private class MacOSXImmediateFileChangeWatcher private constructor(
  private val kqueueFd: Int,
  private val onModify: (Path) -> Unit,
  private val onEmpty: () -> Unit,
) : AutoCloseable {
  companion object {
    fun create(paths: Collection<Path>, onModify: (Path) -> Unit, onEmpty: () -> Unit): MacOSXImmediateFileChangeWatcher? {
      val kqueueFd = runCatching {
        macKQueueApi.kqueue()
      }.getOrDefault(-1)
      if (kqueueFd < 0) {
        return null
      }

      val watcher = MacOSXImmediateFileChangeWatcher(kqueueFd, onModify, onEmpty)
      for (path in paths) {
        watcher.watch(path)
      }
      if (!watcher.hasWatchedFiles()) {
        watcher.close()
        return null
      }
      watcher.start()
      return watcher
    }
  }

  private val lock = Any()
  private val watchedFiles = LinkedHashMap<Path, WatchedFile>()
  private val watchedFds = HashMap<Int, WatchedFile>()
  @Volatile
  private var closed = false

  private fun start() {
    Thread({ runEventLoop() }, "Agent Workbench macOS immediate file watcher").apply {
      isDaemon = true
      start()
    }
  }

  private fun hasWatchedFiles(): Boolean {
    return synchronized(lock) {
      watchedFiles.isNotEmpty()
    }
  }

  private fun watch(path: Path) {
    if (!Files.isRegularFile(path)) {
      return
    }
    synchronized(lock) {
      if (closed || path in watchedFiles) {
        return
      }
    }

    val fd = runCatching {
      macKQueueApi.open(path.toString(), K_QUEUE_OPEN_EVENT_ONLY)
    }.getOrDefault(-1)
    if (fd < 0) {
      return
    }

    var registered = false
    try {
      val event = KEvent(
        ident = fd,
        filter = K_EVENT_FILTER_VNODE,
        flags = (K_EVENT_ADD.toInt() or K_EVENT_CLEAR.toInt()).toShort(),
        fflags = K_NOTE_WRITE or K_NOTE_EXTEND or K_NOTE_DELETE or K_NOTE_RENAME or K_NOTE_REVOKE,
      )
      val result = runCatching {
        macKQueueApi.kevent(kqueueFd, event, 1, null, 0, null)
      }.getOrDefault(-1)
      if (result < 0) {
        return
      }
      val watchedFile = WatchedFile(path, fd)
      synchronized(lock) {
        if (!closed && path !in watchedFiles) {
          watchedFiles[path] = watchedFile
          watchedFds[fd] = watchedFile
          registered = true
        }
      }
    }
    finally {
      if (!registered) {
        closeFileDescriptor(fd)
      }
    }
  }

  private fun unwatch(path: Path) {
    removeWatch(path)?.let { watchedFile ->
      closeFileDescriptor(watchedFile.fd)
    }
  }

  override fun close() {
    val watchedFilesToClose = synchronized(lock) {
      if (closed) {
        return
      }
      closed = true
      val files = ArrayList(watchedFiles.values)
      watchedFiles.clear()
      watchedFds.clear()
      files
    }
    closeFileDescriptor(kqueueFd)
    for ((_, fd) in watchedFilesToClose) {
      closeFileDescriptor(fd)
    }
  }

  private fun runEventLoop() {
    while (!closed) {
      val event = KEvent()
      val result = runCatching {
        macKQueueApi.kevent(kqueueFd, null, 0, event, 1, null)
      }.getOrDefault(-1)
      if (result < 0) {
        return
      }
      if (result == 0) {
        continue
      }
      handleEvent(event)
    }
  }

  private fun handleEvent(event: KEvent) {
    val fd = event.ident.toInt()
    val watchedFile = synchronized(lock) {
      watchedFds[fd]
    } ?: return

    val flags = event.fflags
    if (flags and (K_NOTE_WRITE or K_NOTE_EXTEND) != 0) {
      onModify(watchedFile.path)
    }
    if (flags and (K_NOTE_DELETE or K_NOTE_RENAME or K_NOTE_REVOKE) != 0) {
      unwatch(watchedFile.path)
      if (!hasWatchedFiles()) {
        onEmpty()
      }
    }
  }

  private fun removeWatch(path: Path): WatchedFile? {
    return synchronized(lock) {
      val watchedFile = watchedFiles.remove(path) ?: return@synchronized null
      watchedFds.remove(watchedFile.fd)
      watchedFile
    }
  }

  private fun closeFileDescriptor(fd: Int) {
    runCatching {
      macKQueueApi.close(fd)
    }
  }

  private data class WatchedFile(val path: Path, val fd: Int)
}

private fun normalizeImmediateWatchPath(path: Path): Path {
  return runCatching {
    path.toAbsolutePath().normalize()
  }.getOrElse {
    path.normalize()
  }
}

private fun isMacOS(): Boolean {
  return System.getProperty("os.name").contains("mac", ignoreCase = true)
}
