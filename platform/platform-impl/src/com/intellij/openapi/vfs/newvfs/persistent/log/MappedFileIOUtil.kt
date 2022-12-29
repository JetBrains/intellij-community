// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import kotlin.math.max
import kotlin.math.min

class MappedFileIOUtil(
  private val fileChannel: FileChannel,
  private val mapMode: MapMode
) {
  private val pieces = ArrayList<MappedByteBuffer?>(16)

  private fun tryGetPiece(piece: Int): MappedByteBuffer? {
    if (piece < pieces.size) {
      val p = pieces[piece]
      if (p != null) {
        return p
      }
    }
    return null
  }

  private fun mapPiece(piece: Int): MappedByteBuffer {
    return fileChannel.map(mapMode, PIECE_SIZE * piece, PIECE_SIZE)
  }

  private fun getPiece(piece: Int): MappedByteBuffer {
    assert(piece in 0..MAX_PIECES)
    tryGetPiece(piece)?.let { return it }
    synchronized(pieces) {
      tryGetPiece(piece)?.let { return it }
      while (piece >= pieces.size) {
        pieces.add(null)
      }
      val p = mapPiece(piece)
      pieces[piece] = p
      return p
    }
  }

  /**
   * buf[ offset..(offset+length-1) ] -> piece[ pieceOffset..( pieceOffset+length-1) ]
   */
  private fun writePieceConfined(piece: Int, pieceOffset: Int, buf: ByteBuffer, offset: Int, length: Int) {
    assert(0 <= pieceOffset && pieceOffset + length <= PIECE_SIZE)
    val p = getPiece(piece)
    p.put(pieceOffset, buf, offset, length)
  }

  private inline fun processRangeInPieces(position: Long,
                                          length: Int,
                                          body: (processedBefore: Int, pieceId: Int, pieceOffset: Int, len: Int) -> Unit) {
    if (length == 0) return
    var piece = getPieceIdForByte(position)
    val targetRange = position until position + length
    var processed = 0
    while (true) {
      val pieceRange = getPieceRange(piece)
      val toProcessRange = targetRange.cap(pieceRange)
      if (toProcessRange == null) return
      val len = (toProcessRange.last - toProcessRange.first + 1).toInt()
      body(processed, piece, (toProcessRange.first % PIECE_SIZE).toInt(), len)
      processed += len
      piece++
    }
  }

  fun write(position: Long, buf: ByteBuffer, offset: Int, length: Int) {
    processRangeInPieces(position, length) { processedBefore, pieceId, pieceOffset, len ->
      writePieceConfined(pieceId, pieceOffset, buf, offset + processedBefore, len)
    }
  }

  fun write(position: Long, buf: ByteArray, offset: Int, length: Int) {
    return write(position, ByteBuffer.wrap(buf), offset, length)
  }

  fun write(position: Long, buf: ByteArray) {
    return write(position, ByteBuffer.wrap(buf), 0, buf.size)
  }

  private fun readPieceConfined(piece: Int, pieceOffset: Int, buf: ByteArray, offset: Int, length: Int) {
    assert(0 <= pieceOffset && pieceOffset + length <= PIECE_SIZE)
    val p = getPiece(piece)
    p.get(pieceOffset, buf, offset, length) // TODO: do we need to return anything?
  }

  fun read(position: Long, buf: ByteArray, offset: Int, length: Int) {
    processRangeInPieces(position, length) { processedBefore, pieceId, pieceOffset, len ->
      readPieceConfined(pieceId, pieceOffset, buf, offset + processedBefore, len)
    }
  }

  fun read(position: Long, buf: ByteArray) {
    return read(position, buf, 0, buf.size)
  }

  private fun getPieceIdForByte(bytePosition: Long): Int {
    val p = bytePosition / PIECE_SIZE
    assert(p in 0..MAX_PIECES)
    return p.toInt()
  }

  private fun getPieceRange(piece: Int): LongRange {
    return (piece * PIECE_SIZE) until (piece + 1) * PIECE_SIZE
  }

  private fun LongRange.cap(other: LongRange): LongRange? {
    assert(first <= last && other.first <= other.last)
    if (last < other.first || other.last < first) {
      return null
    }
    return max(first, other.first)..min(last, other.last)
  }

  companion object {
    private const val PIECE_SIZE = 1024 * 1024 * 1024L // 1 GB
    private const val MAX_PIECES = 1024 * 1024
  }
}