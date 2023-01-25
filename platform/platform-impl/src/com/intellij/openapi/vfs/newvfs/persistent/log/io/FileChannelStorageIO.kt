// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicLong

fun FileChannel.asStorageIO() = FileChannelStorageIO(this)

class FileChannelStorageIO(private val fc: FileChannel) : StorageIO {
  override fun write(position: Long, buf: ByteBuffer, offset: Int, length: Int) {
    fc.write(buf.slice(offset, length), position)
  }

  override fun read(position: Long, buf: ByteArray, offset: Int, length: Int) {
    fc.read(ByteBuffer.wrap(buf, offset, length), position)
  }

  override fun isDirty(): Nothing = throw UnsupportedOperationException("unexpected call")

  override fun force() {
    fc.force(false)
  }

  override fun close() {
    fc.close()
  }

  override fun offsetOutputStream(startPosition: Long) = OffsetOutputStream(fc, startPosition)

  class OffsetOutputStream(
    private val fileChannel: FileChannel,
    private val startOffset: Long,
  ) : OutputStreamWithValidation() {
    private var position = AtomicLong(startOffset)
    override fun write(b: Int) {
      fileChannel.write(ByteBuffer.wrap(byteArrayOf(b.toByte())), position.getAndAdd(1))
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      fileChannel.write(ByteBuffer.wrap(b, off, len), position.getAndAdd(len.toLong()))
    }

    override fun validateWrittenBytesCount(expectedBytesWritten: Long) {
      if (position.get() - startOffset != expectedBytesWritten) {
        throw IllegalStateException("unexpected amount of data has been written: written ${position.get() - startOffset} vs expected ${expectedBytesWritten}")
      }
    }
  }
}
