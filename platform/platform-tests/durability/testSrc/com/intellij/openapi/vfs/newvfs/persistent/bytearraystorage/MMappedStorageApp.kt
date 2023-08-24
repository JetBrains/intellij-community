// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.bytearraystorage

import com.intellij.openapi.vfs.newvfs.persistent.App
import com.intellij.openapi.vfs.newvfs.persistent.AppAgent
import com.intellij.openapi.vfs.newvfs.persistent.bytearraystorage.MMappedStorageApp.MMappedStorage.WriteImpl.ByteByByte
import com.intellij.openapi.vfs.newvfs.persistent.bytearraystorage.MMappedStorageApp.MMappedStorage.WriteImpl.SinglePut
import com.intellij.util.io.ByteBufferUtil
import com.intellij.util.io.ResilientFileChannel
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

class MMappedStorageApp: App {

  class MMappedStorage: Storage {
    val path = Path.of("mmapped.data")
    val stateSize = Storage.stateSize

    enum class WriteImpl(val desc: String) {
      SinglePut("single-put"),
      ByteByByte("byte-by-byte")
    }

    val writeImpl = when (val d = System.getenv("stress.mmapped-storage.write-impl") ?: SinglePut.desc) {
      SinglePut.desc -> SinglePut
      ByteByByte.desc -> ByteByByte
      else -> throw IllegalArgumentException(d)
    }

    init {
      if (!path.exists()) {
        path.writeBytes(ByteArray(stateSize), WRITE, CREATE)
      }
    }

    var mmap: MappedByteBuffer? = ResilientFileChannel(path, READ, WRITE, CREATE).use {
      it.map(FileChannel.MapMode.READ_WRITE, 0, stateSize.toLong())
    }

    override fun setBytes(bytes: ByteArray, offset: Int) {
      when (writeImpl) {
        SinglePut -> mmap!!.put(offset, bytes)
        ByteByByte -> {
          for (i in offset until offset + bytes.size) {
            mmap!!.put(i, bytes[i - offset])
          }
        }
      }
    }

    override fun getBytes(offset: Int, size: Int): ByteArray {
      val result = ByteArray(size)
      mmap!!.get(offset, result)
      return result
    }

    override fun flush() {
      mmap!!.force()
    }

    override fun close() {
      ByteBufferUtil.cleanBuffer(mmap!!)
      mmap = null
    }
  }

  override fun run(appAgent: AppAgent) {
    val storage = MMappedStorage()
    StorageApp(storage).run(appAgent)
  }
}
