// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.RefValueHashMapUtil;
import com.intellij.util.containers.ReferenceQueueable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * A concurrent map with weak keys and soft values.
 * Null keys and values are not allowed.
 */
class ConcurrentWeakKeySoftValueHashMap<K, V> implements ConcurrentMap<K, V>, ReferenceQueueable {
  static final int DEFAULT_CAPACITY = 100;
  static final float DEFAULT_LOAD_FACTOR = 0.75f;
  static final int DEFAULT_CONCURRENCY_LEVEL = Runtime.getRuntime().availableProcessors();

  private final ConcurrentMap<Object, ValueReference<K, V>> myMap;
  final ReferenceQueue<K> myKeyQueue = new ReferenceQueue<>();
  final ReferenceQueue<V> myValueQueue = new ReferenceQueue<>();
  final @NotNull HashingStrategy<? super K> myHashingStrategy;

  ConcurrentWeakKeySoftValueHashMap(int initialCapacity,
                                    float loadFactor,
                                    int concurrencyLevel,
                                    @NotNull HashingStrategy<? super K> hashingStrategy) {
    myHashingStrategy = hashingStrategy;
    myMap = new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel, new HashingStrategy<>() {
      @Override
      public int hashCode(Object key) {
        return keyHashCode(key);
      }

      @Override
      public boolean equals(Object key1, Object key2) {
        return keysEqual(key1, key2);
      }
    });
  }

  interface KeyReference<K, V> extends Supplier<K> {
    @Override
    K get();

    @NotNull
    ValueReference<K, V> getValueReference();

    // Equality must work after the garbage collector clears a reference.
    @Override
    boolean equals(Object object);

    @Override
    int hashCode();
  }

  interface ValueReference<K, V> extends Supplier<V> {
    KeyReference<K, V> getKeyReference();

    @Override
    V get();
  }

  static final class WeakKey<K, V> extends WeakReference<K> implements KeyReference<K, V> {
    private final int myHash; // Hash code of the key, stored here since the key may be collected by the GC
    private final HashingStrategy<? super K> myStrategy;
    private final @NotNull ValueReference<K, V> myValueReference;

    WeakKey(@NotNull K key,
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

  static final class SoftValue<K, V> extends SoftReference<V> implements ValueReference<K, V> {
    // The circular dependency between the key and value references prevents a final field.
    volatile KeyReference<K, V> myKeyReference;

    private SoftValue(@NotNull V value, @NotNull ReferenceQueue<? super V> queue) {
      super(value, queue);
    }

    // When the referent is collected, use identity equality so processQueue removes this exact SoftValue.
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
      Object otherValue = ((ValueReference<K, V>)object).get();
      return value != null && value.equals(otherValue);
    }

    @Override
    public KeyReference<K, V> getKeyReference() {
      return myKeyReference;
    }
  }

  @NotNull
  KeyReference<K, V> createKeyReference(@NotNull K key, @NotNull V value) {
    Objects.requireNonNull(key);
    Objects.requireNonNull(value);
    ValueReference<K, V> valueReference = createValueReference(value, myValueQueue);
    KeyReference<K, V> keyReference = new WeakKey<>(key, valueReference, myHashingStrategy, myKeyQueue);
    if (valueReference instanceof SoftValue) {
      ((SoftValue<K, V>)valueReference).myKeyReference = keyReference;
    }
    Reference.reachabilityFence(key);
    Reference.reachabilityFence(value); // Do not queue the value before setting myKeyReference.
    return keyReference;
  }

  protected @NotNull ValueReference<K, V> createValueReference(@NotNull V value, @NotNull ReferenceQueue<? super V> queue) {
    return new SoftValue<>(value, queue);
  }

  @SuppressWarnings("unchecked")
  private K dereference(Object key) {
    return key instanceof KeyReference<?, ?> ? ((KeyReference<K, V>)key).get() : (K)key;
  }

  private int keyHashCode(Object key) {
    Objects.requireNonNull(key);
    //noinspection unchecked
    return key instanceof KeyReference<?, ?> ? key.hashCode() : myHashingStrategy.hashCode((K)key);
  }

  private boolean keysEqual(Object key1, Object key2) {
    if (key1 == key2) {
      return true;
    }
    K referent1 = dereference(key1);
    K referent2 = dereference(key2);
    if (referent1 == null || referent2 == null) {
      return false;
    }
    return referent1 == referent2 || myHashingStrategy.equals(referent1, referent2);
  }

  @Override
  public int size() {
    return myMap.size();
  }

  @Override
  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  @Override
  public void clear() {
    myMap.clear();
    processQueue();
  }

  @Override
  public V get(@NotNull Object key) {
    try {
      ValueReference<K, V> valueReference = myMap.get(key);
      return valueReference == null ? null : valueReference.get();
    }
    finally {
      Reference.reachabilityFence(key);
    }
  }

  @Override
  public boolean containsKey(@NotNull Object key) {
    throw RefValueHashMapUtil.pointlessContainsKey();
  }

  @Override
  public boolean containsValue(@NotNull Object value) {
    throw RefValueHashMapUtil.pointlessContainsValue();
  }

  @Override
  public V remove(@NotNull Object key) {
    try {
      ValueReference<K, V> valueReference = myMap.remove(key);
      return valueReference == null ? null : valueReference.get();
    }
    finally {
      processQueue();
      Reference.reachabilityFence(key);
    }
  }

  @Override
  public void putAll(@NotNull Map<? extends K, ? extends V> map) {
    for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public V put(@NotNull K key, @NotNull V value) {
    KeyReference<K, V> keyReference = createKeyReference(key, value);
    try {
      ValueReference<K, V> valueReference = keyReference.getValueReference();
      ValueReference<K, V> previousReference = myMap.put(keyReference, valueReference);
      return previousReference == null ? null : previousReference.get();
    }
    finally {
      processQueue();
      Reference.reachabilityFence(key);
      Reference.reachabilityFence(value);
    }
  }

  @ApiStatus.Internal
  @Override
  public boolean processQueue() {
    boolean removed = false;
    KeyReference<K, V> keyReference;
    //noinspection unchecked
    while ((keyReference = (KeyReference<K, V>)myKeyQueue.poll()) != null) {
      ValueReference<K, V> valueReference = keyReference.getValueReference();
      removed |= myMap.remove(keyReference, valueReference);
    }

    ValueReference<K, V> valueReference;
    //noinspection unchecked
    while ((valueReference = (ValueReference<K, V>)myValueQueue.poll()) != null) {
      keyReference = valueReference.getKeyReference();
      // keyReference can be null when createValueReference() was called and abandoned immediately, for example in replace(K, V).
      // Ignore this reference because it is not in the map.
      if (keyReference != null) {
        removed |= myMap.remove(keyReference, valueReference);
      }
    }

    return removed;
  }

  @Override
  public @NotNull Set<K> keySet() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull Collection<V> values() {
    List<V> values = new ArrayList<>();
    for (ValueReference<K, V> valueReference : myMap.values()) {
      V value = valueReference == null ? null : valueReference.get();
      if (value != null) {
        values.add(value);
      }
    }
    return values;
  }

  @Override
  public @NotNull Set<Entry<K, V>> entrySet() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(@NotNull Object key, @NotNull Object value) {
    try {
      ValueReference<K, V> valueReference = myMap.get(key);
      V referent = valueReference == null ? null : valueReference.get();
      return value.equals(referent) && myMap.remove(key, valueReference);
    }
    finally {
      processQueue();
      Reference.reachabilityFence(key);
    }
  }

  @Override
  public V putIfAbsent(@NotNull K key, @NotNull V value) {
    KeyReference<K, V> keyReference = createKeyReference(key, value);
    ValueReference<K, V> newReference = keyReference.getValueReference();
    try {
      V previous;
      while (true) {
        ValueReference<K, V> oldReference = myMap.putIfAbsent(keyReference, newReference);
        if (oldReference == null) {
          previous = null;
          break;
        }
        V oldValue = oldReference.get();
        if (oldValue == null) {
          if (myMap.replace(keyReference, oldReference, newReference)) {
            previous = null;
            break;
          }
        }
        else {
          previous = oldValue;
          break;
        }
        processQueue();
      }
      return previous;
    }
    finally {
      processQueue();
      Reference.reachabilityFence(key);
      Reference.reachabilityFence(value);
    }
  }

  @Override
  public boolean replace(@NotNull K key, @NotNull V oldValue, @NotNull V newValue) {
    Objects.requireNonNull(key);
    Objects.requireNonNull(oldValue);
    Objects.requireNonNull(newValue);
    try {
      ValueReference<K, V> oldValueReference = createValueReference(oldValue, myValueQueue);
      ValueReference<K, V> newValueReference = createValueReference(newValue, myValueQueue);

      boolean replaced = myMap.replace(key, oldValueReference, newValueReference);
      processQueue();
      return replaced;
    }
    finally {
      // Do not let these values enter a reference queue while the map operates on them.
      Reference.reachabilityFence(key);
      Reference.reachabilityFence(oldValue);
      Reference.reachabilityFence(newValue);
    }
  }

  @Override
  public V replace(@NotNull K key, @NotNull V value) {
    Objects.requireNonNull(key);
    Objects.requireNonNull(value);
    try {
      ValueReference<K, V> valueReference = createValueReference(value, myValueQueue);
      ValueReference<K, V> result = myMap.replace(key, valueReference);
      V previous = result == null ? null : result.get();
      processQueue();
      return previous;
    }
    finally {
      // Do not let the value enter a reference queue while the map operates on it.
      Reference.reachabilityFence(key);
      Reference.reachabilityFence(value);
    }
  }
}
