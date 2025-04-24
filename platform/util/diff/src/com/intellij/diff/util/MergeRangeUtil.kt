// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import com.intellij.diff.comparison.ComparisonMergeUtil;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.ComparisonUtil;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.fragments.MergeWordFragment;
import com.intellij.diff.tools.util.text.LineOffsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static com.intellij.diff.util.DiffRangeUtil.getLinesContent;

public final class MergeRangeUtil {

  private MergeRangeUtil() { }

  public static @NotNull MergeConflictType getMergeType(@NotNull Predicate<? super ThreeSide> emptiness,
                                                        @NotNull BiPredicate<? super ThreeSide, ? super ThreeSide> equality,
                                                        @Nullable BiPredicate<? super ThreeSide, ? super ThreeSide> trueEquality,
                                                        @NotNull BooleanSupplier conflictResolver) {
    boolean isLeftEmpty = emptiness.test(ThreeSide.LEFT);
    boolean isBaseEmpty = emptiness.test(ThreeSide.BASE);
    boolean isRightEmpty = emptiness.test(ThreeSide.RIGHT);
    assert !isLeftEmpty || !isBaseEmpty || !isRightEmpty;

    if (isBaseEmpty) {
      if (isLeftEmpty) { // --=
        return new MergeConflictType(MergeConflictType.Type.INSERTED, false, true);
      }
      else if (isRightEmpty) { // =--
        return new MergeConflictType(MergeConflictType.Type.INSERTED, true, false);
      }
      else { // =-=
        boolean equalModifications = equality.test(ThreeSide.LEFT, ThreeSide.RIGHT);
        if (equalModifications) {
          return new MergeConflictType(MergeConflictType.Type.INSERTED, true, true);
        }
        else {
          return new MergeConflictType(MergeConflictType.Type.CONFLICT, true, true, null);
        }
      }
    }
    else {
      if (isLeftEmpty && isRightEmpty) { // -=-
        return new MergeConflictType(MergeConflictType.Type.DELETED, true, true);
      }
      else { // -==, ==-, ===
        boolean unchangedLeft = equality.test(ThreeSide.BASE, ThreeSide.LEFT);
        boolean unchangedRight = equality.test(ThreeSide.BASE, ThreeSide.RIGHT);

        if (unchangedLeft && unchangedRight) {
          assert trueEquality != null;
          boolean trueUnchangedLeft = trueEquality.test(ThreeSide.BASE, ThreeSide.LEFT);
          boolean trueUnchangedRight = trueEquality.test(ThreeSide.BASE, ThreeSide.RIGHT);
          assert !trueUnchangedLeft || !trueUnchangedRight;
          return new MergeConflictType(MergeConflictType.Type.MODIFIED, !trueUnchangedLeft, !trueUnchangedRight);
        }

        if (unchangedLeft) return new MergeConflictType(isRightEmpty ? MergeConflictType.Type.DELETED : MergeConflictType.Type.MODIFIED, false, true);
        if (unchangedRight) return new MergeConflictType(isLeftEmpty ? MergeConflictType.Type.DELETED : MergeConflictType.Type.MODIFIED, true, false);

        boolean equalModifications = equality.test(ThreeSide.LEFT, ThreeSide.RIGHT);
        if (equalModifications) {
          return new MergeConflictType(MergeConflictType.Type.MODIFIED, true, true);
        }
        else {
          boolean canBeResolved = !isLeftEmpty && !isRightEmpty && conflictResolver.getAsBoolean();
          return new MergeConflictType(MergeConflictType.Type.CONFLICT, true, true, canBeResolved ? MergeConflictResolutionStrategy.TEXT : null);
        }
      }
    }
  }

  public static @NotNull MergeConflictType getLineThreeWayDiffType(@NotNull MergeLineFragment fragment,
                                                                   @NotNull List<? extends CharSequence> sequences,
                                                                   @NotNull List<? extends LineOffsets> lineOffsets,
                                                                   @NotNull ComparisonPolicy policy) {
    return getMergeType((side) -> isLineMergeIntervalEmpty(fragment, side),
                        (side1, side2) -> compareLineMergeContents(fragment, sequences, lineOffsets, policy, side1, side2),
                        null,
                        () -> canResolveLineConflict(fragment, sequences, lineOffsets));
  }

  public static @NotNull MergeConflictType getLineMergeType(@NotNull MergeLineFragment fragment,
                                                            @NotNull List<? extends CharSequence> sequences,
                                                            @NotNull List<? extends LineOffsets> lineOffsets,
                                                            @NotNull ComparisonPolicy policy) {
    return getMergeType((side) -> isLineMergeIntervalEmpty(fragment, side),
                        (side1, side2) -> compareLineMergeContents(fragment, sequences, lineOffsets, policy, side1, side2),
                        (side1, side2) -> compareLineMergeContents(fragment, sequences, lineOffsets, ComparisonPolicy.DEFAULT, side1, side2),
                        () -> canResolveLineConflict(fragment, sequences, lineOffsets));
  }

  private static boolean canResolveLineConflict(@NotNull MergeLineFragment fragment,
                                                @NotNull List<? extends CharSequence> sequences,
                                                @NotNull List<? extends LineOffsets> lineOffsets) {
    List<? extends CharSequence> contents = ThreeSide.map(side -> getLinesContent(side.select(sequences), side.select(lineOffsets), fragment.getStartLine(side), fragment.getEndLine(side)));
    return ComparisonMergeUtil.tryResolveConflict(contents.get(0), contents.get(1), contents.get(2)) != null;
  }

