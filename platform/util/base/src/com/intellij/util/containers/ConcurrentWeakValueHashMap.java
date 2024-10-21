// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

/**
 * Concurrent strong key:K -> weak value:V map
 * Null keys are NOT allowed
 * Null values are NOT allowed
 * Use {@link ContainerUtil#createConcurrentWeakValueMap()} to create
 */
final class ConcurrentWeakValueHashMap<K,V> extends ConcurrentRefValueHashMap<K,V> {

  ConcurrentWeakValueHashMap(@Nullable BiConsumer<? super @NotNull ConcurrentMap<K,V>, ? super K> evictionListener) {
    super(evictionListener);
  }

  private static final class MyWeakReference<K, V> extends WeakReference<V> implements ValueReference<K, V> {
    private final K key;
    private MyWeakReference(@NotNull K key, @NotNull V referent, @NotNull ReferenceQueue<V> q) {
      super(referent, q);
      this.key = key;
    }

    @Override
    public @NotNull K getKey() {
      return key;
    }

    // When referent is collected, equality should be identity-based (for the processQueues() remove this very same SoftValue)
    // otherwise it's just canonical equals on referents for replace(K,V,V) to work
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      @SuppressWarnings("unchecked")
      ValueReference<K,V> that = (ValueReference<K, V>)o;

      V v = get();
      V thatV = that.get();
      return key.equals(that.getKey()) && v != null && v.equals(thatV);
    }
  }

  @NotNull
  @Override
  ValueReference<K, V> createValueReference(@NotNull K key, @NotNull V value) {
    return new MyWeakReference<>(key, value, myQueue);
  }
}
