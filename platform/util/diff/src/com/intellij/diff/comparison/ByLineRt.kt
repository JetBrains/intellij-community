// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison

import com.intellij.diff.comparison.ChangeCorrector.SmartLineChangeCorrector
import com.intellij.diff.comparison.ChunkOptimizer.LineChunkOptimizer
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.comparison.iterables.DiffIterableUtil.ExpandChangeBuilder
import com.intellij.diff.comparison.iterables.FairDiffIterable
import com.intellij.diff.fragments.MergeLineFragment
import com.intellij.diff.fragments.MergeLineFragmentImpl
import com.intellij.diff.util.MergeRange
import com.intellij.diff.util.Range
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.Strings
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max
import kotlin.math.min

object ByLineRt {
  @JvmStatic
  fun compare(
    lines1: List<CharSequence>,
    lines2: List<CharSequence>,
    policy: ComparisonPolicy,
    indicator: CancellationChecker,
  ): FairDiffIterable {
    indicator.checkCanceled()
    return doCompare(getLines(lines1, policy), getLines(lines2, policy), policy, indicator)
  }

  @JvmStatic
  fun compare(
    lines1: List<CharSequence>,
    lines2: List<CharSequence>,
    lines3: List<CharSequence>,
    policy: ComparisonPolicy,
    indicator: CancellationChecker,
  ): List<MergeRange> {
    indicator.checkCanceled()
    return doCompare(getLines(lines1, policy), getLines(lines2, policy), getLines(lines3, policy), policy, indicator, false)
  }

  @JvmStatic
  fun merge(
    lines1: List<CharSequence>,
    lines2: List<CharSequence>,
    lines3: List<CharSequence>,
    policy: ComparisonPolicy,
    indicator: CancellationChecker,
  ): List<MergeRange> {
    indicator.checkCanceled()
    return doCompare(getLines(lines1, policy), getLines(lines2, policy), getLines(lines3, policy), policy, indicator, true)
  }

  //
  // Impl
  //

  @JvmStatic
  internal fun doCompare(
    lines1: List<Line>,
    lines2: List<Line>,
    policy: ComparisonPolicy,
    indicator: CancellationChecker,
  ): FairDiffIterable {
    indicator.checkCanceled()

    if (policy == ComparisonPolicy.IGNORE_WHITESPACES) {
      var changes = compareSmart(lines1, lines2, indicator)
      changes = optimizeLineChunks(lines1, lines2, changes, indicator)
      return expandRanges(lines1, lines2, changes)
    }
    else {
      val iwLines1 = convertMode(lines1, ComparisonPolicy.IGNORE_WHITESPACES)
      val iwLines2 = convertMode(lines2, ComparisonPolicy.IGNORE_WHITESPACES)

      var iwChanges = compareSmart(iwLines1, iwLines2, indicator)
      iwChanges = optimizeLineChunks(lines1, lines2, iwChanges, indicator)
      return correctChangesSecondStep(lines1, lines2, iwChanges)
    }
  }

  /**
   * @param keepIgnoredChanges if true, blocks of "ignored" changes will not be actually ignored (but will not be included into "conflict" blocks)
   */
  @JvmStatic
  internal fun doCompare(
    lines1: List<Line>,
    lines2: List<Line>,
    lines3: List<Line>,
    policy: ComparisonPolicy,
    indicator: CancellationChecker,
    keepIgnoredChanges: Boolean,
  ): List<MergeRange> {
    indicator.checkCanceled()

    val iwLines1 = convertMode(lines1, ComparisonPolicy.IGNORE_WHITESPACES)
    val iwLines2 = convertMode(lines2, ComparisonPolicy.IGNORE_WHITESPACES)
    val iwLines3 = convertMode(lines3, ComparisonPolicy.IGNORE_WHITESPACES)

    var iwChanges1 = compareSmart(iwLines2, iwLines1, indicator)
    iwChanges1 = optimizeLineChunks(lines2, lines1, iwChanges1, indicator)
    val iterable1 = correctChangesSecondStep(lines2, lines1, iwChanges1)

    var iwChanges2 = compareSmart(iwLines2, iwLines3, indicator)
    iwChanges2 = optimizeLineChunks(lines2, lines3, iwChanges2, indicator)
    val iterable2 = correctChangesSecondStep(lines2, lines3, iwChanges2)

    return if (keepIgnoredChanges && policy != ComparisonPolicy.DEFAULT) {
      ComparisonMergeUtil.buildMerge(iterable1, iterable2,
                                     { index1, index2, index3 ->
                                       equalsDefaultPolicy(lines1, lines2, lines3, index1, index2, index3)
                                     },
                                     indicator)
    }
    else {
      ComparisonMergeUtil.buildSimple(iterable1, iterable2, indicator)
    }
  }

