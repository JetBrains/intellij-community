// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap;

import com.intellij.platform.util.io.storages.intmultimaps.Durable;
import com.intellij.util.io.CleanableStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

/// Stores the header, segments table, and fixed-size segments of an extendible hash map
@ApiStatus.Internal
public interface ExtendibleHashMapStorage extends Durable, CleanableStorage {
  boolean isOpen();

  /// @return `true` when the storage has no header, segments table, or hash map segments
  boolean isEmpty() throws IOException;

  /// @return the size of the segments table and each hash map segment
  int segmentSize();

  /// @return the size of the header
  int headerSize();

  /// @return the fixed-size header, creating it if absent
  @NotNull MemorySegment header() throws IOException;

  /// @return the segments table, creating it if absent
  @NotNull MemorySegment segmentsTable() throws IOException;

  /// @return the specified existing hash map segment
  @NotNull MemorySegment segment(int segmentIndex) throws IOException;

  /// Allocates and returns the specified hash map segment
  @NotNull MemorySegment allocateSegment(int segmentIndex) throws IOException;

  /// Removes the header, segments table, and hash map segments, and keeps the storage open
  void clear() throws IOException;
}
