// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * File-backed buffer: region of file [position, position + length) loaded into memory as {@linkplain java.nio.DirectByteBuffer}.
 * This class works in close collaboration with {@linkplain FilePageCache} and {@linkplain PagedFileStorage} to emulate mmapp-ed
 * file region.
 * <br>
 * <br>
 * This class direct provides access to underlying {@linkplain ByteBuffer}, as long as convenient methods getInt, getLong/putInt,
 * putLong, etc, which also responsible to marking buffer as 'dirty' (hence need to be stored), and extend file, if needed.
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

  private static final long EMPTY_MODIFIED_REGION = 0;

  private static final AtomicIntegerFieldUpdater<DirectBufferWrapper> REF_UPDATER =
    AtomicIntegerFieldUpdater.newUpdater(DirectBufferWrapper.class, "myReferences");

  private final @NotNull PagedFileStorage myFile;
  private volatile ByteBuffer myBuffer;
  /**
   * This buffer start position in file -- i.e. 0-th byte in buffer is actually .myPosition byte in file
   */
  private final long myPosition;

  /**
   * Modified region [modifiedFrom, modifiedTo) of {@linkplain #myBuffer}, packed into a single long for atomic reads.
   * <p>
   * {@code myModifiedRegionPacked = (modifiedTo << 32) | modifiedFrom}.
   */
  private volatile long myModifiedRegionPacked = EMPTY_MODIFIED_REGION;
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

  private void checkWritable() throws IOException {
    if (myFile.isReadOnly()) {
      throw new IOException("Read-only byte buffer can't be modified. File: " + myFile);
    }
  }

  private void regionModified(int modifiedFrom, int modifiedTo) {
    if (modifiedFrom == modifiedTo) {
      return;
    }

    long modifiedRegionOld = myModifiedRegionPacked;
    int modifiedFromOld = unpackModifiedFrom(modifiedRegionOld);
    int modifiedToOld = unpackModifiedTo(modifiedRegionOld);

    int modifiedFromNew = modifiedRegionOld == EMPTY_MODIFIED_REGION ? modifiedFrom : Math.min(modifiedFromOld, modifiedFrom);
    int modifiedToNew = Math.max(modifiedToOld, modifiedTo);

    if (modifiedFromOld == modifiedFromNew && modifiedToOld == modifiedToNew) {
      return;
    }

    long modifiedRegionNew = packModifiedRegion(modifiedFromNew, modifiedToNew);
    myModifiedRegionPacked = modifiedRegionNew;

    //RC: i'd say it is not 'cachedSizeAtLeast' but 'ensureCachedPositionAtLeast',
    //    because (myPosition + modifiedTo) => position of last occupied byte in buffer,
    //    not 'size', which is position of last occupied byte +1
    if (modifiedTo > modifiedToOld) {
      myFile.ensureCachedSizeAtLeast(myPosition + modifiedTo);
    }
    if (modifiedRegionOld == EMPTY_MODIFIED_REGION && modifiedRegionNew != EMPTY_MODIFIED_REGION) {
      myFile.markDirty();
    }
  }

  private static long packModifiedRegion(int modifiedFrom, int modifiedTo) {
    return ((long)modifiedFrom) | (((long)modifiedTo) << Integer.SIZE);
  }

  private static int unpackModifiedFrom(long modifiedRegionPacked) {
    return (int)modifiedRegionPacked;
  }

  private static int unpackModifiedTo(long modifiedRegionPacked) {
    return (int)(modifiedRegionPacked >> Integer.SIZE);
  }

  public boolean isDirty() {
    return myModifiedRegionPacked != EMPTY_MODIFIED_REGION;
  }

  public ByteBuffer copy() {
    ByteBuffer duplicate = myBuffer.duplicate();
    duplicate.order(myBuffer.order());
    return duplicate;
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

    checkWritable();
    myBuffer.putLong(index, value);
    regionModified(index, index + Long.BYTES);
  }

  public int getInt(int index) {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkReadAccess();

    return myBuffer.getInt(index);
  }

  public void putInt(int index, int value) throws IOException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    checkWritable();
    myBuffer.putInt(index, value);
    regionModified(index, index + Integer.BYTES);
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

    checkWritable();
    int modifiedFrom = myBuffer.position();
    myBuffer.put(src);
    regionModified(modifiedFrom, myBuffer.position());
  }

  public void put(int index, byte b) throws IOException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    checkWritable();
    myBuffer.put(index, b);
    regionModified(index, index + 1);
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

    checkWritable();
    ByteBuffer buf = myBuffer.duplicate();
    buf.position(page_offset);
    buf.put(src, o, page_len);
    regionModified(page_offset, buf.position());
  }

  public void putFromBuffer(@NotNull ByteBuffer data, int page_offset) throws IOException, IllegalArgumentException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    checkWritable();
    ByteBuffer buf = myBuffer.duplicate();

    buf.position(page_offset);
    buf.put(data);
    regionModified(page_offset, buf.position());
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
    return myFile.executeIdempotentOp(ch -> {
      final int readBytes = ch.read(buffer, myPosition);
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

      if (isDirty()) {
        force();
      }
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
    StorageLockContext storageLockContext = myFile.getStorageLockContext();
    storageLockContext.assertUnderSegmentAllocationLock();

    if (myFile.isReadOnly()) {
      throw new IllegalStateException("Can't flush .readOnly page: " + this);
    }
    if (isDirty()) {
      long modifiedRegion = myModifiedRegionPacked;
      int modifiedFrom = unpackModifiedFrom(modifiedRegion);
      int modifiedTo = unpackModifiedTo(modifiedRegion);
      ByteBuffer buffer = myBuffer.duplicate();
      buffer.position(modifiedFrom);
      buffer.limit(modifiedTo);

      long startedAtNs = System.nanoTime();
      myFile.executeIdempotentOp(ch -> {
        ch.write(buffer, myPosition + modifiedFrom);
        return null;
      }, /*readOnly: */ false);

      myModifiedRegionPacked = EMPTY_MODIFIED_REGION;

      long durationNs = System.nanoTime() - startedAtNs;
      int bytesStored = modifiedTo - modifiedFrom;
      storageLockContext.getBufferCache().reportStoreStats(bytesStored, NANOSECONDS.toMicros(durationNs) );
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