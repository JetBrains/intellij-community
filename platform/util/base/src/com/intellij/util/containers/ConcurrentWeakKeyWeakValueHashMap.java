// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.ObjectUtilsRt;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * Concurrent map with weak keys and weak values.
 * Null keys are NOT allowed
 * Null values are NOT allowed
 * Use {@link ContainerUtil#createConcurrentWeakKeyWeakValueMap()} to create this
 */
final class ConcurrentWeakKeyWeakValueHashMap<K, V> extends ConcurrentWeakKeySoftValueHashMap<K,V> {
  ConcurrentWeakKeyWeakValueHashMap(int initialCapacity,
                                    float loadFactor,
                                    int concurrencyLevel,
                                    @NotNull HashingStrategy<? super K> hashingStrategy) {
    super(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  private static final class WeakValue<K, V> extends WeakReference<V> implements ValueReference<K,V> {
    private volatile KeyReference<K, V> myKeyReference; // can't make it final because of circular dependency of KeyReference to ValueReference
    private WeakValue(@NotNull V value, @NotNull ReferenceQueue<? super V> queue) {
      super(value, queue);
    }

    // When referent is collected, equality should be identity-based (for the processQueues() remove this very same SoftValue)
    // otherwise it's just canonical equals on referents for replace(K,V,V) to work
    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null) return false;

      V v = get();
      //noinspection unchecked
      V thatV = ((ValueReference<K, V>)o).get();
      return v != null && v.equals(thatV);
    }

    @Override
    public KeyReference<K, V> getKeyReference() {
      return myKeyReference;
    }
 }

  @Override
  @NotNull
  KeyReference<K,V> createKeyReference(@NotNull K k, final @NotNull V v) {
    ValueReference<K, V> valueReference = createValueReference(v, myValueQueue);
    WeakKey<K, V> keyReference = new WeakKey<>(k, valueReference, myHashingStrategy, myKeyQueue);
    if (valueReference instanceof WeakValue) {
      ((WeakValue<K, V>)valueReference).myKeyReference = keyReference;
    }
    ObjectUtilsRt.reachabilityFence(k);
    ObjectUtilsRt.reachabilityFence(v); // to avoid queueing in myValueQueue before setting its myKeyReference to not-null value
    return keyReference;
  }

  @Override
  protected @NotNull ValueReference<K, V> createValueReference(@NotNull V value, @NotNull ReferenceQueue<? super V> queue) {
    return new WeakValue<>(value, queue);
  }
}