  private static boolean compareLineMergeContents(@NotNull MergeLineFragment fragment,
                                                  @NotNull List<? extends CharSequence> sequences,
                                                  @NotNull List<? extends LineOffsets> lineOffsets,
                                                  @NotNull ComparisonPolicy policy,
                                                  @NotNull ThreeSide side1,
                                                  @NotNull ThreeSide side2) {
    int start1 = fragment.getStartLine(side1);
    int end1 = fragment.getEndLine(side1);
    int start2 = fragment.getStartLine(side2);
    int end2 = fragment.getEndLine(side2);

    if (end2 - start2 != end1 - start1) return false;

    CharSequence sequence1 = side1.select(sequences);
    CharSequence sequence2 = side2.select(sequences);
    LineOffsets offsets1 = side1.select(lineOffsets);
    LineOffsets offsets2 = side2.select(lineOffsets);

    for (int i = 0; i < end1 - start1; i++) {
      int line1 = start1 + i;
      int line2 = start2 + i;

      CharSequence content1 = getLinesContent(sequence1, offsets1, line1, line1 + 1);
      CharSequence content2 = getLinesContent(sequence2, offsets2, line2, line2 + 1);
      if (!ComparisonUtil.isEqualTexts(content1, content2, policy)) return false;
    }

    return true;
  }

  private static boolean isLineMergeIntervalEmpty(@NotNull MergeLineFragment fragment, @NotNull ThreeSide side) {
    return fragment.getStartLine(side) == fragment.getEndLine(side);
  }

  public static @NotNull MergeConflictType getWordMergeType(@NotNull MergeWordFragment fragment,
                                                            @NotNull List<? extends CharSequence> texts,
                                                            @NotNull ComparisonPolicy policy) {
    return getMergeType((side) -> isWordMergeIntervalEmpty(fragment, side),
                        (side1, side2) -> compareWordMergeContents(fragment, texts, policy, side1, side2),
                        null,
                        () -> false);
  }

  public static boolean compareWordMergeContents(@NotNull MergeWordFragment fragment,
                                                 @NotNull List<? extends CharSequence> texts,
                                                 @NotNull ComparisonPolicy policy,
                                                 @NotNull ThreeSide side1,
                                                 @NotNull ThreeSide side2) {
    int start1 = fragment.getStartOffset(side1);
    int end1 = fragment.getEndOffset(side1);
    int start2 = fragment.getStartOffset(side2);
    int end2 = fragment.getEndOffset(side2);

    CharSequence document1 = side1.select(texts);
    CharSequence document2 = side2.select(texts);

    CharSequence content1 = document1.subSequence(start1, end1);
    CharSequence content2 = document2.subSequence(start2, end2);
    return ComparisonUtil.isEqualTexts(content1, content2, policy);
  }

  private static boolean isWordMergeIntervalEmpty(@NotNull MergeWordFragment fragment, @NotNull ThreeSide side) {
    return fragment.getStartOffset(side) == fragment.getEndOffset(side);
  }

  public static @NotNull MergeConflictType getLineLeftToRightThreeSideDiffType(@NotNull MergeLineFragment fragment,
                                                                               @NotNull List<? extends CharSequence> sequences,
                                                                               @NotNull List<? extends LineOffsets> lineOffsets,
                                                                               @NotNull ComparisonPolicy policy) {
    return getLeftToRightDiffType((side) -> isLineMergeIntervalEmpty(fragment, side),
                                  (side1, side2) -> compareLineMergeContents(fragment, sequences, lineOffsets, policy, side1, side2));
  }

  private static @NotNull MergeConflictType getLeftToRightDiffType(@NotNull Predicate<? super ThreeSide> emptiness,
                                                                   @NotNull BiPredicate<? super ThreeSide, ? super ThreeSide> equality) {
    boolean isLeftEmpty = emptiness.test(ThreeSide.LEFT);
    boolean isBaseEmpty = emptiness.test(ThreeSide.BASE);
    boolean isRightEmpty = emptiness.test(ThreeSide.RIGHT);
    assert !isLeftEmpty || !isBaseEmpty || !isRightEmpty;

    if (isBaseEmpty) {
      if (isLeftEmpty) { // --=
        return new MergeConflictType(MergeConflictType.Type.INSERTED, false, true);
      }
      else if (isRightEmpty) { // =--
        return new MergeConflictType(MergeConflictType.Type.DELETED, true, false);
      }
      else { // =-=
        return new MergeConflictType(MergeConflictType.Type.MODIFIED, true, true);
      }
    }
    else {
      //noinspection IfStatementWithIdenticalBranches
      if (isLeftEmpty && isRightEmpty) { // -=-
        return new MergeConflictType(MergeConflictType.Type.MODIFIED, true, true);
      }
      else { // -==, ==-, ===
        boolean unchangedLeft = equality.test(ThreeSide.BASE, ThreeSide.LEFT);
        boolean unchangedRight = equality.test(ThreeSide.BASE, ThreeSide.RIGHT);
        assert !unchangedLeft || !unchangedRight;

        if (unchangedLeft) {
          return new MergeConflictType(isRightEmpty ? MergeConflictType.Type.DELETED : MergeConflictType.Type.MODIFIED, false, true);
        }
        if (unchangedRight) {
          return new MergeConflictType(isLeftEmpty ? MergeConflictType.Type.INSERTED : MergeConflictType.Type.MODIFIED, true, false);
        }

        return new MergeConflictType(MergeConflictType.Type.MODIFIED, true, true);
      }
    }
  }

}
