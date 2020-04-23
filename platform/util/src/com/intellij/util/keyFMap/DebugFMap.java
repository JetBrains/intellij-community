// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.keyFMap;

import com.intellij.openapi.util.Key;
import com.intellij.util.containers.UnmodifiableHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DebugFMap implements KeyFMap {
  private final UnmodifiableHashMap<Key<?>, Object> myMap;

  DebugFMap(@NotNull UnmodifiableHashMap<Key<?>, Object> map) {
    myMap = map;
  }

  @Override
  public @NotNull <V> KeyFMap plus(@NotNull Key<V> key, @NotNull V value) {
    UnmodifiableHashMap<Key<?>, Object> newMap = myMap.with(key, value);
    return newMap == myMap ? this : new DebugFMap(newMap);
  }

  @Override
  public @NotNull KeyFMap minus(@NotNull Key<?> key) {
    UnmodifiableHashMap<Key<?>, Object> newMap = myMap.without(key);
    return newMap == myMap ? this : new DebugFMap(newMap);
  }

  @Override
  public <V> @Nullable V get(@NotNull Key<V> key) {
    @SuppressWarnings("unchecked") V value = (V)myMap.get(key);
    return value;
  }

  @Override
  public int size() {
    return myMap.size();
  }

  @Override
  public Key @NotNull [] getKeys() {
    return myMap.keySet().toArray(new Key[0]);
  }

  @Override
  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  @Override
  public int getValueIdentityHashCode() {
    final int[] hash = {0};
    myMap.forEach((key, value) -> {
      hash[0] = (hash[0] * 31 + key.hashCode()) * 31 + System.identityHashCode(value);
    });
    return hash[0];
  }

  @Override
  public boolean equalsByReference(KeyFMap other) {
    if (this.size() != other.size()) return false;
    return myMap.entrySet().stream().noneMatch(entry -> entry.getValue() != other.get(entry.getKey()));
  }
}
