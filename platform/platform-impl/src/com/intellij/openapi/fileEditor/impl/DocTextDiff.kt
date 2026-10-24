// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.diff.comparison.ByCharRt
import com.intellij.diff.comparison.CancellationChecker
import com.intellij.diff.comparison.DiffTooBigException
import com.intellij.diff.comparison.expand
import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.diff.util.Range
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.util.text.CharSequenceSubSequence
import org.jetbrains.annotations.ApiStatus

/**
 * Recovers an edit script from two states of one document.
 *
 * The caller knows the base text and the target text. The caller does not know the edits that
 * produced the target. [diff] returns a list of [DocumentOp.Text] that reproduces the target from
 * the base.
 *
 * The returned script is not the real history. The text cannot record the real history. A user who
 * types a word and then deletes it leaves no trace. The script is one of many that give the same
 * result, so treat it as a reconstruction and not as the truth.
 *
 * The algorithm runs in four steps.
 * 1. It trims the shared prefix and the shared suffix. This is linear, and it finds the one region
 *    that changed.
 * 2. It grows that region to whole lines, so the ops get a line boundary to sit on.
 * 3. It compares the region by line. A line is a coarse anchor, and the alphabet is small.
 * 4. It compares each changed line block by character. The refinement stays local and cheap.
 *
 * A whole document comparison by character is the alternative. It is quadratic in the worst case,
 * and it splits one edit into many small ops. Each op becomes an event that the graph keeps
 * forever, so a small op count matters more here than a small edit distance.
 *
 * Step 3 builds one `String` per line, and that is on purpose. A hand written scan that hashes the
 * characters where they lie and never builds a line looks cheaper. It measures 1.3 to 2.3 times
 * slower. `String.substring`, `String.hashCode` and `String.equals` are JDK intrinsics that the JIT
 * turns into vector code, and a scalar Kotlin loop cannot match them. Do not try it again.
 */
@ApiStatus.Internal
object DocTextDiff {

  /**
   * The ops that turn [base] into [target]. Apply them in the list order.
   *
   * The list is empty when the two texts hold the same characters.
   *
   * The list runs from the last changed region to the first. Every offset then indexes the document
   * that the op applies to, because each later op changes only the text to the right. A replacement
   * becomes a delete and then an insert at the same offset.
   *
   * The line comparison and the character comparison both ask [indicator]. An exception from it
   * leaves this method, so a cancelled caller stops here.
   */
  @JvmStatic
  fun diff(
    base: DocumentText,
    target: DocumentText,
    indicator: CancellationChecker,
  ): List<DocumentOp.Text> {
    // Every phase reads one character at a time. On an ImmutableText that costs a leaf lookup and a
    // virtual call, which measures about twice a String read. luckyChars() hands back the String
    // when the document already holds one, and the rope when it does not, so this never costs more.
    val baseChars = base.luckyChars()
    val targetChars = target.luckyChars()
    val trimmed = expand(baseChars, targetChars, 0, 0, baseChars.length, targetChars.length)
    if (trimmed.isEmpty) {
      return emptyList()
    }
    checkTrimmed(baseChars, targetChars, trimmed)
    val region = snapToLines(baseChars, trimmed) ?: snapToCodePoints(baseChars, targetChars, trimmed)
    val fragments = fragments(baseChars, targetChars, region, indicator)
    return ops(targetChars, fragments)
  }

  /**
   * Fails when [region] did not come from a shared prefix and a shared suffix of the two texts.
   */
  private fun checkTrimmed(baseChars: CharSequence, targetChars: CharSequence, region: Range) {
    require(region.start1 == region.start2) {
      "the trimmed prefix is shared, so the two starts must agree: $region"
    }
    require(baseChars.length - region.end1 == targetChars.length - region.end2) {
      "the trimmed suffix is shared, so the two tails must agree: $region"
    }
  }

