// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables;

import com.intellij.diff.util.Range;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

class RangesDiffIterable extends ChangeDiffIterableBase {
  @NotNull private final Collection<? extends Range> myRanges;

  RangesDiffIterable(@NotNull Collection<? extends Range> ranges, int length1, int length2) {
    super(length1, length2);
    myRanges = ranges;
  }

  @NotNull
  @Override
  protected ChangeIterable createChangeIterable() {
    return new RangesChangeIterable(myRanges);
  }

  private static final class RangesChangeIterable implements ChangeIterable {
    private final Iterator<? extends Range> myIterator;
    private Range myLast;

    private RangesChangeIterable(@NotNull Collection<? extends Range> ranges) {
      myIterator = ranges.iterator();

      next();
    }

    @Override
    public boolean valid() {
      return myLast != null;
    }

    @Override
    public void next() {
      myLast = myIterator.hasNext() ? myIterator.next() : null;
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
