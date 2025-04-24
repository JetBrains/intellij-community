// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util

import com.intellij.diff.comparison.ComparisonMergeUtil
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.comparison.ComparisonUtil.isEqualTexts
import com.intellij.diff.fragments.MergeLineFragment
import com.intellij.diff.fragments.MergeWordFragment
import com.intellij.diff.tools.util.text.LineOffsets
import com.intellij.diff.util.DiffRangeUtil.getLinesContent
import java.util.function.BiPredicate
import java.util.function.BooleanSupplier
import java.util.function.Predicate

object MergeRangeUtil {
  @JvmStatic
  fun getMergeType(
    emptiness: Predicate<in ThreeSide>,
    equality: BiPredicate<in ThreeSide, in ThreeSide>,
    trueEquality: BiPredicate<in ThreeSide, in ThreeSide>?,
    conflictResolver: BooleanSupplier
  ): MergeConflictType {
    val isLeftEmpty = emptiness.test(ThreeSide.LEFT)
    val isBaseEmpty = emptiness.test(ThreeSide.BASE)
    val isRightEmpty = emptiness.test(ThreeSide.RIGHT)
    assert(!isLeftEmpty || !isBaseEmpty || !isRightEmpty)

    if (isBaseEmpty) {
      if (isLeftEmpty) { // --=
        return MergeConflictType(MergeConflictType.Type.INSERTED, false, true)
      }
      else if (isRightEmpty) { // =--
        return MergeConflictType(MergeConflictType.Type.INSERTED, true, false)
      }
      else { // =-=
        val equalModifications = equality.test(ThreeSide.LEFT, ThreeSide.RIGHT)
        if (equalModifications) {
          return MergeConflictType(MergeConflictType.Type.INSERTED, true, true)
        }
        else {
          return MergeConflictType(MergeConflictType.Type.CONFLICT, true, true, null)
        }
      }
    }
    else {
      if (isLeftEmpty && isRightEmpty) { // -=-
        return MergeConflictType(MergeConflictType.Type.DELETED, true, true)
      }
      else { // -==, ==-, ===
        val unchangedLeft = equality.test(ThreeSide.BASE, ThreeSide.LEFT)
        val unchangedRight = equality.test(ThreeSide.BASE, ThreeSide.RIGHT)

        if (unchangedLeft && unchangedRight) {
          checkNotNull(trueEquality)
          val trueUnchangedLeft = trueEquality.test(ThreeSide.BASE, ThreeSide.LEFT)
          val trueUnchangedRight = trueEquality.test(ThreeSide.BASE, ThreeSide.RIGHT)
          assert(!trueUnchangedLeft || !trueUnchangedRight)
          return MergeConflictType(MergeConflictType.Type.MODIFIED, !trueUnchangedLeft, !trueUnchangedRight)
        }

        if (unchangedLeft) return MergeConflictType(if (isRightEmpty) MergeConflictType.Type.DELETED else MergeConflictType.Type.MODIFIED,
                                                    false, true)
        if (unchangedRight) return MergeConflictType(if (isLeftEmpty) MergeConflictType.Type.DELETED else MergeConflictType.Type.MODIFIED,
                                                     true, false)

        val equalModifications = equality.test(ThreeSide.LEFT, ThreeSide.RIGHT)
        if (equalModifications) {
          return MergeConflictType(MergeConflictType.Type.MODIFIED, true, true)
        }
        else {
          val canBeResolved = !isLeftEmpty && !isRightEmpty && conflictResolver.asBoolean
          return MergeConflictType(MergeConflictType.Type.CONFLICT, true, true, if (canBeResolved) MergeConflictResolutionStrategy.TEXT else null)
        }
      }
    }
  }

  @JvmStatic
  fun getLineThreeWayDiffType(
    fragment: MergeLineFragment,
    sequences: List<CharSequence>,
    lineOffsets: List<LineOffsets>,
    policy: ComparisonPolicy
  ): MergeConflictType {
    return getMergeType(Predicate { side -> isLineMergeIntervalEmpty(fragment, side) },
                        BiPredicate { side1, side2 ->
                          compareLineMergeContents(fragment, sequences, lineOffsets, policy, side1, side2)
                        },
                        null,
                        BooleanSupplier { canResolveLineConflict(fragment, sequences, lineOffsets) })
  }

  @JvmStatic
  fun getLineMergeType(
    fragment: MergeLineFragment,
    sequences: List<CharSequence>,
    lineOffsets: List<LineOffsets>,
    policy: ComparisonPolicy
  ): MergeConflictType {
    return getMergeType(Predicate { side -> isLineMergeIntervalEmpty(fragment, side) },
                        BiPredicate { side1, side2 ->
                          compareLineMergeContents(fragment, sequences, lineOffsets, policy, side1, side2)
                        },
                        BiPredicate { side1, side2 ->
                          compareLineMergeContents(fragment, sequences, lineOffsets, ComparisonPolicy.DEFAULT, side1,
                                                                  side2)
                        },
                        BooleanSupplier { canResolveLineConflict(fragment, sequences, lineOffsets) })
  }

  private fun canResolveLineConflict(
    fragment: MergeLineFragment,
    sequences: List<CharSequence>,
    lineOffsets: List<LineOffsets>
  ): Boolean {
    val contents = ThreeSide.map { side ->
      getLinesContent(side.selectNotNull(sequences), side.selectNotNull(lineOffsets),
                      fragment.getStartLine(side), fragment.getEndLine(side))
    }
    return ComparisonMergeUtil.tryResolveConflict(contents[0], contents[1], contents[2]) != null
  }

