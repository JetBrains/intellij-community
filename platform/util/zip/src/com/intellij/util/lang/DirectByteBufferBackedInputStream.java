// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.ByteBuffer;

final class DirectByteBufferBackedInputStream extends InputStream {
  private ByteBuffer buffer;
  private final boolean isPooled;

  DirectByteBufferBackedInputStream(ByteBuffer buffer, boolean isPooled) {
    this.buffer = buffer;
    this.isPooled = isPooled;
  }

  @Override
  public int read() {
    return buffer.hasRemaining() ? buffer.get() & 0xff : -1;
  }

  @Override
  public int read(byte[] bytes, int offset, int length) {
    if (!buffer.hasRemaining()) {
      return -1;
    }

    int actualLength = Math.min(length, buffer.remaining());
    buffer.get(bytes, offset, actualLength);
    return actualLength;
  }

  @Override
  public byte[] readNBytes(int length) {
    byte[] result = new byte[Math.min(length, buffer.remaining())];
    buffer.get(result);
    return result;
  }

  @Override
  public int readNBytes(byte[] bytes, int offset, int length) {
    int actualLength = Math.min(length, buffer.remaining());
    buffer.get(bytes, offset, actualLength);
    return actualLength;
  }

  @Override
  public int available() {
    return buffer.remaining();
  }

  @Override
  public byte @NotNull [] readAllBytes() {
    byte[] result = new byte[buffer.remaining()];
    buffer.get(result);
    return result;
  }

  @Override
  public long skip(long length) {
    int actualLength = Math.min((int)length, buffer.remaining());
    buffer.position(buffer.position() + actualLength);
    return actualLength;
  }

  @Override
  public void close() {
    ByteBuffer buffer = this.buffer;
    if (buffer == null) {
      // already closed
      return;
    }

    this.buffer = null;
    if (isPooled) {
      DirectByteBufferPool.DEFAULT_POOL.release(buffer);
    }
  }
}
