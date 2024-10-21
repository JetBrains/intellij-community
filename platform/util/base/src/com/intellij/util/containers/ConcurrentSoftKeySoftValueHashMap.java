// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.ObjectUtilsRt;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

/**
 * Concurrent map with soft keys and soft values.
 * Null keys are NOT allowed
 * Null values are NOT allowed
 * To create, use {@link ContainerUtil#createConcurrentSoftKeySoftValueMap()}
 */
final class ConcurrentSoftKeySoftValueHashMap<K, V> extends ConcurrentWeakKeySoftValueHashMap<K,V> {
  ConcurrentSoftKeySoftValueHashMap(int initialCapacity,
                                    float loadFactor,
                                    int concurrencyLevel,
                                    @NotNull HashingStrategy<? super K> hashingStrategy) {
    super(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  private static class SoftKey<K, V> extends SoftReference<K> implements KeyReference<K, V> {
    private final int myHash; // Hash code of the key, stored here since the key may be tossed by the GC
    private final HashingStrategy<? super K> myStrategy;
    private final @NotNull ValueReference<K, V> myValueReference;

    SoftKey(@NotNull K k,
            @NotNull ValueReference<K, V> valueReference,
            @NotNull HashingStrategy<? super K> strategy,
            @NotNull ReferenceQueue<? super K> queue) {
      super(k, queue);
      myValueReference = valueReference;
      myHash = strategy.hashCode(k);
      myStrategy = strategy;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof KeyReference)) return false;
      K t = get();
      //noinspection unchecked
      K other = ((KeyReference<K,V>)o).get();
      if (t == null || other == null) return false;
      if (t == other) return true;
      return myHash == o.hashCode() && myStrategy.equals(t, other);
    }
    @Override
    public int hashCode() {
      return myHash;
    }

    @Override
    public @NotNull ValueReference<K, V> getValueReference() {
      return myValueReference;
    }
  }

  @Override
  @NotNull
  KeyReference<K,V> createKeyReference(@NotNull K k, final @NotNull V v) {
    final ValueReference<K, V> valueReference = createValueReference(v, myValueQueue);
    KeyReference<K,V> keyReference = new SoftKey<>(k, valueReference, myHashingStrategy, myKeyQueue);
    if (valueReference instanceof SoftValue) {
      ((SoftValue<K,V>)valueReference).myKeyReference = keyReference;
    }
    ObjectUtilsRt.reachabilityFence(k);
    return keyReference;
  }
}
