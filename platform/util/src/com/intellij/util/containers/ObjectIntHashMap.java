// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * return -1 instead of 0 if no such mapping exists
 */
public class ObjectIntHashMap<K> implements ObjectIntMap<K> {
  private final Object2IntMap<K> myMap;
  public ObjectIntHashMap() {
    this(10);
  }

  public ObjectIntHashMap(int initialCapacity) {
    myMap = new Object2IntOpenHashMap<>(initialCapacity);
    myMap.defaultReturnValue(-1);
  }

  @Override
  public int get(@NotNull K key) {
    return myMap.getInt(key);
  }

  @Override
  public int put(@NotNull K key, int value) {
    return myMap.put(key, value);
  }

  @Override
  public int remove(@NotNull K key) {
    return myMap.removeInt(key);
  }

  @Override
  public boolean containsKey(@NotNull K key) {
    return myMap.containsKey(key);
  }

  @Override
  public void clear() {
    myMap.clear();
  }

  @Override
  public @NotNull Set<K> keySet() {
    return myMap.keySet();
  }

  @Override
  public int size() {
    return myMap.size();
  }

  @Override
  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  @Override
  public int @NotNull [] values() {
    return myMap.values().toIntArray();
  }

  @Override
  public boolean containsValue(int value) {
    return myMap.containsValue(value);
  }

  @Override
  public @NotNull Iterable<Entry<K>> entries() {
    return ContainerUtil.map(myMap.object2IntEntrySet(), e->new Entry<K>() {
      @Override
      public @NotNull K getKey() {
        return e.getKey();
      }

      @Override
      public int getValue() {
        return e.getIntValue();
      }
    });
  }

  /**
   * If the map contains {@code key} then increment its value and return true, otherwise do nothing and return false
   */
  public boolean increment(@NotNull K key) {
    if (!myMap.containsKey(key)) {
      return false;
    }
    myMap.mergeInt(key, 0, (oldValue, __) -> oldValue + 1);
    return true;
  }

  public final int get(@NotNull K key, int defaultValue) {
    return containsKey(key) ? get(key) : defaultValue;
  }
}
