// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade.sort;

import com.intellij.vcs.log.graph.utils.IntList;
import com.intellij.vcs.log.graph.utils.impl.CompressedIntList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface SortIndexMap {
  int size();

  // usualIndex == id
  int getSortedIndex(int usualIndex);

  int getUsualIndex(int sortedIndex);

  static @NotNull SortIndexMap createFromSortedList(@NotNull List<Integer> list) {
    int[] reverseMap = new int[list.size()];
    for (int i = 0; i < list.size(); i++) {
      reverseMap[list.get(i)] = i;
    }

    IntList compressedMap = CompressedIntList.newInstance(list);
    IntList compressedReverseMap = CompressedIntList.newInstance(reverseMap);
    return new SortIndexMap() {
      @Override
      public int size() {
        return compressedMap.size();
      }

      @Override
      public int getSortedIndex(int usualIndex) {
        return compressedReverseMap.get(usualIndex);
      }

      @Override
      public int getUsualIndex(int sortedIndex) {
        return compressedMap.get(sortedIndex);
      }
    };
  }
}
