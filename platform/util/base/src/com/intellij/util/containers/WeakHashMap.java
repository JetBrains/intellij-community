// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * Weak keys hash map.
 * Custom HashingStrategy is supported.
 * Null keys are NOT allowed
 * Null values are allowed
 * Use only when you need custom HashingStrategy. Otherwise {@link java.util.WeakHashMap} is good enough
 * @see CollectionFactory#createWeakMap(int, float, HashingStrategy)
 */
final class WeakHashMap<K, V> extends RefHashMap<K, V> {
  WeakHashMap(int initialCapacity, float loadFactor, @NotNull HashingStrategy<? super K> strategy) {
    super(initialCapacity, loadFactor, strategy);
  }

  @Override
  protected @NotNull <T> Key<T> createKey(@NotNull T k, @NotNull HashingStrategy<? super T> strategy, @NotNull ReferenceQueue<? super T> q) {
    return new WeakKey<>(k, strategy, q);
  }

  private static final class WeakKey<T> extends WeakReference<T> implements Key<T> {
    private final int myHash; // Hashcode of key, stored here since the key may be tossed by the GC
    private final @NotNull HashingStrategy<? super T> myStrategy;

    private WeakKey(@NotNull T k, @NotNull HashingStrategy<? super T> strategy, @NotNull ReferenceQueue<? super T> q) {
      super(k, q);
      myStrategy = strategy;
      myHash = strategy.hashCode(k);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      T t = get();
      @SuppressWarnings("unchecked") T u = ((Key<T>)o).get();
      return keysEqual(t, u, myStrategy);
    }

    @Override
    public int hashCode() {
      return myHash;
    }

    @Override
    public String toString() {
      Object t = get();
      return "WeakKey(" + t + ", " + myHash + ")";
    }
  }
}
