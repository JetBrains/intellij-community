// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

interface RandomAccessReadBuffer {
  /**
   * reads [length] bytes from this buffer starting from [position] into [buf][[offset]..[offset]+[length])
   */
  fun read(position: Long, buf: ByteArray, offset: Int, length: Int)

  /**
   * reads `buf.size` bytes from this buffer starting from [position] into [buf]
   */
  fun read(position: Long, buf: ByteArray): Unit = read(position, buf, 0, buf.size)
}