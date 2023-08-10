// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

import java.nio.ByteBuffer

interface RandomAccessWriteBuffer {

  /**
   * writes data from [buf] range [[offset]..[offset]+[length]) to this buffer starting from [position]
   */
  fun write(position: Long, buf: ByteBuffer, offset: Int, length: Int)

  /**
   * writes data from [buf] range [[offset]..[offset]+[length]) to this buffer starting from [position]
   */
  fun write(position: Long, buf: ByteArray, offset: Int, length: Int): Unit = write(position, ByteBuffer.wrap(buf), offset, length)

  /**
   * writes data from [buf] to this buffer starting from [position]
   */
  fun write(position: Long, buf: ByteArray): Unit = write(position, ByteBuffer.wrap(buf), 0, buf.size)
}