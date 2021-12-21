// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.keyFMap;

import com.intellij.openapi.util.Key;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

final class ArrayBackedFMap implements KeyFMap {
  static final int ARRAY_THRESHOLD = 8;
  // Invariant: keys are always sorted, never mutated inplace
  private final int[] keys;
  private final @NotNull Object @NotNull [] values; //never mutated inplace

  ArrayBackedFMap(int @NotNull [] keys, @NotNull Object @NotNull [] values) {
    this.keys = keys;
    this.values = values;
  }

  @NotNull
  @Override
  public <V> KeyFMap plus(@NotNull Key<V> key, @NotNull V value) {
    int keyCode = key.hashCode();
    int keyPos = indexOf(keyCode);
    if (keyPos >= 0) {
      if (values[keyPos] == value) {
        return this;
      }
      Object[] newValues = values.clone();
      newValues[keyPos] = value;
      // Can reuse keys as it is never mutated
      return new ArrayBackedFMap(keys, newValues);
    }
    if (size() < ARRAY_THRESHOLD) {
      int[] newKeys = ArrayUtil.insert(keys, -keyPos - 1, keyCode);
      Object[] newValues = ArrayUtil.insert(values, -keyPos - 1, value);
      return new ArrayBackedFMap(newKeys, newValues);
    }
    return new MapBackedFMap(keys, keyCode, values, value);
  }

  @Override
  public int size() {
    return keys.length;
  }

  private int indexOf(int keyCode) {
    for (int i = 0; i < keys.length; i++) {
      int key = keys[i];
      if (key == keyCode) return i;
      if (key > keyCode) return -i-1;
    }
    return -keys.length - 1;
  }

  @NotNull
  @Override
  public KeyFMap minus(@NotNull Key<?> key) {
    int i = indexOf(key.hashCode());
    if (i >= 0) {
      if (size() == 3) {
        int otherI1 = (2 - i) / 2;
        int otherI2 = 3 - (i + 2) / 2;
        Key<Object> key1 = Key.getKeyByIndex(keys[otherI1]);
        Key<Object> key2 = Key.getKeyByIndex(keys[otherI2]);
        if (key1 == null && key2 == null) return EMPTY_MAP;
        if (key1 == null) return new OneElementFMap<>(key2, values[otherI2]);
        if (key2 == null) return new OneElementFMap<>(key1, values[otherI1]);
        return new PairElementsFMap<>(key1, values[otherI1], key2, values[otherI2]);
      }
      int[] newKeys = ArrayUtil.remove(keys, i);
      Object[] newValues = ArrayUtil.remove(values, i, ArrayUtil.OBJECT_ARRAY_FACTORY);
      return new ArrayBackedFMap(newKeys, newValues);
    }
    return this;
  }

  @Override
  public <V> V get(@NotNull Key<V> key) {
    int i = indexOf(key.hashCode());
    //noinspection unchecked
    return i < 0 ? null : (V) values[i];
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder("{");
    for (int i = 0; i < keys.length; i++) {
      int key = keys[i];
      Object value = values[i];
      s.append(s.length() == 1 ? "" : ", ").append(Key.getKeyByIndex(key)).append("=").append(value);
    }
    return s.append("}").toString();
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public int getValueIdentityHashCode() {
    int hash = 0;
    for (int i = 0; i < keys.length; i++) {
      hash = hash * 31 + keys[i];
      hash = hash * 31 + System.identityHashCode(values[i]);
    }
    return hash;
  }

  @Override
  public @NotNull Key<?> @NotNull [] getKeys() {
    return getKeysByIndices(keys);
  }

  static @NotNull Key<?> @NotNull [] getKeysByIndices(int @NotNull [] indexes) {
    Key<?>[] result = new Key[indexes.length];

    int o = 0;
    for (int index : indexes) {
      Key<Object> key = Key.getKeyByIndex(index);
      if (key != null) {
        result[o++] = key;
      }
    }
    return o == result.length ? result : Arrays.copyOf(result, o);
  }

  @Override
  public int hashCode() {
    int hash = 0;
    int length = keys.length;
    for (int i = 0; i < length; i++) {
      // key index is its hashcode
      hash += keys[i] ^ values[i].hashCode();
    }
    return hash;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ArrayBackedFMap)) return false;

    ArrayBackedFMap map = (ArrayBackedFMap)o;
    if (map.size() != size()) return false;

    int length = keys.length;
    for (int i = 0; i < length; i++) {
      if (keys[i] != map.keys[i] || !values[i].equals(map.values[i])) return false;
    }
    return true;
  }

  @Override
  public boolean equalsByReference(@NotNull KeyFMap o) {
    if (this == o) return true;
    if (!(o instanceof ArrayBackedFMap)) return false;

    ArrayBackedFMap map = (ArrayBackedFMap)o;
    if (map.size() != size()) return false;

    int length = keys.length;
    for (int i = 0; i < length; i++) {
      if (keys[i] != map.keys[i] || values[i] != map.values[i]) return false;
    }
    return true;
  }
}
