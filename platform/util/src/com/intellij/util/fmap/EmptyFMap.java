// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.fmap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

final class EmptyFMap<K, V> implements FMap<K, V> {

  static final EmptyFMap<?, ?> INSTANCE = new EmptyFMap<>();

  @Override
  public @NotNull FMap<K, V> plus(K key, V value) {
    return new OneKeyFMap<>(key, value);
  }

  @Override
  public @NotNull FMap<K, V> minus(K key) {
    return this;
  }

  @Override
  public @Nullable V get(K key) {
    return null;
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public int size() {
    return 0;
  }

  @Override
  public @NotNull Collection<K> keys() {
    return Collections.emptySet();
  }

  @Override
  public @NotNull Map<K, V> toMap() {
    return Collections.emptyMap();
  }

  @Override
  public String toString() {
    return "[:]";
  }
}
