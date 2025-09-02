// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison

import com.intellij.diff.comparison.MergeResolveUtil.tryGreedyResolve
import com.intellij.diff.comparison.MergeResolveUtil.tryResolve
import com.intellij.diff.comparison.iterables.FairDiffIterable
import com.intellij.diff.util.MergeRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.diff.util.Side.Companion.fromLeft
import com.intellij.util.containers.PeekableIterator
import com.intellij.util.containers.PeekableIteratorWrapper
import com.intellij.util.diff.DiffConfig
import kotlin.jvm.JvmStatic
import kotlin.math.max
import kotlin.math.min

object ComparisonMergeUtil {
  @JvmStatic
  fun buildSimple(
    fragments1: FairDiffIterable,
    fragments2: FairDiffIterable,
    indicator: CancellationChecker
  ): MutableList<MergeRange> {
    check(fragments1.length1 == fragments2.length1)
    return FairMergeBuilder().execute(fragments1, fragments2)
  }

  internal fun buildMerge(
    fragments1: FairDiffIterable,
    fragments2: FairDiffIterable,
    trueEquality: SideEquality,
    indicator: CancellationChecker
  ): MutableList<MergeRange> {
    check(fragments1.length1 == fragments2.length1)
    return FairMergeBuilder(trueEquality).execute(fragments1, fragments2)
  }

  @JvmStatic
  fun tryResolveConflict(
    leftText: CharSequence,
    baseText: CharSequence,
    rightText: CharSequence
  ): CharSequence? {
    if (DiffConfig.USE_GREEDY_MERGE_MAGIC_RESOLVE) {
      return tryGreedyResolve(leftText, baseText, rightText)
    }
    else {
      return tryResolve(leftText, baseText, rightText)
    }
  }

  private class FairMergeBuilder {
    private val myChangesBuilder: ChangeBuilder

    constructor() {
      myChangesBuilder = ChangeBuilder()
    }

    constructor(trueEquality: SideEquality) {
      myChangesBuilder = IgnoringChangeBuilder(trueEquality)
    }

    fun execute(
      fragments1: FairDiffIterable,
      fragments2: FairDiffIterable
    ): MutableList<MergeRange> {
      val unchanged1: PeekableIterator<Range> = PeekableIteratorWrapper(fragments1.unchanged())
      val unchanged2: PeekableIterator<Range> = PeekableIteratorWrapper(fragments2.unchanged())

      while (unchanged1.hasNext() && unchanged2.hasNext()) {
        val side = add(unchanged1.peek(), unchanged2.peek())
        side.selectNotNull(unchanged1, unchanged2).next()
      }

      return myChangesBuilder.finish(fragments1.length2, fragments1.length1, fragments2.length2)
    }

    fun add(range1: Range, range2: Range): Side {
      val start1 = range1.start1
      val end1 = range1.end1

      val start2 = range2.start1
      val end2 = range2.end1

      if (end1 <= start2) return Side.LEFT
      if (end2 <= start1) return Side.RIGHT

      val startBase = max(start1, start2)
      val endBase = min(end1, end2)
      val count = endBase - startBase

      val startShift1 = startBase - start1
      val startShift2 = startBase - start2

      val startLeft = range1.start2 + startShift1
      val endLeft = startLeft + count
      val startRight = range2.start2 + startShift2
      val endRight = startRight + count

      myChangesBuilder.markEqual(startLeft, startBase, startRight, endLeft, endBase, endRight)

      return fromLeft(end1 <= end2)
    }
  }

  private open class ChangeBuilder {
    protected val myChanges: MutableList<MergeRange> = ArrayList()

    private var myIndex1 = 0
    private var myIndex2 = 0
    private var myIndex3 = 0

    protected fun addChange(start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int) {
      if (start1 == end1 && start2 == end2 && start3 == end3) return
      myChanges.add(MergeRange(start1, end1, start2, end2, start3, end3))
    }

    fun markEqual(start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int) {
      check(myIndex1 <= start1)
      check(myIndex2 <= start2)
      check(myIndex3 <= start3)
      check(start1 <= end1)
      check(start2 <= end2)
      check(start3 <= end3)

      processChange(myIndex1, myIndex2, myIndex3, start1, start2, start3)

      myIndex1 = end1
      myIndex2 = end2
      myIndex3 = end3
    }

    fun finish(length1: Int, length2: Int, length3: Int): MutableList<MergeRange> {
      check(myIndex1 <= length1)
      check(myIndex2 <= length2)
      check(myIndex3 <= length3)

      processChange(myIndex1, myIndex2, myIndex3, length1, length2, length3)

      return myChanges
    }

    protected open fun processChange(start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int) {
      addChange(start1, start2, start3, end1, end2, end3)
    }
  }

  private class IgnoringChangeBuilder(private val myTrueEquality: SideEquality) : ChangeBuilder() {
    override fun processChange(start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int) {
      val lastChange = if (myChanges.isEmpty()) null else myChanges[myChanges.size - 1]
      val unchangedStart1 = lastChange?.end1 ?: 0
      val unchangedStart2 = lastChange?.end2 ?: 0
      val unchangedStart3 = lastChange?.end3 ?: 0
      addIgnoredChanges(unchangedStart1, unchangedStart2, unchangedStart3, start1, start2, start3)

      addChange(start1, start2, start3, end1, end2, end3)
    }

    fun addIgnoredChanges(start1: Int, start2: Int, start3: Int, end1: Int, end2: Int, end3: Int) {
      val count = end2 - start2
      check(end1 - start1 == count)
      check(end3 - start3 == count)

      var firstIgnoredCount = -1
      for (i in 0..<count) {
        val isIgnored = !myTrueEquality.equals(start1 + i, start2 + i, start3 + i)
        val previousAreIgnored = firstIgnoredCount != -1

        if (isIgnored && !previousAreIgnored) {
          firstIgnoredCount = i
        }
        if (!isIgnored && previousAreIgnored) {
          addChange(start1 + firstIgnoredCount, start2 + firstIgnoredCount, start3 + firstIgnoredCount,
                    start1 + i, start2 + i, start3 + i)
          firstIgnoredCount = -1
        }
      }

      if (firstIgnoredCount != -1) {
        addChange(start1 + firstIgnoredCount, start2 + firstIgnoredCount, start3 + firstIgnoredCount,
                  start1 + count, start2 + count, start3 + count)
      }
    }
  }

  internal fun interface SideEquality {
    fun equals(leftIndex: Int, baseIndex: Int, rightIndex: Int): Boolean
  }
}