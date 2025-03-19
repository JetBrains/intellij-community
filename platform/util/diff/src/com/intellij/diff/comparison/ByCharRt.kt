// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison

import com.intellij.diff.comparison.ByWordRt.TrimSpacesCorrector
import com.intellij.diff.comparison.ChangeCorrector.DefaultCharChangeCorrector
import com.intellij.diff.comparison.iterables.DiffIterable
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.comparison.iterables.FairDiffIterable
import com.intellij.diff.util.Range
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList

object ByCharRt {
  @JvmStatic
  fun compare(
    text1: CharSequence,
    text2: CharSequence,
    indicator: CancellationChecker,
  ): FairDiffIterable {
    indicator.checkCanceled()

    val codePoints1 = getAllCodePoints(text1)
    val codePoints2 = getAllCodePoints(text2)

    val iterable = DiffIterableUtil.diff(codePoints1, codePoints2, indicator)

    var offset1 = 0
    var offset2 = 0
    val builder = DiffIterableUtil.ChangeBuilder(text1.length, text2.length)
    for (pair in DiffIterableUtil.iterateAll(iterable)) {
      val range = pair.first
      val equals = pair.second

      val end1 = offset1 + countChars(codePoints1, range.start1, range.end1)
      val end2 = offset2 + countChars(codePoints2, range.start2, range.end2)

      if (equals) {
        builder.markEqual(offset1, offset2, end1, end2)
      }

      offset1 = end1
      offset2 = end2
    }
    assert(offset1 == text1.length)
    assert(offset2 == text2.length)

    return DiffIterableUtil.fair(builder.finish())
  }

  @JvmStatic
  fun compareTwoStep(
    text1: CharSequence,
    text2: CharSequence,
    indicator: CancellationChecker,
  ): FairDiffIterable {
    indicator.checkCanceled()

    val codePoints1 = getNonSpaceCodePoints(text1)
    val codePoints2 = getNonSpaceCodePoints(text2)

    val nonSpaceChanges = DiffIterableUtil.diff(codePoints1.codePoints, codePoints2.codePoints, indicator)
    return matchAdjustmentSpaces(codePoints1, codePoints2, text1, text2, nonSpaceChanges, indicator)
  }

  @JvmStatic
  fun compareTrimWhitespaces(
    text1: CharSequence,
    text2: CharSequence,
    indicator: CancellationChecker,
  ): DiffIterable {
    val iterable = compareTwoStep(text1, text2, indicator)
    return TrimSpacesCorrector(iterable, text1, text2, indicator).build()
  }

  @JvmStatic
  fun compareIgnoreWhitespaces(
    text1: CharSequence,
    text2: CharSequence,
    indicator: CancellationChecker,
  ): DiffIterable {
    indicator.checkCanceled()

    val codePoints1 = getNonSpaceCodePoints(text1)
    val codePoints2 = getNonSpaceCodePoints(text2)

    val changes = DiffIterableUtil.diff(codePoints1.codePoints, codePoints2.codePoints, indicator)
    return matchAdjustmentSpacesIW(codePoints1, codePoints2, text1, text2, changes)
  }

  /*
   * Compare punctuation chars only, all other characters are left unmatched
   */
  @JvmStatic
  fun comparePunctuation(
    text1: CharSequence,
    text2: CharSequence,
    indicator: CancellationChecker,
  ): FairDiffIterable {
    indicator.checkCanceled()

    val chars1 = getPunctuationChars(text1)
    val chars2 = getPunctuationChars(text2)

    val nonSpaceChanges = DiffIterableUtil.diff(chars1.codePoints, chars2.codePoints, indicator)
    return transferPunctuation(chars1, chars2, text1, text2, nonSpaceChanges, indicator)
  }

  //
  // Impl
  //

  private fun transferPunctuation(
    chars1: CodePointsOffsets,
    chars2: CodePointsOffsets,
    text1: CharSequence,
    text2: CharSequence,
    changes: FairDiffIterable,
    indicator: CancellationChecker,
  ): FairDiffIterable {
    val builder = DiffIterableUtil.ChangeBuilder(text1.length, text2.length)

    for (range in changes.iterateUnchanged()) {
      val count = range.end1 - range.start1
      for (i in 0 until count) {
        // Punctuation code points are always 1 char
        val offset1 = chars1.offsets[range.start1 + i]
        val offset2 = chars2.offsets[range.start2 + i]
        builder.markEqual(offset1, offset2)
      }
    }

    return DiffIterableUtil.fair(builder.finish())
  }

