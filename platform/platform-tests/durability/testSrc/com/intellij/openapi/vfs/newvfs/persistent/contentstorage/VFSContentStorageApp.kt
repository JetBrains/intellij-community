// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.contentstorage

import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.vfs.newvfs.persistent.App
import com.intellij.openapi.vfs.newvfs.persistent.AppAgent
import com.intellij.openapi.vfs.newvfs.persistent.mapped.content.CompressingAlgo.Lz4Algo
import com.intellij.openapi.vfs.newvfs.persistent.mapped.content.VFSContentStorageOverMMappedFile
import java.nio.file.Path

class VFSContentStorageApp : App {

  class VFSContentStorage : Storage {
    private val path: Path = Path.of("mmapped.data")
    private val storage: VFSContentStorageOverMMappedFile = VFSContentStorageOverMMappedFile(
      path, pageSize(), Lz4Algo(8000)
    )

    override fun setBytes(bytes: ByteArray): Int = storage.storeRecord(ByteArraySequence(bytes))

    override fun getBytes(id: Int): ByteArray = storage.readStream(id).readAllBytes()

    override fun close() {
      storage.closeAndUnsafelyUnmap()
    }
  }

  override fun run(appAgent: AppAgent) {
    val storage = VFSContentStorage()
    StorageApp(storage).run(appAgent)
  }
}
