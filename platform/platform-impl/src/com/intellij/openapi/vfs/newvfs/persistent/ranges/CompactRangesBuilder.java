// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.ranges;

import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Builds compact candidate coverage ranges for DIRECTORY_CHILDREN range-list values from an already sorted children list.
 */
@ApiStatus.Internal
public interface CompactRangesBuilder {
  /**
   * Converts sorted unique child ids into canonical half-open ranges that cover every input child id. Returned ranges may
   * also cover ids absent from {@code children}; readers filter those candidates by deleted flag and parent id.
   * Empty input produces an empty range-list.
   */
  @NotNull RangesList build(@NotNull List<? extends ChildInfo> children);
}
