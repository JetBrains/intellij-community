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
  /** Terminal state of buffer lifecycle: 'released'. See {@linkplain #tryRelease(boolean)} */
  private static final int RELEASED_CODE = -1;

  /** Value of myModifiedRegionPacked that means "empty" */
  private static final long EMPTY_MODIFIED_REGION = 0;

  private static final AtomicIntegerFieldUpdater<DirectBufferWrapper> REF_UPDATER =
    AtomicIntegerFieldUpdater.newUpdater(DirectBufferWrapper.class, "refCount");

  private final @NotNull PagedFileStorage owningStorage;

  private volatile ByteBuffer buffer;
  /** This buffer start position in file -- i.e. 0-th byte in buffer is actually .offsetInFile byte in file */
  private final long offsetInFile;

  /**
   * Modified region [modifiedFrom, modifiedTo) of {@linkplain #buffer}, packed into a single long for atomic reads.
   * <p>
   * {@code myModifiedRegionPacked = (modifiedTo << 32) | modifiedFrom}.
   */
  private volatile long modifiedRegionPacked = EMPTY_MODIFIED_REGION;
  /**
   * How many clients have locked (i.e. have in use) this buffer right now, or {@linkplain #RELEASED_CODE}, if buffer was already
   * released (i.e. taken out of use -- terminal state). See {@linkplain #tryLock()}/{@linkplain #unlock()}/{@linkplain #tryRelease(boolean)}.
   */
  private volatile int refCount = 0;


  //private final Stack<Throwable> myReferenceTraces = new Stack<>();

  DirectBufferWrapper(@NotNull PagedFileStorage file, long positionInFile) throws IOException {
    file.getStorageLockContext().assertUnderSegmentAllocationLock();
    owningStorage = file;
    offsetInFile = positionInFile;
    buffer = allocateAndLoadFileContent();
  }

  public ByteBuffer getBuffer() {
    return buffer;
  }

  private void checkWritable() throws IOException {
    if (owningStorage.isReadOnly()) {
      throw new IOException("Read-only byte buffer can't be modified. File: " + owningStorage);
    }
  }

  private void regionModified(int modifiedFrom, int modifiedTo) {
    if (modifiedFrom == modifiedTo) {
      return;
    }

    long modifiedRegionOld = modifiedRegionPacked;
    int modifiedFromOld = unpackModifiedFrom(modifiedRegionOld);
    int modifiedToOld = unpackModifiedTo(modifiedRegionOld);

    int modifiedFromNew = modifiedRegionOld == EMPTY_MODIFIED_REGION ? modifiedFrom : Math.min(modifiedFromOld, modifiedFrom);
    int modifiedToNew = Math.max(modifiedToOld, modifiedTo);

    if (modifiedFromOld == modifiedFromNew && modifiedToOld == modifiedToNew) {
      return;
    }

    long modifiedRegionNew = packModifiedRegion(modifiedFromNew, modifiedToNew);
    modifiedRegionPacked = modifiedRegionNew;

    if (modifiedTo > modifiedToOld) {
      //RC: I'd say it is not 'cachedSizeAtLeast' but 'ensureCachedPositionAtLeast',
      //    because (offsetInFile + modifiedTo) => position of last occupied byte in buffer,
      //    not 'size', which is position of last occupied byte +1
      owningStorage.ensureCachedSizeAtLeast(offsetInFile + modifiedTo);
    }
    if (modifiedRegionOld == EMPTY_MODIFIED_REGION && modifiedRegionNew != EMPTY_MODIFIED_REGION) {
      owningStorage.markDirty();
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
    return modifiedRegionPacked != EMPTY_MODIFIED_REGION;
  }

  public ByteBuffer copy() {
    ByteBuffer duplicate = buffer.duplicate();
    duplicate.order(buffer.order());
    return duplicate;
  }

  public byte get(int index, boolean checkAccess) {
    if (checkAccess) {
      StorageLockContext context = owningStorage.getStorageLockContext();
      context.checkReadAccess();
    }

    return buffer.get(index);
  }

  public long getLong(int index) {
    StorageLockContext context = owningStorage.getStorageLockContext();
    context.checkReadAccess();

    return buffer.getLong(index);
  }

  public void putLong(int index, long value) throws IOException {
    StorageLockContext context = owningStorage.getStorageLockContext();
    context.checkWriteAccess();

    checkWritable();
    buffer.putLong(index, value);
    regionModified(index, index + Long.BYTES);
  }

  public int getInt(int index) {
    StorageLockContext context = owningStorage.getStorageLockContext();
    context.checkReadAccess();

    return buffer.getInt(index);
  }

  public void putInt(int index, int value) throws IOException {
    StorageLockContext context = owningStorage.getStorageLockContext();
    context.checkWriteAccess();

    checkWritable();
    buffer.putInt(index, value);
    regionModified(index, index + Integer.BYTES);
  }

  public void position(int newPosition) {
    StorageLockContext context = owningStorage.getStorageLockContext();
    context.checkWriteAccess();

    buffer.position(newPosition);
  }

  public int position() {
    StorageLockContext context = owningStorage.getStorageLockContext();
    context.checkReadAccess();

    return buffer.position();
  }

  public void put(ByteBuffer src) throws IOException {
    StorageLockContext context = owningStorage.getStorageLockContext();
    context.checkWriteAccess();

    checkWritable();
    int modifiedFrom = buffer.position();
    buffer.put(src);
    regionModified(modifiedFrom, buffer.position());
  }

  public void put(int index, byte b) throws IOException {
    StorageLockContext context = owningStorage.getStorageLockContext();
    context.checkWriteAccess();

    checkWritable();
    buffer.put(index, b);
    regionModified(index, index + 1);
  }

  public void readToArray(byte[] dst, int o, int page_offset, int page_len, boolean checkAccess) throws IllegalArgumentException {
    if (checkAccess) {
      StorageLockContext context = owningStorage.getStorageLockContext();
      context.checkReadAccess();
    }

    ByteBufferUtil.copyMemory(buffer, page_offset, dst, o, page_len);
  }

  public void putFromArray(byte[] src, int o, int page_offset, int page_len) throws IOException, IllegalArgumentException {
    StorageLockContext context = owningStorage.getStorageLockContext();
    context.checkWriteAccess();

    checkWritable();
    ByteBuffer buf = buffer.duplicate();
    buf.position(page_offset);
    buf.put(src, o, page_len);
    regionModified(page_offset, buf.position());
  }

  public void putFromBuffer(@NotNull ByteBuffer data, int page_offset) throws IOException, IllegalArgumentException {
    StorageLockContext context = owningStorage.getStorageLockContext();
    context.checkWriteAccess();

    checkWritable();
    ByteBuffer buf = buffer.duplicate();

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
    return refCount == RELEASED_CODE;
  }

  public boolean isLocked() {
    return refCount > 0;
  }

  public int getLength() {
    return owningStorage.getPageSize();
  }


  private ByteBuffer allocateAndLoadFileContent() throws IOException {
    int bufferSize = owningStorage.getPageSize();
    ByteBuffer buffer = DirectByteBufferAllocator.ALLOCATOR.allocate(bufferSize);
    buffer.order(owningStorage.isNativeBytesOrder() ? ByteOrder.nativeOrder() : ByteOrder.BIG_ENDIAN);
    assert buffer.limit() > 0;
    return owningStorage.executeIdempotentOp(ch -> {
      int readBytes = ch.read(buffer, offsetInFile);
      if (readBytes < bufferSize) {
        for (int i = Math.max(0, readBytes); i < bufferSize; i++) {
          buffer.put(i, (byte)0);
        }
      }
      return buffer;
    });
  }

  boolean tryRelease(boolean force) throws IOException {
    boolean releaseState = REF_UPDATER.updateAndGet(this, operand -> operand == 0 ? RELEASED_CODE : operand) == RELEASED_CODE;
    if (releaseState || force) {
      owningStorage.getStorageLockContext().assertUnderSegmentAllocationLock();

      if (isDirty()) {
        force();
      }
      if (buffer != null) {
        DirectByteBufferAllocator.ALLOCATOR.release(buffer);
        buffer = null;
      }

      if (force && !releaseState) {
        PagedFileStorage.LOG.error("Page buffer is referenced but was forcibly released for file " + owningStorage.getFile());
      }

      return true;
    }
    return false;
  }

  void force() throws IOException {
    StorageLockContext storageLockContext = owningStorage.getStorageLockContext();
    storageLockContext.assertUnderSegmentAllocationLock();

    if (owningStorage.isReadOnly()) {
      throw new IllegalStateException("Can't flush .readOnly page: " + this);
    }
    if (isDirty()) {
      long modifiedRegion = modifiedRegionPacked;
      int modifiedFrom = unpackModifiedFrom(modifiedRegion);
      int modifiedTo = unpackModifiedTo(modifiedRegion);
      ByteBuffer buffer = this.buffer.duplicate();
      buffer.position(modifiedFrom);
      buffer.limit(modifiedTo);

      long startedAtNs = System.nanoTime();
      owningStorage.executeIdempotentOp(ch -> {
        ch.write(buffer, offsetInFile + modifiedFrom);
        return null;
      });

      modifiedRegionPacked = EMPTY_MODIFIED_REGION;

      long durationNs = System.nanoTime() - startedAtNs;
      int bytesStored = modifiedTo - modifiedFrom;
      storageLockContext.getBufferCache().reportStoreStats(bytesStored, NANOSECONDS.toMicros(durationNs) );
    }
  }

  @NotNull PagedFileStorage getFile() {
    return owningStorage;
  }

  @Override
  public String toString() {
    return "Buffer for " + owningStorage + ", offset:" + offsetInFile + ", size: " + owningStorage.getPageSize();
  }
}