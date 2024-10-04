// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.comparison.iterables.FairDiffIterable
import com.intellij.diff.util.Range
import com.intellij.util.containers.PeekableIteratorWrapper
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max

/**
 * Input: given 3 revisions: A -> B -> C and 2 sets of differences between them: earlyChanges 'A -> B' and laterChanges 'B -> C'.
 * We want to translate earlyChanges into 'A -> C' offsets.
 * 'laterChanges' are ignored, unless they conflict with earlyChanges.
 * In case of conflict, we want to 'merge' them into a single big range.
 *
 * @see com.intellij.openapi.vcs.ex.BulkRangeChangeHandler
 */
@ApiStatus.Internal
class ChangedRangesShifter {
  private val result = mutableListOf<Range>()

  private var dirtyStart = -1
  private var dirtyEnd = -1
  private var dirtyHasEarly = false

  private var earlyShift: Int = 0
  private var laterShift: Int = 0
  private var dirtyEarlyShift: Int = 0
  private var dirtyLaterShift: Int = 0

  fun execute(earlyChanges: FairDiffIterable,
              laterChanges: FairDiffIterable): List<Range> {
    val it1 = PeekableIteratorWrapper(earlyChanges.changes())
    val it2 = PeekableIteratorWrapper(laterChanges.changes())

    while (it1.hasNext() || it2.hasNext()) {
      if (!it2.hasNext()) {
        handleEarly(it1.next())
        continue
      }
      if (!it1.hasNext()) {
        handleLater(it2.next())
        continue
      }

      val range1 = it1.peek()
      val range2 = it2.peek()

      if (range1.start2 <= range2.start1) {
        handleEarly(it1.next())
      }
      else {
        handleLater(it2.next())
      }
    }
    flush(Int.MAX_VALUE)

    return result
  }

  private fun handleEarly(range: Range) {
    flush(range.start2)

    dirtyEarlyShift -= DiffIterableUtil.getRangeDelta(range)

    markDirtyRange(range.start2, range.end2)

    dirtyHasEarly = true
  }

  private fun handleLater(range: Range) {
    flush(range.start1)

    dirtyLaterShift += DiffIterableUtil.getRangeDelta(range)

    markDirtyRange(range.start1, range.end1)
  }

  private fun markDirtyRange(start: Int, end: Int) {
    if (dirtyEnd == -1) {
      dirtyStart = start
      dirtyEnd = end
    }
    else {
      dirtyEnd = max(dirtyEnd, end)
    }
  }

  private fun flush(nextLine: Int) {
    if (dirtyEnd != -1 && dirtyEnd < nextLine) {
      if (dirtyHasEarly) {
        result.add(Range(dirtyStart + earlyShift, dirtyEnd + earlyShift + dirtyEarlyShift,
                         dirtyStart + laterShift, dirtyEnd + laterShift + dirtyLaterShift)
        )
      }

      dirtyStart = -1
      dirtyEnd = -1
      dirtyHasEarly = false

      earlyShift += dirtyEarlyShift
      laterShift += dirtyLaterShift
      dirtyEarlyShift = 0
      dirtyLaterShift = 0
    }
  }
}