  private fun compareLineMergeContents(
    fragment: MergeLineFragment,
    sequences: List<CharSequence>,
    lineOffsets: List<LineOffsets>,
    policy: ComparisonPolicy,
    side1: ThreeSide,
    side2: ThreeSide
  ): Boolean {
    val start1 = fragment.getStartLine(side1)
    val end1 = fragment.getEndLine(side1)
    val start2 = fragment.getStartLine(side2)
    val end2 = fragment.getEndLine(side2)

    if (end2 - start2 != end1 - start1) return false

    val sequence1 = side1.selectNotNull(sequences)
    val sequence2 = side2.selectNotNull(sequences)
    val offsets1 = side1.selectNotNull(lineOffsets)
    val offsets2 = side2.selectNotNull(lineOffsets)

    for (i in 0..<end1 - start1) {
      val line1 = start1 + i
      val line2 = start2 + i

      val content1 = getLinesContent(sequence1, offsets1, line1, line1 + 1)
      val content2 = getLinesContent(sequence2, offsets2, line2, line2 + 1)
      if (!isEqualTexts(content1, content2, policy)) return false
    }

    return true
  }

  private fun isLineMergeIntervalEmpty(fragment: MergeLineFragment, side: ThreeSide): Boolean {
    return fragment.getStartLine(side) == fragment.getEndLine(side)
  }

  @JvmStatic
  fun getWordMergeType(
    fragment: MergeWordFragment,
    texts: List<CharSequence>,
    policy: ComparisonPolicy
  ): MergeConflictType {
    return getMergeType(Predicate { side -> isWordMergeIntervalEmpty(fragment, side) },
                        BiPredicate { side1, side2 ->
                          compareWordMergeContents(fragment, texts, policy, side1, side2)
                        },
                        null,
                        BooleanSupplier { false })
  }

  @JvmStatic
  fun compareWordMergeContents(
    fragment: MergeWordFragment,
    texts: List<CharSequence>,
    policy: ComparisonPolicy,
    side1: ThreeSide,
    side2: ThreeSide
  ): Boolean {
    val start1 = fragment.getStartOffset(side1)
    val end1 = fragment.getEndOffset(side1)
    val start2 = fragment.getStartOffset(side2)
    val end2 = fragment.getEndOffset(side2)

    val document1 = side1.selectNotNull(texts)
    val document2 = side2.selectNotNull(texts)

    val content1 = document1.subSequence(start1, end1)
    val content2 = document2.subSequence(start2, end2)
    return isEqualTexts(content1, content2, policy)
  }

  private fun isWordMergeIntervalEmpty(fragment: MergeWordFragment, side: ThreeSide): Boolean {
    return fragment.getStartOffset(side) == fragment.getEndOffset(side)
  }

  @JvmStatic
  fun getLineLeftToRightThreeSideDiffType(
    fragment: MergeLineFragment,
    sequences: List<CharSequence>,
    lineOffsets: List<LineOffsets>,
    policy: ComparisonPolicy
  ): MergeConflictType {
    return getLeftToRightDiffType(Predicate { side -> isLineMergeIntervalEmpty(fragment, side) },
                                  BiPredicate { side1, side2 ->
                                    compareLineMergeContents(fragment, sequences, lineOffsets, policy, side1, side2)
                                  })
  }

  private fun getLeftToRightDiffType(
    emptiness: Predicate<ThreeSide>,
    equality: BiPredicate<ThreeSide, ThreeSide>
  ): MergeConflictType {
    val isLeftEmpty = emptiness.test(ThreeSide.LEFT)
    val isBaseEmpty = emptiness.test(ThreeSide.BASE)
    val isRightEmpty = emptiness.test(ThreeSide.RIGHT)
    assert(!isLeftEmpty || !isBaseEmpty || !isRightEmpty)

    if (isBaseEmpty) {
      if (isLeftEmpty) { // --=
        return MergeConflictType(MergeConflictType.Type.INSERTED, false, true)
      }
      else if (isRightEmpty) { // =--
        return MergeConflictType(MergeConflictType.Type.DELETED, true, false)
      }
      else { // =-=
        return MergeConflictType(MergeConflictType.Type.MODIFIED, true, true)
      }
    }
    else {
      if (isLeftEmpty && isRightEmpty) { // -=-
        return MergeConflictType(MergeConflictType.Type.MODIFIED, true, true)
      }
      else { // -==, ==-, ===
        val unchangedLeft = equality.test(ThreeSide.BASE, ThreeSide.LEFT)
        val unchangedRight = equality.test(ThreeSide.BASE, ThreeSide.RIGHT)
        assert(!unchangedLeft || !unchangedRight)

        if (unchangedLeft) {
          return MergeConflictType(if (isRightEmpty) MergeConflictType.Type.DELETED else MergeConflictType.Type.MODIFIED, false, true)
        }
        if (unchangedRight) {
          return MergeConflictType(if (isLeftEmpty) MergeConflictType.Type.INSERTED else MergeConflictType.Type.MODIFIED, true, false)
        }

        return MergeConflictType(MergeConflictType.Type.MODIFIED, true, true)
      }
    }
  }
}
