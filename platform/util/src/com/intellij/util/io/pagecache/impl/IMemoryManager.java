// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * Memory manager for {@link com.intellij.util.io.FilePageCacheLockFree}.
 * Provides methods for allocating/releasing of {@link ByteBuffer}, manages re-using buffers (if needed),
 * ensures constraints on total native/heap memory allowed to be used by cache.
 * <p>
 * Implementations <b>MUST</b> be thread-safe (preferable concurrent).
 */
public interface IMemoryManager {
  /**
   * Method tries to allocate buffer of requested size, and returns it, if successful.
   * Method returns null if buffer can't be allocated by any reason -- most likely because
   * manager's capacity is already exceeded.
   * @param allowAllocateAboveCapacity if true, allocate buffer above capacity, if possible.
   * @return allocated buffer (native of heap), or null, if new buffer can't be allocated (e.g. because
   * of capacity overflow)
   */
  @Nullable ByteBuffer tryAllocate(int bufferSize,
                                   boolean allowAllocateAboveCapacity);

  void releaseBuffer(int bufferSize,
                     @NotNull ByteBuffer buffer);

  //MAYBE: do we need to expose separate heap/native accounting, or it should be an implementation detail?

  long nativeCapacityBytes();

  long heapCapacityBytes();

  default long totalMemoryUsed() { return nativeBytesUsed() + heapBytesUsed(); }

  long nativeBytesUsed();

  long heapBytesUsed();

  boolean hasOverflow();

  boolean hasFreeNativeCapacity(int bufferSize);
}
