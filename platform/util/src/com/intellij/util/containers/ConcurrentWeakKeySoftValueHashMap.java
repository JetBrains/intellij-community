/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.containers;

import com.intellij.openapi.util.Getter;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Concurrent map with weak keys and soft values.
 * Null keys are NOT allowed
 * Null values are NOT allowed
 * @deprecated Use {@link ContainerUtil#createConcurrentWeakKeySoftValueMap(int, float, int, TObjectHashingStrategy)} instead
 */
public class ConcurrentWeakKeySoftValueHashMap<K, V> implements ConcurrentMap<K, V> {
  private final ConcurrentMap<KeyReference<K,V>, ValueReference<K,V>> myMap;
  final ReferenceQueue<K> myKeyQueue = new ReferenceQueue<K>();
  final ReferenceQueue<V> myValueQueue = new ReferenceQueue<V>();
  @NotNull final TObjectHashingStrategy<K> myHashingStrategy;

  public ConcurrentWeakKeySoftValueHashMap(int initialCapacity,
                                           float loadFactor,
                                           int concurrencyLevel,
                                           @NotNull final TObjectHashingStrategy<K> hashingStrategy) {
    myHashingStrategy = hashingStrategy;
    myMap = ContainerUtil.newConcurrentMap(initialCapacity, loadFactor, concurrencyLevel);
  }

  public interface KeyReference<K, V> extends Getter<K> {
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

  public interface ValueReference<K, V> extends Getter<V> {
    @NotNull
    KeyReference<K,V> getKeyReference(); // no strong references

    @Override
    V get();
  }

  static class WeakKey<K, V> extends WeakReference<K> implements KeyReference<K, V> {
    private final int myHash; // Hash code of the key, stored here since the key may be tossed by the GC
    private final TObjectHashingStrategy<K> myStrategy;
    @NotNull private final ValueReference<K, V> myValueReference;

    WeakKey(@NotNull K k,
            @NotNull ValueReference<K, V> valueReference,
            @NotNull TObjectHashingStrategy<K> strategy,
            @NotNull ReferenceQueue<K> queue) {
      super(k, queue);
      myValueReference = valueReference;
      myHash = strategy.computeHashCode(k);
      myStrategy = strategy;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof KeyReference)) return false;
      K t = get();
      K other = ((KeyReference<K,V>)o).get();
      if (t == null || other == null) return false;
      if (t == other) return true;
      return myHash == o.hashCode() && myStrategy.equals(t, other);
    }
    @Override
    public int hashCode() {
      return myHash;
    }

    @NotNull
    @Override
    public ValueReference<K, V> getValueReference() {
      return myValueReference;
    }
  }

  static class SoftValue<K, V> extends SoftReference<V> implements ValueReference<K,V> {
   @NotNull volatile KeyReference<K, V> myKeyReference; // can't make it final because of circular dependency of KeyReference to ValueReference
   private SoftValue(@NotNull V value, @NotNull ReferenceQueue<V> queue) {
     super(value, queue);
   }

   // When referent is collected, equality should be identity-based (for the processQueues() remove this very same SoftValue)
   // otherwise it's just canonical equals on referents for replace(K,V,V) to work
   @Override
   public final boolean equals(final Object o) {
     if (this == o) return true;
     if (o == null) return false;

     V v = get();
     Object thatV = ((ValueReference)o).get();
     return v != null && thatV != null && v.equals(thatV);
   }

   @NotNull
   @Override
   public KeyReference<K, V> getKeyReference() {
     return myKeyReference;
   }
 }

  @NotNull
  KeyReference<K,V> createKeyReference(@NotNull K k, @NotNull final V v) {
    final ValueReference<K, V> valueReference = createValueReference(v, myValueQueue);
    WeakKey<K, V> keyReference = new WeakKey<K, V>(k, valueReference, myHashingStrategy, myKeyQueue);
    if (valueReference instanceof SoftValue) {
      ((SoftValue)valueReference).myKeyReference = keyReference;
    }
    return keyReference;
  }

  @NotNull
  protected ValueReference<K, V> createValueReference(@NotNull V value, @NotNull ReferenceQueue<V> queue) {
    return new SoftValue<K, V>(value, queue);
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
    processQueues();
    myMap.clear();
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

    @NotNull
    @Override
    public ValueReference<K, V> getValueReference() {
      throw new UnsupportedOperationException();
    }
  }

  private static final ThreadLocal<HardKey> HARD_KEY = new ThreadLocal<HardKey>() {
    @Override
    protected HardKey initialValue() {
      return new HardKey();
    }
  };

  @NotNull
  private HardKey<K,V> createHardKey(Object o) {
    @SuppressWarnings("unchecked") K key = (K)o;
    @SuppressWarnings("unchecked") HardKey<K,V> hardKey = HARD_KEY.get();
    hardKey.set(key, myHashingStrategy.computeHashCode(key));
    return hardKey;
  }

///////////////////////////////////

  @Override
  public V get(@NotNull Object key) {
    HardKey<K,V> hardKey = createHardKey(key);
    ValueReference<K, V> valueReference = myMap.get(hardKey);
    V v = com.intellij.reference.SoftReference.deref(valueReference);
    hardKey.clear();
    return v;
  }

  @Override
  public boolean containsKey(Object key) {
    HardKey<K,V> hardKey = createHardKey(key);
    boolean result = myMap.containsKey(hardKey);
    hardKey.clear();
    return result;
  }

  @Override
  public boolean containsValue(Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public V remove(Object key) {
    processQueues();
    HardKey<K,V> hardKey = createHardKey(key);
    ValueReference<K, V> valueReference = myMap.remove(hardKey);
    V v = com.intellij.reference.SoftReference.deref(valueReference);
    hardKey.clear();
    return v;
  }

  @Override
  public void putAll(@NotNull Map<? extends K, ? extends V> m) {
    for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  @Override
  public V put(K key, V value) {
    processQueues();

    KeyReference<K, V> keyReference = createKeyReference(key, value);
    ValueReference<K,V> valueReference = keyReference.getValueReference();
    ValueReference<K, V> prevValReference = myMap.put(keyReference, valueReference);

    return com.intellij.reference.SoftReference.deref(prevValReference);
  }

  private boolean processQueues() {
    boolean removed = false;
    KeyReference<K,V> keyReference;
    while ((keyReference = (KeyReference<K, V>)myKeyQueue.poll()) != null) {
      ValueReference<K, V> valueReference = keyReference.getValueReference();
      removed |= myMap.remove(keyReference, valueReference);
    }

    ValueReference<K,V> valueReference;
    while ((valueReference = (ValueReference<K, V>)myValueQueue.poll()) != null) {
      keyReference = valueReference.getKeyReference();
      removed |= myMap.remove(keyReference, valueReference);
    }

    return removed;
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Collection<V> values() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Set<Entry<K, V>> entrySet() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(@NotNull Object key, @NotNull Object value) {
    processQueues();

    HardKey<K, V> hardKey = createHardKey(key);
    ValueReference<K, V> valueReference = myMap.get(hardKey);
    V v = com.intellij.reference.SoftReference.deref(valueReference);

    boolean result = value.equals(v) && myMap.remove(hardKey, valueReference);
    hardKey.clear();
    return result;
  }

  @Override
  public V putIfAbsent(@NotNull K key, @NotNull V value) {
    KeyReference<K, V> keyRef = createKeyReference(key, value);
    ValueReference<K, V> newRef = keyRef.getValueReference();
    while (true) {
      processQueues();
      ValueReference<K, V> oldRef = myMap.putIfAbsent(keyRef, newRef);
      if (oldRef == null) return null;
      final V oldVal = oldRef.get();
      if (oldVal == null) {
        if (myMap.replace(keyRef, oldRef, newRef)) return null;
      }
      else {
        return oldVal;
      }
    }
  }

  @Override
  public boolean replace(@NotNull K key, @NotNull V oldValue, @NotNull V newValue) {
    processQueues();
    KeyReference<K, V> oldKeyReference = createKeyReference(key, oldValue);
    ValueReference<K, V> oldValueReference = oldKeyReference.getValueReference();
    KeyReference<K, V> newKeyReference = createKeyReference(key, newValue);
    ValueReference<K, V> newValueReference = newKeyReference.getValueReference();

    return myMap.replace(oldKeyReference, oldValueReference, newValueReference);
  }

  @Override
  public V replace(@NotNull K key, @NotNull V value) {
    processQueues();
    KeyReference<K, V> keyReference = createKeyReference(key, value);
    ValueReference<K, V> valueReference = keyReference.getValueReference();
    ValueReference<K, V> result = myMap.replace(keyReference, valueReference);
    return com.intellij.reference.SoftReference.deref(result);
  }
}
