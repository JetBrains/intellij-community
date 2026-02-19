// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.bytearraystorage

import com.intellij.openapi.vfs.newvfs.persistent.App
import com.intellij.openapi.vfs.newvfs.persistent.AppAgent
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory
import java.nio.file.Path
import kotlin.math.min

class MMappedFileStorageApp : App {

  class MMappedStorage : Storage {
    private val path: Path = Path.of("mmapped.data")
    private val storage: MMappedFileStorage = MMappedFileStorageFactory
      .withDefaults()
      .pageSize(pageSize())
      .createParentDirectories(true)
      .open(path)

    override fun setBytes(bytes: ByteArray, offset: Int) {
      val page = storage.pageByOffset(offset.toLong())
      val offsetInPage = storage.toOffsetInPage(offset.toLong())
      page.rawPageBuffer().put(offsetInPage, bytes)
    }

    override fun getBytes(offset: Int, size: Int): ByteArray {
      val target = ByteArray(size)
      
      var currentOffsetInFile = offset.toLong()
      var sizeLeft = size
      var targetOffset = 0
      while (sizeLeft > 0) {
        val page = storage.pageByOffset(currentOffsetInFile)
        val offsetInPage = storage.toOffsetInPage(currentOffsetInFile)
        val buffer = page.rawPageBuffer()
        val toCopyFromPage = min(sizeLeft, buffer.capacity() - offsetInPage)

        buffer.get(offsetInPage, target, targetOffset, toCopyFromPage)

        sizeLeft -= toCopyFromPage
        targetOffset += toCopyFromPage
        currentOffsetInFile += toCopyFromPage
      }

      return target
    }

    override fun flush() {
      //nothing
    }

    override fun close() {
      storage.closeAndUnsafelyUnmap()
    }
  }

  override fun run(appAgent: AppAgent) {
    val storage = MMappedStorage()
    StorageApp(storage).run(appAgent)
  }
}
