// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;

final class WeakKeyWeakValueHashMap<K,V> extends RefKeyRefValueHashMap<K,V> implements Map<K,V>{
  WeakKeyWeakValueHashMap() {
    super(new WeakHashMap<>(4, 0.8f, HashingStrategy.canonical()));
  }

  private static final class WeakValueReference<K,V> extends WeakReference<V> implements ValueReference<K,V> {
    private final @NotNull RefHashMap.Key<K> key;

    private WeakValueReference(@NotNull RefHashMap.Key<K> key, V referent, ReferenceQueue<? super V> q) {
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
    return new WeakValueReference<>(key, referent, q);
  }
}
