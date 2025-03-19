// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("NAME_SHADOWING")

package com.intellij.diff.comparison

import com.intellij.diff.comparison.ByWordRt.compare
import com.intellij.diff.comparison.ChunkOptimizer.WordChunkOptimizer
import com.intellij.diff.comparison.LineFragmentSplitter.WordBlock
import com.intellij.diff.comparison.iterables.DiffIterable
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.comparison.iterables.FairDiffIterable
import com.intellij.diff.comparison.iterables.SubiterableDiffIterable
import com.intellij.diff.fragments.DiffFragment
import com.intellij.diff.fragments.DiffFragmentImpl
import com.intellij.diff.fragments.MergeWordFragment
import com.intellij.diff.fragments.MergeWordFragmentImpl
import com.intellij.diff.tools.util.text.LineOffsets
import com.intellij.diff.util.DiffRangeUtil
import com.intellij.diff.util.MergeRange
import com.intellij.diff.util.Range
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.text.Strings
import com.intellij.util.IntPair
import com.intellij.util.text.MergingCharSequence
import org.jetbrains.annotations.ApiStatus

object ByWordRt {
  @JvmStatic
  fun compare(
    text1: CharSequence,
    text2: CharSequence,
    policy: ComparisonPolicy,
    indicator: CancellationChecker,
  ): List<DiffFragment> {
    indicator.checkCanceled()

    val words1 = getInlineChunks(text1)
    val words2 = getInlineChunks(text2)

    return compare(text1, words1, text2, words2, policy, indicator)
  }

  @JvmStatic
  fun compare(
    text1: CharSequence, words1: List<InlineChunk>,
    text2: CharSequence, words2: List<InlineChunk>,
    policy: ComparisonPolicy,
    indicator: CancellationChecker,
  ): List<DiffFragment> {
    var wordChanges = DiffIterableUtil.diff(words1, words2, indicator)
    wordChanges = optimizeWordChunks(text1, text2, words1, words2, wordChanges, indicator)

    val delimitersIterable = matchAdjustmentDelimiters(text1, text2, words1, words2, wordChanges, indicator)
    val iterable = matchAdjustmentWhitespaces(text1, text2, delimitersIterable, policy, indicator)

    return convertIntoDiffFragments(iterable)
  }

  @JvmStatic
  fun compare(
    text1: CharSequence,
    text2: CharSequence,
    text3: CharSequence,
    policy: ComparisonPolicy,
    indicator: CancellationChecker,
  ): List<MergeWordFragment> {
    indicator.checkCanceled()

    val words1 = getInlineChunks(text1)
    val words2 = getInlineChunks(text2)
    val words3 = getInlineChunks(text3)

    var wordChanges1 = DiffIterableUtil.diff(words2, words1, indicator)
    wordChanges1 = optimizeWordChunks(text2, text1, words2, words1, wordChanges1, indicator)
    val iterable1 = matchAdjustmentDelimiters(text2, text1, words2, words1, wordChanges1, indicator)

    var wordChanges2 = DiffIterableUtil.diff(words2, words3, indicator)
    wordChanges2 = optimizeWordChunks(text2, text3, words2, words3, wordChanges2, indicator)
    val iterable2 = matchAdjustmentDelimiters(text2, text3, words2, words3, wordChanges2, indicator)

    val wordConflicts = ComparisonMergeUtil.buildSimple(iterable1, iterable2, indicator)
    val result = matchAdjustmentWhitespaces(text1, text2, text3, wordConflicts, policy, indicator)

    return convertIntoMergeWordFragments(result)
  }

  @JvmStatic
  fun compareAndSplit(
    text1: CharSequence,
    text2: CharSequence,
    policy: ComparisonPolicy,
    indicator: CancellationChecker,
  ): List<LineBlock> {
    indicator.checkCanceled()

    val words1 = getInlineChunks(text1)
    val words2 = getInlineChunks(text2)

    var wordChanges = DiffIterableUtil.diff(words1, words2, indicator)
    wordChanges = optimizeWordChunks(text1, text2, words1, words2, wordChanges, indicator)

    val wordBlocks = LineFragmentSplitter(text1, text2, words1, words2, wordChanges, indicator).run()

    val subIterables = collectWordBlockSubIterables(wordChanges, wordBlocks)

    val lineBlocks = ArrayList<LineBlock>(wordBlocks.size)
    for (i in wordBlocks.indices) {
      val block = wordBlocks[i]
      val offsets = block.offsets
      val words = block.words

      val subtext1 = text1.subSequence(offsets.start1, offsets.end1)
      val subtext2 = text2.subSequence(offsets.start2, offsets.end2)

      val subwords1 = words1.subList(words.start1, words.end1)
      val subwords2 = words2.subList(words.start2, words.end2)

      val subiterable = subIterables[i]

      val delimitersIterable = matchAdjustmentDelimiters(subtext1, subtext2, subwords1, subwords2, subiterable,
                                                         offsets.start1, offsets.start2, indicator)
      val iterable = matchAdjustmentWhitespaces(subtext1, subtext2, delimitersIterable, policy, indicator)

      val fragments = convertIntoDiffFragments(iterable)

      val newlines1 = countNewlines(subwords1)
      val newlines2 = countNewlines(subwords2)

      lineBlocks.add(LineBlock(fragments, offsets, newlines1, newlines2))
    }

    return lineBlocks
  }

