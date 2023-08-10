// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

import com.intellij.openapi.vfs.newvfs.persistent.log.io.ChunkedMemoryMappedIO.Chunk.ChunkState
import com.intellij.openapi.vfs.newvfs.persistent.log.util.SmallIndexMap
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer

class ChunkedMemoryMappedIO(
  private val chunkSize: Int,
  private val firstChunkId: Int = 0,
  private val mapChunk: (chunkId: Int) -> MappedByteBuffer,
) : StorageIO {
  init {
    require(chunkSize > 0 && (chunkSize and (chunkSize - 1)) == 0) { "chunkSize must be a power of 2" }
  }

  private val chunkBitsMask = chunkSize - 1

  // firstChunkId offsets chunks only in SmallIndexMap
  // e.g. firstChunkId = 4 -> first existing chunk will be #4 but stored as index #0 in shiftedChunks
  private val shiftedChunks: SmallIndexMap<Chunk> = SmallIndexMap(64) {
    id -> Chunk(mapChunk(id + firstChunkId)) // unshift id
  }

  private inline fun processRangeInChunks(position: Long,
                                          length: Int,
                                          body: (processedBytesBefore: Int, chunkId: Int, chunkOffset: Int, len: Int) -> Unit) {
    if (length == 0) return
    var chunkId = getChunkIdForByte(position)
    val endPosition = position + length
    var processed = 0
    while (firstByteOf(chunkId) < endPosition) {
      val toProcessStart = firstByteOf(chunkId).coerceAtLeast(position)
      val toProcessEnd = byteAfterLastOf(chunkId).coerceAtMost(endPosition)
      val len = (toProcessEnd - toProcessStart).toInt()
      assert(len > 0)
      body(processed, chunkId,
           toProcessStart.toInt() and chunkBitsMask, // <=> toProcessStart % chunkSize
           len)
      processed += len
      chunkId++
    }
  }

  /**
   * buf[ offset..offset+length ) -> chunk[ chunkOffset..chunkOffset+length )
   */
  private fun writeChunkConfined(chunkId: Int, chunkOffset: Int, buf: ByteBuffer, offset: Int, length: Int) {
    assert(0 <= chunkOffset && chunkOffset + length <= chunkSize)
    val chunk = shiftedChunks.getOrCreate(chunkId - firstChunkId) // shift id
    chunk.getBuffer().put(chunkOffset, buf, offset, length)
  }

  override fun write(position: Long, buf: ByteBuffer, offset: Int, length: Int) {
    processRangeInChunks(position, length) { processedBytesBefore, chunkId, chunkOffset, len ->
      writeChunkConfined(chunkId, chunkOffset, buf, offset + processedBytesBefore, len)
    }
  }

  private fun readChunkConfined(chunkId: Int, chunkOffset: Int, buf: ByteArray, offset: Int, length: Int) {
    assert(0 <= chunkOffset && chunkOffset + length <= chunkSize)
    val chunk = shiftedChunks.getOrCreate(chunkId - firstChunkId) // shift id
    chunk.getBuffer().get(chunkOffset, buf, offset, length)
  }

  override fun read(position: Long, buf: ByteArray, offset: Int, length: Int) {
    processRangeInChunks(position, length) { processedBytesBefore, chunkId, chunkOffset, len ->
      readChunkConfined(chunkId, chunkOffset, buf, offset + processedBytesBefore, len)
    }
  }

  fun getChunkIdForByte(bytePosition: Long): Int {
    val p = bytePosition / chunkSize
    return p.toInt()
  }

  private fun firstByteOf(chunkId: Int): Long = chunkId.toLong() * chunkSize
  private fun byteAfterLastOf(chunkId: Int): Long = (chunkId + 1).toLong() * chunkSize

  override fun flush() {
    shiftedChunks.forEachExisting { _, chunk ->
      when (val s = chunk.state) {
        ChunkState.Disposed -> {}
        is ChunkState.Mapped -> s.buffer.force()
      }
    }
  }

  /**
   * Free mapped buffers that were already allocated and are fully contained in [positionRange]. By invoking this
   * method, one guarantees that read/write access to the [positionRange] region will never happen.
   */
  fun disposeChunksContainedIn(positionRange: LongRange) {
    shiftedChunks.forEachExisting { shiftedId, chunk ->
      val id = shiftedId + firstChunkId // unshift id
      if (positionRange.first <= firstByteOf(id) && byteAfterLastOf(id) - 1 <= positionRange.last) {
        when (chunk.state) {
          ChunkState.Disposed -> {}
          is ChunkState.Mapped -> chunk.state = ChunkState.Disposed
        }
      }
    }
  }

  override fun close() {
    shiftedChunks.close()
  }

  override fun offsetOutputStream(startPosition: Long): OffsetOutputStream =
    OffsetOutputStream(this, startPosition)

  class OffsetOutputStream(
    private val mmapIO: ChunkedMemoryMappedIO,
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

  private class Chunk(buffer: MappedByteBuffer) {
    // Chunk states may be modified (mapped -> disposed), but such data race is not a problem here due to external guarantees
    @Volatile
    var state: ChunkState = ChunkState.Mapped(buffer)

    fun getBuffer(): MappedByteBuffer = when (val s = state) {
      is ChunkState.Mapped -> s.buffer
      ChunkState.Disposed -> throw AssertionError("access to a disposed chunk")
    }

    sealed interface ChunkState {
      class Mapped(val buffer: MappedByteBuffer) : ChunkState
      object Disposed : ChunkState
    }
  }
}