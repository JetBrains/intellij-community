// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.ranges;

import com.intellij.util.ArrayUtil;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

/**
 * Mutable-while-building list of canonical half-open child-id ranges used by DIRECTORY_CHILDREN range-list encoding.
 * Mutability is an implementation detail of builder/parser code; callers should treat returned instances as values.
 */
@ApiStatus.Internal
public final class RangesList {
  private int[] boundaries;
  private int rangesCount;

  public RangesList(int expectedRangeCount) {
    if (expectedRangeCount < 0) {
      throw new IllegalArgumentException("expectedRangeCount must be >=0 but got: " + expectedRangeCount);
    }
    boundaries = expectedRangeCount == 0 ? ArrayUtil.EMPTY_INT_ARRAY : ArrayUtil.newIntArray(2 * expectedRangeCount);
  }

  public int rangesCount() {
    return rangesCount;
  }

  public boolean isEmpty() {
    return rangesCount == 0;
  }

  /** @return inclusive lower bound of the {@code rangeIndex}-th half-open range */
  public int minChildIdInclusive(int rangeIndex) {
    checkRangeIndex(rangeIndex);
    return boundaries[2 * rangeIndex];
  }

  /** @return exclusive upper bound of the {@code rangeIndex}-th half-open range */
  public int maxChildIdExclusive(int rangeIndex) {
    checkRangeIndex(rangeIndex);
    return boundaries[2 * rangeIndex + 1];
  }

  /** Appends a structurally canonical range decoded from persistent storage. */
  public void addCanonicalRange(int minChildIdInclusive,
                                int maxChildIdExclusive) {
    checkNonEmptyRange(minChildIdInclusive, maxChildIdExclusive);
    if (rangesCount > 0 && minChildIdInclusive <= maxChildIdExclusive(rangesCount - 1)) {
      throw new IllegalArgumentException("Range must start after previous range: min=" + minChildIdInclusive +
                                         ", previousMaxExclusive=" + maxChildIdExclusive(rangesCount - 1));
    }
    appendRawRange(minChildIdInclusive, maxChildIdExclusive);
  }

  /**
   * Appends a builder-produced half-open range and merges adjacency to keep the range list canonical.
   * On-disk range-list format represents adjacent ranges as one broad range.
   */
  public void appendRangeMergingAdjacent(int minChildIdInclusive,
                                         int maxChildIdExclusive) {
    checkNonEmptyRange(minChildIdInclusive, maxChildIdExclusive);
    if (rangesCount == 0) {
      appendRawRange(minChildIdInclusive, maxChildIdExclusive);
      return;
    }

    int previousMaxExclusive = maxChildIdExclusive(rangesCount - 1);
    if (minChildIdInclusive < previousMaxExclusive) {
      throw new IllegalArgumentException("Range intersects previous range: min=" + minChildIdInclusive +
                                         ", previousMaxExclusive=" + previousMaxExclusive);
    }
    if (minChildIdInclusive == previousMaxExclusive) {
      boundaries[2 * rangesCount - 1] = maxChildIdExclusive;
      return;
    }
    appendRawRange(minChildIdInclusive, maxChildIdExclusive);
  }

  public boolean contains(int childId) {
    for (int i = 0; i < rangesCount; i++) {
      if (minChildIdInclusive(i) <= childId && childId < maxChildIdExclusive(i)) {
        return true;
      }
    }
    return false;
  }

  /** @return total width covered by all the ranges in the list */
  public long totalRangeWidth() {
    long totalWidth = 0;
    for (int i = 0; i < rangesCount; i++) {
      totalWidth += maxChildIdExclusive(i) - minChildIdInclusive(i);
    }
    return totalWidth;
  }

  /** @return bytes required to serialize only diff-compressed boundaries; the caller accounts for header size separately */
  public int serializedSize(int parentId) {
    //keep the public size accounting as int but use long accumulator here: if the serialized size
    // no longer fits into signed int -- fail instead of silently overflowing the accumulator
    long size = 0;
    int previousBoundary = parentId;
    for (int i = 0; i < rangesCount; i++) {
      int minChildId = minChildIdInclusive(i);
      int maxExclusiveChildId = maxChildIdExclusive(i);
      size += DataInputOutputUtil.sizeOfVarint(minChildId - previousBoundary);
      size += DataInputOutputUtil.sizeOfVarint(maxExclusiveChildId - minChildId);
      previousBoundary = maxExclusiveChildId;
    }
    return Math.toIntExact(size);
  }

  /** Writes only diff-compressed boundaries; the caller must write the negative range-count header separately. */
  public void serializeTo(int parentId, @NotNull DataOutput output) throws IOException {
    int previousBoundary = parentId;
    for (int i = 0; i < rangesCount; i++) {
      int minChildId = minChildIdInclusive(i);
      int maxExclusiveChildId = maxChildIdExclusive(i);
      DataInputOutputUtil.writeINT(output, minChildId - previousBoundary);
      DataInputOutputUtil.writeINT(output, maxExclusiveChildId - minChildId);
      previousBoundary = maxExclusiveChildId;
    }
  }

  /** Returns canonical half-open ranges for assertion and corruption diagnostics. */
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("RangesList{");
    for (int i = 0; i < rangesCount; i++) {
      if (i > 0) {
        builder.append(", ");
      }
      builder.append('[').append(minChildIdInclusive(i)).append(", ").append(maxChildIdExclusive(i)).append(')');
    }
    return builder.append('}').toString();
  }

  private void appendRawRange(int minChildIdInclusive,
                               int maxChildIdExclusive) {
    ensureCapacity(rangesCount + 1);
    boundaries[2 * rangesCount] = minChildIdInclusive;
    boundaries[2 * rangesCount + 1] = maxChildIdExclusive;
    rangesCount++;
  }

  private void ensureCapacity(int rangesToFit) {
    int boundariesToFit = 2 * rangesToFit;
    if (boundaries.length >= boundariesToFit) {
      return;
    }
    int newCapacity = Math.max(boundariesToFit, Math.max(2, boundaries.length * 2));
    boundaries = Arrays.copyOf(boundaries, newCapacity);
  }

  private void checkRangeIndex(int rangeIndex) {
    if (rangeIndex < 0 || rangeIndex >= rangesCount) {
      throw new IndexOutOfBoundsException("rangeIndex=" + rangeIndex + ", rangeCount=" + rangesCount);
    }
  }

  private static void checkNonEmptyRange(int minChildIdInclusive,
                                         int maxChildIdExclusive) {
    if (minChildIdInclusive >= maxChildIdExclusive) {
      throw new IllegalArgumentException("Range must be non-empty: [" + minChildIdInclusive + ", " + maxChildIdExclusive + ")");
    }
  }
}
