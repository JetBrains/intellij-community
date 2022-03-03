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

final class StrippedLongArrayList {
  private static final long[] DEFAULT_EMPTY_ARRAY = {};
  private static final long[] EMPTY_ARRAY = {};
  private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

  /**
   * The initial default capacity of an array list.
   */
  private static final int DEFAULT_INITIAL_CAPACITY = 10;

  /**
   * The backing array.
   */
  private transient long[] a;

  /**
   * The current actual size of the list (never greater than the backing-array
   * length).
   */
  private int size;

  public StrippedLongArrayList(int capacity) {
    if (capacity < 0) {
      throw new IllegalArgumentException("Initial capacity (" + capacity + ") is negative");
    }
    a = capacity == 0 ? EMPTY_ARRAY : new long[capacity];
  }

  public StrippedLongArrayList() {
    a = DEFAULT_EMPTY_ARRAY;
  }

  /**
   * Returns the backing array of this list.
   *
   * @return the backing array.
   */
  public long[] elements() {
    return a;
  }

  /**
   * Ensures that this array list can contain the given number of entries without
   * resizing.
   *
   * @param capacity the new minimum capacity for this array list.
   */
  public void ensureCapacity(final int capacity) {
    if (capacity <= a.length || (a == DEFAULT_EMPTY_ARRAY && capacity <= DEFAULT_INITIAL_CAPACITY)) {
      return;
    }
    a = ensureCapacity(a, capacity, size);
    assert size <= a.length;
  }

  private static long[] ensureCapacity(final long[] array, final int length, final int preserve) {
    return length > array.length ? forceCapacity(array, length, preserve) : array;
  }

  private static long[] forceCapacity(final long[] array, final int length, final int preserve) {
    final long[] t = new long[length];
    System.arraycopy(array, 0, t, 0, preserve);
    return t;
  }

  /**
   * Grows this array list, ensuring that it can contain the given number of
   * entries without resizing, and in case increasing the current capacity at
   * least by a factor of 50%.
   *
   * @param capacity the new minimum capacity for this array list.
   */
  private void grow(int capacity) {
    if (capacity <= a.length) {
      return;
    }
    if (a != DEFAULT_EMPTY_ARRAY) {
      capacity = (int)Math.max(
        Math.min((long)a.length + (a.length >> 1), MAX_ARRAY_SIZE), capacity);
    }
    else if (capacity < DEFAULT_INITIAL_CAPACITY) {
      capacity = DEFAULT_INITIAL_CAPACITY;
    }
    a = forceCapacity(a, capacity, size);
    assert size <= a.length;
  }

  public void add(final int index, final long k) {
    grow(size + 1);
    if (index != size) {
      System.arraycopy(a, index, a, index + 1, size - index);
    }
    a[index] = k;
    size++;
    assert size <= a.length;
  }

  public boolean add(final long k) {
    grow(size + 1);
    a[size++] = k;
    assert size <= a.length;
    return true;
  }

  public long getLong(final int index) {
    if (index >= size) {
      throw new IndexOutOfBoundsException(
        "Index (" + index + ") is greater than or equal to list size (" + size + ")");
    }
    return a[index];
  }

  public void clear() {
    size = 0;
  }

  public int size() {
    return size;
  }

  @SuppressWarnings("unlikely-arg-type")
  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof StrippedLongArrayList)) {
      return false;
    }

    int s = size();
    if (s != ((StrippedLongArrayList)o).size()) {
      return false;
    }

    final long[] a1 = a;
    final long[] a2 = ((StrippedLongArrayList)o).a;
    if (a1 == a2) {
      return true;
    }
    while (s-- != 0) {
      if (a1[s] != a2[s]) {
        return false;
      }
    }
    return true;
  }
}