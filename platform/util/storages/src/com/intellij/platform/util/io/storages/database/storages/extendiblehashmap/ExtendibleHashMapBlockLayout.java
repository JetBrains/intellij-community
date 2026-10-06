// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.extendiblehashmap;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT32_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.fieldHandle;

/// Identifies an extendible hash map LOOKUP block.
/// ```
/// ExtendibleHashMapBlockLayout { segmentIndex: int32 }
/// ```
/// The value `-1` marks the block that contains the header and segments table. Non-negative values identify hash map segments.
@ApiStatus.Internal
public final class ExtendibleHashMapBlockLayout {
  public static final MemoryLayout BLOCK_HEADER_LAYOUT = MemoryLayout.structLayout(
    INT32_LAYOUT.withName("segmentIndex")
  ).withName("ExtendibleHashMapBlockHeaderLayout");

  public static final VarHandle SEGMENT_INDEX_HANDLE = fieldHandle(BLOCK_HEADER_LAYOUT, "segmentIndex");
  public static final int BLOCK_HEADER_SIZE = Math.toIntExact(BLOCK_HEADER_LAYOUT.byteSize());

  public static final int HEADER_AND_SEGMENTS_TABLE_BLOCK_MARKER = -1;

  /// Initializes regular data-segment block with `segmentIndex`
  public static void initializeSegmentIndex(@NotNull MemorySegment target, int segmentIndex) {
    if (segmentIndex < 0) {
      throw new IllegalArgumentException("segmentIndex(=" + segmentIndex + ") must not be negative");
    }
    SEGMENT_INDEX_HANDLE.set(target, 0L, segmentIndex);
  }

  /// Initializes header-and-segments-table block
  public static void initializeHeaderAndSegmentsTable(@NotNull MemorySegment target) {
    SEGMENT_INDEX_HANDLE.set(target, 0L, HEADER_AND_SEGMENTS_TABLE_BLOCK_MARKER);
  }

  public static int segmentIndex(@NotNull MemorySegment source) {
    return (int)SEGMENT_INDEX_HANDLE.get(source, 0L);
  }

  static int segmentsTableOffset(int headerSize) {
    // Keep the header and segments table aligned for primitive access.
    int int64AlignedHeaderSize = (headerSize + Long.BYTES - 1) & -Long.BYTES;
    return BLOCK_HEADER_SIZE + int64AlignedHeaderSize;
  }

  /// @return (headerSize, int64-aligned) + segmentsSize
  public static int headerAndSegmentsTableBlockPayloadSize(int headerSize, int segmentSize) {
    return segmentsTableOffset(headerSize) + segmentSize;
  }

  private ExtendibleHashMapBlockLayout() { }
}
