// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef.PayloadSource
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef.PayloadSource.Companion.isInline
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadStorageIO.Companion.fillData
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadStorageIO.PayloadAppendContext
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State
import com.intellij.openapi.vfs.newvfs.persistent.log.util.ULongPacker
import com.intellij.util.io.UnsyncByteArrayOutputStream
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.toPersistentSet
import java.io.OutputStream

object InlinedPayloadStorage : PayloadStorageIO {
  override val sourcesDeclaration: PersistentSet<PayloadSource> = PayloadSource.entries.filter { it.isInline }.toPersistentSet()

  fun isSuitableForInlining(sizeBytes: Long) = sizeBytes <= 7

  override fun appendPayload(sizeBytes: Long): PayloadAppendContext {
    require(isSuitableForInlining(sizeBytes)) { "payload of size $sizeBytes cannot be inlined (max size 7)" }
    return object : PayloadAppendContext {
      override fun fillData(data: ByteArray, offset: Int, length: Int): PayloadRef {
        require(length.toLong() == sizeBytes) { "expected entry of size $sizeBytes, got $length" }
        return inlineData(data.copyOfRange(offset, offset + length))
      }

      override fun fillData(body: OutputStream.() -> Unit): PayloadRef {
        return fillData(UnsyncByteArrayOutputStream().run {
          body()
          toByteArray()
        })
      }

      override fun close() {}
    }
  }

  override fun readPayload(payloadRef: PayloadRef): State.DefinedState<ByteArray> {
    return payloadRef.unInlineData().let(State::Ready)
  }

  private fun inlineData(data: ByteArray): PayloadRef {
    require(isSuitableForInlining(data.size.toLong()))
    var packedOffset: ULong = 0.toULong()
    with(ULongPacker) {
      data.forEachIndexed { index, byte ->
        packedOffset = packedOffset.setInt(byte.toUByte().toInt(), index * Byte.SIZE_BITS, Byte.SIZE_BITS)
      }
    }
    return PayloadRef(packedOffset.toLong(), PayloadSource.entries[data.size])
  }

  private fun PayloadRef.unInlineData(): ByteArray {
    require(source.isInline)
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