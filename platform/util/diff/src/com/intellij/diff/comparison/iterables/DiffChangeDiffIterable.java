// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables;

import com.intellij.util.diff.Diff;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class DiffChangeDiffIterable extends ChangeDiffIterableBase {
  @Nullable private final Diff.Change myChange;

  DiffChangeDiffIterable(@Nullable Diff.Change change, int length1, int length2) {
    super(length1, length2);
    myChange = change;
  }

  @NotNull
  @Override
  protected ChangeIterable createChangeIterable() {
    return new DiffChangeChangeIterable(myChange);
  }

  @SuppressWarnings("ConstantConditions")
  private static class DiffChangeChangeIterable implements ChangeIterable {
    @Nullable private Diff.Change myChange;

    DiffChangeChangeIterable(@Nullable Diff.Change change) {
      myChange = change;
    }

    @Override
    public boolean valid() {
      return myChange != null;
    }

    @Override
    public void next() {
      myChange = myChange.link;
    }

    @Override
    public int getStart1() {
      return myChange.line0;
    }

    @Override
    public int getStart2() {
      return myChange.line1;
    }

    @Override
    public int getEnd1() {
      return myChange.line0 + myChange.deleted;
    }

    @Override
    public int getEnd2() {
      return myChange.line1 + myChange.inserted;
    }
  }
}
