// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison

import com.intellij.diff.comparison.ByCharRt.compare
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.comparison.iterables.FairDiffIterable
import com.intellij.util.IntPair
import com.intellij.util.fastutil.ints.IntList

/*
 * Base class for two-step diff algorithms.
 * Given matching between some sub-sequences of base sequences - build matching on whole base sequences
 */
internal abstract class ChangeCorrector(
  private val myLength1: Int,
  private val myLength2: Int,
  private val myChanges: FairDiffIterable,
  protected val myIndicator: CancellationChecker
) {
  protected val myBuilder: DiffIterableUtil.ChangeBuilder = DiffIterableUtil.ChangeBuilder(myLength1, myLength2)

  fun build(): FairDiffIterable {
    execute()
    return DiffIterableUtil.fair(myBuilder.finish())
  }

  protected fun execute() {
    var last1 = 0
    var last2 = 0

    for (ch in myChanges.iterateUnchanged()) {
      val count = ch.end1 - ch.start1
      for (i in 0..<count) {
        val range1 = getOriginalRange1(ch.start1 + i)
        val range2 = getOriginalRange2(ch.start2 + i)

        val start1 = range1.first
        val start2 = range2.first
        val end1 = range1.second
        val end2 = range2.second

        matchGap(last1, start1, last2, start2)
        myBuilder.markEqual(start1, start2, end1, end2)

        last1 = end1
        last2 = end2
      }
    }
    matchGap(last1, myLength1, last2, myLength2)
  }

  // match elements in range [start1 - end1) -> [start2 - end2)
  protected abstract fun matchGap(start1: Int, end1: Int, start2: Int, end2: Int)

  protected abstract fun getOriginalRange1(index: Int): IntPair

  protected abstract fun getOriginalRange2(index: Int): IntPair

  //
  // Implementations
  //
  class DefaultCharChangeCorrector(
    private val myCodePoints1: ByCharRt.CodePointsOffsets,
    private val myCodePoints2: ByCharRt.CodePointsOffsets,
    private val myText1: CharSequence,
    private val myText2: CharSequence,
    changes: FairDiffIterable,
    indicator: CancellationChecker
  ) : ChangeCorrector(myText1.length, myText2.length, changes, indicator) {
    override fun matchGap(start1: Int, end1: Int, start2: Int, end2: Int) {
      val inner1 = myText1.subSequence(start1, end1)
      val inner2 = myText2.subSequence(start2, end2)
      val innerChanges = compare(inner1, inner2, myIndicator)

      for (chunk in innerChanges.iterateUnchanged()) {
        myBuilder.markEqual(start1 + chunk.start1, start2 + chunk.start2, chunk.end1 - chunk.start1)
      }
    }

    override fun getOriginalRange1(index: Int): IntPair {
      val startOffset = myCodePoints1.charOffset(index)
      val endOffset = myCodePoints1.charOffsetAfter(index)
      return IntPair(startOffset, endOffset)
    }

    override fun getOriginalRange2(index: Int): IntPair {
      val startOffset = myCodePoints2.charOffset(index)
      val endOffset = myCodePoints2.charOffsetAfter(index)
      return IntPair(startOffset, endOffset)
    }
  }

  class SmartLineChangeCorrector(
    private val myIndexes1: IntList,
    private val myIndexes2: IntList,
    private val myLines1: List<ByLineRt.Line>,
    private val myLines2: List<ByLineRt.Line>,
    changes: FairDiffIterable,
    indicator: CancellationChecker
  ) : ChangeCorrector(myLines1.size, myLines2.size, changes, indicator) {
    override fun matchGap(start1: Int, end1: Int, start2: Int, end2: Int) {
      val expand = expand(myLines1, myLines2, start1, start2, end1, end2)

      val inner1 = myLines1.subList(expand.start1, expand.end1)
      val inner2 = myLines2.subList(expand.start2, expand.end2)
      val innerChanges = DiffIterableUtil.diff(inner1, inner2, myIndicator)

      myBuilder.markEqual(start1, start2, expand.start1, expand.start2)

      for (chunk in innerChanges.iterateUnchanged()) {
        myBuilder.markEqual(expand.start1 + chunk.start1, expand.start2 + chunk.start2, chunk.end1 - chunk.start1)
      }

      myBuilder.markEqual(expand.end1, expand.end2, end1, end2)
    }

    override fun getOriginalRange1(index: Int): IntPair {
      val offset = myIndexes1[index]
      return IntPair(offset, offset + 1)
    }

    override fun getOriginalRange2(index: Int): IntPair {
      val offset = myIndexes2[index]
      return IntPair(offset, offset + 1)
    }
  }
}