  private fun collectWordBlockSubIterables(
    wordChanges: FairDiffIterable,
    wordBlocks: List<WordBlock>,
  ): List<FairDiffIterable> {
    val changed = ArrayList<Range>()
    for (range in wordChanges.iterateChanges()) {
      changed.add(range)
    }

    var index = 0

    val subIterables = ArrayList<FairDiffIterable>(wordBlocks.size)
    for (block in wordBlocks) {
      val words = block.words

      while (index < changed.size) {
        val range = changed[index]
        if (range.end1 < words.start1 || range.end2 < words.start2) {
          index++
          continue
        }

        break
      }

      subIterables.add(DiffIterableUtil.fair(SubiterableDiffIterable(changed, words.start1, words.end1, words.start2, words.end2, index)))
    }
    return subIterables
  }

  /**
   * Constructs 'lines + words' differences while giving priority to the 'words' delta.
   *
   * This works better than [compare] for inputs that have no matching lines (and get dragged away by the empty lines as a result).
   * For example,
   *  'Java -> Kotlin' conversions, as the latter has no trailing ';'
   *  Text in natural language with manual line wrapping (that is inconsistent between compared texts)
   */
  @JvmStatic
  @ApiStatus.Internal
  fun compareWordsFirst(
    text1: CharSequence,
    text2: CharSequence,
    lineOffsets1: LineOffsets,
    lineOffsets2: LineOffsets,
    policy: ComparisonPolicy,
    indicator: CancellationChecker,
  ): List<WordLineBlock> {
    val words1 = getInlineChunks(text1).filterIsInstance<WordChunk>()
    val words2 = getInlineChunks(text2).filterIsInstance<WordChunk>()

    var wordChanges = DiffIterableUtil.diff(words1, words2, indicator)
    wordChanges = optimizeWordChunks(text1, text2, words1, words2, wordChanges, indicator)

    val delimitersIterable = matchAdjustmentDelimiters(text1, text2, words1, words2, wordChanges, indicator)

    val lineBlocks = LineUplifter(text1, text2, lineOffsets1, lineOffsets2, delimitersIterable, indicator).build()

    val subIterables = collectLineBlockSubIterables(delimitersIterable, lineBlocks, lineOffsets1, lineOffsets2)

    val result = mutableListOf<WordLineBlock>()
    for (i in lineBlocks.indices) {
      val lineBlock = lineBlocks[i]
      val subIterable = subIterables[i]

      val subRange1 = DiffRangeUtil.getLinesRange(lineOffsets1, lineBlock.start1, lineBlock.end1, true)
      val subRange2 = DiffRangeUtil.getLinesRange(lineOffsets2, lineBlock.start2, lineBlock.end2, true)

      val subtext1 = text1.subSequence(subRange1.startOffset, subRange1.endOffset)
      val subtext2 = text2.subSequence(subRange2.startOffset, subRange2.endOffset)

      val iterable = matchAdjustmentWhitespaces(subtext1, subtext2, subIterable, policy, indicator)

      val innerFragments = convertIntoDiffFragments(iterable)
      if (innerFragments.isEmpty()) continue

      val shiftedLineBlock = Range(lineBlock.start1,
                                   lineBlock.end1,
                                   lineBlock.start2,
                                   lineBlock.end2)
      result += WordLineBlock(shiftedLineBlock, innerFragments)
    }

    return result
  }

