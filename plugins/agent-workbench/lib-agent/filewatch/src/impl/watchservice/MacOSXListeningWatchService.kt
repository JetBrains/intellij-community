// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.filewatch.impl.watchservice

import com.intellij.agent.workbench.filewatch.impl.DefaultFileTreeVisitor
import com.intellij.agent.workbench.filewatch.impl.FileTreeVisitor
import com.intellij.agent.workbench.filewatch.impl.recursiveListFiles
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.CFArrayRef
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.FSEventStreamRef
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.K_FSEVENT_STREAM_CREATE_FLAG_FILE_EVENTS
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.K_FSEVENT_STREAM_CREATE_FLAG_NO_DEFER
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.MacFSEventsApi
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.cfIndex
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.libDispatchApi
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.macFSEventsApi
import com.intellij.agent.workbench.filewatch.impl.watchservice.jna.toCFString
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchEvent

internal class MacOSXListeningWatchService(config: Config) : AbstractWatchService() {
  interface Config {
    val latency: Double
      get() = DEFAULT_LATENCY
    val queueSize: Int
      get() = DEFAULT_QUEUE_SIZE
    val fileTreeVisitor: FileTreeVisitor
      get() = DefaultFileTreeVisitor
  }

  private val callbacks = ArrayList<MacOSXListeningCallback>()
  private val streams = ArrayList<FSEventStream>()
  private val pathsWatching = HashSet<Path>()
  private val latency = config.latency
  private val queueSize = config.queueSize
  private val fileTreeVisitor = config.fileTreeVisitor

  @Synchronized
  override fun register(watchable: WatchablePath, eventTypes: Iterable<WatchEvent.Kind<*>>): AbstractWatchKey {
    checkOpen()
    val watchKey = MacOSXWatchKey(this, watchable, eventTypes, queueSize)
    val file = watchable.file.toAbsolutePath()
    val pathToWatch = file.toString().toCFString()
    val values = arrayOf(pathToWatch.pointer)
    val pathsToWatch: CFArrayRef = macFSEventsApi.CFArrayCreate(null, values, cfIndex(1), null)
    val callback = MacOSXListeningCallback(watchKey, fileTreeVisitor, file)
    try {
      val streamRef = macFSEventsApi.FSEventStreamCreate(
        Pointer.NULL,
        callback,
        Pointer.NULL,
        pathsToWatch,
        -1L,
        latency,
        K_FSEVENT_STREAM_CREATE_FLAG_NO_DEFER or K_FSEVENT_STREAM_CREATE_FLAG_FILE_EVENTS,
      )
      val stream = FSEventStream(streamRef, file)
      callback.onClose { close(stream, callback, file) }
      stream.start()
      callbacks.add(callback)
      streams.add(stream)
      pathsWatching.add(file)
      return watchKey
    }
    catch (e: Exception) {
      watchKey.cancel()
      throw e
    }
    finally {
      macFSEventsApi.CFRelease(pathsToWatch)
      macFSEventsApi.CFRelease(pathToWatch)
    }
  }

  @Synchronized
  override fun close() {
    super.close()
    streams.forEach(FSEventStream::close)
    streams.clear()
    callbacks.clear()
    pathsWatching.clear()
  }

  @Synchronized
  private fun close(stream: FSEventStream, callback: MacOSXListeningCallback, path: Path) {
    streams.remove(stream)
    callbacks.remove(callback)
    pathsWatching.remove(path)
    stream.close()
  }

  internal class FSEventStream(
    private val streamRef: FSEventStreamRef,
    private val file: Path,
  ) : AutoCloseable {
    private val dispatchQueue = libDispatchApi.dispatch_queue_create("WatchService for $file", null)
    private var closed = false

    fun start() {
      synchronized(streamRef) {
        if (closed) {
          return
        }
        macFSEventsApi.FSEventStreamSetDispatchQueue(streamRef, dispatchQueue)
        if (!macFSEventsApi.FSEventStreamStart(streamRef)) {
          close()
          throw IllegalStateException("Could not start FSEventStream for $file")
        }
      }
    }

    override fun close() {
      synchronized(streamRef) {
        if (closed) {
          return
        }
        macFSEventsApi.FSEventStreamStop(streamRef)
        macFSEventsApi.FSEventStreamInvalidate(streamRef)
        macFSEventsApi.FSEventStreamRelease(streamRef)
        libDispatchApi.dispatch_release(dispatchQueue)
        closed = true
      }
    }
  }
}

