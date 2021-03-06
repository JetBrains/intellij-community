// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

@ApiStatus.Internal
public final class DirectBufferWrapper {
  private final @NotNull PagedFileStorage myFile;
  private final long myPosition;
  private final int myLength;
  private final boolean myReadOnly;

  private volatile ByteBuffer myBuffer;
  private volatile boolean myDirty;

  DirectBufferWrapper(@NotNull PagedFileStorage file, long offset, int length, boolean readOnly) {
    myFile = file;
    myPosition = offset;
    myLength = length;
    myReadOnly = readOnly;
  }

  void markDirty() throws IOException {
    if (myReadOnly) {
      throw new IOException("Read-only byte buffer can't be modified. File: " + myFile);
    }
    if (!myDirty) myDirty = true;
  }

  final boolean isDirty() {
    return myDirty;
  }

  public ByteBuffer getCachedBuffer() {
    return myBuffer;
  }

  ByteBuffer getBuffer() throws IOException {
    ByteBuffer buffer = myBuffer;
    if (buffer == null) {
      myBuffer = buffer = DirectByteBufferAllocator.allocate(() -> create());
    }
    return buffer;
  }

  private ByteBuffer create() throws IOException {
    return myFile.useChannel(ch -> {
      ByteBuffer buffer = ByteBuffer.allocateDirect(myLength);
      ch.read(buffer, myPosition);
      return buffer;
    }, myReadOnly);
  }

  void release() throws IOException {
    if (isDirty()) force();
    if (myBuffer != null) {
      ByteBufferUtil.cleanBuffer(myBuffer);
      myBuffer = null;
    }
  }

  void force() throws IOException {
    assert !myReadOnly;
    ByteBuffer buffer = getCachedBuffer();
    if (buffer != null && isDirty()) {
      myFile.useChannel(ch -> {
        buffer.rewind();
        ch.write(buffer, myPosition);
        myDirty = false;
        return null;
      }, myReadOnly);
    }
  }

  int getLength() {
    return myLength;
  }

  @Override
  public String toString() {
    return "Buffer for " + myFile + ", offset:" + myPosition + ", size: " + myLength;
  }

  public static DirectBufferWrapper readWriteDirect(PagedFileStorage file, long offset, int length) {
    return new DirectBufferWrapper(file, offset, length, false);
  }

  public static DirectBufferWrapper readOnlyDirect(PagedFileStorage file, long offset, int length) {
    return new DirectBufferWrapper(file, offset, length, true);
  }
}