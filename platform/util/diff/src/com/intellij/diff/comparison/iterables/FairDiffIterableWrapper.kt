// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables;

import com.intellij.diff.util.Range;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

class FairDiffIterableWrapper implements FairDiffIterable {
  private final @NotNull DiffIterable myIterable;

  FairDiffIterableWrapper(@NotNull DiffIterable iterable) {
    myIterable = iterable;
  }

  @Override
  public int getLength1() {
    return myIterable.getLength1();
  }

  @Override
  public int getLength2() {
    return myIterable.getLength2();
  }

  @Override
  public @NotNull Iterator<Range> changes() {
    return myIterable.changes();
  }

  @Override
  public @NotNull Iterator<Range> unchanged() {
    return myIterable.unchanged();
  }
}
