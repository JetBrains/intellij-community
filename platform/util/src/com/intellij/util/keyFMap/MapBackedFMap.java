// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.keyFMap;

import com.intellij.openapi.util.Key;
import com.intellij.util.ArrayUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

@ApiStatus.Internal
public class MapBackedFMap implements KeyFMap {
  /**
   * Maps a key index ({@link Key#hashCode()}) to its value. Never mutated after the constructor has finished.
   */
  protected final @NotNull Int2ObjectOpenHashMap<Object> map;

  protected MapBackedFMap(@NotNull MapBackedFMap oldMap, final int keyToExclude) {
    map = new Int2ObjectOpenHashMap<>(oldMap.map.size());

    ObjectIterator<Int2ObjectMap.Entry<Object>> iterator = oldMap.map.int2ObjectEntrySet().fastIterator();
    while (iterator.hasNext()) {
      Int2ObjectMap.Entry<Object> entry = iterator.next();
      int key = entry.getIntKey();
      if (key != keyToExclude) {
        map.put(key, entry.getValue());
      }
      assert key >= 0 : key;
    }
    assert map.size() > ArrayBackedFMap.ARRAY_THRESHOLD;
  }

  protected MapBackedFMap(@NotNull MapBackedFMap oldMap, int newKey, @NotNull Object newValue) {
    map = new Int2ObjectOpenHashMap<>(oldMap.map.size() + 1);

    map.putAll(oldMap.map);
    map.put(newKey, newValue);
    assert map.size() > ArrayBackedFMap.ARRAY_THRESHOLD;
  }

  protected MapBackedFMap(int @NotNull [] keys, int newKey, @NotNull Object @NotNull [] values, @NotNull Object newValue) {
    map = new Int2ObjectOpenHashMap<>(keys.length + 1);

    for (int i = 0; i < keys.length; i++) {
      int key = keys[i];
      map.put(key, values[i]);
      assert key >= 0 : key;
    }
    map.put(newKey, newValue);
    assert newKey >= 0 : newKey;
    assert map.size() > ArrayBackedFMap.ARRAY_THRESHOLD;
  }

  /**
   * Builds a map out of the parallel {@code keys}/{@code values} arrays, where every key is a key index
   * ({@link Key#hashCode()}).
   */
  protected MapBackedFMap(int @NotNull [] keys, @NotNull Object @NotNull [] values) {
    map = new Int2ObjectOpenHashMap<>(keys.length);

    for (int i = 0; i < keys.length; i++) {
      int key = keys[i];
      assert key >= 0 : key;
      map.put(key, values[i]);
    }
    assert map.size() > ArrayBackedFMap.ARRAY_THRESHOLD;
  }

  /**
   * Returns the value stored for the given key index ({@link Key#hashCode()}), or {@code null} if there is none.
   */
  protected final @Nullable Object valueAt(int keyIndex) {
    return map.get(keyIndex);
  }

  @Override
  public @NotNull <V> KeyFMap plus(@NotNull Key<V> key, @NotNull V value) {
    int keyCode = key.hashCode();
    assert keyCode >= 0 : key;
    //noinspection unchecked
    V oldValue = (V)map.get(keyCode);
    return value == oldValue ? this : new MapBackedFMap(this, keyCode, value);
  }

  @Override
  public @NotNull KeyFMap minus(@NotNull Key<?> key) {
    int oldSize = map.size();
    int keyCode = key.hashCode();
    if (!map.containsKey(keyCode)) {
      return this;
    }
    if (oldSize == ArrayBackedFMap.ARRAY_THRESHOLD + 1) {
      int[] keys = map.keySet().toIntArray();
      int[] newKeys = ArrayUtil.remove(keys, ArrayUtil.indexOf(keys, keyCode));
      Arrays.sort(newKeys);
      Object[] newValues = new Object[newKeys.length];
      for (int i = 0; i < newKeys.length; i++) {
        Object value = map.get(newKeys[i]);
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
    return (V)map.get(key.hashCode());
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public Key<?> @NotNull [] getKeys() {
    return ArrayBackedFMap.getKeysByIndices(map.keySet().toIntArray());
  }

  @Override
  public int getValueIdentityHashCode() {
    int hash = 0;
    ObjectIterator<Int2ObjectMap.Entry<Object>> iterator = map.int2ObjectEntrySet().fastIterator();
    while (iterator.hasNext()) {
      Int2ObjectMap.Entry<Object> entry = iterator.next();
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
    if (other.getClass() != MapBackedFMap.class) {
      return false;
    }

    MapBackedFMap otherMap = (MapBackedFMap)other;
    if (otherMap.map.size() != map.size()) {
      return false;
    }
    ObjectIterator<Int2ObjectMap.Entry<Object>> iterator = map.int2ObjectEntrySet().fastIterator();
    while (iterator.hasNext()) {
      Int2ObjectMap.Entry<Object> next = iterator.next();
      if (otherMap.map.get(next.getIntKey()) != next.getValue()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    // the same as java.util.Map: a sum of (key.hashCode() ^ value.hashCode()), where the key index is the key hash code
    return map.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || o.getClass() != MapBackedFMap.class) return false;

    return map.equals(((MapBackedFMap)o).map);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    map.int2ObjectEntrySet().fastForEach(entry -> {
      s.append(s.length() == 0 ? "" : ", ").append(Key.getKeyByIndex(entry.getIntKey())).append(" -> ").append(entry.getValue());
    });
    return "[" + s + "]";
  }
}
