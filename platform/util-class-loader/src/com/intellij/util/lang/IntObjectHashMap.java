// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

/**
 * Specialized memory saving map implementation for UrlClassLoader to avoid extra dependencies.
 */
final class IntObjectHashMap {
  private int size;
  private int[] keys;
  private Object[] values;
  private Object specialZeroValue;
  private boolean hasZeroValue;

  IntObjectHashMap() {
    keys = new int[4];
    values = new Object[keys.length];
  }

  public int size() {
    return size + (hasZeroValue ? 1 : 0);
  }

  public void put(int key, Object value) {
    if (key == 0) {
      specialZeroValue = value;
      hasZeroValue = true;
      return;
    }

    if (size >= (2 * values.length) / 3) rehash();
    Object previousValue = doPut(keys, values, key, value);
    if (previousValue == null) ++size;
  }

  private static Object doPut(int[] keys, Object[] values, int key, Object value) {
    int index = hashIndex(keys, key);
    Object obj = values[index];
    values[index] = value;
    if (keys[index] == 0) keys[index] = key;
    return obj;
  }

  private static int hashIndex(int[] keys, int key) {
    int hash = (int)((key * 0x9E3779B9L) & 0x7fffffff);
    int index = hash & (keys.length - 1);

    int candidate;
    while ((candidate = keys[index]) != 0) {
      if (candidate == key) return index;
      if (index == 0) index = keys.length;
      index--;
    }

    return index;
  }

  private void rehash() {
    int[] newKeys = new int[keys.length << 1];
    Object[] newValues = new Object[newKeys.length];

    for (int i = keys.length - 1; i >= 0; i--) {
      int key = keys[i];
      if (key != 0) {
        doPut(newKeys, newValues, key, values[i]);
      }
    }

    keys = newKeys;
    values = newValues;
  }

  public Object get(int key) {
    return key == 0 ? specialZeroValue : values[hashIndex(keys, key)];
  }
}