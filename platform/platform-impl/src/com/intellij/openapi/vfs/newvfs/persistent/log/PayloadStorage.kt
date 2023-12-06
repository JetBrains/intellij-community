// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State
import kotlinx.collections.immutable.PersistentSet
import java.io.OutputStream

interface PayloadStorage: PayloadStorageIO {
  /**
   * Actual amount of space consumed for data that is available through reading (bytes)
   */
  fun size(): Long

  fun flush()
  fun dispose()
}

interface PayloadStorageIO {
  val sourcesDeclaration: PersistentSet<PayloadRef.PayloadSource>
  fun appendPayload(sizeBytes: Long): PayloadAppendContext
  fun readPayload(payloadRef: PayloadRef): State.DefinedState<ByteArray>

  interface PayloadAppendContext: AutoCloseable {
    fun fillData(data: ByteArray, offset: Int, length: Int): PayloadRef
    fun fillData(body: OutputStream.() -> Unit): PayloadRef
  }
  companion object {
    fun PayloadAppendContext.fillData(data: ByteArray): PayloadRef = fillData(data, 0, data.size)
  }
}

typealias PayloadReader = (PayloadRef) -> State.DefinedState<ByteArray>
typealias PayloadWriter = (data: ByteArray) -> PayloadRef