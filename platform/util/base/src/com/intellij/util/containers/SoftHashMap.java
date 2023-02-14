// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;

/**
 * Soft keys hash map.
 * Null keys are NOT allowed
 * Null values are allowed
 */
final class SoftHashMap<K,V> extends RefHashMap<K,V> {
  SoftHashMap(int initialCapacity) {
    super(initialCapacity);
  }

  SoftHashMap(@NotNull HashingStrategy<? super K> hashingStrategy) {
    super(hashingStrategy);
  }

  @Override
  protected @NotNull <T> Key<T> createKey(@NotNull T k, @NotNull HashingStrategy<? super T> strategy, @NotNull ReferenceQueue<? super T> q) {
    return new SoftKey<>(k, strategy, q);
  }

  private static final class SoftKey<T> extends SoftReference<T> implements Key<T> {
    private final int myHash;  /* Hash code of key, stored here since the key may be tossed by the GC */
    @NotNull
    private final HashingStrategy<? super T> myStrategy;

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
