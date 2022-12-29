// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong

class MMapIOOffsetOutputStream(
  private val mmapIO: MappedFileIOUtil,
  private val startOffset: Long,
) : OutputStream() {
  private var position = AtomicLong(startOffset)

  override fun write(b: Int) {
    mmapIO.write(position.getAndAdd(1), byteArrayOf(b.toByte()))
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    mmapIO.write(position.getAndAdd(len.toLong()), b, off, len)
  }

  fun validateWrittenBytesCount(expectedBytesWritten: Long) {
    if (position.get() - startOffset != expectedBytesWritten) {
      throw IllegalStateException("unexpected amount of data has been written: written ${position.get() - startOffset} vs expected ${expectedBytesWritten}")
    }
  }
}