  /**
   * The changed parts of [region], in the ascending offset order. A part never overlaps its neighbour,
   * and a part is never empty on both sides.
   */
  private fun fragments(
    baseChars: CharSequence,
    targetChars: CharSequence,
    region: Range,
    indicator: CancellationChecker,
  ): List<Range> {
    val baseLines = split(baseChars, region.start1, region.end1)
    val targetLines = split(targetChars, region.start2, region.end2)
    val lineChanges = try {
      DiffIterableUtil.diff(baseLines.texts, targetLines.texts, indicator)
    }
    catch (@Suppress("IncorrectCancellationExceptionHandling") _: DiffTooBigException) {
      // The comparison gives up on a large input. That is a size limit and not a cancellation, so
      // the region stays coarse.
      return listOf(region)
    }
    val fragments = ArrayList<Range>()
    for (lineChange in lineChanges.iterateChanges()) {
      addRefined(
        baseChars, targetChars,
        baseLines.offset(lineChange.start1), baseLines.offset(lineChange.end1),
        targetLines.offset(lineChange.start2), targetLines.offset(lineChange.end2),
        fragments,
        indicator,
      )
    }
    return fragments
  }

  /**
   * Splits one changed line block into character level parts, and adds them to [fragments].
   */
  private fun addRefined(
    baseChars: CharSequence,
    targetChars: CharSequence,
    start1: Int,
    end1: Int,
    start2: Int,
    end2: Int,
    fragments: MutableList<Range>,
    indicator: CancellationChecker,
  ) {
    if (start1 == end1 || start2 == end2) {
      // A pure insert or a pure delete has nothing to align.
      fragments.add(Range(start1, end1, start2, end2))
      return
    }
    val charChanges = try {
      ByCharRt.compare(
        CharSequenceSubSequence(baseChars, start1, end1),
        CharSequenceSubSequence(targetChars, start2, end2),
        indicator,
      )
    }
    catch (@Suppress("IncorrectCancellationExceptionHandling") _: DiffTooBigException) {
      // A size limit, as in fragments(), so the block stays coarse.
      fragments.add(Range(start1, end1, start2, end2))
      return
    }
    for (charChange in charChanges.iterateChanges()) {
      fragments.add(
        Range(
          start1 + charChange.start1, start1 + charChange.end1,
          start2 + charChange.start2, start2 + charChange.end2,
        )
      )
    }
  }

  /**
   * The ops for [fragments], emitted from the last fragment to the first.
   *
   * The right to left order keeps every offset valid without any arithmetic. When an op runs, the
   * document still holds the base text at and before that offset. Only the part to the right changed.
   */
  private fun ops(targetChars: CharSequence, fragments: List<Range>): List<DocumentOp.Text> {
    val ops = ArrayList<DocumentOp.Text>(2 * fragments.size)
    for (index in fragments.indices.reversed()) {
      val fragment = fragments[index]
      if (fragment.start1 < fragment.end1) {
        ops.add(DocumentOp.deleteOp(fragment.start1, fragment.end1 - fragment.start1))
      }
      if (fragment.start2 < fragment.end2) {
        // The delete already ran, so the insert offset is still the start of the fragment.
        val inserted = targetChars.subSequence(fragment.start2, fragment.end2).toString()
        ops.add(DocumentOp.insertOp(fragment.start1, inserted))
      }
    }
    return ops
  }

  /**
   * [region] grown to whole lines, or `null` when the growth is too wide.
   *
   * [expand] trims character by character, so the region rarely starts at a line start. A new line
   * then looks like an edit that starts in the middle of the line above it. The comparison still
   * gives the right text, but it anchors the op at the wrong place. The document "one, two, three"
   * that gains the line "two and a half" shows the effect. Without the growth the op inserts
   * `"wo and a half\nt"` after `"...two\nt"`. With it the op inserts the whole new line at the start
   * of the line "three".
   *
   * The anchor is invisible in the text and visible after a merge. A concurrent edit on the line
   * "three" fights an op that starts inside that line, and it ignores an op that ends before it.
   *
   * The growth costs at most one line on each side. A document with no line feed is the bad case,
   * because the only "line" is the whole text. [MAX_LINE_GROWTH] gives up there and leaves the
   * character region alone.
   */
  private fun snapToLines(baseChars: CharSequence, region: Range): Range? {
    val back = region.start1 - lineStart(baseChars, region.start1)
    val forward = nextLineStart(baseChars, region.end1) - region.end1
    if (back + forward > MAX_LINE_GROWTH) {
      return null
    }
    // The trimmed prefix and the trimmed suffix are shared, so one distance serves both sides.
    return Range(region.start1 - back, region.end1 + forward, region.start2 - back, region.end2 + forward)
  }

