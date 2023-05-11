// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.util.io.DataOutputStream
import java.io.DataInputStream
import java.io.OutputStream

interface PayloadStorage {
  fun writePayload(sizeBytes: Long, body: OutputStream.() -> Unit): PayloadRef
  fun readAt(ref: PayloadRef): ByteArray?

  fun size(): Long

  fun flush()
  fun dispose()
}

@JvmInline
value class PayloadRef(val value: Long) {
  val storeId get() = value ushr STORE_BITS
  val offset get() = value and ((1L shl STORE_BITS) - 1)

  companion object {
    /**
     * max payload size = 2^40 - 1 B ~= 1 TB
     * max store id = 2^(63-40) - 1 ~= 8e6
     */
    private const val STORE_BITS = 40

    const val SIZE_BYTES = Long.SIZE_BYTES

    val ZERO_SIZE = PayloadRef(-1L)

    fun DataInputStream.readPayloadRef(): PayloadRef {
      return PayloadRef(readLong())
    }

    fun DataOutputStream.writePayloadRef(payloadRef: PayloadRef) {
      writeLong(payloadRef.value)
    }
  }
}
