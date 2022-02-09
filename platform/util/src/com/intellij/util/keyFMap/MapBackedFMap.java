// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.keyFMap;

import com.intellij.openapi.util.Key;
import com.intellij.util.ArrayUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

final class MapBackedFMap extends Int2ObjectOpenHashMap<Object> implements KeyFMap {
  private MapBackedFMap(@NotNull MapBackedFMap oldMap, final int keyToExclude) {
    super(oldMap.size());

    oldMap.int2ObjectEntrySet().fastForEach(entry -> {
      int key = entry.getIntKey();
      if (key != keyToExclude) {
        put(key, entry.getValue());
      }
      assert key >= 0 : key;
    });
    assert size() > ArrayBackedFMap.ARRAY_THRESHOLD;
  }

  private MapBackedFMap(@NotNull MapBackedFMap oldMap, int newKey, @NotNull Object newValue) {
    super(oldMap.size() + 1);

    putAll(oldMap);
    put(newKey, newValue);
    assert size() > ArrayBackedFMap.ARRAY_THRESHOLD;
  }

  MapBackedFMap(int @NotNull [] keys, int newKey, @NotNull Object @NotNull [] values, @NotNull Object newValue) {
    super(keys.length + 1);

    for (int i = 0; i < keys.length; i++) {
      int key = keys[i];
      put(key, values[i]);
      assert key >= 0 : key;
    }
    put(newKey, newValue);
    assert newKey >= 0 : newKey;
    assert size() > ArrayBackedFMap.ARRAY_THRESHOLD;
  }

  @NotNull
  @Override
  public <V> KeyFMap plus(@NotNull Key<V> key, @NotNull V value) {
    int keyCode = key.hashCode();
    assert keyCode >= 0 : key;
    //noinspection unchecked
    V oldValue = (V)get(keyCode);
    return value == oldValue ? this : new MapBackedFMap(this, keyCode, value);
  }

  @NotNull
  @Override
  public KeyFMap minus(@NotNull Key<?> key) {
    int oldSize = size();
    int keyCode = key.hashCode();
    if (!containsKey(keyCode)) {
      return this;
    }
    if (oldSize == ArrayBackedFMap.ARRAY_THRESHOLD + 1) {
      int[] keys = keySet().toIntArray();
      int[] newKeys = ArrayUtil.remove(keys, ArrayUtil.indexOf(keys, keyCode));
      Arrays.sort(newKeys);
      Object[] newValues = new Object[newKeys.length];
      for (int i = 0; i < newKeys.length; i++) {
        Object value = get(newKeys[i]);
        assert value != null;
        newValues[i] = value;
      }
      return new ArrayBackedFMap(newKeys, newValues);
    }
    return new MapBackedFMap(this, keyCode);
  }

  @Override
  public <V> V get(@NotNull Key<V> key) {
    //noinspection unchecked
    return (V)get(key.hashCode());
  }

  @Override
  public Key<?> @NotNull [] getKeys() {
    return ArrayBackedFMap.getKeysByIndices(keySet().toIntArray());
  }

  @Override
  public int getValueIdentityHashCode() {
    int hash = 0;
    ObjectIterator<Entry<Object>> iterator = int2ObjectEntrySet().fastIterator();
    while (iterator.hasNext()) {
      Entry<Object> entry = iterator.next();
      int key = entry.getIntKey();
      hash = (hash * 31 + key) * 31 + System.identityHashCode(entry.getValue());
    }
    return hash;
  }

  @Override
  public boolean equalsByReference(@NotNull KeyFMap other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof MapBackedFMap) || other.size() != size()) {
      return false;
    }

    MapBackedFMap map = (MapBackedFMap)other;
    ObjectIterator<Entry<Object>> iterator = int2ObjectEntrySet().fastIterator();
    while (iterator.hasNext()) {
      Entry<Object> next = iterator.next();
      if (map.get(next.getIntKey()) != next.getValue()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    int2ObjectEntrySet().fastForEach(entry -> {
      s.append(s.length() == 0 ? "" : ", ").append(Key.getKeyByIndex(entry.getIntKey())).append(" -> ").append(entry.getValue());
    });
    return "[" + s + "]";
  }
}
