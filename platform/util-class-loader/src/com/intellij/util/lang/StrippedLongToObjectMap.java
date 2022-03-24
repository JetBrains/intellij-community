/*
 * Copyright (C) 2002-2021 Sebastiano Vigna
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

import org.jetbrains.annotations.NotNull;

import java.util.function.IntFunction;
import java.util.function.LongFunction;

final class StrippedLongToObjectMap<V> implements LongFunction<V> {
  /**
   * The array of keys.
   */
  private long[] keys;
  /**
   * The array of values.
   */
  private V[] values;
  /**
   * The mask for wrapping a position counter.
   */
  private int mask;
  /**
   * Whether this map contains the key zero.
   */
  private boolean containsNullKey;
  /**
   * The current table size.
   */
  private int tableSize;
  /**
   * Threshold after which we rehash. It must be the table size times loadFactor.
   */
  private int maxFill;

  /**
   * Number of entries in the set (including the key zero, if present).
   */
  private int size;

  private final IntFunction<V[]> valueArrayFactory;

  StrippedLongToObjectMap(IntFunction<V[]> valueArrayFactory, int expectedCapacity) {
    this.valueArrayFactory = valueArrayFactory;
    tableSize = Hash.arraySize(expectedCapacity, Hash.DEFAULT_LOAD_FACTOR);
    mask = tableSize - 1;
    maxFill = Hash.maxFill(tableSize, Hash.DEFAULT_LOAD_FACTOR);
    keys = new long[tableSize + 1];
    values = valueArrayFactory.apply(tableSize + 1);
  }

  StrippedLongToObjectMap(StrippedLongToObjectMap<V> original) {
    valueArrayFactory = original.valueArrayFactory;
    tableSize = original.tableSize;
    mask = original.mask;
    maxFill = original.maxFill;
    size = original.size;
    keys = original.keys.clone();
    values = original.values.clone();
    containsNullKey = original.containsNullKey;
  }

  private int realSize() {
    return containsNullKey ? size - 1 : size;
  }

  /**
   * Index is negative for non-existing key.
   */
  public int index(final long key) {
    if (key == 0) {
      return containsNullKey ? tableSize : -(tableSize + 1);
    }

    long current;
    long[] keys = this.keys;
    int index;
    // the starting point
    if ((current = keys[index = (int)Hash.mix(key) & mask]) == 0) {
      return -(index + 1);
    }
    if (key == current) {
      return index;
    }
    // there's always an unused entry
    while (true) {
      if ((current = keys[index = index + 1 & mask]) == 0) {
        return -(index + 1);
      }
      if (key == current) {
        return index;
      }
    }
  }

  public void addByIndex(int index, long key, V value) {
    replaceByIndex(-index - 1, key, value);
    if (size++ >= maxFill) {
      rehash(Hash.arraySize(size + 1, Hash.DEFAULT_LOAD_FACTOR));
    }
  }

  public void replaceByIndex(int index, long key, @NotNull V value) {
    if (index == tableSize) {
      containsNullKey = true;
    }
    keys[index] = key;
    values[index] = value;
  }

  public V getByIndex(int index) {
    return values[index];
  }

  @Override
  public V apply(long k) {
    if (k == 0) {
      return containsNullKey ? values[tableSize] : null;
    }

    long curr;
    final long[] key = this.keys;
    int pos;
    // The starting point.
    if ((curr = key[pos = (int)Hash.mix(k) & mask]) == 0) {
      return null;
    }
    if (k == curr) {
      return values[pos];
    }
    // There's always an unused entry.
    while (true) {
      if ((curr = key[pos = pos + 1 & mask]) == 0) {
        return null;
      }
      if (k == curr) {
        return values[pos];
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
   * Rehashes the map.
   *
   * <p>
   * This method implements the basic rehashing strategy, and may be overridden by
   * subclasses implementing different rehashing strategies (e.g., disk-based
   * rehashing). However, you should not override this method unless you
   * understand the internal workings of this class.
   *
   * @param newN the new size
   */
  @SuppressWarnings("DuplicatedCode")
  private void rehash(final int newN) {
    final long[] keys = this.keys;
    final V[] values = this.values;
    final int mask = newN - 1; // Note that this is used by the hashing macro
    final long[] newKey = new long[newN + 1];
    final V[] newValue = valueArrayFactory.apply(newN + 1);
    int i = tableSize;
    int pos;
    for (int j = realSize(); j-- != 0; ) {
      //noinspection StatementWithEmptyBody
      while (keys[--i] == 0) ;
      if (!(newKey[pos = (int)Hash.mix(keys[i]) & mask] == 0)) {
        //noinspection StatementWithEmptyBody
        while (!(newKey[pos = pos + 1 & mask] == 0)) ;
      }
      newKey[pos] = keys[i];
      newValue[pos] = values[i];
    }
    newValue[newN] = values[tableSize];
    tableSize = newN;
    this.mask = mask;
    maxFill = Hash.maxFill(tableSize, Hash.DEFAULT_LOAD_FACTOR);
    this.keys = newKey;
    this.values = newValue;
  }
}
