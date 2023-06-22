// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import java.io.OutputStream

interface PayloadStorage: PayloadStorageIO {
  fun size(): Long

  fun flush()
  fun dispose()
}

interface PayloadStorageIO {
  fun writePayload(sizeBytes: Long, body: OutputStream.() -> Unit): PayloadRef
  fun readPayload(payloadRef: PayloadRef): ByteArray? // TODO DefinedState
}