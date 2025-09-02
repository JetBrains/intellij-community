// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables

import com.intellij.diff.util.Range

/**
 * Represents computed differences between two sequences.
 *
 *
 * All [Range] are not empty (have at least one element in one of the sides). Ranges do not overlap.
 *
 *
 * Differences are guaranteed to be 'squashed': there are no two changed or two unchanged [Range] with
 * `(range1.end1 == range2.start1 && range1.end2 == range2.start2)`.
 *
 * @see FairDiffIterable
 *
 * @see DiffIterableUtil.iterateAll
 * @see DiffIterableUtil.verify
 */
interface DiffIterable {
  /**
   * @return length of the first sequence
   */
  val length1: Int

  /**
   * @return length of the second sequence
   */
  val length2: Int

  fun changes(): Iterator<Range>

  fun unchanged(): Iterator<Range>

  fun iterateChanges(): Iterable<Range> {
    return Iterable { this.changes() }
  }

  fun iterateUnchanged(): Iterable<Range> {
    return Iterable { this.unchanged() }
  }
}
