// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

/**
 * Concurrent soft key:K -> strong value:V map
 * Null keys are NOT allowed
 * Null values are NOT allowed
 * Use {@link ContainerUtil#createConcurrentSoftMap()} to create
 */
final class ConcurrentSoftHashMap<K, V> extends ConcurrentRefHashMap<K, V> {
  ConcurrentSoftHashMap(@Nullable BiConsumer<? super @NotNull ConcurrentMap<K,V>, ? super V> evictionListener) {
    super(evictionListener);
  }

  ConcurrentSoftHashMap(int initialCapacity,
                        float loadFactor,
                        int concurrencyLevel,
                        @NotNull HashingStrategy<? super K> hashingStrategy) {
    super(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  private static final class SoftKey<K> extends SoftReference<K> implements KeyReference<K> {
    private final int myHash; // Hashcode of key, stored here since the key may be tossed by the GC
    private final HashingStrategy<? super K> myStrategy;

    private SoftKey(@NotNull K k,
                    final int hash,
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
      K u = ((KeyReference<K>)o).get();
      if (t == u) return true;
      if (t == null || u == null) return false;
      return myStrategy.equals(t, u);
    }

    @Override
    public int hashCode() {
      return myHash;
    }
  }

  @Override
  protected @NotNull KeyReference<K> createKeyReference(@NotNull K key,
                                                        @NotNull HashingStrategy<? super K> hashingStrategy) {
    return new SoftKey<>(key, hashingStrategy.hashCode(key), hashingStrategy, myReferenceQueue);
  }
}
