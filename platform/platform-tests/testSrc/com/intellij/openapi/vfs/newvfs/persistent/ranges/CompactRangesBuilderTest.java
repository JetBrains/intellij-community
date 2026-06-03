// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.ranges;

import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompactRangesBuilderTest {
  @Test
  public void emptyInput_returnsEmptyRanges() {
    RangesList ranges = new FixedRangeCountRangesBuilder(4).build(Collections.emptyList());

    assertTrue(ranges.isEmpty(), "Empty children input should not create synthetic coverage ranges");
    assertEquals(0, ranges.totalRangeWidth(), "Empty range-list should have zero scan width");
  }

  @Test
  public void singletonInput_isCoveredByHalfOpenSingletonRange() {
    RangesList ranges = new FixedRangeCountRangesBuilder(4).build(children(42));

    assertEquals(1, ranges.rangesCount(), "A single child should be represented by one half-open range");
    assertEquals(42, ranges.minChildIdInclusive(0), "The singleton range should start at the child id");
    assertEquals(43, ranges.maxChildIdExclusive(0), "The singleton range should end right after the child id");
    assertTrue(ranges.contains(42), "The singleton child id must be covered by the returned range");
  }

  @Test
  public void adjacentChunkRanges_areMergedIntoCanonicalRange() {
    RangesList ranges = new FixedRangeCountRangesBuilder(10).build(children(10, 11, 12));

    assertEquals(1, ranges.rangesCount(), "Adjacent singleton chunks must be merged to keep range-list canonical");
    assertEquals(10, ranges.minChildIdInclusive(0), "Merged range should keep the first child as min boundary");
    assertEquals(13, ranges.maxChildIdExclusive(0), "Merged range should end after the last adjacent child");
  }

  @Test
  public void returnedRanges_areCanonicalAndCoverAllInputChildren() {
    SplittableRandom random = new SplittableRandom(0x5EED_244588L);
    for (int attempt = 0; attempt < 1_000; attempt++) {
      int childrenCount = random.nextInt(1, 512);
      List<ChildInfo> children = randomSortedUniqueChildren(random, childrenCount);
      int maxRangesCount = random.nextInt(1, 64);

      RangesList ranges = new FixedRangeCountRangesBuilder(maxRangesCount).build(children);

      assertCanonical(ranges);
      assertTrue(
        ranges.rangesCount() <= maxRangesCount,
        "Builder should keep the serialized ranges count under the configured bound: ranges=" + ranges
      );
      for (ChildInfo child : children) {
        assertTrue(
          ranges.contains(child.getId()),
          "Every input child must be covered: childId=" + child.getId() + ", ranges=" + ranges
        );
      }
      assertFalse(ranges.isEmpty(), "Non-empty input must produce at least one coverage range");
    }
  }

  @Test
  public void maxRangesCount_limitsReturnedRangesCount() {
    int maxRangesCount = 3;

    RangesList ranges = new FixedRangeCountRangesBuilder(maxRangesCount).build(children(10, 20, 30, 40, 50, 60, 70, 80, 90, 100));

    assertEquals(maxRangesCount, ranges.rangesCount(), "Sparse input should be compacted to the configured number of ranges");
    assertCanonical(ranges);
  }

  @Test
  public void integerMaxValueChildId_isRejectedBecauseHalfOpenRangeCannotBeEncoded() {
    CompactRangesBuilder builder = new FixedRangeCountRangesBuilder(4);

    assertThrows(
      IllegalArgumentException.class,
      () -> builder.build(children(Integer.MAX_VALUE)),
      "Range-list format stores maxExclusive boundary, so Integer.MAX_VALUE child id cannot be represented"
    );
  }

  @Test
  public void unsortedOrDuplicateInput_isRejectedBeforeChunking() {
    CompactRangesBuilder builder = new FixedRangeCountRangesBuilder(4);

    assertThrows(
      IllegalArgumentException.class,
      () -> builder.build(children(10, 9, 11)),
      "Unsorted children would break diff-compressed canonical range construction"
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> builder.build(children(10, 10, 11)),
      "Duplicate child ids would make coverage ambiguous and should be rejected"
    );
  }

  private static void assertCanonical(RangesList ranges) {
    int previousMaxExclusive = 0;
    for (int i = 0; i < ranges.rangesCount(); i++) {
      int min = ranges.minChildIdInclusive(i);
      int maxExclusive = ranges.maxChildIdExclusive(i);
      assertTrue(min < maxExclusive, "Range must be non-empty at index " + i + ": [" + min + ", " + maxExclusive + ")");
      if (i > 0) {
        assertTrue(
          min > previousMaxExclusive,
          "Ranges must be strictly separated, not adjacent/intersecting: previousMaxExclusive=" + previousMaxExclusive + ", min=" + min
        );
      }
      previousMaxExclusive = maxExclusive;
    }
  }

  private static List<ChildInfo> randomSortedUniqueChildren(SplittableRandom random,
                                                            int childrenCount) {
    List<ChildInfo> children = new ArrayList<>(childrenCount);
    int childId = random.nextInt(1, 20);
    for (int i = 0; i < childrenCount; i++) {
      childId += random.nextInt(1, 20);
      children.add(child(childId));
    }
    return children;
  }

  private static List<ChildInfo> children(int... childIds) {
    List<ChildInfo> children = new ArrayList<>(childIds.length);
    for (int childId : childIds) {
      children.add(child(childId));
    }
    return children;
  }

  private static ChildInfo child(int childId) {
    return new ChildInfoImpl(childId, childId, null, null, null);
  }
}