  private fun equalsDefaultPolicy(
    lines1: List<Line>,
    lines2: List<Line>,
    lines3: List<Line>,
    index1: Int, index2: Int, index3: Int,
  ): Boolean {
    val content1 = lines1[index1].content
    val content2 = lines2[index2].content
    val content3 = lines3[index3].content
    return ComparisonUtil.isEquals(content2, content1, ComparisonPolicy.DEFAULT) &&
           ComparisonUtil.isEquals(content2, content3, ComparisonPolicy.DEFAULT)
  }

  private fun correctChangesSecondStep(
    lines1: List<Line>,
    lines2: List<Line>,
    changes: FairDiffIterable,
  ): FairDiffIterable {
    /*
     * We want to fix invalid matching here:
     *
     * .{        ..{
     * ..{   vs  ...{
     * ...{
     *
     * first step will return matching (0,2)-(0,2). And we should adjust it to (1,3)-(0,2)
     *
     *
     * From the other hand, we don't want to reduce number of IW-matched lines.
     *
     * .{         ...{
     * ..{    vs  ..{
     * ...{       .{
     *
     * first step will return (0,3)-(0,3) and 'correcting' it to (0,1)-(2,3) is wrong (and it will break ByWord highlighting).
     *
     *
     * Idea:
     * 1. lines are matched at first step and equal -> match them
     * 2. lines are not matched at first step -> do not match them
     * 3. lines are matched at first step and not equal ->
     *   a. find all IW-equal lines in the same unmatched block
     *   b. find a maximum matching between them, maximising amount of equal pairs in it
     *   c. match equal lines using result of the previous step
     */

    val builder = ExpandChangeBuilder(lines1, lines2)
    object : Any() {
      private var sample: CharSequence? = null
      private var last1 = 0
      private var last2 = 0

      fun run() {
        for (range in changes.iterateUnchanged()) {
          val count = range.end1 - range.start1
          for (i in 0 until count) {
            val index1 = range.start1 + i
            val index2 = range.start2 + i
            val line1 = lines1[index1]
            val line2 = lines2[index2]

            if (!ComparisonUtil.isEquals(sample, line1.content, ComparisonPolicy.IGNORE_WHITESPACES)) {
              if (line1 == line2) {
                flush(index1, index2)
                builder.markEqual(index1, index2)
              }
              else {
                flush(index1, index2)
                sample = line1.content
              }
            }
          }
        }
        flush(changes.length1, changes.length2)
      }

      fun flush(line1: Int, line2: Int) {
        if (sample == null) return

        val start1 = max(last1.toDouble(), builder.index1.toDouble()).toInt()
        val start2 = max(last2.toDouble(), builder.index2.toDouble()).toInt()

        val subLines1: IntList = IntArrayList()
        val subLines2: IntList = IntArrayList()
        for (i in start1 until line1) {
          if (ComparisonUtil.isEquals(sample, lines1[i].content, ComparisonPolicy.IGNORE_WHITESPACES)) {
            subLines1.add(i)
            last1 = i + 1
          }
        }
        for (i in start2 until line2) {
          if (ComparisonUtil.isEquals(sample, lines2[i].content, ComparisonPolicy.IGNORE_WHITESPACES)) {
            subLines2.add(i)
            last2 = i + 1
          }
        }

        assert(subLines1.size > 0 && subLines2.size > 0)
        alignExactMatching(subLines1, subLines2)

        sample = null
      }

      fun alignExactMatching(subLines1: IntList, subLines2: IntList) {
        val n = max(subLines1.size.toDouble(), subLines2.size.toDouble()).toInt()
        val skipAligning = n > 10 ||  // we use brute-force algorithm (C_n_k). This will limit search space by ~250 cases.
                           subLines1.size == subLines2.size // nothing to do

        if (skipAligning) {
          val count = min(subLines1.size.toDouble(), subLines2.size.toDouble()).toInt()
          for (i in 0 until count) {
            val index1 = subLines1.getInt(i)
            val index2 = subLines2.getInt(i)
            if (lines1[index1] == lines2[index2]) {
              builder.markEqual(index1, index2)
            }
          }
          return
        }

        if (subLines1.size < subLines2.size) {
          val matching = getBestMatchingAlignment(subLines1, subLines2, lines1, lines2)
          for (i in subLines1.indices) {
            val index1 = subLines1.getInt(i)
            val index2 = subLines2.getInt(matching[i])
            if (lines1[index1] == lines2[index2]) {
              builder.markEqual(index1, index2)
            }
          }
        }
        else {
          val matching = getBestMatchingAlignment(subLines2, subLines1, lines2, lines1)
          for (i in subLines2.indices) {
            val index1 = subLines1.getInt(matching[i])
            val index2 = subLines2.getInt(i)
            if (lines1[index1] == lines2[index2]) {
              builder.markEqual(index1, index2)
            }
          }
        }
      }
    }.run()

    return DiffIterableUtil.fair(builder.finish())
  }

