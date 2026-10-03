// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

/**
 * A concurrent map with soft keys and soft values.
 * Null keys and values are not allowed.
 */
final class ConcurrentSoftKeySoftValueHashMap<K, V> extends ConcurrentWeakKeySoftValueHashMap<K, V> {
  ConcurrentSoftKeySoftValueHashMap(int initialCapacity,
                                    float loadFactor,
                                    int concurrencyLevel,
                                    @NotNull HashingStrategy<? super K> hashingStrategy) {
    super(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  private static final class SoftKey<K, V> extends SoftReference<K> implements KeyReference<K, V> {
    private final int myHash; // Hash code of the key, stored here since the key may be collected by the GC
    private final HashingStrategy<? super K> myStrategy;
    private final @NotNull ValueReference<K, V> myValueReference;

    private SoftKey(@NotNull K key,
                    @NotNull ValueReference<K, V> valueReference,
                    @NotNull HashingStrategy<? super K> strategy,
                    @NotNull ReferenceQueue<? super K> queue) {
      super(key, queue);
      myValueReference = valueReference;
      myHash = strategy.hashCode(key);
      myStrategy = strategy;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (!(object instanceof KeyReference)) {
        return false;
      }
      K key = get();
      //noinspection unchecked
      K other = ((KeyReference<K, V>)object).get();
      if (key == null || other == null) {
        return false;
      }
      if (key == other) {
        return true;
      }
      return myHash == object.hashCode() && myStrategy.equals(key, other);
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
  KeyReference<K, V> createKeyReference(@NotNull K key, @NotNull V value) {
    ValueReference<K, V> valueReference = createValueReference(value, myValueQueue);
    KeyReference<K, V> keyReference = new SoftKey<>(key, valueReference, myHashingStrategy, myKeyQueue);
    if (valueReference instanceof SoftValue) {
      ((SoftValue<K, V>)valueReference).myKeyReference = keyReference;
    }
    Reference.reachabilityFence(key);
    Reference.reachabilityFence(value); // Do not queue the value before setting myKeyReference.
    return keyReference;
  }
}
