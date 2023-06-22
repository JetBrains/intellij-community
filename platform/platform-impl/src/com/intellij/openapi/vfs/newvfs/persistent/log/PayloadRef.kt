// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef.Source
import com.intellij.util.io.DataOutputStream
import java.io.DataInputStream

/**
 * Comprises information about where the data is stored. Use [offset] for the actual offset in the data store. [source] is conceptually
 * inspired by Unicode Planes and is used to support data source demultiplexing (see [Source] docs for details).
 */
@JvmInline
value class PayloadRef(val compressedInfo: ULong) {
  constructor(offset: Long, source: Source) : this(compressInfo(offset, source))

  val sourceOrdinal: Int get() = with(ULongPacker) { compressedInfo.getInt(SOURCE_OFFSET, SOURCE_BITS) }

  init {
    require(sourceOrdinal < Source.VALUES.size) {
      "unknown plane: id=$sourceOrdinal is outside of the registered ids range"
    }
  }

  /**
   * @see [Source]
   */
  val source: Source get() = Source.VALUES[sourceOrdinal]

  /**
   * 0 <= [offset] < 2^56
   */
  val offset: Long get() = with(ULongPacker) { compressedInfo.getLong(OFFSET_OFFSET, OFFSET_BITS) }


  /** Sources 0 to 7 are used to store data right in the reference,
   * 8 is reserved for [VfsLog PayloadStorage][com.intellij.openapi.vfs.newvfs.persistent.log.PayloadStorage],
   * 9 is for TODO compacted vfs */
  enum class Source {
    Inline0,
    Inline1,
    Inline2,
    Inline3,
    Inline4,
    Inline5,
    Inline6,
    Inline7,
    PayloadStorage;

    companion object {
      internal val VALUES = Source.values() // to not generate too much garbage
    }
  }

  companion object {
    const val SIZE_BYTES: Int = ULong.SIZE_BYTES
    const val SIZE_BITS: Int = ULong.SIZE_BITS

    fun DataInputStream.readPayloadRef(): PayloadRef = PayloadRef(readLong().toULong())
    fun DataOutputStream.writePayloadRef(payloadRef: PayloadRef) = writeLong(payloadRef.compressedInfo.toLong())

    private const val OFFSET_OFFSET: Int = 0
    private const val OFFSET_BITS: Int = SIZE_BITS - Byte.SIZE_BITS
    private const val SOURCE_OFFSET: Int = OFFSET_OFFSET + OFFSET_BITS
    private const val SOURCE_BITS: Int = Byte.SIZE_BITS

    private fun compressInfo(offset: Long, source: Source): ULong = with(ULongPacker) {
      0.toULong()
        .setInt(source.ordinal, SOURCE_OFFSET, SOURCE_BITS)
        .setLong(offset, OFFSET_OFFSET, OFFSET_BITS)
    }

    val PayloadRef.isInlined: Boolean get() = sourceOrdinal in Source.Inline0.ordinal..Source.Inline7.ordinal

    fun isSuitableForInlining(data: ByteArray) = data.size <= 7

    fun inlineData(data: ByteArray): PayloadRef {
      require(data.size <= 7)
      var packedOffset: ULong = 0.toULong()
      with(ULongPacker) {
        data.forEachIndexed { index, byte ->
          packedOffset = packedOffset.setInt(byte.toUByte().toInt(), index * Byte.SIZE_BITS, Byte.SIZE_BITS)
        }
      }
      return PayloadRef(packedOffset.toLong(), Source.VALUES[data.size])
    }

    fun PayloadRef.unInlineData(): ByteArray {
      require(isInlined)
      val size = sourceOrdinal
      val packedOffset = offset.toULong()
      val data = ByteArray(size)
      with(ULongPacker) {
        for (index in 0 until size) {
          data[index] = packedOffset.getInt(index * Byte.SIZE_BITS, Byte.SIZE_BITS).toByte()
        }
      }
      return data
    }
  }

  override fun toString(): String = "PayloadRef(source=$source, offset=$offset)"
}