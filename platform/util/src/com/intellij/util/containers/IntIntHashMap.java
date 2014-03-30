package com.intellij.util.containers;

import gnu.trove.TIntIntHashMap;

public final class IntIntHashMap extends TIntIntHashMap {
  public IntIntHashMap(int initialCapacity) {
    super(initialCapacity);
  }

  public IntIntHashMap() {
  }

  @Override
  public int get(int key) {
    int index = index(key);
    return index < 0 ? -1 : _values[index];
  }
}