  /*
   * Given DiffIterable on non-space characters, convert it into DiffIterable on original texts.
   *
   * Idea: run fair diff on all gaps between matched characters
   * (inside these pairs could met non-space characters, but they will be unique and can't be matched)
   */
  private fun matchAdjustmentSpaces(
    codePoints1: CodePointsOffsets,
    codePoints2: CodePointsOffsets,
    text1: CharSequence,
    text2: CharSequence,
    changes: FairDiffIterable,
    indicator: CancellationChecker,
  ): FairDiffIterable {
    return DefaultCharChangeCorrector(codePoints1, codePoints2, text1, text2, changes, indicator).build()
  }

  /*
   * Given DiffIterable on non-whitespace characters, convert it into DiffIterable on original texts.
   *
   * matched characters: matched non-space characters + all adjustment whitespaces
   */
  private fun matchAdjustmentSpacesIW(
    codePoints1: CodePointsOffsets,
    codePoints2: CodePointsOffsets,
    text1: CharSequence,
    text2: CharSequence,
    changes: FairDiffIterable,
  ): DiffIterable {
    val ranges: MutableList<Range> = ArrayList()

    for (ch in changes.iterateChanges()) {
      var startOffset1: Int
      var endOffset1: Int
      if (ch.start1 == ch.end1) {
        endOffset1 = expandForwardW(codePoints1, codePoints2, text1, text2, ch, true)
        startOffset1 = endOffset1
      }
      else {
        startOffset1 = codePoints1.charOffset(ch.start1)
        endOffset1 = codePoints1.charOffsetAfter(ch.end1 - 1)
      }

      var startOffset2: Int
      var endOffset2: Int
      if (ch.start2 == ch.end2) {
        endOffset2 = expandForwardW(codePoints1, codePoints2, text1, text2, ch, false)
        startOffset2 = endOffset2
      }
      else {
        startOffset2 = codePoints2.charOffset(ch.start2)
        endOffset2 = codePoints2.charOffsetAfter(ch.end2 - 1)
      }

      ranges.add(Range(startOffset1, endOffset1, startOffset2, endOffset2))
    }
    return DiffIterableUtil.create(ranges, text1.length, text2.length)
  }

  /*
   * we need it to correct place of insertion/deletion: we want to match whitespaces, if we can to
   *
   * sample: "x y" -> "x zy", space should be matched instead of being ignored.
   */
  private fun expandForwardW(
    codePoints1: CodePointsOffsets,
    codePoints2: CodePointsOffsets,
    text1: CharSequence,
    text2: CharSequence,
    ch: Range,
    left: Boolean,
  ): Int {
    val offset1 = if (ch.start1 == 0) 0 else codePoints1.charOffsetAfter(ch.start1 - 1)
    val offset2 = if (ch.start2 == 0) 0 else codePoints2.charOffsetAfter(ch.start2 - 1)

    val start = if (left) offset1 else offset2

    return start + expandWhitespacesForward(text1, text2, offset1, offset2, text1.length, text2.length)
  }

  //
  // Misc
  //
  private fun getAllCodePoints(text: CharSequence): IntArray {
    val list: IntList = IntArrayList(text.length)

    val len = text.length
    var offset = 0

    while (offset < len) {
      val ch = Character.codePointAt(text, offset)
      val charCount = Character.charCount(ch)

      list.add(ch)

      offset += charCount
    }

    return list.toIntArray()
  }

  private fun getNonSpaceCodePoints(text: CharSequence): CodePointsOffsets {
    val codePoints: IntList = IntArrayList(text.length)
    val offsets: IntList = IntArrayList(text.length)

    val len = text.length
    var offset = 0

    while (offset < len) {
      val ch = Character.codePointAt(text, offset)
      val charCount = Character.charCount(ch)

      if (!isWhiteSpaceCodePoint(ch)) {
        codePoints.add(ch)
        offsets.add(offset)
      }

      offset += charCount
    }

    return CodePointsOffsets(codePoints.toIntArray(), offsets.toIntArray())
  }

  private fun getPunctuationChars(text: CharSequence): CodePointsOffsets {
    val codePoints: IntList = IntArrayList(text.length)
    val offsets: IntList = IntArrayList(text.length)

    for (i in text.indices) {
      val c = text[i]
      if (isPunctuation(c)) {
        codePoints.add(c.code)
        offsets.add(i)
      }
    }

    return CodePointsOffsets(codePoints.toIntArray(), offsets.toIntArray())
  }

  private fun countChars(codePoints: IntArray, start: Int, end: Int): Int {
    var count = 0
    for (i in start until end) {
      count += Character.charCount(codePoints[i])
    }
    return count
  }

  internal class CodePointsOffsets(val codePoints: IntArray, val offsets: IntArray) {
    fun charOffset(index: Int): Int {
      return offsets[index]
    }

    fun charOffsetAfter(index: Int): Int {
      return offsets[index] + Character.charCount(codePoints[index])
    }
  }
}
