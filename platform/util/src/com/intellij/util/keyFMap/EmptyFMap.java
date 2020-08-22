// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.keyFMap;

import com.intellij.openapi.util.Key;
import com.intellij.util.containers.UnmodifiableHashMap;
import org.jetbrains.annotations.NotNull;

final class EmptyFMap implements KeyFMap {
  private static final Key[] EMPTY_KEYS_ARRAY = {};

  EmptyFMap() {
  }

  @NotNull
  @Override
  public <V> KeyFMap plus(@NotNull Key<V> key, @NotNull V value) {
    return new OneElementFMap<>(key, value);
  }

  @NotNull
  @Override
  public KeyFMap minus(@NotNull Key<?> key) {
    return this;
  }

  @Override
  public <V> V get(@NotNull Key<V> key) {
    return null;
  }

  @Override
  public int size() {
    return 0;
  }

  @Override
  public Key @NotNull [] getKeys() {
    return EMPTY_KEYS_ARRAY;
  }

  @Override
  public String toString() {
    return "{}";
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public int getValueIdentityHashCode() {
    return 0;
  }

  @Override
  public boolean equalsByReference(KeyFMap other) {
    return other == this;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  static KeyFMap create() {
    return DebugFMap.DEBUG_FMAP ? new DebugFMap(UnmodifiableHashMap.empty()) : new EmptyFMap();
  }
}
