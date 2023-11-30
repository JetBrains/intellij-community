// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

import com.intellij.util.io.ResilientFileChannel
import org.jetbrains.annotations.ApiStatus
import java.nio.channels.FileChannel
import java.nio.file.OpenOption
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Semaphore

@ApiStatus.Experimental
@ApiStatus.Internal
class FileChannelCache(
  private val maxOpenedFiles: Int,
  private val openFileChannel: (path: Path) -> FileChannel
) : AutoCloseable {
  constructor(maxOpenedFiles: Int, openOptions: Set<OpenOption>, openFileChannel: (path: Path) -> FileChannel = { path ->
    ResilientFileChannel(path, openOptions)
  }) : this(maxOpenedFiles, openFileChannel)

  private val liveHandlesQueue: Deque<Handle> = ArrayDeque(maxOpenedFiles)
  private val handles: MutableMap<Path, Handle> = hashMapOf()

  fun get(path: Path): CachedFileChannel = synchronized(this) {
    handles.getOrPut(path) { Handle(path) }
  }

  private fun requestOpenWithAccess(handle: Handle) = synchronized(this) {
    if (handle.state is HandleState.Opened) {
      // is there really #MAX_ACCESSORS simultaneous accesses? o_O
      handle.acquireAccess() // wait until one finishes
      return@synchronized
    }
    if (liveHandlesQueue.size == maxOpenedFiles) {
      closeIdleHandles()
    }
    val fc = openFileChannel(handle.path)
    handle.state = HandleState.Opened(fc)
    handle.releaseOpen()
    handle.acquireAccess()
  }

  /**
   * removes at least 1 handle from the [liveHandlesQueue]
   */
  private fun closeIdleHandles() {
    while (true) {
      val handle = liveHandlesQueue.removeFirst()
      if (handle.tryAcquireClose()) {
        handle.closeWithPermitsAcquired()
        return
      }
      liveHandlesQueue.addLast(handle) // put it back
    }
  }

  private inner class Handle(val path: Path): CachedFileChannel {
    @Volatile
    var state: HandleState = HandleState.Closed
    val readLock = Semaphore(0) // 0..MAX_READERS

    fun tryAcquireAccess() = readLock.tryAcquire()
    fun acquireAccess() = readLock.acquireUninterruptibly()
    fun releaseAccess() = readLock.release()

    fun acquireClose() = readLock.acquireUninterruptibly(MAX_ACCESSORS)
    fun tryAcquireClose() = readLock.tryAcquire(MAX_ACCESSORS)
    fun releaseOpen() = readLock.release(MAX_ACCESSORS)

    fun closeWithPermitsAcquired() {
      when (val s = state) {
        HandleState.Closed -> throw AssertionError("acquired closed, but state is already closed")
        is HandleState.Opened -> {
          s.fc.close()
          state = HandleState.Closed
        }
      }
    }

    override fun <R> access(body: FileChannel.() -> R): R {
      if (!tryAcquireAccess()) {
        requestOpenWithAccess(this)
      }
      try {
        when (val s = state) {
          HandleState.Closed -> throw AssertionError("handle is in closed state with access acquired")
          is HandleState.Opened -> {
            assert(s.fc.isOpen)
            return s.fc.body()
          }
        }
      } finally {
        releaseAccess()
      }
    }
  }

  private sealed class HandleState {
    object Closed : HandleState()
    class Opened(val fc: FileChannel): HandleState()
  }

  interface CachedFileChannel {
    /**
     * [FileChannel.isOpen] is true until body terminates (or exception happens TODO state after IO exception)
     */
    fun <R> access(body: FileChannel.() -> R): R
  }

  override fun close() = synchronized(this) {
    while (liveHandlesQueue.isNotEmpty()) {
      val handle = liveHandlesQueue.removeFirst()
      handle.acquireClose() // wait everyone to finish
      handle.closeWithPermitsAcquired()
    }
  }

  private companion object {
    const val MAX_ACCESSORS = 239_239_239
  }
}