  private fun collectLineBlockSubIterables(
    iterable: FairDiffIterable,
    lineBlocks: List<Range>,
    lineOffsets1: LineOffsets,
    lineOffsets2: LineOffsets,
  ): List<FairDiffIterable> {
    val changed = ArrayList<Range>()
    for (range in iterable.iterateChanges()) {
      changed.add(range)
    }

    var index = 0

    val subIterables = ArrayList<FairDiffIterable>(lineBlocks.size)
    for (lineBlock in lineBlocks) {
      val subRange1 = DiffRangeUtil.getLinesRange(lineOffsets1, lineBlock.start1, lineBlock.end1, true)
      val subRange2 = DiffRangeUtil.getLinesRange(lineOffsets2, lineBlock.start2, lineBlock.end2, true)

      while (index < changed.size) {
        val range = changed[index]
        if (range.end1 < subRange1.startOffset || range.end2 < subRange2.startOffset) {
          index++
          continue
        }

        break
      }

      subIterables.add(DiffIterableUtil.fair(SubiterableDiffIterable(changed,
                                                                     subRange1.startOffset, subRange1.endOffset,
                                                                     subRange2.startOffset, subRange2.endOffset,
                                                                     index)))
    }
    return subIterables
  }

  /**
   * Collects 'changed line blocks' that correspond to the given 'by-word differences' (or 'by-word + delimiters')
   *
   * The result is 'good enough' and may not be optimal from the human's point of view.
   * For example, it leaves the 'unmatched empty lines' inside an unmatched block,
   * when a human would match these empty lines, breaking the changed block into two changed blocks.
   * The correct answer in this case may depend on the context, so we can't blindly match these as post-processing.
   */
  private class LineUplifter(
    private val text1: CharSequence,
    private val text2: CharSequence,
    private val lineOffsets1: LineOffsets,
    private val lineOffsets2: LineOffsets,
    private val changes: FairDiffIterable,
    private val indicator: CancellationChecker,
  ) {
    val lineBlocks = mutableListOf<Range>()

    var blockStart1 = 0
    var blockStart2 = 0

    fun build(): List<Range> {
      execute()
      return lineBlocks
    }

    fun execute() {
      var lastIndex1 = 0
      var lastIndex2 = 0

      // walk from 'unchanged' deeper into the 'changed' territory,
      // find 'closest' and 'furthest' newline, aborting walking at the first non-whitespace symbol,
      // if found - end current block at the nearest newline, mark the furthest newline as a separate whitespaces-only block

      for (range in changes.iterateUnchanged()) {
        val pair = walkForward(lastIndex1, lastIndex2, range.start1, range.start2)
        if (pair != null) {
          walkBackward(pair.first, pair.second, range.start1, range.start2)
        }
        else {
          walkBackward(lastIndex1, lastIndex2, range.start1, range.start2)
        }
        lastIndex1 = range.end1
        lastIndex2 = range.end2
      }

      val pair = walkForward(lastIndex1, lastIndex2, text1.length, text2.length)
      if (pair != null) {
        walkBackward(pair.first, pair.second, text1.length, text2.length)
      }
      else {
        walkBackward(lastIndex1, lastIndex2, text1.length, text2.length)
      }
      markNextBlock(lineOffsets1.lineCount, lineOffsets2.lineCount)
    }

    /**
     * @return offsets of the nearest block, if found
     */
    private fun walkForward(start1: Int, start2: Int, end1: Int, end2: Int): IntPair? {
      val found1 = walkSideForward(text1, start1, end1) ?: return null
      val found2 = walkSideForward(text2, start2, end2) ?: return null

      markNextBlockOffset(found1.first, found2.first)
      markNextBlockOffset(found1.second, found2.second)
      return IntPair(found1.second, found2.second)
    }

    private fun walkBackward(start1: Int, start2: Int, end1: Int, end2: Int) {
      val found1 = walkSideBackward(text1, start1, end1) ?: return
      val found2 = walkSideBackward(text2, start2, end2) ?: return

      markNextBlockOffset(found1.second, found2.second)
      markNextBlockOffset(found1.first, found2.first)
    }

    private fun markNextBlockOffset(blockEndOffset1: Int, blockEndOffset2: Int) {
      val blockEnd1 = lineOffsets1.getLineNumber(blockEndOffset1) + 1
      val blockEnd2 = lineOffsets2.getLineNumber(blockEndOffset2) + 1
      markNextBlock(blockEnd1, blockEnd2)
    }

    private fun markNextBlock(blockEnd1: Int, blockEnd2: Int) {
      if (blockStart1 == blockEnd1 && blockStart2 == blockEnd2) return

      lineBlocks += Range(blockStart1, blockEnd1, blockStart2, blockEnd2)
      blockStart1 = blockEnd1
      blockStart2 = blockEnd2
    }

    companion object {
      private fun walkSideForward(text: CharSequence, start: Int, end: Int): IntPair? {
        var foundFirst: Int? = null
        var foundLast: Int? = null

        var index = start
        outer@ while (true) {
          while (index < end) {
            val ch = text[index]

            if (ch == '\n') {
              break
            }
            if (!Strings.isWhiteSpace(ch)) break@outer
            index++
          }
          if (index == end) break@outer
          if (foundFirst == null) foundFirst = index
          foundLast = index

          index++
        }

        if (foundFirst == null || foundLast == null) return null

        return IntPair(foundFirst, foundLast)
      }

      private fun walkSideBackward(text: CharSequence, start: Int, end: Int): IntPair? {
        var foundFirst: Int? = null
        var foundLast: Int? = null

        var index = end - 1
        outer@ while (true) {
          while (start <= index) {
            val ch = text[index]

            if (ch == '\n') {
              break
            }
            if (!Strings.isWhiteSpace(ch)) break@outer
            index--
          }
          if (index < start) break@outer

          if (foundFirst == null) foundFirst = index
          foundLast = index

          index--
        }

        if (foundFirst == null || foundLast == null) return null

        return IntPair(foundFirst, foundLast)
      }
    }
  }

