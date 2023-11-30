// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.bytearraystorage

import com.intellij.openapi.vfs.newvfs.persistent.App
import com.intellij.openapi.vfs.newvfs.persistent.AppAgent
import com.intellij.openapi.vfs.newvfs.persistent.log.io.DurablePersistentByteArray
import com.intellij.openapi.vfs.newvfs.persistent.log.io.DurablePersistentByteArray.Companion.OpenMode
import java.nio.file.Path

class DurablePersistentByteArrayStorageApp : App {
  private class DurablePersistentByteArrayStorage: Storage {
    val path = Path.of("dpba.dat")
    val stateSize = Storage.stateSize

    val persistence = DurablePersistentByteArray.open(path, OpenMode.ReadWrite, stateSize) { ByteArray(stateSize) }

    override fun setBytes(bytes: ByteArray, offset: Int) {
      persistence.commitChange {
        bytes.copyInto(it, offset)
      }
    }

    override fun getBytes(offset: Int, size: Int): ByteArray {
      return persistence.getLastSnapshot().copyOfRange(offset, offset + size)
    }

    override fun flush() {
      // no op
    }

    override fun close() {
      persistence.close()
    }
  }

  override fun run(appAgent: AppAgent) {
    val storage = DurablePersistentByteArrayStorage()
    StorageApp(storage).run(appAgent)
  }
}