// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.DirectByteBufferAllocator;
import com.intellij.util.io.pagecache.FilePageCacheStatistics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default {@link IMemoryManager} implementation:
 * Utilises {@link DirectByteBufferAllocator} for direct ByteBuffer caching & re-use
 * Heap buffers are not cached: just allocated and released to GC.
 */
public final class DefaultMemoryManager implements IMemoryManager {
  private static final Logger LOG = Logger.getInstance(DefaultMemoryManager.class);

  private final long nativeCapacityBytes;
  private final long heapCapacityBytes;

  /** Total bytes currently cached as DirectByteBuffers */
  private final AtomicLong nativeBytesUsed = new AtomicLong(0);
  /** Total bytes currently cached as HeapByteBuffers */
  private final AtomicLong heapBytesUsed = new AtomicLong(0);

  private final DirectByteBufferAllocator directBufferAllocator = DirectByteBufferAllocator.ALLOCATOR;

  private final @NotNull FilePageCacheStatistics statistics;

  public DefaultMemoryManager(long nativeCapacityBytes,
                              long heapCapacityBytes,
                              @NotNull FilePageCacheStatistics statistics) {
    if (nativeCapacityBytes <= 0) {
      throw new IllegalArgumentException("nativeCapacityBytes(=" + nativeCapacityBytes + ") must be >0");
    }
    if (heapCapacityBytes <= 0) {
      throw new IllegalArgumentException("heapCapacityBytes(=" + heapCapacityBytes + ") must be >0");
    }
    this.nativeCapacityBytes = nativeCapacityBytes;
    this.heapCapacityBytes = heapCapacityBytes;
    this.statistics = statistics;
  }

  private ByteBuffer tryReserveInNative(int bufferSize) {
    while (true) {
      long used = nativeBytesUsed.get();
      if (used + bufferSize > nativeCapacityBytes) {
        return null;
      }
      if (nativeBytesUsed.compareAndSet(used, used + bufferSize)) {
        ByteBuffer directByteBuffer = directBufferAllocator.allocate(bufferSize);
        statistics.pageAllocatedNative(bufferSize);
        return directByteBuffer;
      }
    }
  }

  private ByteBuffer tryReserveInHeap(int bufferSize) {
    if (heapCapacityBytes == 0) {
      return null;
    }
    while (true) {
      long used = heapBytesUsed.get();
      if (used + bufferSize > heapCapacityBytes) {
        return null;
      }
      if (heapBytesUsed.compareAndSet(used, used + bufferSize)) {
        try {
          ByteBuffer heapByteBuffer = ByteBuffer.allocate(bufferSize);
          statistics.pageAllocatedHeap(bufferSize);
          return heapByteBuffer;
        }
        catch (OutOfMemoryError e) {
          LOG.warnWithDebug("OutOfMemory: can't allocate heap buffer[size: " + bufferSize + "b] -> skip, will try to deal without it", e);
          heapBytesUsed.addAndGet(-bufferSize);
          return null;
        }
      }
    }
  }

  @Override
  public @Nullable ByteBuffer tryAllocate(int bufferSize,
                                          boolean allowAllocateAboveCapacity) {
    //if we have >= bufferSize of free capacity -> just allocate new direct buffer:
    ByteBuffer nativeBuffer = tryReserveInNative(bufferSize);
    if (nativeBuffer != null) {
      return nativeBuffer;
    }

    if (allowAllocateAboveCapacity) {
      ByteBuffer heapBuffer = tryReserveInHeap(bufferSize);
      if (heapBuffer != null) {
        return heapBuffer;
      }
    }
    return null;
  }

  @Override
  public void releaseBuffer(int bufferSize,
                            @NotNull ByteBuffer buffer) {
    //RC: why don't we use buffer.capacity() instead of bufferSize? Because DirectByteBufferAllocator could
    //    return buffers of capacity > size requested. In tryAllocate() we account only for requested size,
    //    not actually returned, so must do the same here, otherwise numbers don't check out.
    //
    //    This makes sense (kind of): we ask for 1M buffer, and we account for 1M we asked -- we don't care
    //    if we really receive 2M we didn't ask for. Allocator's responsibility is to manage the actual capacity
    //    of buffers it allocates -- MemoryManager's responsibility is to manage the capacity it has requested.

    if (buffer.isDirect()) {
      directBufferAllocator.release(buffer);

      long memoryUsed = nativeBytesUsed.addAndGet(-bufferSize);
      if (memoryUsed < 0) {
        throw new IllegalStateException("nativeBytesUsed(=" + memoryUsed + ") must be >=0");
      }

      statistics.pageReclaimedNative(bufferSize);
    }
    else {
      long memoryUsed = heapBytesUsed.addAndGet(-bufferSize);
      if (memoryUsed < 0) {
        throw new IllegalStateException("heapBytesUsed(=" + memoryUsed + ") must be >=0");
      }

      statistics.pageReclaimedHeap(bufferSize);
    }
  }

  @Override
  public boolean hasOverflow() {
    //heap buffers considered an overflow:
    return totalMemoryUsed() > nativeCapacityBytes;
  }

  @Override
  public long nativeBytesUsed() {
    return nativeBytesUsed.get();
  }

  @Override
  public long heapBytesUsed() {
    return heapBytesUsed.get();
  }

  @Override
  public long nativeCapacityBytes() {
    return nativeCapacityBytes;
  }

  @Override
  public long heapCapacityBytes() {
    return heapCapacityBytes;
  }

  @Override
  public boolean hasFreeNativeCapacity(int bufferSize) {
    return nativeBytesUsed.get() + bufferSize <= nativeCapacityBytes;
  }

  @Override
  public String toString() {
    return "DefaultMemoryManager{" +
           "nativeCapacity: " + nativeCapacityBytes +
           ", heapCapacity: " + heapCapacityBytes +
           ", nativeUsed: " + nativeBytesUsed +
           ", heapUsed: " + heapBytesUsed +
           '}';
  }
}
