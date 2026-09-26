// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.utils.impl;

import com.intellij.vcs.log.graph.utils.IntToIntMap;
import com.intellij.vcs.log.graph.utils.UpdatableIntToIntMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class IDIntToIntMap implements IntToIntMap {
  public static final @NotNull UpdatableIntToIntMap EMPTY = new EmptyIDIntToIntMap();

  private final int size;

  public IDIntToIntMap(int size) {
    this.size = size;
  }

  @Override
  public int shortSize() {
    return size;
  }

  @Override
  public int longSize() {
    return size;
  }

  @Override
  public int getLongIndex(int shortIndex) {
    return shortIndex;
  }

  @Override
  public int getShortIndex(int longIndex) {
    return longIndex;
  }

  private static class EmptyIDIntToIntMap extends IDIntToIntMap implements UpdatableIntToIntMap {

    EmptyIDIntToIntMap() {
      super(0);
    }

    @Override
    public void update(int startLongIndex, int endLongIndex) {
      // do nothing
    }
  }
}
