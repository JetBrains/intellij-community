// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

@ApiStatus.Internal
public final class DirectBufferWrapper {
  @NotNull
  private static final ByteOrder ourNativeByteOrder = ByteOrder.nativeOrder();
  private static final int RELEASED_CODE = -1;

  private final @NotNull PagedFileStorage myFile;
  private final long myPosition;
  private final boolean myReadOnly;

  private volatile ByteBuffer myBuffer;
  private volatile boolean myDirty;
  private final AtomicInteger myReferences = new AtomicInteger();
  private volatile int myBufferDataEndPos;

  //private final Stack<Throwable> myReferenceTraces = new Stack<>();

  DirectBufferWrapper(@NotNull PagedFileStorage file, long offset, boolean readOnly) throws IOException {
    myFile = file;
    myPosition = offset;
    myReadOnly = readOnly;
    myBuffer = DirectByteBufferAllocator.allocate(() -> create());
    myFile.getStorageLockContext().assertUnderSegmentAllocationLock();
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

  private void fileSizeMayChanged(int bufferDataEndPos) {
    if (bufferDataEndPos > myBufferDataEndPos) {
      myBufferDataEndPos = bufferDataEndPos;
      myFile.ensureCachedSizeAtLeast(myPosition + myBufferDataEndPos);
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
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkReadAccess();

    return myBuffer.get(index);
  }

  public long getLong(int index) {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkReadAccess();

    return myBuffer.getLong(index);
  }

  public void putLong(int index, long value) throws IOException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    markDirty();
    myBuffer.putLong(index, value);
    fileSizeMayChanged(index + 8);
  }

  public int getInt(int index) {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkReadAccess();

    return myBuffer.getInt(index);
  }

  public void putInt(int index, int value) throws IOException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    markDirty();
    myBuffer.putInt(index, value);
    fileSizeMayChanged(index + 4);
  }

  public void position(int newPosition) {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    myBuffer.position(newPosition);
  }

  public int position() {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkReadAccess();

    return myBuffer.position();
  }

  public void put(ByteBuffer src) throws IOException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    markDirty();
    myBuffer.put(src);
    fileSizeMayChanged(myBuffer.position());
  }

  public void put(int index, byte b) throws IOException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    markDirty();
    myBuffer.put(index, b);
    fileSizeMayChanged(index + 1);
  }

  public void readToArray(byte[] dst, int o, int page_offset, int page_len) throws IllegalArgumentException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkReadAccess();

    synchronized (myReferences) {
      myBuffer.position(page_offset);
      myBuffer.get(dst, o, page_len);
    }
  }

  public void putFromArray(byte[] src, int o, int page_offset, int page_len) throws IOException, IllegalArgumentException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    markDirty();
    synchronized (myReferences) {
      myBuffer.position(page_offset);
      myBuffer.put(src, o, page_len);
    }
    fileSizeMayChanged(myBuffer.position());
  }


  private ByteBuffer create() throws IOException {
    ByteBuffer buffer = ByteBuffer.allocateDirect(myFile.myPageSize);
    assert buffer.capacity() > 0;
    return myFile.useChannel(ch -> {
      ch.read(buffer, myPosition);
      return buffer;
    }, myReadOnly);
  }

  boolean tryRelease(boolean force) throws IOException {
    boolean releaseState = myReferences.updateAndGet(operand -> operand == 0 ? RELEASED_CODE : operand) == RELEASED_CODE;
    if (releaseState || force) {
      myFile.getStorageLockContext().assertUnderSegmentAllocationLock();

      if (isDirty()) force();
      if (myBuffer != null) {
        ByteBufferUtil.cleanBuffer(myBuffer);
        myBuffer = null;
      }

      if (force && !releaseState) {
        PagedFileStorage.LOG.error("Page buffer is referenced but was forcibly released for file " + myFile.getFile());
      }

      return true;
    }
    return false;
  }

  boolean isReleased() {
    return myReferences.get() == RELEASED_CODE;
  }

  @SuppressWarnings("RedundantCast")
  void force() throws IOException {
    myFile.getStorageLockContext().assertUnderSegmentAllocationLock();

    assert !myReadOnly;
    if (isDirty()) {
      synchronized (myReferences) {
        ByteBuffer buffer = myBuffer;
        buffer.rewind();

        myFile.useChannel(ch -> {
          int oldLimit = buffer.limit();
          try {
            ch.write((ByteBuffer)buffer.limit(myBufferDataEndPos), myPosition);
          }
          finally {
            buffer.limit(oldLimit);
          }
          return null;
        }, myReadOnly);
      }

      myDirty = false;
    }
  }

  int getLength() {
    return myFile.myPageSize;
  }

  @Override
  public String toString() {
    return "Buffer for " + myFile + ", offset:" + myPosition + ", size: " + myFile.myPageSize;
  }

  public void useNativeByteOrder() {
    if (myBuffer.order() != ourNativeByteOrder) {
      myBuffer.order(ourNativeByteOrder);
    }
  }

  boolean belongs(@NotNull StorageLockContext context) {
    return myFile.getStorageLockContext() == context;
  }

  public boolean tryLock() {
    //myReferenceTraces.add(new Throwable());
    //assert !isReleased();
    return myReferences.updateAndGet(operand -> operand >= 0 ? operand + 1 : operand) >= 0;
  }

  public void unlock() {
    //myReferenceTraces.pop();
    int currentRefs = myReferences.decrementAndGet();
    assert currentRefs >= 0;
  }

  @NotNull PagedFileStorage getFile() {
    return myFile;
  }

  public static DirectBufferWrapper readWriteDirect(PagedFileStorage file, long offset) throws IOException {
    return new DirectBufferWrapper(file, offset,false);
  }

  public static DirectBufferWrapper readOnlyDirect(PagedFileStorage file, long offset) throws IOException {
    return new DirectBufferWrapper(file, offset, true);
  }
}