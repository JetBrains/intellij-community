// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.filewatch.impl

import com.intellij.agent.workbench.filewatch.impl.DirectoryChangeEvent.EventType
import com.intellij.agent.workbench.filewatch.impl.watchservice.MacOSXListeningWatchService
import com.intellij.agent.workbench.filewatch.impl.watchservice.WatchablePath
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.sun.nio.file.ExtendedWatchEventModifier
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.Watchable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = logger<DirectoryWatcher>()

internal class DirectoryWatcher(
  paths: List<Path>,
  private val listener: DirectoryChangeListener,
  private val watchService: WatchService = createDefaultWatchService(),
  private val fileTreeVisitor: FileTreeVisitor = DefaultFileTreeVisitor,
  private val watchablePathFactory: (Path) -> Watchable = if (watchService is MacOSXListeningWatchService) ::WatchablePath else { path -> path },
) : AutoCloseable {
  private val paths = paths.map { path -> path.toAbsolutePath() }
  private val isMac = watchService is MacOSXListeningWatchService
  private val registeredPathToRootPath = HashMap<Path, Path>()
  private val directories = ConcurrentHashMap.newKeySet<Path>()
  private val keyRoots = ConcurrentHashMap<WatchKey, Path>()
  private val closed = AtomicBoolean()
  private var fileTreeSupported: Boolean? = null

  suspend fun watch() {
    check(!closed.get()) { "watcher already closed" }
    use {
      registerPaths()
      runEventLoop()
    }
  }

  override fun close() {
    if (closed.compareAndSet(false, true)) {
      watchService.close()
    }
  }

  private suspend fun registerPaths() {
    for (path in paths) {
      registerAll(path, path)
    }
  }

  private suspend fun runEventLoop() {
    while (!closed.get() && currentCoroutineContext().isActive) {
      val key = try {
        runInterruptible { watchService.take() }
      }
      catch (_: InterruptedException) {
        return
      }
      catch (_: ClosedWatchServiceException) {
        return
      }
      for (event in key.pollEvents()) {
        try {
          handleEvent(key, event)
        }
        catch (e: Exception) {
          LOG.debug("DirectoryWatcher got an exception while watching!", e)
          listener.onException(e)
        }
      }
      if (!key.reset()) {
        LOG.debug { "WatchKey for [${key.watchable()}] no longer valid; removing." }
        val registeredPath = keyRoots.remove(key)
        registeredPathToRootPath.remove(registeredPath)
        if (keyRoots.isEmpty()) {
          LOG.debug("No more directories left to watch; terminating watcher.")
          throw IllegalStateException("No more directories left to watch")
        }
      }
    }
  }

  private suspend fun handleEvent(key: WatchKey, event: WatchEvent<*>) {
    val kind = event.kind()
    val count = event.count()
    val eventPath = event.pathContext()
    val registeredPath = keyRoots[key] ?: throw IllegalStateException("WatchService returned key [$key] but it was not found in keyRoots!")
    val rootPath = registeredPathToRootPath[registeredPath]
    val childPath = eventPath?.let { registeredPath.resolve(it) }
    LOG.debug { "$kind [$childPath]" }
    when {
      kind === OVERFLOW -> onEvent(EventType.OVERFLOW, false, childPath, count, rootPath)
      eventPath == null -> throw IllegalStateException("WatchService returned a null path for ${kind.name()}")
      kind === ENTRY_CREATE -> handleCreate(childPath!!, count, rootPath)
      kind === ENTRY_MODIFY -> onEvent(EventType.MODIFY, directories.contains(childPath), childPath, count, rootPath)
      kind === ENTRY_DELETE -> onEvent(EventType.DELETE, directories.remove(childPath), childPath, count, rootPath)
    }
  }

  private suspend fun handleCreate(childPath: Path, count: Int, rootPath: Path?) {
    val isDirectory = Files.isDirectory(childPath, NOFOLLOW_LINKS)
    if (isDirectory) {
      if (!isFileTreeSupported()) {
        registerAll(childPath, rootPath)
      }
      if (!isMac) {
        val createdDirectories = ArrayList<Path>()
        val createdFiles = ArrayList<Path>()
        runInterruptible {
          fileTreeVisitor.recursiveVisitFiles(childPath, createdDirectories::add, createdFiles::add)
        }
        for (dir in createdDirectories) {
          notifyCreateEvent(true, dir, count, rootPath)
        }
        for (file in createdFiles) {
          notifyCreateEvent(false, file, count, rootPath)
        }
      }
      else {
        notifyCreateEvent(true, childPath, count, rootPath)
      }
    }
    else {
      notifyCreateEvent(false, childPath, count, rootPath)
    }
  }

  private suspend fun onEvent(eventType: EventType, isDirectory: Boolean, childPath: Path?, count: Int, rootPath: Path?) {
    LOG.debug { "-> $eventType [$childPath] (isDirectory: $isDirectory)" }
    listener.onEvent(DirectoryChangeEvent(eventType, isDirectory, childPath, count, rootPath))
  }

  private suspend fun registerAll(start: Path, context: Path?) {
    if (fileTreeSupported != false) {
      try {
        register(start, true, context)
        fileTreeSupported = true
        addKnownDirectories(start)
      }
      catch (e: UnsupportedOperationException) {
        LOG.debug("Assuming ExtendedWatchEventModifier.FILE_TREE is not supported", e)
        fileTreeSupported = false
        registerAll(start, context)
      }
    }
    else {
      val directories = ArrayList<Path>()
      runInterruptible {
        fileTreeVisitor.recursiveVisitFiles(start, directories::add) {}
      }
      for (directory in directories) {
        this.directories.add(directory)
        register(directory, false, context)
      }
    }
  }

  private suspend fun addKnownDirectories(start: Path) {
    runInterruptible {
      fileTreeVisitor.recursiveVisitFiles(start, directories::add) {}
    }
  }

  private suspend fun register(directory: Path, useFileTreeModifier: Boolean, context: Path?) {
    LOG.debug { "Registering [$directory]." }
    val modifiers = if (useFileTreeModifier) arrayOf(ExtendedWatchEventModifier.FILE_TREE) else emptyArray()
    val watchKey = runInterruptible {
      val watchable = watchablePathFactory(directory)
      watchable.register(watchService, arrayOf(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), *modifiers)
    }
    keyRoots[watchKey] = directory
    if (context != null) {
      registeredPathToRootPath[directory] = context
    }
  }

  private suspend fun notifyCreateEvent(isDirectory: Boolean, path: Path, count: Int, rootPath: Path?) {
    if (isDirectory) {
      directories.add(path)
    }
    onEvent(EventType.CREATE, isDirectory, path, count, rootPath)
  }

  private fun isFileTreeSupported(): Boolean = fileTreeSupported ?: error("fileTreeSupported not initialized")

}

private fun createDefaultWatchService(): WatchService {
  return if (System.getProperty("os.name").contains("mac", ignoreCase = true)) {
    MacOSXListeningWatchService(object : MacOSXListeningWatchService.Config {
    })
  }
  else {
    FileSystems.getDefault().newWatchService()
  }
}
