// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State
import kotlinx.collections.immutable.PersistentSet
import java.io.OutputStream

interface PayloadStorage: PayloadStorageIO {
  val sourcesDeclaration: PersistentSet<PayloadRef.Source>

  fun size(): Long

  fun flush()
  fun dispose()
}

interface PayloadStorageIO {
  fun writePayload(sizeBytes: Long, body: OutputStream.() -> Unit): PayloadRef
  fun readPayload(payloadRef: PayloadRef): State.DefinedState<ByteArray>
}

typealias PayloadReader = (PayloadRef) -> State.DefinedState<ByteArray>