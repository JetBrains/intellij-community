// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.ranges;

import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Splits sorted children into a fixed number of broad chunks, trading a bounded scan width for compact persistent storage.
 */
@ApiStatus.Internal
public final class FixedRangeCountRangesBuilder implements CompactRangesBuilder {
  private final int maxRangesCount;

  /**
   * @param maxRangesCount upper bound for returned ranges; adjacent chunk ranges may be merged, so the actual count can be smaller
   */
  public FixedRangeCountRangesBuilder(int maxRangesCount) {
    if (maxRangesCount <= 0) {
      throw new IllegalArgumentException("maxRangesCount must be >0 but got: " + maxRangesCount);
    }
    this.maxRangesCount = maxRangesCount;
  }

  @Override
  public @NotNull RangesList build(@NotNull List<? extends ChildInfo> children) {
    validateSortedByUniqueId(children);
    if (children.isEmpty()) {
      return new RangesList(0);
    }

    int targetRangesCount = Math.min(children.size(), maxRangesCount);
    int chunkSize = (children.size() + targetRangesCount - 1) / targetRangesCount;
    RangesList ranges = new RangesList(targetRangesCount);
    for (int chunkStart = 0; chunkStart < children.size(); chunkStart += chunkSize) {
      int chunkEnd = Math.min(chunkStart + chunkSize, children.size());
      int firstChildId = children.get(chunkStart).getId();
      int lastChildId = children.get(chunkEnd - 1).getId();
      if (lastChildId == Integer.MAX_VALUE) {
        throw new IllegalArgumentException("Range-list can't encode child id Integer.MAX_VALUE: " + lastChildId);
      }
      // Each chunk is encoded as one broad half-open range. It may cover non-children;
      // reader filters those by record state.
      ranges.appendRangeMergingAdjacent(firstChildId, lastChildId + 1);
    }
    return ranges;
  }

  private static void validateSortedByUniqueId(@NotNull List<? extends ChildInfo> children) {
    int previousChildId = Integer.MIN_VALUE;
    for (int i = 0; i < children.size(); i++) {
      int childId = children.get(i).getId();
      if (i > 0 && childId <= previousChildId) {
        throw new IllegalArgumentException("children must be sorted by unique id, but children[" + (i - 1) + "].id=" +
                                           previousChildId + ", children[" + i + "].id=" + childId);
      }
      previousChildId = childId;
    }
  }
}
