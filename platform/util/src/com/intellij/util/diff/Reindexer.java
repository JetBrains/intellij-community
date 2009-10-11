/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.diff;

import gnu.trove.TIntArrayList;

import java.util.Arrays;

/**
 * @author dyoma
 */
class Reindexer {
  private final int[][] myOldIndecies = new int[2][];
  private final int[] myOriginalLengths = new int[]{-1, -1};

  public int[][] discardUnique(int[] ints1, int[] ints2) {
    int[] discarded1 = discard(ints2, ints1, 0);
    return new int[][]{discarded1, discard(discarded1, ints2, 1)};
  }

  void idInit(int length1, int length2) {
    myOriginalLengths[0] = length1;
    myOriginalLengths[1] = length2;
    for (int j = 0; j < 2; j++) {
      int originalLength = myOriginalLengths[j];
      myOldIndecies[j] = new int[originalLength];
      for (int i = 0; i < originalLength; i++)
        myOldIndecies[j][i] = i;
    }
  }

  public int restoreIndex(int index, int array) {
    return myOldIndecies[array][index];
  }

  private int[] discard(int[] needed, int[] toDiscard, int arrayIndex) {
    myOriginalLengths[arrayIndex] = toDiscard.length;
    int[] sorted1 = createSorted(needed);
    TIntArrayList discarded = new TIntArrayList(toDiscard.length);
    TIntArrayList oldIndecies = new TIntArrayList(toDiscard.length);
    for (int i = 0; i < toDiscard.length; i++) {
      int index = toDiscard[i];
      if (Arrays.binarySearch(sorted1, index) >= 0) {
        discarded.add(index);
        oldIndecies.add(i);
      }
    }
    myOldIndecies[arrayIndex] = oldIndecies.toNativeArray();
    return discarded.toNativeArray();
  }

  private int[] createSorted(int[] ints1) {
    int[] sorted1 = new int[ints1.length];
    System.arraycopy(ints1, 0, sorted1, 0, ints1.length);
    Arrays.sort(sorted1);
    return sorted1;
  }

  public void reindex(LinkedDiffPaths paths, LCSBuilder builder) {
    final boolean[] changes1 = new boolean[myOriginalLengths[0]];
    final boolean[] changes2 = new boolean[myOriginalLengths[1]];
    Arrays.fill(changes1, true);
    Arrays.fill(changes2, true);
    paths.decodePath(new LCSBuilder() {
      private int x = myOldIndecies[0].length - 1;
      private int y = myOldIndecies[1].length - 1;
      private int originalX = myOriginalLengths[0] - 1;
      private int originalY = myOriginalLengths[1] - 1;

      public void addChange(int first, int second) {
        x -= first;
        y -= second;
        originalX = markChanged(changes1, originalX, myOldIndecies[0], x);
        originalY = markChanged(changes2, originalY, myOldIndecies[1], y);
      }

      public void addEqual(int length) {
        for (int i = length; i > 0; i--) {
          originalX = markChanged(changes1, originalX, myOldIndecies[0], x);
          originalY = markChanged(changes2, originalY, myOldIndecies[1], y);
          x--;
          y--;
          changes1[originalX] = false;
          changes2[originalY] = false;
          originalX--;
          originalY--;
        }
      }
    });
    int x = 0;
    int y = 0;
    while (x < changes1.length && y < changes2.length) {
      int startX = x;
      while (x < changes1.length && y < changes2.length && !changes1[x] && !changes2[y]) {
        x++;
        y++;
      }
      if (x> startX) builder.addEqual(x - startX);
      int dx = 0;
      int dy = 0;
      while (x < changes1.length && changes1[x]) {
        dx++;
        x++;
      }
      while (y < changes2.length && changes2[y]) {
        dy++;
        y++;
      }
      if (dx != 0 || dy != 0) builder.addChange(dx, dy);
    }
    if (x != changes1.length || y != changes2.length)
      builder.addChange(changes1.length - x, changes2.length - y);
  }

  private int markChanged(final boolean[] changes, int from, int[] oldIndecies, int newTo) {
    int oldTo = newTo != -1 ? oldIndecies[newTo] : -1;
    for (int i = from; i > oldTo; i--) changes[i] = true;
    return oldTo;
  }
}
