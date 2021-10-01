// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@ApiStatus.Internal
public final class DirectBufferWrapper {
  @NotNull
  private static final ByteOrder ourNativeByteOrder = ByteOrder.nativeOrder();

  private final @NotNull PagedFileStorage myFile;
  private final long myPosition;
  private final int myLength;
  private final boolean myReadOnly;

  private volatile ByteBuffer myBuffer;
  private volatile boolean myDirty;
  private volatile boolean myReleased = false;

  DirectBufferWrapper(@NotNull PagedFileStorage file, long offset, int length, boolean readOnly) throws IOException {
    myFile = file;
    myPosition = offset;
    myLength = length;
    myReadOnly = readOnly;
    myBuffer = DirectByteBufferAllocator.allocate(() -> create());
  }

  private void markDirty() throws IOException {
    if (myReadOnly) {
      throw new IOException("Read-only byte buffer can't be modified. File: " + myFile);
    }
    if (!myDirty) {
      myDirty = true;
      myFile.markDirty();
    }
  }

  boolean isDirty() {
    return myDirty;
  }

  public ByteBuffer copy() {
    try {
      return DirectByteBufferAllocator.allocate(() -> {
        ByteBuffer duplicate = myBuffer.duplicate();
        duplicate.order(myBuffer.order());
        return duplicate;
      });
    }
    catch (IOException e) {
      // not expected there
      throw new RuntimeException(e);
    }
  }

  public byte get(int index) {
    return myBuffer.get(index);
  }

  public long getLong(int index) {
    return myBuffer.getLong(index);
  }

  public ByteBuffer putLong(int index, long value) throws IOException {
    markDirty();
    return myBuffer.putLong(index, value);
  }

  public int getInt(int index) {
    return myBuffer.getInt(index);
  }

  public ByteBuffer putInt(int index, int value) throws IOException {
    markDirty();
    return myBuffer.putInt(index, value);
  }

  public void position(int newPosition) {
    myBuffer.position(newPosition);
  }

  public int position() {
    return myBuffer.position();
  }

  public void put(ByteBuffer src) throws IOException {
    markDirty();
    myBuffer.put(src);
  }

  public void put(int index, byte b) throws IOException {
    markDirty();
    myBuffer.put(index, b);
  }

  public void readToArray(byte[] dst, int o, int page_offset, int page_len) throws IllegalArgumentException {
    // TODO do a proper synchronization
    //noinspection SynchronizeOnNonFinalField
    synchronized (myBuffer) {
      myBuffer.position(page_offset);
      myBuffer.get(dst, o, page_len);
    }
  }

  public void putFromArray(byte[] src, int o, int page_offset, int page_len) throws IOException, IllegalArgumentException {
    markDirty();
    // TODO do a proper synchronization
    //noinspection SynchronizeOnNonFinalField
    synchronized (myBuffer) {
      myBuffer.position(page_offset);
      myBuffer.put(src, o, page_len);
    }
  }


  private ByteBuffer create() throws IOException {
    ByteBuffer buffer = ByteBuffer.allocateDirect(myLength);
    return myFile.useChannel(ch -> {
      ch.read(buffer, myPosition);
      return buffer;
    }, myReadOnly);
  }

  void release() throws IOException {
    if (isDirty()) force();
    if (myBuffer != null) {
      ByteBufferUtil.cleanBuffer(myBuffer);
      myBuffer = null;
      myReleased = true;
    }
  }

  boolean isReleased() {
    return myReleased;
  }

  void force() throws IOException {
    assert !myReadOnly;
    if (!isReleased() && isDirty()) {
      ByteBuffer buffer = myBuffer;
      buffer.rewind();

      myFile.useChannel(ch -> {
        ch.write(buffer, myPosition);
        return null;
      }, myReadOnly);

      myDirty = false;
    }
  }

  int getLength() {
    return myLength;
  }

  @Override
  public String toString() {
    return "Buffer for " + myFile + ", offset:" + myPosition + ", size: " + myLength;
  }

  public void useNativeByteOrder() {
    if (myBuffer.order() != ourNativeByteOrder) {
      myBuffer.order(ourNativeByteOrder);
    }
  }

  public static DirectBufferWrapper readWriteDirect(PagedFileStorage file, long offset, int length) throws IOException {
    return new DirectBufferWrapper(file, offset, length, false);
  }

  public static DirectBufferWrapper readOnlyDirect(PagedFileStorage file, long offset, int length) throws IOException {
    return new DirectBufferWrapper(file, offset, length, true);
  }
}