  //
  // Impl
  //

  private fun optimizeWordChunks(
    text1: CharSequence,
    text2: CharSequence,
    words1: List<InlineChunk>,
    words2: List<InlineChunk>,
    iterable: FairDiffIterable,
    indicator: CancellationChecker,
  ): FairDiffIterable {
    return WordChunkOptimizer(words1, words2, text1, text2, iterable, indicator).build()
  }

  private fun matchAdjustmentDelimiters(
    text1: CharSequence,
    text2: CharSequence,
    words1: List<InlineChunk>,
    words2: List<InlineChunk>,
    changes: FairDiffIterable,
    indicator: CancellationChecker,
  ): FairDiffIterable {
    return matchAdjustmentDelimiters(text1, text2, words1, words2, changes, 0, 0, indicator)
  }

  private fun matchAdjustmentDelimiters(
    text1: CharSequence,
    text2: CharSequence,
    words1: List<InlineChunk>,
    words2: List<InlineChunk>,
    changes: FairDiffIterable,
    startShift1: Int,
    startShift2: Int,
    indicator: CancellationChecker,
  ): FairDiffIterable {
    return AdjustmentPunctuationMatcher(text1, text2, words1, words2, startShift1, startShift2, changes, indicator).build()
  }

  private fun matchAdjustmentWhitespaces(
    text1: CharSequence,
    text2: CharSequence,
    iterable: FairDiffIterable,
    policy: ComparisonPolicy,
    indicator: CancellationChecker,
  ): DiffIterable {
    when (policy) {
      ComparisonPolicy.DEFAULT -> {
        return DefaultCorrector(iterable, text1, text2, indicator).build()
      }
      ComparisonPolicy.TRIM_WHITESPACES -> {
        val defaultIterable = DefaultCorrector(iterable, text1, text2, indicator).build()
        return TrimSpacesCorrector(defaultIterable, text1, text2, indicator).build()
      }
      ComparisonPolicy.IGNORE_WHITESPACES -> {
        return IgnoreSpacesCorrector(iterable, text1, text2, indicator).build()
      }
    }
  }

  private fun matchAdjustmentWhitespaces(
    text1: CharSequence,
    text2: CharSequence,
    text3: CharSequence,
    conflicts: List<MergeRange>,
    policy: ComparisonPolicy,
    indicator: CancellationChecker,
  ): List<MergeRange> {
    when (policy) {
      ComparisonPolicy.DEFAULT -> {
        return MergeDefaultCorrector(conflicts, text1, text2, text3, indicator).build()
      }
      ComparisonPolicy.TRIM_WHITESPACES -> {
        val defaultConflicts = MergeDefaultCorrector(conflicts, text1, text2, text3, indicator).build()
        return MergeTrimSpacesCorrector(defaultConflicts, text1, text2, text3, indicator).build()
      }
      ComparisonPolicy.IGNORE_WHITESPACES -> {
        return MergeIgnoreSpacesCorrector(conflicts, text1, text2, text3, indicator).build()
      }
    }
  }

  @JvmStatic
  fun convertIntoMergeWordFragments(conflicts: List<MergeRange>): List<MergeWordFragment> {
    return conflicts.map { MergeWordFragmentImpl(it) }
  }

  @JvmStatic
  fun convertIntoDiffFragments(changes: DiffIterable): List<DiffFragment> {
    val fragments = ArrayList<DiffFragment>()
    for (ch in changes.iterateChanges()) {
      fragments.add(DiffFragmentImpl(ch.start1, ch.end1, ch.start2, ch.end2))
    }
    return fragments
  }