private class MacOSXListeningCallback(
  private val watchKey: MacOSXWatchKey,
  private val fileTreeVisitor: FileTreeVisitor,
  private val absPath: Path,
) : MacFSEventsApi.FSEventStreamCallback {
  private val realPathSize: Int
  private val knownFiles: MutableSet<Path>
  private var onCloseCallback: (() -> Unit)? = null

  init {
    try {
      val realPath = absPath.toRealPath()
      knownFiles = HashSet(recursiveListFiles(fileTreeVisitor, absPath))
      realPathSize = realPath.toString().length + 1
    }
    catch (e: Exception) {
      throw IllegalStateException("Could not initialize watcher for $absPath", e)
    }
  }

  fun onClose(callback: () -> Unit) {
    onCloseCallback = callback
  }

  override fun invoke(
    streamRef: FSEventStreamRef,
    clientCallBackInfo: Pointer?,
    numEvents: NativeLong,
    eventPaths: Pointer,
    eventFlags: Pointer?,
    eventIds: Pointer?,
  ) {
    val length = numEvents.toInt()
    val pathNames = eventPaths.getStringArray(0, length)
    for (index in pathNames.indices) {
      if (!watchKey.isValid) {
        return
      }
      val path = toWatchedPath(pathNames[index])
      val flags = FSEventFlags(eventFlags?.getInt((index * Int.SIZE_BYTES).toLong()) ?: 0)
      when {
        flags.requiresRescan -> rescanAfterOverflow(path)
        flags.removed -> handleRemoved(path)
        flags.created -> handleCreated(path, flags)
        flags.renamed -> handleRenamed(path, flags)
        flags.modified -> handleModified(path)
        path in knownFiles && Files.exists(path) -> handleModified(path)
        else -> rescanPath(path)
      }
    }
  }

  private fun toWatchedPath(fileName: String): Path {
    return if (fileName.length + 1 != realPathSize) absPath.resolve(fileName.substring(realPathSize)) else absPath
  }

  private fun handleCreated(path: Path, flags: FSEventFlags) {
    val wasKnown = !knownFiles.add(path)
    if (wasKnown) {
      handleModified(path)
    }
    else if (watchKey.reportCreateEvents) {
      watchKey.signalEvent(ENTRY_CREATE, path)
    }
    if (flags.directory) {
      rescanPath(path)
    }
  }

  private fun handleRemoved(path: Path) {
    val deletedPaths = removeKnownSubtree(path)
    if (deletedPaths.isEmpty()) {
      return
    }
    for (deletedPath in deletedPaths) {
      if (watchKey.reportDeleteEvents) {
        watchKey.signalEvent(ENTRY_DELETE, deletedPath)
      }
    }
    closeIfRootRemoved()
  }

  private fun handleRenamed(path: Path, flags: FSEventFlags) {
    if (Files.exists(path)) {
      if (path !in knownFiles) {
        handleCreated(path, flags)
      }
      else {
        handleModified(path)
      }
    }
    else {
      handleRemoved(path)
    }
  }

  private fun handleModified(path: Path) {
    if (path in knownFiles && !Files.isDirectory(path) && watchKey.reportModifyEvents) {
      watchKey.signalEvent(ENTRY_MODIFY, path)
    }
  }

  private fun rescanAfterOverflow(path: Path) {
    watchKey.signalOverflow(path)
    rescanPath(absPath)
  }

  private fun rescanPath(path: Path) {
    val previouslyKnownFiles = HashSet(knownFiles)
    val filesOnDisk = try {
      recursiveListFiles(fileTreeVisitor, path)
    }
    catch (_: NoSuchFileException) {
      handleRemoved(path)
      return
    }
    catch (_: Exception) {
      watchKey.signalOverflow(path)
      return
    }
    for (file in findCreatedFiles(filesOnDisk)) {
      if (watchKey.reportCreateEvents) {
        watchKey.signalEvent(ENTRY_CREATE, file)
      }
    }
    for (file in findModifiedFiles(filesOnDisk, previouslyKnownFiles)) {
      if (watchKey.reportModifyEvents) {
        watchKey.signalEvent(ENTRY_MODIFY, file)
      }
    }
    for (file in findDeletedFiles(path, filesOnDisk)) {
      if (watchKey.reportDeleteEvents) {
        watchKey.signalEvent(ENTRY_DELETE, file)
      }
    }
    closeIfRootRemoved()
  }

  private fun removeKnownSubtree(path: Path): List<Path> {
    val deletedPaths = ArrayList<Path>()
    for (file in ArrayList(knownFiles)) {
      if (file.startsWith(path)) {
        deletedPaths.add(file)
        knownFiles.remove(file)
      }
    }
    return deletedPaths
  }

  private fun closeIfRootRemoved() {
    if (knownFiles.isEmpty()) {
      watchKey.cancel()
      onCloseCallback?.invoke()
    }
  }

  private fun findCreatedFiles(filesOnDisk: Set<Path>): List<Path> {
    val createdFiles = ArrayList<Path>()
    for (file in filesOnDisk) {
      if (knownFiles.add(file)) {
        createdFiles.add(file)
      }
    }
    return createdFiles
  }

  private fun findModifiedFiles(filesOnDisk: Set<Path>, previouslyKnownFiles: Set<Path>): List<Path> {
    val modifiedFiles = ArrayList<Path>()
    for (file in filesOnDisk) {
      if (file in previouslyKnownFiles && file in knownFiles && !Files.isDirectory(file)) {
        modifiedFiles.add(file)
      }
    }
    return modifiedFiles
  }

  private fun findDeletedFiles(path: Path, filesOnDisk: Set<Path>): List<Path> {
    val deletedFiles = ArrayList<Path>()
    for (file in ArrayList(knownFiles)) {
      if (file.startsWith(path) && file !in filesOnDisk) {
        deletedFiles.add(file)
        knownFiles.remove(file)
      }
    }
    return deletedFiles
  }
}

