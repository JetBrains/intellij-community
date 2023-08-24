// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.bytearraystorage

import com.intellij.openapi.vfs.newvfs.persistent.App
import com.intellij.openapi.vfs.newvfs.persistent.AppAgent
import com.intellij.util.io.ResilientFileChannel
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

class FileChannelStorageApp: App {
  class FileChannelStorage: Storage {
    val path = Path.of("fc.data")
    val stateSize = Storage.stateSize

    init {
      if (!path.exists()) {
        path.writeBytes(ByteArray(stateSize), WRITE, CREATE)
      }
    }

    val fileChannel = ResilientFileChannel(path, READ, WRITE, CREATE)

    override fun setBytes(bytes: ByteArray, offset: Int) {
      val buf = ByteBuffer.wrap(bytes)
      fileChannel.position(offset.toLong())
      while (buf.hasRemaining()) {
        fileChannel.write(buf)
      }
    }

    override fun getBytes(offset: Int, size: Int): ByteArray {
      val result = ByteArray(size)
      val buf = ByteBuffer.wrap(result)
      fileChannel.position(offset.toLong())
      while (buf.hasRemaining()) {
        fileChannel.read(buf)
      }
      return result
    }

    override fun flush() {
      fileChannel.force(true)
    }

    override fun close() {
      fileChannel.close()
    }
  }

  override fun run(appAgent: AppAgent) {
    val storage = FileChannelStorage()
    StorageApp(storage).run(appAgent)
  }
}