  /**
   * The start of the line that holds [offset].
   */
  private fun lineStart(chars: CharSequence, offset: Int): Int {
    var start = offset
    while (start > 0 && chars[start - 1] != '\n') {
      start--
    }
    return start
  }

  /**
   * The start of the line after [offset], or the end of [chars] when no line follows.
   */
  private fun nextLineStart(chars: CharSequence, offset: Int): Int {
    var end = offset
    while (end < chars.length && chars[end] != '\n') {
      end++
    }
    return if (end < chars.length) end + 1 else end
  }

  /**
   * [region] with every boundary moved off a surrogate pair.
   *
   * This is the fallback for a document that [snapToLines] gave up on. [expand] trims character by
   * character, so a boundary can land between the two halves of one code point. An op that starts
   * there inserts or deletes half a character. The text still round trips, but the event graph keeps
   * that op forever. The snap widens the region by at most one character on each side.
   */
  private fun snapToCodePoints(baseChars: CharSequence, targetChars: CharSequence, region: Range): Range {
    var start1 = region.start1
    var start2 = region.start2
    var end1 = region.end1
    var end2 = region.end2
    // The trimmed prefix is shared, so one step moves both sides off the same pair.
    if (splitsPair(baseChars, start1) || splitsPair(targetChars, start2)) {
      start1--
      start2--
    }
    // The trimmed suffix is shared, so one step moves both sides off the same pair.
    if (splitsPair(baseChars, end1) || splitsPair(targetChars, end2)) {
      end1++
      end2++
    }
    return Range(start1, end1, start2, end2)
  }

  /**
   * True when [offset] falls between the two halves of one code point of [chars].
   */
  private fun splitsPair(chars: CharSequence, offset: Int): Boolean {
    return offset > 0 &&
           offset < chars.length &&
           Character.isHighSurrogate(chars[offset - 1]) &&
           Character.isLowSurrogate(chars[offset])
  }

  /**
   * The `[start, end)` part of [chars], cut after every line feed.
   *
   * A line keeps its separator, so the parts concatenate back to the whole region. [snapToLines]
   * puts the boundaries on whole lines. When it gives up, the first part and the last part can be
   * partial lines. That costs nothing: the comparison aligns the parts by equality.
   */
  private fun split(chars: CharSequence, start: Int, end: Int): Lines {
    val texts = ArrayList<String>()
    var lineStart = start
    for (offset in start until end) {
      if (chars[offset] != '\n') {
        continue
      }
      texts.add(chars.subSequence(lineStart, offset + 1).toString())
      lineStart = offset + 1
    }
    if (lineStart < end) {
      texts.add(chars.subSequence(lineStart, end).toString())
    }
    return Lines(start, texts.toTypedArray())
  }

  /**
   * The lines of one region, plus the document offset of every line.
   *
   * [texts] feeds the line comparison. [offset] maps a line index back to a document offset, so a
   * changed line block becomes a character range.
   */
  private class Lines(start: Int, val texts: Array<String>) {
    private val offsets: IntArray = IntArray(texts.size + 1).also { offsets ->
      var offset = start
      offsets[0] = offset
      for (index in texts.indices) {
        offset += texts[index].length
        offsets[index + 1] = offset
      }
    }

    /**
     * The document offset of the line [line]. The line count itself maps to the end of the region.
     */
    fun offset(line: Int): Int = offsets[line]
  }

  /**
   * The widest growth that [snapToLines] accepts, in characters.
   *
   * A source file keeps a line far below this number, so the growth always runs there. A minified
   * file holds the whole text on one line, and the growth would turn a small region into the whole
   * document. The limit protects that case.
   */
  private const val MAX_LINE_GROWTH = 4096
}
