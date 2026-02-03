// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

@SuppressWarnings("DuplicatedCode")
final class StrippedIntToIntMap {
  /**
   * The array of keys.
   */
  private final int[] keys;
  private final int[] values;

  /**
   * The mask for wrapping a position counter.
   */
  private final transient int mask;
  /**
   * Whether this set contains the null key.
   */
  private transient boolean containsNullKey;

  private StrippedIntToIntMap(final int expected, final float loadFactor) {
    if (loadFactor <= 0 || loadFactor > 1) {
      throw new IllegalArgumentException("Load factor must be greater than 0 and smaller than or equal to 1");
    }
    if (expected < 0) {
      throw new IllegalArgumentException("The expected number of elements must be non-negative");
    }

    // The current table size. Note that an additional element is allocated for storing the null key.
    int n = Hash.arraySize(expected, loadFactor);
    mask = n - 1;
    keys = new int[n + 1];
    // the last for the null key
    values = new int[keys.length + 1];
  }

  StrippedIntToIntMap(final int[] metadata) {
    this(metadata.length / 2, Hash.FAST_LOAD_FACTOR);

    for (int i = 0; i < metadata.length; i += 2) {
      add(metadata[i], metadata[i + 1]);
    }
  }

  private void add(final int k, final int v) {
    if (k == 0) {
      if (containsNullKey) {
        return;
      }
      containsNullKey = true;
      values[keys.length] = v;
    }
    else {
      int index;
      int curr;
      // the starting point
      if (!((curr = keys[index = k & mask]) == 0)) {
        if (curr == k) {
          return;
        }
        while (!((curr = keys[index = index + 1 & mask]) == 0)) {
          if (curr == k) {
            return;
          }
        }
      }
      keys[index] = k;
      values[index] = v;
    }
  }

  public int get(final int k) {
    if (k == 0) {
      return containsNullKey ? values[keys.length] : -1;
    }

    int curr;
    int index;
    // the starting point
    if ((curr = keys[index = k & mask]) == 0) {
      return -1;
    }

    if (k == curr) {
      return values[index];
    }
    // there's always an unused entry
    while (true) {
      if ((curr = keys[index = index + 1 & mask]) == 0) {
        return -1;
      }
      if (k == curr) {
        return values[index];
      }
    }
  }
}