  /*
   * Compare one char sequence with two others (as if they were a single sequence)
   *
   * Return two DiffIterable: (0, len1) - (0, len21) and (0, len1) - (0, len22)
   */
  private fun comparePunctuation2Side(
    text1: CharSequence,
    text21: CharSequence,
    text22: CharSequence,
    indicator: CancellationChecker,
  ): Couple<FairDiffIterable> {
    val text2: CharSequence = MergingCharSequence(text21, text22)
    val changes = ByCharRt.comparePunctuation(text1, text2, indicator)

    val ranges = splitIterable2Side(changes, text21.length)

    val iterable1 = DiffIterableUtil.fair(DiffIterableUtil.createUnchanged(ranges.first, text1.length, text21.length))
    val iterable2 = DiffIterableUtil.fair(DiffIterableUtil.createUnchanged(ranges.second, text1.length, text22.length))
    return Couple.of(iterable1, iterable2)
  }

  private fun splitIterable2Side(changes: FairDiffIterable, offset: Int): Couple<List<Range>> {
    val ranges1 = ArrayList<Range>()
    val ranges2 = ArrayList<Range>()
    for (ch in changes.iterateUnchanged()) {
      if (ch.end2 <= offset) {
        ranges1.add(Range(ch.start1, ch.end1, ch.start2, ch.end2))
      }
      else if (ch.start2 >= offset) {
        ranges2.add(Range(ch.start1, ch.end1, ch.start2 - offset, ch.end2 - offset))
      }
      else {
        val len2 = offset - ch.start2
        ranges1.add(Range(ch.start1, ch.start1 + len2, ch.start2, offset))
        ranges2.add(Range(ch.start1 + len2, ch.end1, 0, ch.end2 - offset))
      }
    }
    return Couple.of(ranges1, ranges2)
  }

  @JvmStatic
  fun isWordChunk(chunk: InlineChunk): Boolean {
    return chunk is WordChunk
  }

  private fun isLeadingTrailingSpace(text: CharSequence, start: Int): Boolean {
    return isLeadingSpace(text, start) || isTrailingSpace(text, start)
  }

  private fun isLeadingSpace(text: CharSequence, start: Int): Boolean {
    var start = start
    if (start < 0) return false
    if (start == text.length) return false
    if (!Strings.isWhiteSpace(text[start])) return false

    start--
    while (start >= 0) {
      val c = text[start]
      if (c == '\n') return true
      if (!Strings.isWhiteSpace(c)) return false
      start--
    }
    return true
  }

  private fun isTrailingSpace(text: CharSequence, end: Int): Boolean {
    var end = end
    if (end < 0) return false
    if (end == text.length) return false
    if (!Strings.isWhiteSpace(text[end])) return false

    while (end < text.length) {
      val c = text[end]
      if (c == '\n') return true
      if (!Strings.isWhiteSpace(c)) return false
      end++
    }
    return true
  }

  //
  // Misc
  //

  private fun countNewlines(words: List<InlineChunk>): Int {
    var count = 0
    for (word in words) {
      if (word is NewlineChunk) count++
    }
    return count
  }

  @JvmStatic
  fun getInlineChunks(text: CharSequence): List<InlineChunk> {
    val chunks = ArrayList<InlineChunk>()

    val len = text.length
    var offset = 0

    var wordStart = -1
    var wordHash = 0

    while (offset < len) {
      val ch = Character.codePointAt(text, offset)
      val charCount = Character.charCount(ch)

      val isAlpha = isAlpha(ch)
      val isWordPart = isAlpha && !isContinuousScript(ch)

      if (isWordPart) {
        if (wordStart == -1) {
          wordStart = offset
          wordHash = 0
        }
        wordHash = wordHash * 31 + ch
      }
      else {
        if (wordStart != -1) {
          chunks.add(WordChunk(text, wordStart, offset, wordHash))
          wordStart = -1
        }

        if (isAlpha) { // continuous script
          chunks.add(WordChunk(text, offset, offset + charCount, ch))
        }
        else if (ch == '\n'.code) {
          chunks.add(NewlineChunk(offset))
        }
      }

      offset += charCount
    }

    if (wordStart != -1) {
      chunks.add(WordChunk(text, wordStart, len, wordHash))
    }

    return chunks
  }

  //
  // Punctuation matching
  //

