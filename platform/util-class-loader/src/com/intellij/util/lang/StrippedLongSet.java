/*
 * Copyright (C) 2002-2020 Sebastiano Vigna
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.lang;

import java.util.NoSuchElementException;

final class StrippedLongSet {
  /**
   * The array of keys.
   */
  public transient long[] keys;
  /**
   * The mask for wrapping a position counter.
   */
  private transient int mask;
  /**
   * Whether this set contains the null key.
   */
  private transient boolean containsNull;
  /**
   * The current table size. Note that an additional element is allocated for storing the null key.
   */
  private transient int n;
  /**
   * Threshold after which we rehash. It must be the table size times {@link #loadFactor}.
   */
  private transient int maxFill;
  /**
   * Number of entries in the set (including the null key, if present).
   */
  private int size;
  /**
   * The acceptable load factor.
   */
  private final float loadFactor;

  /**
   * Creates a new hash set.
   *
   * <p>
   * The actual table size will be the least power of two greater than
   * {@code expected}/{@code loadFactor}.
   *
   * @param expected the expected number of elements in the hash set.
   * @param loadFactor        the load factor.
   */
  StrippedLongSet(final int expected, final float loadFactor) {
    if (loadFactor <= 0 || loadFactor > 1) {
      throw new IllegalArgumentException("Load factor must be greater than 0 and smaller than or equal to 1");
    }
    if (expected < 0) {
      throw new IllegalArgumentException("The expected number of elements must be non-negative");
    }
    this.loadFactor = loadFactor;
    n = Hash.arraySize(expected, loadFactor);
    mask = n - 1;
    maxFill = Hash.maxFill(n, loadFactor);
    keys = new long[n + 1];
  }

  StrippedLongSet() {
    this(Hash.DEFAULT_INITIAL_SIZE, Hash.FAST_LOAD_FACTOR);
  }

  private int realSize() {
    return containsNull ? size - 1 : size;
  }

  public boolean hasNull() {
    return containsNull;
  }

  public boolean add(final long k) {
    int pos;
    if (k == 0) {
      if (containsNull) {
        return false;
      }
      containsNull = true;
    }
    else {
      long curr;
      final long[] key = this.keys;
      // The starting point.
      if (!((curr = key[pos = (int)k & mask]) == 0)) {
        if (curr == k) {
          return false;
        }
        while (!((curr = key[pos = pos + 1 & mask]) == 0)) {
          if (curr == k) {
            return false;
          }
        }
      }
      key[pos] = k;
    }
    if (size++ >= maxFill) {
      rehash(Hash.arraySize(size + 1, loadFactor));
    }
    return true;
  }

  public boolean contains(final long k) {
    if (k == 0) {
      return containsNull;
    }
    long curr;
    final long[] key = this.keys;
    int pos;
    // The starting point.
    if ((curr = key[pos = (int)k & mask]) == 0) {
      return false;
    }
    if (k == curr) {
      return true;
    }
    while (true) {
      if ((curr = key[pos = pos + 1 & mask]) == 0) {
        return false;
      }
      if (k == curr) {
        return true;
      }
    }
  }

  public int size() {
    return size;
  }

  public boolean isEmpty() {
    return size == 0;
  }

  /**
   * An iterator over a hash set.
   */
  final class SetIterator {
    /**
     * The index of the last entry returned, if positive or zero; initially,
     * {@link #n}.
     */
    int pos = n;

    /**
     * A downward counter measuring how many entries must still be returned.
     */
    int c = size;

    /**
     * A boolean telling us whether we should return the null key.
     */
    boolean mustReturnNull = StrippedLongSet.this.containsNull;

    public boolean hasNext() {
      return c != 0;
    }

    public long nextLong() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      c--;
      if (mustReturnNull) {
        mustReturnNull = false;
        return keys[n];
      }
      long[] key = StrippedLongSet.this.keys;
      for (; ; ) {
        long v = key[--pos];
        if (v != 0) {
          return v;
        }
      }
    }
  }

  public SetIterator iterator() {
    return new SetIterator();
  }

  @SuppressWarnings("StatementWithEmptyBody")
  private void rehash(final int newN) {
    final long[] key = this.keys;
    final int mask = newN - 1; // Note that this is used by the hashing macro
    final long[] newKey = new long[newN + 1];
    int i = n, pos;
    for (int j = realSize(); j-- != 0; ) {
      while (key[--i] == 0) ;
      if (!(newKey[pos = (int)key[i] & mask] == 0)) {
        while (!(newKey[pos = pos + 1 & mask] == 0)) ;
      }
      newKey[pos] = key[i];
    }
    n = newN;
    this.mask = mask;
    maxFill = Hash.maxFill(n, loadFactor);
    this.keys = newKey;
  }

  public long[] toArray() {
    long[] result = new long[size];
    SetIterator iterator = iterator();
    int i = 0;
    while (iterator.hasNext()) {
      result[i++] = iterator.nextLong();
    }
    return result;
  }
}
