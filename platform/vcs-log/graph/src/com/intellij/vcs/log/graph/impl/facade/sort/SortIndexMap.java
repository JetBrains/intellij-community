// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade.sort;

import it.unimi.dsi.fastutil.ints.IntImmutableList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public class SortIndexMap {
  private final IntList map;
  private final IntList reverseMap;

  public SortIndexMap(@NotNull List<Integer> list) {
    int[] reverseMap = new int[list.size()];
    for (int i = 0; i < list.size(); i++) {
      reverseMap[list.get(i)] = i;
    }
    this.map = new IntImmutableList(list);
    this.reverseMap = new IntImmutableList(reverseMap);
  }

  public int size() {
    return map.size();
  }

  // usualIndex == id
  public int getSortedIndex(int usualIndex) {
    return reverseMap.getInt(usualIndex);
  }

  public int getUsualIndex(int sortedIndex) {
    return map.getInt(sortedIndex);
  }
}