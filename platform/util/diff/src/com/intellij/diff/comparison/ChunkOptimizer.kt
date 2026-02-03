// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison

import com.intellij.diff.comparison.iterables.DiffIterableUtil.createUnchanged
import com.intellij.diff.comparison.iterables.DiffIterableUtil.fair
import com.intellij.diff.comparison.iterables.FairDiffIterable
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.diff.util.Side.Companion.fromLeft

internal abstract class ChunkOptimizer<T>(
  protected val myData1: List<T>,
  protected val myData2: List<T>,
  private val myIterable: FairDiffIterable,
  protected val myIndicator: CancellationChecker
) {
  private val myRanges: MutableList<Range> = ArrayList()

  fun build(): FairDiffIterable {
    for (range in myIterable.iterateUnchanged()) {
      myRanges.add(range)
      processLastRanges()
    }

    return fair(createUnchanged(myRanges, myData1.size, myData2.size))
  }

  private fun processLastRanges() {
    if (myRanges.size < 2) return  // nothing to do


    val range1 = myRanges[myRanges.size - 2]
    val range2 = myRanges[myRanges.size - 1]
    if (range1.end1 != range2.start1 && range1.end2 != range2.start2) {
      // if changes do not touch and we still can perform one of these optimisations,
      // it means that given DiffIterable is not LCS (because we can build a smaller one). This should not happen.
      return
    }

    val count1 = range1.end1 - range1.start1
    val count2 = range2.end1 - range2.start1

    val equalForward = expandForward(myData1, myData2, range1.end1, range1.end2, range1.end1 + count2, range1.end2 + count2)
    val equalBackward = expandBackward(myData1, myData2, range2.start1 - count1, range2.start2 - count1, range2.start1, range2.start2)

    // nothing to do
    if (equalForward == 0 && equalBackward == 0) return

    // merge chunks left [A]B[B] -> [AB]B
    if (equalForward == count2) {
      myRanges.removeAt(myRanges.size - 1)
      myRanges.removeAt(myRanges.size - 1)
      myRanges.add(Range(range1.start1, range1.end1 + count2, range1.start2, range1.end2 + count2))
      processLastRanges()
      return
    }

    // merge chunks right [A]A[B] -> A[AB]
    if (equalBackward == count1) {
      myRanges.removeAt(myRanges.size - 1)
      myRanges.removeAt(myRanges.size - 1)
      myRanges.add(Range(range2.start1 - count1, range2.end1, range2.start2 - count1, range2.end2))
      processLastRanges()
      return
    }


    val touchSide = fromLeft(range1.end1 == range2.start1)

    val shift = getShift(touchSide, equalForward, equalBackward, range1, range2)
    if (shift != 0) {
      myRanges.removeAt(myRanges.size - 1)
      myRanges.removeAt(myRanges.size - 1)
      myRanges.add(Range(range1.start1, range1.end1 + shift, range1.start2, range1.end2 + shift))
      myRanges.add(Range(range2.start1 + shift, range2.end1, range2.start2 + shift, range2.end2))
    }
  }

  // 0 - do nothing
  // >0 - shift forward
  // <0 - shift backward
  protected abstract fun getShift(
    touchSide: Side, equalForward: Int, equalBackward: Int,
    range1: Range, range2: Range
  ): Int

  //
  // Implementations
  //
  /*
   * 1. Minimise amount of chunks
   *      good: "AX[AB]" - "[AB]"
   *      bad: "[A]XA[B]" - "[A][B]"
   *
   * 2. Minimise amount of modified 'sentences', where sentence is a sequence of words, that are not separated by whitespace
   *      good: "[AX] [AZ]" - "[AX] AY [AZ]"
   *      bad: "[AX A][Z]" - "[AX A]Y A[Z]"
   *      ex: "1.0.123 1.0.155" vs "1.0.123 1.0.134 1.0.155"
   */
  class WordChunkOptimizer(
    words1: List<ByWordRt.InlineChunk>,
    words2: List<ByWordRt.InlineChunk>,
    private val myText1: CharSequence,
    private val myText2: CharSequence,
    changes: FairDiffIterable,
    indicator: CancellationChecker
  ) : ChunkOptimizer<ByWordRt.InlineChunk>(words1, words2, changes, indicator) {
    override fun getShift(touchSide: Side, equalForward: Int, equalBackward: Int, range1: Range, range2: Range): Int {
      val touchWords: List<ByWordRt.InlineChunk> = touchSide.selectNotNull(myData1, myData2)
      val touchText: CharSequence = touchSide.select(myText1, myText2)!!
      val touchStart = touchSide.select(range2.start1, range2.start2)

      // check if chunks are already separated by whitespaces
      if (isSeparatedWithWhitespace(touchText, touchWords[touchStart - 1], touchWords[touchStart])) return 0

      // shift chunks left [X]A Y[A ZA] -> [XA] YA [ZA]
      //                   [X][A ZA] -> [XA] [ZA]
      val leftShift: Int = findSequenceEdgeShift(touchText, touchWords, touchStart, equalForward, true)
      if (leftShift > 0) return leftShift

      // shift chunks right [AX A]Y A[Z] -> [AX] AY [AZ]
      //                    [AX A][Z] -> [AX] [AZ]
      val rightShift: Int = findSequenceEdgeShift(touchText, touchWords, touchStart - 1, equalBackward, false)
      if (rightShift > 0) return -rightShift

      // nothing to do
      return 0
    }

    companion object {
      private fun findSequenceEdgeShift(
        text: CharSequence, words: List<ByWordRt.InlineChunk>, offset: Int, count: Int,
        leftToRight: Boolean
      ): Int {
        for (i in 0..<count) {
          val word1: ByWordRt.InlineChunk
          val word2: ByWordRt.InlineChunk
          if (leftToRight) {
            word1 = words[offset + i]
            word2 = words[offset + i + 1]
          }
          else {
            word1 = words[offset - i - 1]
            word2 = words[offset - i]
          }
          if (isSeparatedWithWhitespace(text, word1, word2)) return i + 1
        }
        return -1
      }

      private fun isSeparatedWithWhitespace(text: CharSequence, word1: ByWordRt.InlineChunk, word2: ByWordRt.InlineChunk): Boolean {
        if (word1 is ByWordRt.NewlineChunk || word2 is ByWordRt.NewlineChunk) return true

        val offset1 = word1.offset2
        val offset2 = word2.offset1

        for (i in offset1..<offset2) {
          if (text[i].isSpaceEnterOrTab()) return true
        }
        return false
      }
    }
  }

  /*
   * 1. Minimise amount of chunks
   *      good: "AX[AB]" - "[AB]"
   *      bad: "[A]XA[B]" - "[A][B]"
   *
   * 2. Prefer insertions/deletions, that are bounded by empty(or 'unimportant') line
   *      good: "ABooYZ [ABuuYZ ]ABzzYZ" - "ABooYZ []ABzzYZ"
   *      bad: "ABooYZ AB[uuYZ AB]zzYZ" - "ABooYZ AB[]zzYZ"
   */
  class LineChunkOptimizer(
    lines1: List<ByLineRt.Line>,
    lines2: List<ByLineRt.Line>,
    changes: FairDiffIterable,
    indicator: CancellationChecker
  ) : ChunkOptimizer<ByLineRt.Line>(lines1, lines2, changes, indicator) {
    override fun getShift(touchSide: Side, equalForward: Int, equalBackward: Int, range1: Range, range2: Range): Int {
      var shift: Int?
      val threshold = ComparisonUtil.getUnimportantLineCharCount()

      shift = getUnchangedBoundaryShift(touchSide, equalForward, equalBackward, range1, range2, 0)
      if (shift != null) return shift

      shift = getChangedBoundaryShift(touchSide, equalForward, equalBackward, range1, range2, 0)
      if (shift != null) return shift

      shift = getUnchangedBoundaryShift(touchSide, equalForward, equalBackward, range1, range2, threshold)
      if (shift != null) return shift

      shift = getChangedBoundaryShift(touchSide, equalForward, equalBackward, range1, range2, threshold)
      if (shift != null) return shift

      return 0
    }

    /**
     * search for an empty line boundary in unchanged lines
     * ie: we want insertion/deletion to go right before/after of an empty line
     */
    private fun getUnchangedBoundaryShift(
      touchSide: Side,
      equalForward: Int, equalBackward: Int,
      range1: Range, range2: Range,
      threshold: Int
    ): Int? {
      val touchLines = touchSide.selectNotNull(myData1, myData2)
      val touchStart = touchSide.select(range2.start1, range2.start2)

      val shiftForward: Int = findNextUnimportantLine(touchLines, touchStart, equalForward + 1, threshold)
      val shiftBackward: Int = findPrevUnimportantLine(touchLines, touchStart - 1, equalBackward + 1, threshold)

      return getShift(shiftForward, shiftBackward)
    }

    /**
     * search for an empty line boundary in changed lines
     * ie: we want insertion/deletion to start/end with an empty line
     */
    private fun getChangedBoundaryShift(
      touchSide: Side,
      equalForward: Int, equalBackward: Int,
      range1: Range, range2: Range,
      threshold: Int
    ): Int? {
      val nonTouchSide = touchSide.other()
      val nonTouchLines = nonTouchSide.selectNotNull(myData1, myData2)
      val changeStart = nonTouchSide.select(range1.end1, range1.end2)
      val changeEnd = nonTouchSide.select(range2.start1, range2.start2)

      val shiftForward: Int = findNextUnimportantLine(nonTouchLines, changeStart, equalForward + 1, threshold)
      val shiftBackward: Int = findPrevUnimportantLine(nonTouchLines, changeEnd - 1, equalBackward + 1, threshold)

      return getShift(shiftForward, shiftBackward)
    }

    companion object {
      private fun findNextUnimportantLine(lines: List<ByLineRt.Line>, offset: Int, count: Int, threshold: Int): Int {
        for (i in 0..<count) {
          if (lines[offset + i].nonSpaceChars <= threshold) return i
        }
        return -1
      }

      private fun findPrevUnimportantLine(lines: List<ByLineRt.Line>, offset: Int, count: Int, threshold: Int): Int {
        for (i in 0..<count) {
          if (lines[offset - i].nonSpaceChars <= threshold) return i
        }
        return -1
      }

      private fun getShift(shiftForward: Int, shiftBackward: Int): Int? {
        if (shiftForward == -1 && shiftBackward == -1) return null
        if (shiftForward == 0 || shiftBackward == 0) return 0

        return if (shiftForward != -1) shiftForward else -shiftBackward
      }
    }
  }
}
