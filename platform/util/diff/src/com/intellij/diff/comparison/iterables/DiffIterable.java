// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables;

import com.intellij.diff.util.Range;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * Represents computed differences between two sequences.
 * <p/>
 * All {@link Range} are not empty (have at least one element in one of the sides). Ranges do not overlap.
 * <p/>
 * Differences are guaranteed to be 'squashed': there are no two changed or two unchanged {@link Range} with
 * <code>(range1.end1 == range2.start1 && range1.end2 == range2.start2)</code>.
 *
 * @see FairDiffIterable
 * @see DiffIterableUtil#iterateAll(DiffIterable)
 * @see DiffIterableUtil#verify(DiffIterable)
 */
public interface DiffIterable {
  /**
   * @return length of the first sequence
   */
  int getLength1();

  /**
   * @return length of the second sequence
   */
  int getLength2();

  @NotNull
  Iterator<Range> changes();

  @NotNull
  Iterator<Range> unchanged();

  @NotNull
  default Iterable<Range> iterateChanges() {
    return this::changes;
  }

  @NotNull
  default Iterable<Range> iterateUnchanged() {
    return this::unchanged;
  }
}
