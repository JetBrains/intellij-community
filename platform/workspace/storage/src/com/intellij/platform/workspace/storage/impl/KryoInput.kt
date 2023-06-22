// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DeprecatedCallableAddReplaceWith")

package com.intellij.platform.workspace.storage.impl

import com.esotericsoftware.kryo.kryo5.io.ByteBufferInput
import com.intellij.util.ArrayUtilRt
import com.intellij.util.io.ByteBufferUtil
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

internal class KryoInput(file: Path) : ByteBufferInput() {
  init {
    byteBuffer = FileChannel.open(file, EnumSet.of(StandardOpenOption.READ)).use { fileChannel ->
      fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size())
    }
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
    variableLengthEncoding = false
    capacity = byteBuffer.capacity()
    limit = byteBuffer.limit()
  }

  override fun readLongs(length: Int): LongArray {
    if (length == 0) {
      return ArrayUtilRt.EMPTY_LONG_ARRAY
    }

    val array = LongArray(length)
    byteBuffer.asLongBuffer().get(array)
    byteBuffer.position(byteBuffer.position() + (Long.SIZE_BYTES * length))
    position = byteBuffer.position()
    return array
  }

  override fun readInts(length: Int): IntArray {
    if (length == 0) {
      return ArrayUtilRt.EMPTY_INT_ARRAY
    }

    val array = IntArray(length)
    byteBuffer.asIntBuffer().get(array)
    byteBuffer.position(byteBuffer.position() + (Int.SIZE_BYTES * length))
    position = byteBuffer.position()
    return array
  }

  override fun readInt(): Int {
    val result = byteBuffer.getInt()
    position += Int.SIZE_BYTES
    return result
  }

  override fun readLong(): Long {
    val result = byteBuffer.getLong()
    position += Long.SIZE_BYTES
    return result
  }

  override fun close() {
    try {
      super.close()
    }
    finally {
      ByteBufferUtil.cleanBuffer(byteBuffer)
    }
  }
}
