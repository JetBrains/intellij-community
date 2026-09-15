// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap;

import com.intellij.platform.util.io.storages.intmultimaps.Durable;
import com.intellij.util.io.CleanableStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

/// Stores the fixed-size logical segments of an extendible hash map
@ApiStatus.Internal
public interface ExtendibleHashMapStorage extends Durable, CleanableStorage {
  boolean isOpen();

  /// @return `true` when the storage has no logical segments
  boolean isEmpty() throws IOException;

  /// @return the size of each logical segment
  int segmentSize();

  /// @return the specified existing logical segment
  @NotNull MemorySegment segment(int segmentIndex) throws IOException;

  /// Allocates and returns the specified logical segment
  @NotNull MemorySegment allocateSegment(int segmentIndex) throws IOException;

  /// Removes all logical segments and keeps the storage open
  void clear() throws IOException;
}
