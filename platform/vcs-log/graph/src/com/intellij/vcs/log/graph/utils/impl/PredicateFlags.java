// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.utils.impl;

import com.intellij.vcs.log.graph.utils.Flags;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class PredicateFlags implements Flags {
  private final @NotNull Predicate<? super Integer> myVisible;
  private final int mySize;

  public PredicateFlags(@NotNull Predicate<? super Integer> visible, int size) {
    myVisible = visible;
    mySize = size;
  }

  @Override
  public int size() {
    return mySize;
  }

  @Override
  public boolean get(int index) {
    return myVisible.test(index);
  }

  @Override
  public void set(int index, boolean value) {
    throw new UnsupportedOperationException("Modification is not supported");
  }

  @Override
  public void setAll(boolean value) {
    throw new UnsupportedOperationException("Modification is not supported");
  }
}