  /*
   * sample: "[ X { A ! B } Y ]" "( X ... Y )" will lead to comparison of 3 groups of separators
   *      "["  vs "(",
   *      "{" + "}" vs "..."
   *      "]"  vs ")"
   */
  private class AdjustmentPunctuationMatcher(
    private val text1: CharSequence,
    private val text2: CharSequence,
    private val words1: List<InlineChunk>,
    private val words2: List<InlineChunk>,
    private val startShift1: Int,
    private val startShift2: Int,
    private val changes: FairDiffIterable,
    private val indicator: CancellationChecker,
  ) {
    private val len1: Int = text1.length
    private val len2: Int = text2.length

    private val builder = DiffIterableUtil.ChangeBuilder(len1, len2)

    fun build(): FairDiffIterable {
      execute()
      return DiffIterableUtil.fair(builder.finish())
    }

    var lastStart1: Int = 0
    var lastStart2: Int = 0
    var lastEnd1: Int = 0
    var lastEnd2: Int = 0

    fun execute() {
      clearLastRange()

      matchForward(-1, -1)

      for (ch in changes.iterateUnchanged()) {
        val count = ch.end1 - ch.start1
        for (i in 0 until count) {
          val index1 = ch.start1 + i
          val index2 = ch.start2 + i

          val start1 = getStartOffset1(index1)
          val start2 = getStartOffset2(index2)
          val end1 = getEndOffset1(index1)
          val end2 = getEndOffset2(index2)

          matchBackward(index1, index2)

          builder.markEqual(start1, start2, end1, end2)

          matchForward(index1, index2)
        }
      }

      matchBackward(words1.size, words2.size)
    }

    fun clearLastRange() {
      lastStart1 = -1
      lastStart2 = -1
      lastEnd1 = -1
      lastEnd2 = -1
    }

    fun matchBackward(index1: Int, index2: Int) {
      val start1 = if (index1 == 0) 0 else getEndOffset1(index1 - 1)
      val start2 = if (index2 == 0) 0 else getEndOffset2(index2 - 1)
      val end1 = if (index1 == words1.size) len1 else getStartOffset1(index1)
      val end2 = if (index2 == words2.size) len2 else getStartOffset2(index2)

      matchBackward(start1, start2, end1, end2)
      clearLastRange()
    }

    fun matchForward(index1: Int, index2: Int) {
      val start1 = if (index1 == -1) 0 else getEndOffset1(index1)
      val start2 = if (index2 == -1) 0 else getEndOffset2(index2)
      val end1 = if (index1 + 1 == words1.size) len1 else getStartOffset1(index1 + 1)
      val end2 = if (index2 + 1 == words2.size) len2 else getStartOffset2(index2 + 1)

      matchForward(start1, start2, end1, end2)
    }

    fun matchForward(start1: Int, start2: Int, end1: Int, end2: Int) {
      assert(lastStart1 == -1 && lastStart2 == -1 && lastEnd1 == -1 && lastEnd2 == -1)

      lastStart1 = start1
      lastStart2 = start2
      lastEnd1 = end1
      lastEnd2 = end2
    }

    fun matchBackward(start1: Int, start2: Int, end1: Int, end2: Int) {
      assert(lastStart1 != -1 && lastStart2 != -1 && lastEnd1 != -1 && lastEnd2 != -1)

      if (lastStart1 == start1 && lastStart2 == start2) { // pair of adjustment matched words, match gap between ("A B" - "A B")
        assert(lastEnd1 == end1 && lastEnd2 == end2)

        matchRange(start1, start2, end1, end2)
        return
      }
      if (lastStart1 < start1 && lastStart2 < start2) { // pair of matched words, with few unmatched ones between ("A X B" - "A Y B")
        assert(lastEnd1 <= start1 && lastEnd2 <= start2)

        matchRange(lastStart1, lastStart2, lastEnd1, lastEnd2)
        matchRange(start1, start2, end1, end2)
        return
      }

      // one side adjustment, and other has non-matched words between ("A B" - "A Y B")
      matchComplexRange(lastStart1, lastStart2, lastEnd1, lastEnd2, start1, start2, end1, end2)
    }

    fun matchRange(start1: Int, start2: Int, end1: Int, end2: Int) {
      if (start1 == end1 && start2 == end2) return

      val sequence1 = text1.subSequence(start1, end1)
      val sequence2 = text2.subSequence(start2, end2)

      val changes: DiffIterable = ByCharRt.comparePunctuation(sequence1, sequence2, indicator)

      for (ch in changes.iterateUnchanged()) {
        builder.markEqual(start1 + ch.start1, start2 + ch.start2, start1 + ch.end1, start2 + ch.end2)
      }
    }

    fun matchComplexRange(start11: Int, start12: Int, end11: Int, end12: Int, start21: Int, start22: Int, end21: Int, end22: Int) {
      if (start11 == start21 && end11 == end21) {
        matchComplexRangeLeft(start11, end11, start12, end12, start22, end22)
      }
      else if (start12 == start22 && end12 == end22) {
        matchComplexRangeRight(start12, end12, start11, end11, start21, end21)
      }
      else {
        throw IllegalStateException()
      }
    }

    fun matchComplexRangeLeft(start1: Int, end1: Int, start12: Int, end12: Int, start22: Int, end22: Int) {
      val sequence1 = text1.subSequence(start1, end1)
      val sequence21 = text2.subSequence(start12, end12)
      val sequence22 = text2.subSequence(start22, end22)

      val changes = comparePunctuation2Side(sequence1, sequence21, sequence22, indicator)

      for (ch in changes.first.iterateUnchanged()) {
        builder.markEqual(start1 + ch.start1, start12 + ch.start2, start1 + ch.end1, start12 + ch.end2)
      }
      for (ch in changes.second.iterateUnchanged()) {
        builder.markEqual(start1 + ch.start1, start22 + ch.start2, start1 + ch.end1, start22 + ch.end2)
      }
    }

    fun matchComplexRangeRight(start2: Int, end2: Int, start11: Int, end11: Int, start21: Int, end21: Int) {
      val sequence11 = text1.subSequence(start11, end11)
      val sequence12 = text1.subSequence(start21, end21)
      val sequence2 = text2.subSequence(start2, end2)

      val changes = comparePunctuation2Side(sequence2, sequence11, sequence12, indicator)

      // Mirrored ch.*1 and ch.*2 as we use "compare2Side" that works with 2 right sides, while we have 2 left here
      for (ch in changes.first.iterateUnchanged()) {
        builder.markEqual(start11 + ch.start2, start2 + ch.start1, start11 + ch.end2, start2 + ch.end1)
      }
      for (ch in changes.second.iterateUnchanged()) {
        builder.markEqual(start21 + ch.start2, start2 + ch.start1, start21 + ch.end2, start2 + ch.end1)
      }
    }

    fun getStartOffset1(index: Int): Int {
      return words1[index].offset1 - startShift1
    }

    fun getStartOffset2(index: Int): Int {
      return words2[index].offset1 - startShift2
    }

    fun getEndOffset1(index: Int): Int {
      return words1[index].offset2 - startShift1
    }

    fun getEndOffset2(index: Int): Int {
      return words2[index].offset2 - startShift2
    }
  }

