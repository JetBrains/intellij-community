// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.extendiblehashmap;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT32_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.fieldHandle;

/// Locates one logical extendible hash map segment inside a database block
/// ```
/// ExtendibleHashMapSegmentLayout {
///   segmentIndex: int32
/// }
/// ```
@ApiStatus.Internal
public final class ExtendibleHashMapSegmentBlockLayout {
  public static final MemoryLayout HEADER_LAYOUT = MemoryLayout.structLayout(
    INT32_LAYOUT.withName("segmentIndex")
  ).withName("ExtendibleHashMapSegmentBlockHeaderLayout");

  public static final VarHandle SEGMENT_INDEX_HANDLE = fieldHandle(HEADER_LAYOUT, "segmentIndex");
  public static final int HEADER_SIZE = Math.toIntExact(HEADER_LAYOUT.byteSize());

  public static void initializeSegmentIndex(@NotNull MemorySegment target, int segmentIndex) {
    if (segmentIndex < 0) {
      throw new IllegalArgumentException("segmentIndex(=" + segmentIndex + ") must not be negative");
    }
    SEGMENT_INDEX_HANDLE.set(target, 0L, segmentIndex);
  }

  public static int segmentIndex(@NotNull MemorySegment source) {
    return (int)SEGMENT_INDEX_HANDLE.get(source, 0L);
  }

  private ExtendibleHashMapSegmentBlockLayout() { }
}
