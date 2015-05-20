/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.lang;

// Nongeneral purpose memory saving map implementation for UrlClassLoader to avoid extra dependencies
final class IntObjectHashMap {
  private int size;
  private int[] keys;
  private Object[] values;
  private Object specialZeroValue;
  private boolean hasZeroValue;

  public IntObjectHashMap() {
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

    for (int i = keys.length; --i >= 0; ) {
      int key = keys[i];
      if (key != 0) doPut(newKeys, newValues, key, values[i]);
    }

    keys = newKeys;
    values = newValues;
  }

  public Object get(int key) {
    if (key == 0) {
      return specialZeroValue;
    }
    return values[hashIndex(keys, key)];
  }
}
