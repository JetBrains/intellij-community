// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

import com.intellij.openapi.vfs.newvfs.persistent.log.io.PagedMemoryMappedIO.Page.PageState
import com.intellij.openapi.vfs.newvfs.persistent.log.util.SmallIndexMap
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer

class PagedMemoryMappedIO(
  private val pageSize: Int,
  private val firstPageId: Int = 0,
  private val mapPage: (pageId: Int) -> MappedByteBuffer,
) : StorageIO {
  init {
    require(pageSize > 0 && (pageSize and (pageSize - 1)) == 0) { "pageSize must be a power of 2" }
  }

  private val pageBitsMask = pageSize - 1

  // firstPageId offsets pages only in SmallIndexMap
  // e.g. firstPageId = 4 -> first existing page will be #4 but stored as index #0 in shiftedPages
  private val shiftedPages: SmallIndexMap<Page> = SmallIndexMap(64) {
    id -> Page(mapPage(id + firstPageId)) // unshift id
  }

  private inline fun processRangeInPages(position: Long,
                                          length: Int,
                                          body: (processedBytesBefore: Int, pageId: Int, pageOffset: Int, len: Int) -> Unit) {
    if (length == 0) return
    var pageId = getPageIdForByte(position)
    val endPosition = position + length
    var processed = 0
    while (firstByteOf(pageId) < endPosition) {
      val toProcessStart = firstByteOf(pageId).coerceAtLeast(position)
      val toProcessEnd = byteAfterLastOf(pageId).coerceAtMost(endPosition)
      val len = (toProcessEnd - toProcessStart).toInt()
      assert(len > 0)
      body(processed, pageId,
           toProcessStart.toInt() and pageBitsMask, // <=> toProcessStart % pageSize
           len)
      processed += len
      pageId++
    }
  }

  /**
   * buf[ offset..offset+length ) -> page[ pageOffset..pageOffset+length )
   */
  private fun writePageConfined(pageId: Int, pageOffset: Int, buf: ByteBuffer, offset: Int, length: Int) {
    assert(0 <= pageOffset && pageOffset + length <= pageSize)
    val page = shiftedPages.getOrCreate(pageId - firstPageId) // shift id
    page.getBuffer().put(pageOffset, buf, offset, length)
  }

  override fun write(position: Long, buf: ByteBuffer, offset: Int, length: Int) {
    processRangeInPages(position, length) { processedBytesBefore, pageId, pageOffset, len ->
      writePageConfined(pageId, pageOffset, buf, offset + processedBytesBefore, len)
    }
  }

  private fun readPageConfined(pageId: Int, pageOffset: Int, buf: ByteArray, offset: Int, length: Int) {
    assert(0 <= pageOffset && pageOffset + length <= pageSize)
    val page = shiftedPages.getOrCreate(pageId - firstPageId) // shift id
    page.getBuffer().get(pageOffset, buf, offset, length)
  }

  override fun read(position: Long, buf: ByteArray, offset: Int, length: Int) {
    processRangeInPages(position, length) { processedBytesBefore, pageId, pageOffset, len ->
      readPageConfined(pageId, pageOffset, buf, offset + processedBytesBefore, len)
    }
  }

  fun getPageIdForByte(bytePosition: Long): Int {
    val p = bytePosition / pageSize
    return p.toInt()
  }

  private fun firstByteOf(pageId: Int): Long = pageId.toLong() * pageSize
  private fun byteAfterLastOf(pageId: Int): Long = (pageId + 1).toLong() * pageSize

  override fun flush() {
    shiftedPages.forEachExisting { _, page ->
      when (val s = page.state) {
        PageState.Disposed -> {}
        is PageState.Mapped -> s.buffer.force()
      }
    }
  }

  /**
   * Free mapped buffers that were already allocated and are fully contained in [positionRange]. By invoking this
   * method, one guarantees that read/write access to the [positionRange] region will never happen.
   */
  fun disposePagesContainedIn(positionRange: LongRange) {
    shiftedPages.forEachExisting { shiftedId, page ->
      val id = shiftedId + firstPageId // unshift id
      if (positionRange.first <= firstByteOf(id) && byteAfterLastOf(id) - 1 <= positionRange.last) {
        when (page.state) {
          PageState.Disposed -> {}
          is PageState.Mapped -> page.state = PageState.Disposed
        }
      }
    }
  }

  override fun close() {
    shiftedPages.close()
  }

  override fun offsetOutputStream(startPosition: Long): OffsetOutputStream =
    OffsetOutputStream(this, startPosition)

  class OffsetOutputStream(
    private val mmapIO: PagedMemoryMappedIO,
    private val startOffset: Long,
  ) : OutputStreamWithValidation() {
    private var position = startOffset

    override fun write(b: Int) {
      mmapIO.write(position, byteArrayOf(b.toByte()))
      position++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      mmapIO.write(position, b, off, len)
      position += len
    }

    override fun validateWrittenBytesCount(expectedBytesWritten: Long) {
      if (position - startOffset != expectedBytesWritten) {
        throw IllegalStateException(
          "unexpected amount of data has been written: written ${position - startOffset} vs expected ${expectedBytesWritten}")
      }
    }
  }

  private class Page(buffer: MappedByteBuffer) {
    // Page states may be modified: mapped -> disposed, but such data race is not a problem here due to external guarantees
    @Volatile
    var state: PageState = PageState.Mapped(buffer)

    fun getBuffer(): MappedByteBuffer = when (val s = state) {
      is PageState.Mapped -> s.buffer
      PageState.Disposed -> throw AssertionError("access to a disposed page")
    }

    sealed interface PageState {
      class Mapped(val buffer: MappedByteBuffer) : PageState
      object Disposed : PageState
    }
  }
}