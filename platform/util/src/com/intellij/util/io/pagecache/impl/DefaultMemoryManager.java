// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

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
public class DefaultMemoryManager implements IMemoryManager {
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
    while (true) {
      long used = heapBytesUsed.get();
      if (used + bufferSize > heapCapacityBytes) {
        return null;
      }
      if (heapBytesUsed.compareAndSet(used, used + bufferSize)) {
        ByteBuffer heapByteBuffer = ByteBuffer.allocate(bufferSize);
        statistics.pageAllocatedHeap(bufferSize);
        return heapByteBuffer;
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
  public void releaseBuffer(@NotNull ByteBuffer buffer) {
    int bufferSize = buffer.capacity();
    if (buffer.isDirect()) {
      DirectByteBufferAllocator.ALLOCATOR.release(buffer);

      nativeBytesUsed.addAndGet(-bufferSize);

      statistics.pageReclaimedNative(bufferSize);
    }
    else {
      heapBytesUsed.addAndGet(-bufferSize);

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
}
