// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * return -1 instead of 0 if no such mapping exists
 * @deprecated Use {@link Object2IntOpenHashMap}
 */
@ApiStatus.Internal
@Deprecated
public final class ObjectIntHashMap<K> implements ObjectIntMap<K> {
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
  public int getOrDefault(@NotNull K key, int defaultValue) {
    return myMap.getOrDefault(key, defaultValue);
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
    return ContainerUtil.map(myMap.object2IntEntrySet(), e-> new IntEntry(e));
  }

  /**
   * @deprecated use {@link #getOrDefault(Object, int)}
   */
  @Deprecated
  public int get(@NotNull K key, int defaultValue) {
    return getOrDefault(key, defaultValue);
  }

  private final class IntEntry implements Entry<K> {
    private final Object2IntMap.Entry<? extends K> myEntry;

    IntEntry(@NotNull Object2IntMap.Entry<? extends K> entry) { myEntry = entry; }

    @Override
    public @NotNull K getKey() {
      return myEntry.getKey();
    }

    @Override
    public int getValue() {
      return myEntry.getIntValue();
    }

    @Override
    public String toString() {
      return myEntry.toString();
    }
  }
}
