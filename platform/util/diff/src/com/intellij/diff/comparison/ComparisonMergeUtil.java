// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison;

import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.util.MergeRange;
import com.intellij.diff.util.Range;
import com.intellij.diff.util.Side;
import com.intellij.util.containers.PeekableIterator;
import com.intellij.util.containers.PeekableIteratorWrapper;
import com.intellij.util.diff.DiffConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ComparisonMergeUtil {
  @NotNull
  public static List<MergeRange> buildSimple(@NotNull FairDiffIterable fragments1,
                                             @NotNull FairDiffIterable fragments2,
                                             @NotNull CancellationChecker indicator) {
    assert fragments1.getLength1() == fragments2.getLength1();
    return new FairMergeBuilder().execute(fragments1, fragments2);
  }

  @NotNull
  public static List<MergeRange> buildMerge(@NotNull FairDiffIterable fragments1,
                                            @NotNull FairDiffIterable fragments2,
                                            @NotNull SideEquality trueEquality,
                                            @NotNull CancellationChecker indicator) {
    assert fragments1.getLength1() == fragments2.getLength1();
    return new FairMergeBuilder(trueEquality).execute(fragments1, fragments2);
  }

  private static final class FairMergeBuilder {
    @NotNull private final ChangeBuilder myChangesBuilder;

    private FairMergeBuilder() {
      myChangesBuilder = new ChangeBuilder();
    }

    private FairMergeBuilder(@NotNull SideEquality trueEquality) {
      myChangesBuilder = new IgnoringChangeBuilder(trueEquality);
    }

    @NotNull
    public List<MergeRange> execute(@NotNull FairDiffIterable fragments1,
                                    @NotNull FairDiffIterable fragments2) {
      PeekableIterator<Range> unchanged1 = new PeekableIteratorWrapper<>(fragments1.unchanged());
      PeekableIterator<Range> unchanged2 = new PeekableIteratorWrapper<>(fragments2.unchanged());

      while (unchanged1.hasNext() && unchanged2.hasNext()) {
        Side side = add(unchanged1.peek(), unchanged2.peek());
        side.select(unchanged1, unchanged2).next();
      }

      return myChangesBuilder.finish(fragments1.getLength2(), fragments1.getLength1(), fragments2.getLength2());
    }

    @NotNull
    private Side add(@NotNull Range range1, @NotNull Range range2) {
      int start1 = range1.start1;
      int end1 = range1.end1;

      int start2 = range2.start1;
      int end2 = range2.end1;

      if (end1 <= start2) return Side.LEFT;
      if (end2 <= start1) return Side.RIGHT;

      int startBase = Math.max(start1, start2);
      int endBase = Math.min(end1, end2);
      int count = endBase - startBase;

      int startShift1 = startBase - start1;
      int startShift2 = startBase - start2;

      int startLeft = range1.start2 + startShift1;
      int endLeft = startLeft + count;
      int startRight = range2.start2 + startShift2;
      int endRight = startRight + count;

      myChangesBuilder.markEqual(startLeft, startBase, startRight, endLeft, endBase, endRight);

      return Side.fromLeft(end1 <= end2);
    }
  }

  private static class ChangeBuilder {
    @NotNull protected final List<MergeRange> myChanges = new ArrayList<>();

    private int myIndex1 = 0;
    private int myIndex2 = 0;
    private int myIndex3 = 0;

    protected void addChange(int start1, int start2, int start3, int end1, int end2, int end3) {
      if (start1 == end1 && start2 == end2 && start3 == end3) return;
      myChanges.add(new MergeRange(start1, end1, start2, end2, start3, end3));
    }

    public void markEqual(int start1, int start2, int start3, int end1, int end2, int end3) {
      assert myIndex1 <= start1;
      assert myIndex2 <= start2;
      assert myIndex3 <= start3;
      assert start1 <= end1;
      assert start2 <= end2;
      assert start3 <= end3;

      processChange(myIndex1, myIndex2, myIndex3, start1, start2, start3);

      myIndex1 = end1;
      myIndex2 = end2;
      myIndex3 = end3;
    }

    @NotNull
    public List<MergeRange> finish(int length1, int length2, int length3) {
      assert myIndex1 <= length1;
      assert myIndex2 <= length2;
      assert myIndex3 <= length3;

      processChange(myIndex1, myIndex2, myIndex3, length1, length2, length3);

      return myChanges;
    }

    protected void processChange(int start1, int start2, int start3, int end1, int end2, int end3) {
      addChange(start1, start2, start3, end1, end2, end3);
    }
  }

  private static final class IgnoringChangeBuilder extends ChangeBuilder {
    @NotNull private final SideEquality myTrueEquality;

    private IgnoringChangeBuilder(@NotNull SideEquality trueEquality) {
      myTrueEquality = trueEquality;
    }

    @Override
    protected void processChange(int start1, int start2, int start3, int end1, int end2, int end3) {
      MergeRange lastChange = myChanges.isEmpty() ? null : myChanges.get(myChanges.size() - 1);
      int unchangedStart1 = lastChange != null ? lastChange.end1 : 0;
      int unchangedStart2 = lastChange != null ? lastChange.end2 : 0;
      int unchangedStart3 = lastChange != null ? lastChange.end3 : 0;
      addIgnoredChanges(unchangedStart1, unchangedStart2, unchangedStart3, start1, start2, start3);

      addChange(start1, start2, start3, end1, end2, end3);
    }

    private void addIgnoredChanges(int start1, int start2, int start3, int end1, int end2, int end3) {
      int count = end2 - start2;
      assert end1 - start1 == count;
      assert end3 - start3 == count;

      int firstIgnoredCount = -1;
      for (int i = 0; i < count; i++) {
        boolean isIgnored = !myTrueEquality.equals(start1 + i, start2 + i, start3 + i);
        boolean previousAreIgnored = firstIgnoredCount != -1;

        if (isIgnored && !previousAreIgnored) {
          firstIgnoredCount = i;
        }
        if (!isIgnored && previousAreIgnored) {
          addChange(start1 + firstIgnoredCount, start2 + firstIgnoredCount, start3 + firstIgnoredCount,
                    start1 + i, start2 + i, start3 + i);
          firstIgnoredCount = -1;
        }
      }

      if (firstIgnoredCount != -1) {
        addChange(start1 + firstIgnoredCount, start2 + firstIgnoredCount, start3 + firstIgnoredCount,
                  start1 + count, start2 + count, start3 + count);
      }
    }
  }

  @Nullable
  public static CharSequence tryResolveConflict(@NotNull CharSequence leftText,
                                                @NotNull CharSequence baseText,
                                                @NotNull CharSequence rightText) {
    if (DiffConfig.USE_GREEDY_MERGE_MAGIC_RESOLVE) {
      return MergeResolveUtil.tryGreedyResolve(leftText, baseText, rightText);
    }
    else {
      return MergeResolveUtil.tryResolve(leftText, baseText, rightText);
    }
  }

  interface SideEquality {
    boolean equals(int leftIndex, int baseIndex, int rightIndex);
  }
}