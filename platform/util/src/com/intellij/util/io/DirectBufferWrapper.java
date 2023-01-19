// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * File-backed buffer: region of file [position, position + length) loaded into memory as {@linkplain java.nio.DirectByteBuffer}.
 * This class works in close collaboration with {@linkplain FilePageCache} and {@linkplain PagedFileStorage} to emulate mmapp-ed
 * file region.
 * <br>
 * <br>
 * This class direct provides access to underlying {@linkplain ByteBuffer}, as long as convenient methods getInt, getLong/putInt,
 * putLong, etc, which also responsible to marking buffer as 'dirty' (hence need to be stored), and extend file, if needed
 * (see {@linkplain #fileSizeMayChanged}).
 * <br>
 * <br>
 * Buffer lifecycle is managed with reference counting: buffer initializes with refCount=0, and refCount incremented/decremented by calls
 * {@linkplain #tryLock()}/{@linkplain #unlock()} as long, as it remains >=0. Method {@linkplain #tryRelease(boolean)} sets refCount to
 * -1 only if it is 0, and then stores buffer content (if it was changed), and deallocate buffer. (With flag force=true buffer could
 * be deallocated even if it is still used/referenced by some client). 'released' is a terminal state, once released, buffer state is
 * not changing anymore.
 */
@ApiStatus.Internal
public final class DirectBufferWrapper {
  /**
   * Terminal state of buffer lifecycle: 'released'. See {@linkplain #tryRelease(boolean)}
   */
  private static final int RELEASED_CODE = -1;

  private static final AtomicIntegerFieldUpdater<DirectBufferWrapper> REF_UPDATER =
    AtomicIntegerFieldUpdater.newUpdater(DirectBufferWrapper.class, "myReferences");

  private final @NotNull PagedFileStorage myFile;
  private volatile ByteBuffer myBuffer;
  /**
   * This buffer start position in file -- i.e. 0-th byte in buffer is actually .myPosition byte in file
   */
  private final long myPosition;

  /**
   * Offset of the first _not_ changed byte within buffer -- i.e. it is the same as buffer.limit(), but kept separated from buffer.
   */
  private volatile int myBufferDataEndPos;

  private volatile boolean myDirty;
  /**
   * How many clients have locked (i.e. have in use) this buffer right now, or {@linkplain #RELEASED_CODE}, if buffer was already
   * released (i.e. taken out of use -- terminal state). See {@linkplain #tryLock()}/{@linkplain #unlock()}/{@linkplain #tryRelease(boolean)}.
   */
  private volatile int myReferences = 0;


  //private final Stack<Throwable> myReferenceTraces = new Stack<>();

  DirectBufferWrapper(@NotNull PagedFileStorage file, long positionInFile) throws IOException {
    file.getStorageLockContext().assertUnderSegmentAllocationLock();
    myFile = file;
    myPosition = positionInFile;
    myBuffer = allocateAndLoadFileContent();
  }

  public ByteBuffer getBuffer() {
    return myBuffer;
  }

  public void markDirty() throws IOException {
    if (!myDirty) {
      if (myFile.isReadOnly()) {
        throw new IOException("Read-only byte buffer can't be modified. File: " + myFile);
      }
      myDirty = true;
      myFile.markDirty();
    }
  }

  public void fileSizeMayChanged(int bufferDataEndPos) {
    if (bufferDataEndPos > myBufferDataEndPos) {
      myBufferDataEndPos = bufferDataEndPos;
      //RC: i'd say it is not 'cachedSizeAtLeast' but 'ensureCachedPositionAtLeast',
      //    because (myPosition + myBufferDataEndPos) => position of last occupied
      //    byte in buffer, not 'size', which is position of last occupied byte +1
      myFile.ensureCachedSizeAtLeast(myPosition + myBufferDataEndPos);
    }
  }

  public boolean isDirty() {
    return myDirty;
  }

  public ByteBuffer copy() {
    //TODO RC: do we really need call to Allocator here? .duplicate() only creates a wrapper,
    //         main buffer content (native memory chunk for direct buffer) is not allocated anew,
    //         but shared with myBuffer -- hence neither caching, nor 'IDEA-222358 linux native memory
    //         leak' are not applicable. Seems like plain call to .duplicate().order(...) should be
    //         enough:
    return DirectByteBufferAllocator.allocate(() -> {
      ByteBuffer duplicate = myBuffer.duplicate();
      duplicate.order(myBuffer.order());
      return duplicate;
    });
  }

  public byte get(int index, boolean checkAccess) {
    if (checkAccess) {
      StorageLockContext context = myFile.getStorageLockContext();
      context.checkReadAccess();
    }

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

  public void readToArray(byte[] dst, int o, int page_offset, int page_len, boolean checkAccess) throws IllegalArgumentException {
    if (checkAccess) {
      StorageLockContext context = myFile.getStorageLockContext();
      context.checkReadAccess();
    }

    ByteBufferUtil.copyMemory(myBuffer, page_offset, dst, o, page_len);
  }

  public void putFromArray(byte[] src, int o, int page_offset, int page_len) throws IOException, IllegalArgumentException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    markDirty();
    ByteBuffer buf = myBuffer.duplicate();
    buf.position(page_offset);
    buf.put(src, o, page_len);
    fileSizeMayChanged(buf.position());
  }

  public void putFromBuffer(@NotNull ByteBuffer data, int page_offset) throws IOException, IllegalArgumentException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    markDirty();
    ByteBuffer buf = myBuffer.duplicate();

    buf.position(page_offset);
    buf.put(data);
    fileSizeMayChanged(buf.position());
  }

  public boolean tryLock() {
    //myReferenceTraces.add(new Throwable());
    //assert !isReleased();
    return REF_UPDATER.updateAndGet(this, refCount -> refCount >= 0 ? refCount + 1 : refCount) >= 0;
  }

  public void unlock() {
    //myReferenceTraces.pop();
    int currentRefs = REF_UPDATER.decrementAndGet(this);
    assert currentRefs >= 0;
  }

  public boolean isReleased() {
    return myReferences == RELEASED_CODE;
  }

  public boolean isLocked() {
    return myReferences > 0;
  }

  public int getLength() {
    return myFile.getPageSize();
  }

  

  private ByteBuffer allocateAndLoadFileContent() throws IOException {
    final int bufferSize = myFile.getPageSize();
    final ByteBuffer buffer = DirectByteBufferAllocator.ALLOCATOR.allocate(bufferSize);
    buffer.order(myFile.isNativeBytesOrder() ? ByteOrder.nativeOrder() : ByteOrder.BIG_ENDIAN);
    assert buffer.limit() > 0;
    return myFile.useChannel(ch -> {
      int readBytes = ch.read(buffer, myPosition);
      if (readBytes < bufferSize) {
        for (int i = Math.max(0, readBytes); i < bufferSize; i++) {
          buffer.put(i, (byte)0);
        }
      }
      return buffer;
    }, myFile.isReadOnly());
  }

  boolean tryRelease(boolean force) throws IOException {
    boolean releaseState = REF_UPDATER.updateAndGet(this, operand -> operand == 0 ? RELEASED_CODE : operand) == RELEASED_CODE;
    if (releaseState || force) {
      myFile.getStorageLockContext().assertUnderSegmentAllocationLock();

      if (isDirty()) force();
      if (myBuffer != null) {
        DirectByteBufferAllocator.ALLOCATOR.release(myBuffer);
        myBuffer = null;
      }

      if (force && !releaseState) {
        PagedFileStorage.LOG.error("Page buffer is referenced but was forcibly released for file " + myFile.getFile());
      }

      return true;
    }
    return false;
  }

  void force() throws IOException {
    myFile.getStorageLockContext().assertUnderSegmentAllocationLock();

    assert !myFile.isReadOnly();
    if (isDirty()) {
      ByteBuffer buffer = myBuffer.duplicate();
      buffer.rewind();
      buffer.limit(myBufferDataEndPos);

      myFile.useChannel(ch -> {
        ch.write(buffer, myPosition);
        return null;
      }, myFile.isReadOnly());

      myDirty = false;
    }
  }

  @NotNull PagedFileStorage getFile() {
    return myFile;
  }

  @Override
  public String toString() {
    return "Buffer for " + myFile + ", offset:" + myPosition + ", size: " + myFile.getPageSize();
  }
}