  //
  // Whitespaces matching
  //

  private class DefaultCorrector(
    private val iterable: DiffIterable,
    private val text1: CharSequence,
    private val text2: CharSequence,
    private val indicator: CancellationChecker,
  ) {
    private val changes = ArrayList<Range>()

    fun build(): DiffIterable {
      for (range in iterable.iterateChanges()) {
        val endCut = expandWhitespacesBackward(text1, text2,
                                               range.start1, range.start2,
                                               range.end1, range.end2)
        val startCut = expandWhitespacesForward(text1, text2,
                                                range.start1, range.start2,
                                                range.end1 - endCut, range.end2 - endCut)

        val expand = Range(range.start1 + startCut, range.end1 - endCut, range.start2 + startCut, range.end2 - endCut)

        if (!expand.isEmpty) {
          changes.add(expand)
        }
      }

      return DiffIterableUtil.create(changes, text1.length, text2.length)
    }
  }

  private class MergeDefaultCorrector(
    private val iterable: List<MergeRange>,
    private val text1: CharSequence,
    private val text2: CharSequence,
    private val text3: CharSequence,
    private val indicator: CancellationChecker,
  ) {
    private val changes = ArrayList<MergeRange>()

    fun build(): List<MergeRange> {
      for (range in iterable) {
        val endCut = expandWhitespacesBackward(text1, text2, text3,
                                               range.start1, range.start2, range.start3,
                                               range.end1, range.end2, range.end3)
        val startCut = expandWhitespacesForward(text1, text2, text3,
                                                range.start1, range.start2, range.start3,
                                                range.end1 - endCut, range.end2 - endCut, range.end3 - endCut)

        val expand = MergeRange(range.start1 + startCut, range.end1 - endCut,
                                range.start2 + startCut, range.end2 - endCut,
                                range.start3 + startCut, range.end3 - endCut)

        if (!expand.isEmpty) {
          changes.add(expand)
        }
      }

      return changes
    }
  }

  private class IgnoreSpacesCorrector(
    private val iterable: DiffIterable,
    private val text1: CharSequence,
    private val text2: CharSequence,
    private val indicator: CancellationChecker,
  ) {
    private val changes = ArrayList<Range>()

    fun build(): DiffIterable {
      for (range in iterable.iterateChanges()) {
        // match spaces if we can, ignore them if we can't
        val expanded = expandWhitespaces(text1, text2, range)
        val trimmed = trim(text1, text2, expanded)

        if (!trimmed.isEmpty &&
            !isEqualsIgnoreWhitespaces(text1, text2, trimmed)
        ) {
          changes.add(trimmed)
        }
      }

      return DiffIterableUtil.create(changes, text1.length, text2.length)
    }
  }

