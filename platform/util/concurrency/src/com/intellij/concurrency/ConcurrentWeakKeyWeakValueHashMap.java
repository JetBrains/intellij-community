// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * A concurrent map with weak keys and weak values.
 * Null keys and values are not allowed.
 */
final class ConcurrentWeakKeyWeakValueHashMap<K, V> extends ConcurrentWeakKeySoftValueHashMap<K, V> {
  ConcurrentWeakKeyWeakValueHashMap(int initialCapacity,
                                    float loadFactor,
                                    int concurrencyLevel,
                                    @NotNull HashingStrategy<? super K> hashingStrategy) {
    super(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  private static final class WeakValue<K, V> extends WeakReference<V> implements ValueReference<K, V> {
    // The circular dependency between the key and value references prevents a final field.
    private volatile KeyReference<K, V> myKeyReference;

    private WeakValue(@NotNull V value, @NotNull ReferenceQueue<? super V> queue) {
      super(value, queue);
    }

    // When the referent is collected, use identity equality so processQueue removes this exact WeakValue.
    // Otherwise, use canonical equality on referents so replace(K,V,V) works.
    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (object == null) {
        return false;
      }
      if (!(object instanceof ValueReference<?, ?>)) {
        return false;
      }

      V value = get();
      //noinspection unchecked
      V otherValue = ((ValueReference<K, V>)object).get();
      return value != null && value.equals(otherValue);
    }

    @Override
    public KeyReference<K, V> getKeyReference() {
      return myKeyReference;
    }
  }

  @Override
  @NotNull
  KeyReference<K, V> createKeyReference(@NotNull K key, @NotNull V value) {
    ValueReference<K, V> valueReference = createValueReference(value, myValueQueue);
    WeakKey<K, V> keyReference = new WeakKey<>(key, valueReference, myHashingStrategy, myKeyQueue);
    if (valueReference instanceof WeakValue) {
      ((WeakValue<K, V>)valueReference).myKeyReference = keyReference;
    }
    Reference.reachabilityFence(key);
    Reference.reachabilityFence(value); // Do not queue the value before setting myKeyReference.
    return keyReference;
  }

  @Override
  protected @NotNull ValueReference<K, V> createValueReference(@NotNull V value, @NotNull ReferenceQueue<? super V> queue) {
    return new WeakValue<>(value, queue);
  }
}
