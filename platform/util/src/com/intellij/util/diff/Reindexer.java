/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.annotations.TestOnly;

import java.util.Arrays;
import java.util.BitSet;

/**
 * @author dyoma
 */
class Reindexer {
  private final int[][] myOldIndecies = new int[2][];
  private final int[] myOriginalLengths = new int[]{-1, -1};
  private final int[] myDiscardedLengths = new int[]{-1, -1};

  public int[][] discardUnique(int[] ints1, int[] ints2) {
    int[] discarded1 = discard(ints2, ints1, 0);
    return new int[][]{discarded1, discard(discarded1, ints2, 1)};
  }

  @TestOnly
  void idInit(int length1, int length2) {
    myOriginalLengths[0] = length1;
    myOriginalLengths[1] = length2;
    myDiscardedLengths[0] = length1;
    myDiscardedLengths[1] = length2;
    for (int j = 0; j < 2; j++) {
      int originalLength = myOriginalLengths[j];
      myOldIndecies[j] = new int[originalLength];
      for (int i = 0; i < originalLength; i++) {
        myOldIndecies[j][i] = i;
      }
    }
  }

  @TestOnly
  int restoreIndex(int index, int array) {
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
    myDiscardedLengths[arrayIndex] = discarded.size();
    return discarded.toNativeArray();
  }

  private int[] createSorted(int[] ints1) {
    int[] sorted1 = new int[ints1.length];
    System.arraycopy(ints1, 0, sorted1, 0, ints1.length);
    Arrays.sort(sorted1);
    return sorted1;
  }

  public void reindex(BitSet[] discardedChanges, LCSBuilder builder) {
    BitSet changes1;
    BitSet changes2;

    if (myDiscardedLengths[0] == myOriginalLengths[0] && myDiscardedLengths[1] == myOriginalLengths[1]) {
      changes1 = discardedChanges[0];
      changes2 = discardedChanges[1];
    }
    else {
      changes1 = new BitSet(myOriginalLengths[0]);
      changes2 = new BitSet(myOriginalLengths[1]);
      int x = 0;
      int y = 0;
      while (x < myDiscardedLengths[0] || y < myDiscardedLengths[1]) {
        if ((x < myDiscardedLengths[0] && y < myDiscardedLengths[1]) && !discardedChanges[0].get(x) && !discardedChanges[1].get(y)) {
          x = increment(myOldIndecies[0], x, changes1, myOriginalLengths[0]);
          y = increment(myOldIndecies[1], y, changes2, myOriginalLengths[1]);
          continue;
        }
        if (discardedChanges[0].get(x)) {
          changes1.set(getOriginal(myOldIndecies[0], x));
          x = increment(myOldIndecies[0], x, changes1, myOriginalLengths[0]);
          continue;
        }
        if (discardedChanges[1].get(y)) {
          changes2.set(getOriginal(myOldIndecies[1], y));
          y = increment(myOldIndecies[1], y, changes2, myOriginalLengths[1]);
          continue;
        }
      }
      if (myDiscardedLengths[0] == 0) {
        changes1.set(0, myOriginalLengths[0]);
      }
      else {
        changes1.set(0, myOldIndecies[0][0]);
      }
      if (myDiscardedLengths[1] == 0) {
        changes2.set(0, myOriginalLengths[1]);
      }
      else {
        changes2.set(0, myOldIndecies[1][0]);
      }
    }

    int x = 0;
    int y = 0;
    while (x < myOriginalLengths[0] && y < myOriginalLengths[1]) {
      int startX = x;
      while (x < myOriginalLengths[0] && y < myOriginalLengths[1] && !changes1.get(x) && !changes2.get(y)) {
        x++;
        y++;
      }
      if (x > startX) builder.addEqual(x - startX);
      int dx = 0;
      int dy = 0;
      while (x < myOriginalLengths[0] && changes1.get(x)) {
        dx++;
        x++;
      }
      while (y < myOriginalLengths[1] && changes2.get(y)) {
        dy++;
        y++;
      }
      if (dx != 0 || dy != 0) builder.addChange(dx, dy);
    }
    if (x != myOriginalLengths[0] || y != myOriginalLengths[1]) builder.addChange(myOriginalLengths[0] - x, myOriginalLengths[1] - y);
  }

  private int getOriginal(int[] indexes, int i) {
    return indexes[i];
  }

  private int increment(int[] indexes, int i, BitSet set, int length) {
    if (i + 1 < indexes.length) {
      set.set(indexes[i] + 1, indexes[i + 1]);
    }
    else {
      set.set(indexes[i] + 1, length);
    }
    return i + 1;
  }
}
