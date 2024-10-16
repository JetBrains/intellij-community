// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Soft keys hash map.
 * Null keys are NOT allowed
 * Null values are allowed
 */
final class SoftHashMap<K,V> extends RefHashMap<K,V> {
  SoftHashMap(int initialCapacity) {
    super(initialCapacity, 0.8f, HashingStrategy.canonical());
  }

  SoftHashMap(@NotNull HashingStrategy<? super K> hashingStrategy) {
    super(hashingStrategy);
  }
  SoftHashMap(int initialCapacity, @NotNull HashingStrategy<? super K> hashingStrategy, @Nullable BiConsumer<? super @NotNull Map<K, V>, ? super V> evictionListener) {
    super(initialCapacity, 0.8f, hashingStrategy, evictionListener);
  }

  @Override
  protected @NotNull <T> Key<T> createKey(@NotNull T k, @NotNull HashingStrategy<? super T> strategy, @NotNull ReferenceQueue<? super T> q) {
    return new SoftKey<>(k, strategy, q);
  }

  private static final class SoftKey<T> extends SoftReference<T> implements Key<T> {
    private final int myHash;  /* Hash code of key, stored here since the key may be tossed by the GC */
    private final @NotNull HashingStrategy<? super T> myStrategy;

    private SoftKey(@NotNull T k, @NotNull HashingStrategy<? super T> strategy, @NotNull ReferenceQueue<? super T> q) {
      super(k, q);
      myStrategy = strategy;
      myHash = strategy.hashCode(k);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      if (myHash != o.hashCode()) return false;
      T t = get();
      @SuppressWarnings("unchecked") T u = ((Key<T>)o).get();
      if (t == null || u == null) return false;
      return keysEqual(t, u, myStrategy);
    }

    @Override
    public int hashCode() {
      return myHash;
    }

    @Override
    public String toString() {
      return "SoftHashMap.SoftKey(" + get() + ")";
    }
  }
}
