// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables;

import com.intellij.diff.fragments.DiffFragment;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

class DiffFragmentsDiffIterable extends ChangeDiffIterableBase {
  @NotNull private final Collection<? extends DiffFragment> myFragments;

  DiffFragmentsDiffIterable(@NotNull Collection<? extends DiffFragment> ranges, int length1, int length2) {
    super(length1, length2);
    myFragments = ranges;
  }

  @NotNull
  @Override
  protected ChangeIterable createChangeIterable() {
    return new FragmentsChangeIterable(myFragments);
  }

  private static final class FragmentsChangeIterable implements ChangeIterable {
    private final Iterator<? extends DiffFragment> myIterator;
    private DiffFragment myLast;

    private FragmentsChangeIterable(@NotNull Collection<? extends DiffFragment> fragments) {
      myIterator = fragments.iterator();

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
      return myLast.getStartOffset1();
    }

    @Override
    public int getStart2() {
      return myLast.getStartOffset2();
    }

    @Override
    public int getEnd1() {
      return myLast.getEndOffset1();
    }

    @Override
    public int getEnd2() {
      return myLast.getEndOffset2();
    }
  }
}
