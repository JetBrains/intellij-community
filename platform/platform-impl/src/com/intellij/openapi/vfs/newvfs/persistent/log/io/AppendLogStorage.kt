// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.persistent.log.io.AtomicDurableRecord.Companion.RecordBuilder
import com.intellij.openapi.vfs.newvfs.persistent.log.io.DurablePersistentByteArray.Companion.OpenMode.ReadWrite
import com.intellij.openapi.vfs.newvfs.persistent.log.util.AdvancingPositionTracker.AdvanceToken
import com.intellij.openapi.vfs.newvfs.persistent.log.util.CloseableAdvancingPositionTracker
import com.intellij.openapi.vfs.newvfs.persistent.log.util.LockFreeAdvancingPositionTracker
import com.intellij.util.runSuppressing
import java.io.Closeable
import java.io.Flushable
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel.MapMode
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardOpenOption.*
import java.util.*
import kotlin.io.path.*

/**
 * @param pageSize if changed between instantiations, storage must be cleared beforehand (everything in [storagePath])
 */
class AppendLogStorage(
  private val storagePath: Path,
  private val openMode: Mode,
  private val pageSize: Int
) : RandomAccessReadBuffer, Flushable, Closeable {
  private val pagesDir = storagePath / "chunks"
  private val atomicState: AtomicDurableRecord<State>
  private val storageIO: PagedMemoryMappedIO
  private val positionTracker: CloseableAdvancingPositionTracker

  init {
    require(pageSize > 0 && (pageSize and (pageSize - 1)) == 0) { "pageSize must be a power of 2" }

    FileUtil.ensureExists(storagePath.toFile())
    FileUtil.ensureExists(pagesDir.toFile())

    atomicState = AtomicDurableRecord.open(storagePath / "state", ReadWrite, stateBuilder)

    val stateSnapshot = atomicState.get()
    val firstPageId = stateSnapshot.startOffset / pageSize
    storageIO = PagedMemoryMappedIO.openFilePerPage(pageSize, openMode.mapMode, firstPageId = firstPageId.toInt()) {
      pagesDir / pageName(it)
    }
    positionTracker = LockFreeAdvancingPositionTracker(stateSnapshot.size)
  }

  /**
   * position in [RandomAccessWriteBuffer]'s methods is entry-local
   */
  interface AppendContext : AutoCloseable, RandomAccessWriteBuffer {
    val position: Long
    fun fillEntry(body: OutputStream.() -> Unit)
    fun fillEntry(data: ByteArray, offset: Int, length: Int)
    fun fillEntry(data: ByteArray): Unit = fillEntry(data, 0, data.size)
  }

  fun appendEntry(size: Long): AppendContext {
    val token = positionTracker.startAdvance(size)
    return AppendContextImpl(token, size)
  }

  private inner class AppendContextImpl(
    val advanceToken: AdvanceToken,
    val expectedSize: Long
  ) : AppendContext {
    override val position: Long get() = advanceToken.position

    override fun fillEntry(data: ByteArray, offset: Int, length: Int) {
      require(length.toLong() == expectedSize) { "expected entry of size $expectedSize, got $length" }
      storageIO.write(advanceToken.position, data, offset, length)
    }

    override fun fillEntry(body: OutputStream.() -> Unit) {
      storageIO.offsetOutputStream(advanceToken.position).use {
        it.body()
        it.validateWrittenBytesCount(expectedSize)
      }
    }

    override fun close() {
      positionTracker.finishAdvance(advanceToken)
    }

    override fun write(position: Long, buf: ByteBuffer, offset: Int, length: Int) {
      require(position >= 0 && position + length <= expectedSize) {
        "[$position..${position + length}) is not contained in [0..$expectedSize)"
      }
      storageIO.write(position + advanceToken.position, buf, offset, length)
    }
  }

  override fun read(position: Long, buf: ByteArray, offset: Int, length: Int): Unit = storageIO.read(position, buf, offset, length)

  /**
   * There must be an external guarantee that read/write access will never happen to the [0, position) memory region.
   */
  fun clearUpTo(position: Long) {
    atomicState.update {
      require(position >= startOffset) {
        "new start offset is smaller than before: was $startOffset, new $position"
      }
      startOffset = position
    }
    val lastByteToFree = position - 1
    if (lastByteToFree < 0) return
    storageIO.disposePagesContainedIn(0..lastByteToFree)
    val lastPageToClear = storageIO.getPageIdForByte(lastByteToFree) - 1 // lastByteToFree might be in the middle of the page
    pagesDir.forEachDirectoryEntry("*.dat") { pageFile ->
      val id = try {
        pageFile.name.removeSuffix(".dat").toInt(PAGE_ENCODING_RADIX)
      }
      catch (e: Throwable) {
        LOG.warn("alien file in pages directory: ${pageFile.toAbsolutePath()}", e)
        null
      }
      if (id != null && id <= lastPageToClear) {
        try {
          pageFile.deleteExisting()
        }
        catch (e: IOException) {
          LOG.warn("failed to clear page ${pageFile.toAbsolutePath()}", e)
        }
      }
    }
  }

  fun size(): Long = positionTracker.getReadyPosition()
  fun emergingSize(): Long = positionTracker.getCurrentAdvancePosition()
  fun persistentSize(): Long = atomicState.get().size
  fun startOffset(): Long = atomicState.get().startOffset

  override fun flush() {
    val readyPosition = positionTracker.getReadyPosition()
    if (readyPosition != persistentSize()) {
      storageIO.flush()
      atomicState.update {
        size = readyPosition
      }
    }
  }

  fun forbidNewAppends() {
    positionTracker.close()
  }

  override fun close() {
    runSuppressing(
      storageIO::close,
      atomicState::close
    )
  }

  private fun pageName(id: Int): String = id.toString(PAGE_ENCODING_RADIX) + ".dat"

  companion object {
    private val LOG = Logger.getInstance(AppendLogStorage::class.java)
    private const val PAGE_ENCODING_RADIX = 16

    enum class Mode(val openOptions: Set<StandardOpenOption>, val mapMode: MapMode) {
      Read(EnumSet.of(READ), MapMode.READ_ONLY),
      ReadWrite(EnumSet.of(READ, WRITE, CREATE), MapMode.READ_WRITE)
    }

    private interface State {
      var size: Long
      var startOffset: Long
    }

    private val stateBuilder: RecordBuilder<State>.() -> State = {
      object : State { // 32 bytes
        override var size by long(0)
        override var startOffset by long(0)
        private val reserved_ by bytearray(16)
      }
    }

    fun resetSize(storagePath: Path, newSize: Long) {
      val statePath = storagePath / "state"
      require(statePath.exists()) { "state file of AppendLogStorage does not exist in $storagePath" }
      val atomicState = AtomicDurableRecord.open(statePath, ReadWrite, stateBuilder)
      atomicState.update {
        size = newSize
      }
    }
  }
}