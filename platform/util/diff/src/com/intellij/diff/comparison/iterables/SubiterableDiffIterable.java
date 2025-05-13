// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables;

import com.intellij.diff.util.Range;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Internal
public final class SubiterableDiffIterable extends ChangeDiffIterableBase {
  private final @NotNull List<Range> myChanged;
  private final int myStart1;
  private final int myStart2;
  private final int myEnd1;
  private final int myEnd2;
  private final int myFirstIndex;

  /**
   * @param firstIndex First range in {@code changed} that might affects our range.
   *                   This is an optimization to avoid O(changed.size) lookups of the first element for each subIterable.
   */
  public SubiterableDiffIterable(@NotNull List<Range> changed, int start1, int end1, int start2, int end2,
                                 int firstIndex) {
    super(end1 - start1, end2 - start2);
    myChanged = changed;
    myStart1 = start1;
    myStart2 = start2;
    myEnd1 = end1;
    myEnd2 = end2;
    myFirstIndex = firstIndex;
  }

  @Override
  protected @NotNull ChangeIterable createChangeIterable() {
    return new SubiterableChangeIterable(myChanged, myStart1, myEnd1, myStart2, myEnd2, myFirstIndex);
  }

  private static class SubiterableChangeIterable implements ChangeIterable {
    private final List<Range> myChanged;
    private final int myStart1;
    private final int myEnd1;
    private final int myStart2;
    private final int myEnd2;

    private int myIndex;
    private Range myLast;

    SubiterableChangeIterable(@NotNull List<Range> changed, int start1, int end1, int start2, int end2,
                              int firstIndex) {
      myChanged = changed;
      myStart1 = start1;
      myEnd1 = end1;
      myStart2 = start2;
      myEnd2 = end2;
      myIndex = firstIndex;

      next();
    }

    @Override
    public boolean valid() {
      return myLast != null;
    }

    @Override
    public void next() {
      myLast = null;

      while (myIndex < myChanged.size()) {
        Range range = myChanged.get(myIndex);
        myIndex++;

        if (range.end1 < myStart1 || range.end2 < myStart2) continue;
        if (range.start1 > myEnd1 || range.start2 > myEnd2) break;

        Range newRange = new Range(Math.max(myStart1, range.start1) - myStart1, Math.min(myEnd1, range.end1) - myStart1,
                                   Math.max(myStart2, range.start2) - myStart2, Math.min(myEnd2, range.end2) - myStart2);
        if (newRange.isEmpty()) continue;

        myLast = newRange;
        break;
      }
    }

    @Override
    public int getStart1() {
      return myLast.start1;
    }

    @Override
    public int getStart2() {
      return myLast.start2;
    }

    @Override
    public int getEnd1() {
      return myLast.end1;
    }

    @Override
    public int getEnd2() {
      return myLast.end2;
    }
  }
}
