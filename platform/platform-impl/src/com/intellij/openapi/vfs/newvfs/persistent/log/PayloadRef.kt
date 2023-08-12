// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef.PayloadSource
import com.intellij.openapi.vfs.newvfs.persistent.log.util.ULongPacker
import com.intellij.util.io.DataOutputStream
import java.io.DataInputStream

/**
 * Comprises information about where the data is stored. Use [offset] for the actual offset in the data store.
 * [source] is used to support data source demultiplexing (see [PayloadSource] for details).
 */
@JvmInline
value class PayloadRef(val compressedInfo: ULong) {
  constructor(offset: Long, source: PayloadSource) : this(compressInfo(offset, source))

  val sourceOrdinal: Int get() = with(ULongPacker) { compressedInfo.getInt(SOURCE_OFFSET, SOURCE_BITS) }

  init {
    require(sourceOrdinal < PayloadSource.VALUES.size) { "unknown source: id=$sourceOrdinal is outside of the registered ids range" }
  }

  /**
   * @see [PayloadSource]
   */
  val source: PayloadSource get() = PayloadSource.VALUES[sourceOrdinal]

  /**
   * 0 <= [offset] < 2^56
   */
  val offset: Long get() = with(ULongPacker) { compressedInfo.getLong(OFFSET_OFFSET, OFFSET_BITS) }


  /** Sources 0 to 7 are used to store data right in the reference,
   * 8 is reserved for [VfsLog PayloadStorage][com.intellij.openapi.vfs.newvfs.persistent.log.PayloadStorage],
   * 9 is for attributes of compacted vfs */
  enum class PayloadSource {
    Inline0,
    Inline1,
    Inline2,
    Inline3,
    Inline4,
    Inline5,
    Inline6,
    Inline7,
    PayloadStorage,
    CompactedVfsAttributes;

    companion object {
      internal val VALUES = PayloadSource.values() // to not generate too much garbage
      val PayloadSource.isInline: Boolean get() = this in Inline0..Inline7
    }
  }

  companion object {
    const val SIZE_BYTES: Int = ULong.SIZE_BYTES
    private const val SIZE_BITS: Int = ULong.SIZE_BITS

    fun DataInputStream.readPayloadRef(): PayloadRef = PayloadRef(readLong().toULong())
    fun DataOutputStream.writePayloadRef(payloadRef: PayloadRef) = writeLong(payloadRef.compressedInfo.toLong())

    private const val OFFSET_OFFSET: Int = 0
    private const val OFFSET_BITS: Int = SIZE_BITS - Byte.SIZE_BITS
    private const val SOURCE_OFFSET: Int = OFFSET_OFFSET + OFFSET_BITS
    private const val SOURCE_BITS: Int = Byte.SIZE_BITS

    private fun compressInfo(offset: Long, source: PayloadSource): ULong = with(ULongPacker) {
      0.toULong()
        .setInt(source.ordinal, SOURCE_OFFSET, SOURCE_BITS)
        .setLong(offset, OFFSET_OFFSET, OFFSET_BITS)
    }
  }

  override fun toString(): String = "PayloadRef(source=$source, offset=$offset)"
}