// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

@SuppressWarnings("DuplicatedCode")
final class StrippedLongToLongMap {
  /**
   * The array of keys.
   */
  private final long[] keys;
  private final long[] values;

  /**
   * The mask for wrapping a position counter.
   */
  private final transient int mask;
  /**
   * Whether this set contains the null key.
   */
  private transient boolean containsNullKey;
  private final int size;

  private StrippedLongToLongMap(final int size, final float loadFactor) {
    if (loadFactor <= 0 || loadFactor > 1) {
      throw new IllegalArgumentException("Load factor must be greater than 0 and smaller than or equal to 1");
    }
    if (size < 0) {
      throw new IllegalArgumentException("The size number of elements must be non-negative");
    }

    this.size = size;

    // The current table size. Note that an additional element is allocated for storing the null key.
    int n = Hash.arraySize(size, loadFactor);
    mask = n - 1;
    keys = new long[n + 1];
    // the last for the null key
    values = new long[keys.length + 1];
  }

  StrippedLongToLongMap(final long[] metadata) {
    this(metadata.length / 2, Hash.FAST_LOAD_FACTOR);

    for (int i = 0; i < metadata.length; i += 2) {
      add(metadata[i], metadata[i + 1]);
    }
  }

  @SuppressWarnings("DuplicatedCode")
  private void add(final long k, final long v) {
    if (k == 0) {
      if (containsNullKey) {
        return;
      }
      containsNullKey = true;
      values[keys.length] = v;
    }
    else {
      int index;
      long curr;
      // the starting point
      if (!((curr = keys[index = (int)k & mask]) == 0)) {
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

  public long get(long k) {
    if (k == 0) {
      return containsNullKey ? values[keys.length] : -1;
    }

    long curr;
    int index;
    // the starting point
    if ((curr = keys[index = (int)k & mask]) == 0) {
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

  public int size() {
    return size;
  }
}
