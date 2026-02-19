// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Map;

final class WeakKeySoftValueHashMap<K,V> extends RefKeyRefValueHashMap<K,V> implements Map<K,V>{
  WeakKeySoftValueHashMap() {
    super(new WeakHashMap<>(4, 0.8f, HashingStrategy.canonical()));
  }

  private static final class SoftValueReference<K,V> extends SoftReference<V> implements ValueReference<K,V> {
    private final @NotNull RefHashMap.Key<K> key;

    private SoftValueReference(@NotNull RefHashMap.Key<K> key, V referent, ReferenceQueue<? super V> q) {
      super(referent, q);
      this.key = key;
    }

    @Override
    public @NotNull RefHashMap.Key<K> getKey() {
      return key;
    }
  }

  @Override
  protected @NotNull ValueReference<K, V> createValueReference(@NotNull RefHashMap.Key<K> key,
                                                               V referent,
                                                               ReferenceQueue<? super V> q) {
    return new SoftValueReference<>(key, referent, q);
  }
}