private class FSEventFlags(private val value: Int) {
  val created: Boolean = has(K_FSEVENT_STREAM_EVENT_FLAG_ITEM_CREATED)
  val removed: Boolean = has(K_FSEVENT_STREAM_EVENT_FLAG_ITEM_REMOVED)
  val renamed: Boolean = has(K_FSEVENT_STREAM_EVENT_FLAG_ITEM_RENAMED)
  val directory: Boolean = has(K_FSEVENT_STREAM_EVENT_FLAG_ITEM_IS_DIR)
  val modified: Boolean = has(K_FSEVENT_STREAM_EVENT_FLAG_ITEM_MODIFIED) ||
                          has(K_FSEVENT_STREAM_EVENT_FLAG_ITEM_INODE_META_MOD) ||
                          has(K_FSEVENT_STREAM_EVENT_FLAG_ITEM_FINDER_INFO_MOD) ||
                          has(K_FSEVENT_STREAM_EVENT_FLAG_ITEM_CHANGE_OWNER) ||
                          has(K_FSEVENT_STREAM_EVENT_FLAG_ITEM_XATTR_MOD)
  val requiresRescan: Boolean = has(K_FSEVENT_STREAM_EVENT_FLAG_MUST_SCAN_SUB_DIRS) ||
                                has(K_FSEVENT_STREAM_EVENT_FLAG_USER_DROPPED) ||
                                has(K_FSEVENT_STREAM_EVENT_FLAG_KERNEL_DROPPED) ||
                                has(K_FSEVENT_STREAM_EVENT_FLAG_ROOT_CHANGED)

  private fun has(flag: Int): Boolean = value and flag != 0
}

private const val K_FSEVENT_STREAM_EVENT_FLAG_MUST_SCAN_SUB_DIRS: Int = 0x00000001
private const val K_FSEVENT_STREAM_EVENT_FLAG_USER_DROPPED: Int = 0x00000002
private const val K_FSEVENT_STREAM_EVENT_FLAG_KERNEL_DROPPED: Int = 0x00000004
private const val K_FSEVENT_STREAM_EVENT_FLAG_ROOT_CHANGED: Int = 0x00000020
private const val K_FSEVENT_STREAM_EVENT_FLAG_ITEM_CREATED: Int = 0x00000100
private const val K_FSEVENT_STREAM_EVENT_FLAG_ITEM_REMOVED: Int = 0x00000200
private const val K_FSEVENT_STREAM_EVENT_FLAG_ITEM_INODE_META_MOD: Int = 0x00000400
private const val K_FSEVENT_STREAM_EVENT_FLAG_ITEM_RENAMED: Int = 0x00000800
private const val K_FSEVENT_STREAM_EVENT_FLAG_ITEM_MODIFIED: Int = 0x00001000
private const val K_FSEVENT_STREAM_EVENT_FLAG_ITEM_FINDER_INFO_MOD: Int = 0x00002000
private const val K_FSEVENT_STREAM_EVENT_FLAG_ITEM_CHANGE_OWNER: Int = 0x00004000
private const val K_FSEVENT_STREAM_EVENT_FLAG_ITEM_XATTR_MOD: Int = 0x00008000
private const val K_FSEVENT_STREAM_EVENT_FLAG_ITEM_IS_DIR: Int = 0x00020000

private class MacOSXWatchKey(
  macOSXWatchService: AbstractWatchService,
  watchable: WatchablePath,
  events: Iterable<WatchEvent.Kind<*>>,
  queueSize: Int,
) : AbstractWatchKey(macOSXWatchService, watchable, events, queueSize) {
  val reportCreateEvents = events.any { it === ENTRY_CREATE }
  val reportModifyEvents = events.any { it === ENTRY_MODIFY }
  val reportDeleteEvents = events.any { it === ENTRY_DELETE }
}

private const val DEFAULT_LATENCY = 0.5
private const val DEFAULT_QUEUE_SIZE = 1024
