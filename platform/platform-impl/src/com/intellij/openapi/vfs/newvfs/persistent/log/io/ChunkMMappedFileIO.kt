// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

import com.intellij.openapi.vfs.newvfs.persistent.log.util.SmallIndexMap
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode

class ChunkMMappedFileIO(
  private val fileChannel: FileChannel,
  private val mapMode: MapMode
) : StorageIO {
  private val chunks: SmallIndexMap<MappedByteBuffer> = SmallIndexMap(::mapChunk)

  private fun mapChunk(chunkId: Int): MappedByteBuffer {
    return fileChannel.map(mapMode, CHUNK_SIZE * chunkId, CHUNK_SIZE)
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
      body(processed, chunkId, (toProcessStart % CHUNK_SIZE).toInt(), len)
      processed += len
      chunkId++
    }
  }

  /**
   * buf[ offset..offset+length ) -> chunk[ chunkOffset..chunkOffset+length )
   */
  private fun writeChunkConfined(chunkId: Int, chunkOffset: Int, buf: ByteBuffer, offset: Int, length: Int) {
    assert(0 <= chunkOffset && chunkOffset + length <= CHUNK_SIZE)
    val chunk = chunks.getOrCreate(chunkId)
    chunk.put(chunkOffset, buf, offset, length)
  }

  override fun write(position: Long, buf: ByteBuffer, offset: Int, length: Int) {
    processRangeInChunks(position, length) { processedBytesBefore, chunkId, chunkOffset, len ->
      writeChunkConfined(chunkId, chunkOffset, buf, offset + processedBytesBefore, len)
    }
  }

  private fun readChunkConfined(chunkId: Int, chunkOffset: Int, buf: ByteArray, offset: Int, length: Int) {
    assert(0 <= chunkOffset && chunkOffset + length <= CHUNK_SIZE)
    val chunk = chunks.getOrCreate(chunkId)
    chunk.get(chunkOffset, buf, offset, length)
  }

  override fun read(position: Long, buf: ByteArray, offset: Int, length: Int) {
    processRangeInChunks(position, length) { processedBytesBefore, chunkId, chunkOffset, len ->
      readChunkConfined(chunkId, chunkOffset, buf, offset + processedBytesBefore, len)
    }
  }

  private fun getChunkIdForByte(bytePosition: Long): Int {
    val p = bytePosition / CHUNK_SIZE
    assert(p in 0..MAX_CHUNKS)
    return p.toInt()
  }

  private fun firstByteOf(chunkId: Int): Long = chunkId * CHUNK_SIZE
  private fun byteAfterLastOf(chunkId: Int): Long = (chunkId + 1) * CHUNK_SIZE

  override fun isDirty(): Nothing = throw UnsupportedOperationException("unexpected call")

  override fun force() {
    chunks.forEachExisting {
      it.force()
    }
  }

  override fun close() {
    fileChannel.close()
    chunks.close()
  }

  override fun offsetOutputStream(startPosition: Long): OffsetOutputStream = OffsetOutputStream(this, startPosition)

  class OffsetOutputStream(
    private val mmapIO: ChunkMMappedFileIO,
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

  companion object {
    private const val CHUNK_SIZE = 1024 * 1024 * 128L // 128 MiB
    private const val MAX_CHUNKS = 1024 * 1024
  }
}