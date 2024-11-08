// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.ObjectUtilsRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Concurrent map with weak keys and soft values.
 * Null keys are NOT allowed
 * Null values are NOT allowed
 * @deprecated Use {@link ContainerUtil#createConcurrentWeakKeySoftValueMap()} instead
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
public class ConcurrentWeakKeySoftValueHashMap<K, V> implements ConcurrentMap<K, V> {
  private final ConcurrentMap<KeyReference<K,V>, ValueReference<K,V>> myMap;
  final ReferenceQueue<K> myKeyQueue = new ReferenceQueue<>();
  final ReferenceQueue<V> myValueQueue = new ReferenceQueue<>();
  final @NotNull HashingStrategy<? super K> myHashingStrategy;

  protected ConcurrentWeakKeySoftValueHashMap(int initialCapacity,
                                              float loadFactor,
                                              int concurrencyLevel) {
    this(initialCapacity, loadFactor, concurrencyLevel, HashingStrategy.canonical());
  }

  protected ConcurrentWeakKeySoftValueHashMap(int initialCapacity,
                                              float loadFactor,
                                              int concurrencyLevel,
                                              @NotNull HashingStrategy<? super K> hashingStrategy) {
    myHashingStrategy = hashingStrategy;
    myMap = new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel);
  }

  public interface KeyReference<K, V> extends Supplier<K> {
    @Override
    K get();

    @NotNull
    ValueReference<K,V> getValueReference(); // no strong references

    // MUST work even with gced references for the code in processQueue to work
    @Override
    boolean equals(Object o);

    @Override
    int hashCode();
  }

  public interface ValueReference<K, V> extends Supplier<V> {
    @NotNull
    KeyReference<K,V> getKeyReference(); // no strong references

    @Override
    V get();
  }

  static final class WeakKey<K, V> extends WeakReference<K> implements KeyReference<K, V> {
    private final int myHash; // Hash code of the key, stored here since the key may be tossed by the GC
    private final HashingStrategy<? super K> myStrategy;
    private final @NotNull ValueReference<K, V> myValueReference;

    WeakKey(@NotNull K k,
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

  static final class SoftValue<K, V> extends SoftReference<V> implements ValueReference<K,V> {
   volatile @NotNull KeyReference<K, V> myKeyReference; // can't make it final because of circular dependency of KeyReference to ValueReference
   private SoftValue(@NotNull V value, @NotNull ReferenceQueue<? super V> queue) {
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
     Object thatV = ((ValueReference<K, V>)o).get();
     return v != null && v.equals(thatV);
   }

   @Override
   public @NotNull KeyReference<K, V> getKeyReference() {
     return myKeyReference;
   }
 }

  @NotNull
  KeyReference<K,V> createKeyReference(@NotNull K k, final @NotNull V v) {
    final ValueReference<K, V> valueReference = createValueReference(v, myValueQueue);
    KeyReference<K,V> keyReference = new WeakKey<>(k, valueReference, myHashingStrategy, myKeyQueue);
    if (valueReference instanceof SoftValue) {
      ((SoftValue<K, V>)valueReference).myKeyReference = keyReference;
    }
    ObjectUtilsRt.reachabilityFence(k);
    ObjectUtilsRt.reachabilityFence(v); // to avoid queueing in myValueQueue before setting its myKeyReference to not-null value
    return keyReference;
  }

  protected @NotNull ValueReference<K, V> createValueReference(@NotNull V value, @NotNull ReferenceQueue<? super V> queue) {
    return new SoftValue<>(value, queue);
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
    processQueues();
  }

  /////////////////////////////
  private static class HardKey<K,V> implements KeyReference<K,V> {
    private K myKey;
    private int myHash;

    private void set(@NotNull K key, int hash) {
      myKey = key;
      myHash = hash;
    }

    private void clear() {
      myKey = null;
    }

    @Override
    public K get() {
      return myKey;
    }

    @Override
    public boolean equals(Object o) {
      return o.equals(this); // see WeakKey.equals()
    }

    @Override
    public int hashCode() {
      return myHash;
    }

    @Override
    public @NotNull ValueReference<K, V> getValueReference() {
      throw new UnsupportedOperationException();
    }
  }

  private static final ThreadLocal<HardKey<?,?>> HARD_KEY = ThreadLocal.withInitial(() -> new HardKey<>());

  private @NotNull HardKey<K,V> createHardKey(@NotNull Object o) {
    //noinspection unchecked
    K key = (K)o;
    //noinspection unchecked
    HardKey<K,V> hardKey = (HardKey<K, V>)HARD_KEY.get();
    hardKey.set(key, myHashingStrategy.hashCode(key));
    return hardKey;
  }

///////////////////////////////////

  @Override
  public V get(@NotNull Object key) {
    HardKey<K,V> hardKey = createHardKey(key);
    try {
      ValueReference<K, V> valueReference = myMap.get(hardKey);
      return valueReference == null ? null : valueReference.get();
    }
    finally {
      hardKey.clear();
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
    HardKey<K,V> hardKey = createHardKey(key);
    try {
      ValueReference<K, V> valueReference = myMap.remove(hardKey);
      return valueReference == null ? null : valueReference.get();
    }
    finally {
      hardKey.clear();
      processQueues();
    }
  }

  @Override
  public void putAll(@NotNull Map<? extends K, ? extends V> m) {
    for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  @Override
  public V put(K key, V value) {
    KeyReference<K, V> keyReference = createKeyReference(key, value);
    ValueReference<K,V> valueReference = keyReference.getValueReference();
    ValueReference<K, V> prevValReference = myMap.put(keyReference, valueReference);
    processQueues();
    return prevValReference == null ? null : prevValReference.get();
  }

  private boolean processQueues() {
    boolean removed = false;
    KeyReference<K,V> keyReference;
    //noinspection unchecked
    while ((keyReference = (KeyReference<K, V>)myKeyQueue.poll()) != null) {
      ValueReference<K, V> valueReference = keyReference.getValueReference();
      removed |= myMap.remove(keyReference, valueReference);
    }

    ValueReference<K,V> valueReference;
    //noinspection unchecked
    while ((valueReference = (ValueReference<K, V>)myValueQueue.poll()) != null) {
      keyReference = valueReference.getKeyReference();
      removed |= myMap.remove(keyReference, valueReference);
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
      V v = valueReference == null ? null : valueReference.get();
      if (v != null) {
        values.add(v);
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
    HardKey<K, V> hardKey = createHardKey(key);
    try {
      ValueReference<K, V> valueReference = myMap.get(hardKey);
      V v = valueReference == null ? null : valueReference.get();
      return value.equals(v) && myMap.remove(hardKey, valueReference);
    }
    finally {
      hardKey.clear();
      processQueues();
    }
  }

  @Override
  public V putIfAbsent(@NotNull K key, @NotNull V value) {
    KeyReference<K, V> keyRef = createKeyReference(key, value);
    ValueReference<K, V> newRef = keyRef.getValueReference();
    V prev;
    while (true) {
      ValueReference<K, V> oldRef = myMap.putIfAbsent(keyRef, newRef);
      if (oldRef == null) {
        prev = null;
        break;
      }
      final V oldVal = oldRef.get();
      if (oldVal == null) {
        if (myMap.replace(keyRef, oldRef, newRef)) {
          prev = null;
          break;
        }
      }
      else {
        prev = oldVal;
        break;
      }
      processQueues();
    }
    processQueues();
    return prev;
  }

  @Override
  public boolean replace(@NotNull K key, @NotNull V oldValue, @NotNull V newValue) {
    HardKey<K, V> oldKeyReference = createHardKey(key);
    ValueReference<K, V> oldValueReference = null;
    try {
      oldValueReference = createValueReference(oldValue, myValueQueue);
      ValueReference<K, V> newValueReference = createValueReference(newValue, myValueQueue);

      boolean replaced = myMap.replace(oldKeyReference, oldValueReference, newValueReference);
      processQueues();
      return replaced;
    }
    finally {
      oldKeyReference.clear();
    }
  }

  @Override
  public V replace(@NotNull K key, @NotNull V value) {
    HardKey<K, V> keyReference = createHardKey(key);
    try {
      ValueReference<K, V> valueReference = createValueReference(value, myValueQueue);
      ValueReference<K, V> result = myMap.replace(keyReference, valueReference);
      V prev = result == null ? null : result.get();
      processQueues();
      return prev;
    }
    finally {
      keyReference.clear();
    }
  }
}