  private fun getBestMatchingAlignment(
    subLines1: IntList,
    subLines2: IntList,
    lines1: List<Line>,
    lines2: List<Line>,
  ): IntArray {
    assert(subLines1.size < subLines2.size)
    val size = subLines1.size

    val comb = IntArray(size)
    val best = IntArray(size)
    for (i in 0 until size) {
      best[i] = i
    }

    // find a combination with maximum weight (maximum number of equal lines)
    object : Any() {
      var bestWeight: Int = 0

      fun run() {
        combinations(0, subLines2.size - 1, 0)
      }

      fun combinations(start: Int, n: Int, k: Int) {
        if (k == size) {
          processCombination()
          return
        }

        for (i in start..n) {
          comb[k] = i
          combinations(i + 1, n, k + 1)
        }
      }

      fun processCombination() {
        var weight = 0
        for (i in 0 until size) {
          val index1 = subLines1.getInt(i)
          val index2 = subLines2.getInt(comb[i])
          if (lines1[index1] == lines2[index2]) weight++
        }

        if (weight > bestWeight) {
          bestWeight = weight
          System.arraycopy(comb, 0, best, 0, comb.size)
        }
      }
    }.run()

    return best
  }

  private fun optimizeLineChunks(
    lines1: List<Line>,
    lines2: List<Line>,
    iterable: FairDiffIterable,
    indicator: CancellationChecker,
  ): FairDiffIterable {
    return LineChunkOptimizer(lines1, lines2, iterable, indicator).build()
  }

  /*
   * Compare lines in two steps:
   *  - compare ignoring "unimportant" lines
   *  - correct changes (compare all lines gaps between matched chunks)
   */
  private fun compareSmart(
    lines1: List<Line>,
    lines2: List<Line>,
    indicator: CancellationChecker,
  ): FairDiffIterable {
    val threshold = ComparisonUtil.getUnimportantLineCharCount()
    if (threshold == 0) return DiffIterableUtil.diff(lines1, lines2, indicator)

    val bigLines1 = getBigLines(lines1, threshold)
    val bigLines2 = getBigLines(lines2, threshold)

    val changes = DiffIterableUtil.diff(bigLines1.first, bigLines2.first, indicator)
    return SmartLineChangeCorrector(bigLines1.second, bigLines2.second, lines1, lines2, changes, indicator).build()
  }

  private fun getBigLines(lines: List<Line>, threshold: Int): Pair<List<Line>, IntList> {
    val bigLines: MutableList<Line> = ArrayList(lines.size)
    val indexes: IntList = IntArrayList(lines.size)

    for (i in lines.indices) {
      val line = lines[i]
      if (line.nonSpaceChars > threshold) {
        bigLines.add(line)
        indexes.add(i)
      }
    }
    return Pair.create(bigLines, indexes)
  }

  private fun expandRanges(
    lines1: List<Line>,
    lines2: List<Line>,
    iterable: FairDiffIterable,
  ): FairDiffIterable {
    val changes = ArrayList<Range>()

    for (ch in iterable.iterateChanges()) {
      val expanded = expand(lines1, lines2, ch.start1, ch.start2, ch.end1, ch.end2)
      if (!expanded.isEmpty) changes.add(expanded)
    }

    return DiffIterableUtil.fair(DiffIterableUtil.create(changes, lines1.size, lines2.size))
  }

  //
  // Lines
  //

  private fun getLines(text: List<CharSequence>, policy: ComparisonPolicy): List<Line> {
    return text.map { line -> Line(line, policy) }
  }

  private fun convertMode(original: List<Line>, policy: ComparisonPolicy): List<Line> {
    val result = ArrayList<Line>(original.size)
    for (line in original) {
      val newLine = if (line.policy != policy)
        Line(line.content, policy)
      else
        line
      result.add(newLine)
    }
    return result
  }

  @JvmStatic
  fun convertIntoMergeLineFragments(conflicts: List<MergeRange>): List<MergeLineFragment> {
    return conflicts.map { MergeLineFragmentImpl(it) }
  }

  @JvmStatic
  @ApiStatus.Internal
  fun convertIntoMergeLineFragments(
    conflicts: List<MergeRange>,
    range: MergeRange,
  ): List<MergeLineFragment> {
    return conflicts.map { conflict ->
      MergeLineFragmentImpl(conflict.start1 + range.start1, conflict.end1 + range.start1,
                            conflict.start2 + range.start2, conflict.end2 + range.start2,
                            conflict.start3 + range.start3, conflict.end3 + range.start3)
    }
  }

  @ApiStatus.Internal
  class Line(val content: CharSequence, val policy: ComparisonPolicy) {
    private val hash = ComparisonUtil.hashCode(content, policy)

    val nonSpaceChars: Int = countNonSpaceChars(content)

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false

      val line = other as Line
      assert(policy == line.policy)

      if (hashCode() != line.hashCode()) return false

      return ComparisonUtil.isEquals(content, line.content, policy)
    }

    override fun hashCode(): Int {
      return hash
    }

    companion object {
      private fun countNonSpaceChars(text: CharSequence): Int {
        var nonSpace = 0

        val len = text.length
        var offset = 0

        while (offset < len) {
          val c = text[offset]
          if (!Strings.isWhiteSpace(c)) nonSpace++
          offset++
        }

        return nonSpace
      }
    }
  }
}
