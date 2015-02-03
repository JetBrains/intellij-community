package com.intellij.util.containers;

import gnu.trove.TIntIntHashMap;

public final class IntIntHashMap extends TIntIntHashMap {
  private static final int DEFAULT_NULL_VALUE = -1;

  private final int NULL_VALUE;

  public IntIntHashMap(int initialCapacity, int null_value) {
    super(initialCapacity);
    NULL_VALUE = null_value;
  }

  public IntIntHashMap(int initialCapacity) {
    this(initialCapacity, DEFAULT_NULL_VALUE);
  }

  public IntIntHashMap() {
    NULL_VALUE = DEFAULT_NULL_VALUE;
  }

  @Override
  public int get(int key) {
    int index = index(key);
    return index < 0 ? NULL_VALUE : _values[index];
  }
}