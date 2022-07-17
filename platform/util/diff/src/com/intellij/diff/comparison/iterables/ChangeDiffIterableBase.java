// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables;

import com.intellij.diff.util.Range;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

abstract class ChangeDiffIterableBase implements DiffIterable {
  private final int myLength1;
  private final int myLength2;

  ChangeDiffIterableBase(int length1, int length2) {
    myLength1 = length1;
    myLength2 = length2;
  }

  @Override
  public int getLength1() {
    return myLength1;
  }

  @Override
  public int getLength2() {
    return myLength2;
  }

  @NotNull
  @Override
  public Iterator<Range> changes() {
    return new ChangedIterator(createChangeIterable());
  }

  @NotNull
  @Override
  public Iterator<Range> unchanged() {
    return new UnchangedIterator(createChangeIterable(), myLength1, myLength2);
  }

  private static final class ChangedIterator implements Iterator<Range> {
    @NotNull private final ChangeIterable myIterable;

    private ChangedIterator(@NotNull ChangeIterable iterable) {
      myIterable = iterable;
    }

    @Override
    public boolean hasNext() {
      return myIterable.valid();
    }

    @Override
    public Range next() {
      Range range = new Range(myIterable.getStart1(), myIterable.getEnd1(), myIterable.getStart2(), myIterable.getEnd2());
      myIterable.next();
      return range;
    }
  }

  private static final class UnchangedIterator implements Iterator<Range> {
    @NotNull private final ChangeIterable myIterable;
    private final int myLength1;
    private final int myLength2;

    private int lastIndex1 = 0;
    private int lastIndex2 = 0;

    private UnchangedIterator(@NotNull ChangeIterable iterable, int length1, int length2) {
      myIterable = iterable;
      myLength1 = length1;
      myLength2 = length2;

      if (myIterable.valid()) {
        if (myIterable.getStart1() == 0 && myIterable.getStart2() == 0) {
          lastIndex1 = myIterable.getEnd1();
          lastIndex2 = myIterable.getEnd2();
          myIterable.next();
        }
      }
    }

    @Override
    public boolean hasNext() {
      return myIterable.valid() || (lastIndex1 != myLength1 || lastIndex2 != myLength2);
    }

    @Override
    public Range next() {
      if (myIterable.valid()) {
        assert (myIterable.getStart1() - lastIndex1 != 0) || (myIterable.getStart2() - lastIndex2 != 0);
        Range chunk = new Range(lastIndex1, myIterable.getStart1(), lastIndex2, myIterable.getStart2());

        lastIndex1 = myIterable.getEnd1();
        lastIndex2 = myIterable.getEnd2();

        myIterable.next();

        return chunk;
      }
      else {
        assert (myLength1 - lastIndex1 != 0) || (myLength2 - lastIndex2 != 0);
        Range chunk = new Range(lastIndex1, myLength1, lastIndex2, myLength2);

        lastIndex1 = myLength1;
        lastIndex2 = myLength2;

        return chunk;
      }
    }
  }

  @NotNull
  protected abstract ChangeIterable createChangeIterable();

  protected interface ChangeIterable {
    boolean valid();

    void next();

    int getStart1();

    int getStart2();

    int getEnd1();

    int getEnd2();
  }
}
