// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade.sort;

import it.unimi.dsi.fastutil.ints.IntImmutableList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class SortIndexMap {
  private final IntList map;
  private final IntList reverseMap;

  /**
   * @param sortedToUsual the usual index of each sorted index; the map keeps the array and does not copy it
   */
  public SortIndexMap(int @NotNull [] sortedToUsual) {
    int[] reverseMap = new int[sortedToUsual.length];
    for (int i = 0; i < sortedToUsual.length; i++) {
      reverseMap[sortedToUsual[i]] = i;
    }
    this.map = new IntImmutableList(sortedToUsual);
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
