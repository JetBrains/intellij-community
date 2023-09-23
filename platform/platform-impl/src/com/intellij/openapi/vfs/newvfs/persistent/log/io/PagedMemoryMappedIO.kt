// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

import com.intellij.util.io.ResilientFileChannel
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel.MapMode
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.util.*

@ApiStatus.Experimental
class PagedMemoryMappedIO private constructor(
  private val pageSize: Int,
  /**
   * shifts page indices, e.g., firstPageId = 4 -> first existing page will be #4 but stored as index #0 in shiftedPages
   */
  private val firstPageId: Int = 0,
  private val mapPage: (pageId: Int) -> MappedByteBuffer,
) : StorageIO {
  init {
    require(pageSize > 0 && (pageSize and (pageSize - 1)) == 0) { "pageSize must be a power of 2" }
  }

  private val offsetMask = pageSize - 1
  private val pageBits = pageSize.countTrailingZeroBits()

  // benign data races can happen here, it's fine
  // property is nullable because of unsafe publication and is not marked volatile intentionally
  // all modifications happen in synchronized blocks
  private var shiftedPages: Array<Page>? = null
  private var closed: Boolean = false

  private fun getExistingMappedPage(shiftedPageId: Int): MappedPage? {
    val pagesRef = shiftedPages
    if (pagesRef != null && shiftedPageId < pagesRef.size) {
      val page = pagesRef[shiftedPageId]
      return page as? MappedPage
    }
    return null
  }

  private fun getMappedPage(pageId: Int): MappedPage {
    require(pageId >= firstPageId) { "pageId(=$pageId) < declared first pageId(=$firstPageId)" }
    require(pageId < firstPageId + MAX_PAGES) { "pageId(=($pageId) >= first pageId(=${firstPageId}) + MAX_PAGES(=$MAX_PAGES)" }

    val shiftedPageId = pageId - firstPageId

    val existingPage = getExistingMappedPage(shiftedPageId)
    if (existingPage != null) return existingPage

    synchronized(this) {
      if (closed) throw IllegalAccessError("data is accessed after close()")

      var pagesRef = shiftedPages ?: emptyArray()
      if (shiftedPageId >= pagesRef.size) {
        val extendedPagesArray = Array((pagesRef.size * 2).coerceAtLeast(shiftedPageId + 1)) {
          if (it < pagesRef.size) pagesRef[it]
          else NotInitializedPage
        }
        shiftedPages = extendedPagesArray
        pagesRef = extendedPagesArray
      }

      when (val page = pagesRef[shiftedPageId]) {
        is MappedPage -> return page
        NotInitializedPage -> {
          val buf = mapPage(pageId)
          val newPage = MappedPage(buf)
          pagesRef[shiftedPageId] = newPage
          return newPage
        }
        DisposedPage -> throw IllegalAccessError("access to a disposed page")
      }
    }
  }

  private inline fun processRangeInPages(position: Long,
                                         length: Int,
                                         body: (processedBytesBefore: Int, pageId: Int, pageOffset: Int, len: Int) -> Unit) {
    require(position >= 0 && length >= 0) { "invalid memory access to region [$position..${position+length})" }
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
           toProcessStart.toInt() and offsetMask, // <=> toProcessStart % pageSize
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
    val page = getMappedPage(pageId)
    page.getBuffer().put(pageOffset, buf, offset, length)
  }

  override fun write(position: Long, buf: ByteBuffer, offset: Int, length: Int) {
    processRangeInPages(position, length) { processedBytesBefore, pageId, pageOffset, len ->
      writePageConfined(pageId, pageOffset, buf, offset + processedBytesBefore, len)
    }
  }

  private fun readPageConfined(pageId: Int, pageOffset: Int, buf: ByteArray, offset: Int, length: Int) {
    assert(0 <= pageOffset && pageOffset + length <= pageSize)
    val page = getMappedPage(pageId)
    page.getBuffer().get(pageOffset, buf, offset, length)
  }

  override fun read(position: Long, buf: ByteArray, offset: Int, length: Int) {
    processRangeInPages(position, length) { processedBytesBefore, pageId, pageOffset, len ->
      readPageConfined(pageId, pageOffset, buf, offset + processedBytesBefore, len)
    }
  }

  fun getPageIdForByte(bytePosition: Long): Int {
    val p = bytePosition ushr pageBits
    return Math.toIntExact(p)
  }

  private fun firstByteOf(pageId: Int): Long = pageId.toLong() shl pageBits
  private fun byteAfterLastOf(pageId: Int): Long = (pageId + 1).toLong() shl pageBits

  override fun flush() {
    synchronized(this) {
      val pagesRef = shiftedPages ?: emptyArray()
      for (page in pagesRef) {
        if (page is MappedPage) page.getBuffer().force()
      }
    }
  }

  /**
   * Free mapped buffers that were already allocated and are fully contained in [positionRange]. By invoking this
   * method, one guarantees that read/write access to the [positionRange] region will never happen.
   */
  fun disposePagesContainedIn(positionRange: LongRange) {
    synchronized(this) {
      // FIXME maybe we should allocate additional pages if positionRange spans beyond currently allocated range
      val pagesRef = shiftedPages ?: emptyArray()
      pagesRef.forEachIndexed { shiftedId, page ->
        if (page is MappedPage) {
          val id = shiftedId + firstPageId // unshift id
          if (positionRange.first <= firstByteOf(id) && byteAfterLastOf(id) - 1 <= positionRange.last) {
            pagesRef[shiftedId] = DisposedPage
          }
        }
      }
    }
  }

  override fun close() {
    if (closed) return
    synchronized(this) {
      if (closed) return
      closed = true
      val pagesRef = shiftedPages
      pagesRef?.forEachIndexed { shiftedPageId, page ->
        if (page is MappedPage) {
          // unmapping the page manually through Unsafe can lead to JVM crash,
          // if other thread caches a reference and tries to access the buffer
          pagesRef[shiftedPageId] = DisposedPage
        }
      }
    }
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

  private sealed interface Page {
    fun getBuffer(): MappedByteBuffer
  }

  private object NotInitializedPage : Page {
    override fun getBuffer(): MappedByteBuffer = throw AssertionError("page is not initialized")
  }

  private class MappedPage(private val buffer: MappedByteBuffer) : Page {
    override fun getBuffer(): MappedByteBuffer = buffer
  }

  private object DisposedPage : Page {
    override fun getBuffer(): MappedByteBuffer = throw AssertionError("page is already disposed")
  }

  companion object {
    private const val MAX_PAGES = 2 * 1024

    fun openFilePerPage(pageSize: Int,
                        mapMode: MapMode,
                        openMode: Set<OpenOption> = EnumSet.of(CREATE, READ, WRITE),
                        firstPageId: Int = 0,
                        pageLocation: (pageId: Int) -> Path): PagedMemoryMappedIO {
      return PagedMemoryMappedIO(pageSize, firstPageId) { pageId ->
        ResilientFileChannel(pageLocation(pageId), openMode).use { file ->
          file.map(mapMode, 0, pageSize.toLong())
        }
      }
    }
  }
}