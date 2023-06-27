// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DeprecatedCallableAddReplaceWith", "ReplaceGetOrSet")

package com.intellij.platform.workspace.storage.impl

import com.esotericsoftware.kryo.kryo5.KryoException
import com.esotericsoftware.kryo.kryo5.io.ByteBufferOutput
import java.io.IOException
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

internal class KryoOutput(file: Path) : ByteBufferOutput(512 * 1024, Int.MAX_VALUE) {
  private val fileChannel: FileChannel = FileChannel.open(file, EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE))

  init {
    variableLengthEncoding = false
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
  }

  override fun writeInt(value: Int) {
    require(Int.SIZE_BYTES)
    byteBuffer.putInt(value)
    position += Int.SIZE_BYTES
  }

  override fun writeLong(value: Long) {
    require(Long.SIZE_BYTES)
    byteBuffer.putLong(value)
    position += Long.SIZE_BYTES
  }

  override fun writeInts(array: IntArray, offset: Int, count: Int) {
    if (count == 0) {
      return
    }

    val required = count shl 2
    require(required)
    byteBuffer.asIntBuffer().put(array, offset, count)
    byteBuffer.position(byteBuffer.position() + required)
    position = byteBuffer.position()
  }

  override fun writeLongs(array: LongArray?, offset: Int, count: Int, optimizePositive: Boolean) {
    if (count == 0) {
      return
    }

    val required = count shl 3
    require(required)
    byteBuffer.asLongBuffer().put(array, offset, count)
    byteBuffer.position(byteBuffer.position() + required)
    position = byteBuffer.position()
  }

  override fun flush() {
    var filePosition = total
    try {
      byteBuffer.flip()
      while (byteBuffer.hasRemaining()) {
        filePosition += fileChannel.write(byteBuffer, filePosition)
      }
      byteBuffer.clear()
    }
    catch (e: IOException) {
      throw KryoException(e)
    }
    total = filePosition
    position = 0
  }

  override fun close() {
    fileChannel.use {
      super.close()
    }
  }
}
