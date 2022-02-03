// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables;

import com.intellij.diff.util.Range;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/*
 * Chunks are guaranteed to be squashed
 * Chunks are not empty
 */
public interface DiffIterable {
  int getLength1();

  int getLength2();

  @NotNull
  Iterator<Range> changes();

  @NotNull
  Iterator<Range> unchanged();

  @NotNull
  default Iterable<Range> iterateChanges() {
    return this::changes;
  }

  @NotNull
  default Iterable<Range> iterateUnchanged() {
    return this::unchanged;
  }
}
