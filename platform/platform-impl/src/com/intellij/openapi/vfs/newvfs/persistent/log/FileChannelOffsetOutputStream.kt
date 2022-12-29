// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicLong

class FileChannelOffsetOutputStream(
  private val fileChannel: FileChannel,
  private val startOffset: Long,
) : OutputStream() {
  private var position = AtomicLong(startOffset)
  override fun write(b: Int) {
    val pos = position.getAndAdd(1)
    fileChannel.write(ByteBuffer.wrap(byteArrayOf(b.toByte())), )
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    fileChannel.write(ByteBuffer.wrap(b, off, len), position.getAndAdd(len.toLong()))
  }

  fun validateWrittenBytesCount(expectedBytesWritten: Long) {
    if (position.get() - startOffset != expectedBytesWritten) {
      throw IllegalStateException("unexpected amount of data has been written: written ${position.get() - startOffset} vs expected ${expectedBytesWritten}")
    }
  }
}