// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

class ByteCountingOutputStream : OutputStreamWithValidation() {
  var written: Long = 0L
    private set
  override fun write(b: Int) {
    written++
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    written += len
  }

  override fun validateWrittenBytesCount(expectedBytesWritten: Long) {
    if (written != expectedBytesWritten) {
      throw IllegalStateException("unexpected amount of data has been written: written ${written} vs expected ${expectedBytesWritten}")
    }
  }
}