  private class MergeIgnoreSpacesCorrector(
    private val iterable: List<MergeRange>,
    private val text1: CharSequence,
    private val text2: CharSequence,
    private val text3: CharSequence,
    private val indicator: CancellationChecker,
  ) {
    private val changes = ArrayList<MergeRange>()

    fun build(): List<MergeRange> {
      for (range in iterable) {
        val expanded = expandWhitespaces(text1, text2, text3, range)
        val trimmed = trim(text1, text2, text3, expanded)

        if (!trimmed.isEmpty &&
            !isEqualsIgnoreWhitespaces(text1, text2, text3, trimmed)
        ) {
          changes.add(trimmed)
        }
      }

      return changes
    }
  }

  internal class TrimSpacesCorrector(
    private val iterable: DiffIterable,
    private val text1: CharSequence,
    private val text2: CharSequence,
    private val indicator: CancellationChecker,
  ) {
    private val changes = ArrayList<Range>()

    fun build(): DiffIterable {
      for (range in iterable.iterateChanges()) {
        var start1 = range.start1
        var start2 = range.start2
        var end1 = range.end1
        var end2 = range.end2

        if (isLeadingTrailingSpace(text1, start1)) {
          start1 = trimStart(text1, start1, end1)
        }
        if (isLeadingTrailingSpace(text1, end1 - 1)) {
          end1 = trimEnd(text1, start1, end1)
        }
        if (isLeadingTrailingSpace(text2, start2)) {
          start2 = trimStart(text2, start2, end2)
        }
        if (isLeadingTrailingSpace(text2, end2 - 1)) {
          end2 = trimEnd(text2, start2, end2)
        }

        val trimmed = Range(start1, end1, start2, end2)

        if (!trimmed.isEmpty &&
            !isEquals(text1, text2, trimmed)
        ) {
          changes.add(trimmed)
        }
      }

      return DiffIterableUtil.create(changes, text1.length, text2.length)
    }
  }

  private class MergeTrimSpacesCorrector(
    private val iterable: List<MergeRange>,
    private val text1: CharSequence,
    private val text2: CharSequence,
    private val text3: CharSequence,
    private val indicator: CancellationChecker,
  ) {
    private val changes = ArrayList<MergeRange>()

    fun build(): List<MergeRange> {
      for (range in iterable) {
        var start1 = range.start1
        var start2 = range.start2
        var start3 = range.start3
        var end1 = range.end1
        var end2 = range.end2
        var end3 = range.end3

        if (isLeadingTrailingSpace(text1, start1)) {
          start1 = trimStart(text1, start1, end1)
        }
        if (isLeadingTrailingSpace(text1, end1 - 1)) {
          end1 = trimEnd(text1, start1, end1)
        }
        if (isLeadingTrailingSpace(text2, start2)) {
          start2 = trimStart(text2, start2, end2)
        }
        if (isLeadingTrailingSpace(text2, end2 - 1)) {
          end2 = trimEnd(text2, start2, end2)
        }
        if (isLeadingTrailingSpace(text3, start3)) {
          start3 = trimStart(text3, start3, end3)
        }
        if (isLeadingTrailingSpace(text3, end3 - 1)) {
          end3 = trimEnd(text3, start3, end3)
        }

        val trimmed = MergeRange(start1, end1, start2, end2, start3, end3)

        if (!trimmed.isEmpty &&
            !isEquals(text1, text2, text3, trimmed)
        ) {
          changes.add(trimmed)
        }
      }

      return changes
    }
  }

  //
  // Helpers
  //
  interface InlineChunk {
    val offset1: Int

    val offset2: Int
  }

  internal class WordChunk(
    private val text: CharSequence,
    override val offset1: Int,
    override val offset2: Int,
    private val hash: Int,
  ) : InlineChunk {
    val content: CharSequence
      get() = text.subSequence(offset1, offset2)

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false

      val word = other as WordChunk

      if (hash != word.hash) return false

      return ComparisonUtil.isEquals(content, word.content, ComparisonPolicy.DEFAULT)
    }

    override fun hashCode(): Int {
      return hash
    }
  }

  internal class NewlineChunk(override val offset1: Int) : InlineChunk {
    override val offset2: Int
      get() = offset1 + 1

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || javaClass != other.javaClass) return false

      return true
    }

    override fun hashCode(): Int {
      return javaClass.hashCode()
    }
  }

  class LineBlock(
    @JvmField
    val fragments: List<DiffFragment>,
    @JvmField
    val offsets: Range,
    @JvmField
    val newlines1: Int,
    @JvmField
    val newlines2: Int,
  )

  @ApiStatus.Experimental
  class WordLineBlock(
    @JvmField
    val lineRange: Range,
    @JvmField
    val fragments: List<DiffFragment>,
  )
}
