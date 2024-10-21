// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * Concurrent weak key:K -> strong value:V map.
 * Null keys are NOT allowed
 * Null values are NOT allowed
 * To create, use {@link CollectionFactory#createConcurrentWeakMap}
 */
final class ConcurrentWeakHashMap<K, V> extends ConcurrentRefHashMap<K, V> {
  ConcurrentWeakHashMap(float loadFactor) {
    super(DEFAULT_CAPACITY, loadFactor, DEFAULT_CONCURRENCY_LEVEL, HashingStrategy.canonical());
  }

  ConcurrentWeakHashMap(int initialCapacity,
                        float loadFactor,
                        int concurrencyLevel,
                        @NotNull HashingStrategy<? super K> hashingStrategy) {
    super(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  ConcurrentWeakHashMap(@NotNull HashingStrategy<? super K> hashingStrategy) {
    super(DEFAULT_CAPACITY, LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL, hashingStrategy);
  }

  private static final class WeakKey<K> extends WeakReference<K> implements KeyReference<K> {
    private final int myHash; /* Hashcode of key, stored here since the key may be tossed by the GC */
    private final @NotNull HashingStrategy<? super K> myStrategy;

    private WeakKey(@NotNull K k,
                    int hash,
                    @NotNull HashingStrategy<? super K> strategy,
                    @NotNull ReferenceQueue<K> q) {
      super(k, q);
      myStrategy = strategy;
      myHash = hash;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof KeyReference)) return false;
      K t = get();
      //noinspection unchecked
      K u = ((KeyReference<K>)o).get();
      if (t == null || u == null) return false;
      return t == u || myStrategy.equals(t, u);
    }

    @Override
    public int hashCode() {
      return myHash;
    }
  }

  @Override
  protected @NotNull KeyReference<K> createKeyReference(@NotNull K key,
                                                        @NotNull HashingStrategy<? super K> hashingStrategy) {
    return new WeakKey<>(key, hashingStrategy.hashCode(key), hashingStrategy, myReferenceQueue);
  }
}
