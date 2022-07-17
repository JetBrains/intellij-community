// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.Arrays;

final class UniqueLCS {
  private final int[] myFirst;
  private final int[] mySecond;

  private final int myStart1;
  private final int myStart2;
  private final int myCount1;
  private final int myCount2;

  UniqueLCS(int[] first, int[] second) {
    this(first, second, 0, first.length, 0, second.length);
  }

  UniqueLCS(int[] first, int[] second, int start1, int count1, int start2, int count2) {
    myFirst = first;
    mySecond = second;
    myStart1 = start1;
    myStart2 = start2;
    myCount1 = count1;
    myCount2 = count2;
  }

  public int[][] execute() {
    // map: key -> (offset1 + 1)
    // match: offset1 -> (offset2 + 1)
    Int2IntMap map = new Int2IntOpenHashMap(myCount1 + myCount2);
    int[] match = new int[myCount1];

    for (int i = 0; i < myCount1; i++) {
      int index = myStart1 + i;
      int val = map.get(myFirst[index]);

      if (val == -1) continue;
      if (val == 0) {
        map.put(myFirst[index], i + 1);
      }
      else {
        map.put(myFirst[index], -1);
      }
    }

    int count = 0;
    for (int i = 0; i < myCount2; i++) {
      int index = myStart2 + i;
      int val = map.get(mySecond[index]);

      if (val == 0 || val == -1) continue;
      if (match[val - 1] == 0) {
        match[val - 1] = i + 1;
        count++;
      }
      else {
        match[val - 1] = 0;
        map.put(mySecond[index], -1);
        count--;
      }
    }

    if (count == 0) {
      return null;
    }

    // Largest increasing subsequence on unique elements
    int[] sequence = new int[count];
    int[] lastElement = new int[count];
    int[] predecessor = new int[myCount1];

    int length = 0;
    for (int i = 0; i < myCount1; i++) {
      if (match[i] == 0) continue;

      int j = binarySearch(sequence, match[i], length);
      if (j == length || match[i] < sequence[j]) {
        sequence[j] = match[i];
        lastElement[j] = i;
        predecessor[i] = j > 0 ? lastElement[j - 1] : -1;
        if (j == length) {
          length++;
        }
      }
    }

    int[][] ret = new int[][]{new int[length], new int[length]};

    int i = length - 1;
    int curr = lastElement[length - 1];
    while (curr != -1) {
      ret[0][i] = curr;
      ret[1][i] = match[curr] - 1;
      i--;
      curr = predecessor[curr];
    }

    return ret;
  }

  // find max i: a[i] < val
  // return i + 1
  // assert a[i] != val
  private static int binarySearch(final int[] sequence, final int val, int length) {
    int i = Arrays.binarySearch(sequence, 0, length, val);
    assert i < 0;
    return -i - 1;
  }
}
