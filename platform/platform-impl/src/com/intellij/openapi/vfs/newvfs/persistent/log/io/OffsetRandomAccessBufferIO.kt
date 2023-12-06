// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

import java.nio.ByteBuffer

fun RandomAccessReadBuffer.offsetView(newZeroPosition: Long): RandomAccessReadBuffer =
  if (this is OffsetRandomAccessReadBuffer) OffsetRandomAccessReadBuffer(buffer, this.newZeroPosition + newZeroPosition)
  else OffsetRandomAccessReadBuffer(this, newZeroPosition)

fun RandomAccessWriteBuffer.offsetView(newZeroPosition: Long): RandomAccessWriteBuffer =
  if (this is OffsetRandomAccessWriteBuffer) OffsetRandomAccessWriteBuffer(buffer, this.newZeroPosition + newZeroPosition)
  else OffsetRandomAccessWriteBuffer(this, newZeroPosition)

fun RandomAccessBufferIO.offsetView(newZeroPosition: Long): RandomAccessBufferIO =
  if (this is OffsetRandomAccessBufferIO) OffsetRandomAccessBufferIO(buffer, this.newZeroPosition + newZeroPosition)
  else OffsetRandomAccessBufferIO(this, newZeroPosition)

private class OffsetRandomAccessReadBuffer(val buffer: RandomAccessReadBuffer, val newZeroPosition: Long) : RandomAccessReadBuffer {
  override fun read(position: Long, buf: ByteArray, offset: Int, length: Int) {
    buffer.read(position + newZeroPosition, buf, offset, length)
  }
}

private class OffsetRandomAccessWriteBuffer(val buffer: RandomAccessWriteBuffer, val newZeroPosition: Long) : RandomAccessWriteBuffer {
  override fun write(position: Long, buf: ByteBuffer, offset: Int, length: Int) {
    buffer.write(position + newZeroPosition, buf, offset, length)
  }
}

private class OffsetRandomAccessBufferIO(val buffer: RandomAccessBufferIO, val newZeroPosition: Long) : RandomAccessBufferIO {
  override fun write(position: Long, buf: ByteBuffer, offset: Int, length: Int) {
    buffer.write(position + newZeroPosition, buf, offset, length)
  }

  override fun read(position: Long, buf: ByteArray, offset: Int, length: Int) {
    buffer.read(position + newZeroPosition, buf, offset, length)
  }
}