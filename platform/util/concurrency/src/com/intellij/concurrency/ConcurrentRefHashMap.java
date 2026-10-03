// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.RefValueHashMapUtil;
import com.intellij.util.containers.ReferenceQueueable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Base class for concurrent maps with weak or soft keys and strong values.
 * Null keys and values are not allowed.
 */
abstract sealed  class ConcurrentRefHashMap<K, V> extends AbstractMap<K, V>
  implements ConcurrentMap<K, V>, HashingStrategy<K>, ReferenceQueueable
  permits ConcurrentSoftHashMap, ConcurrentWeakHashMap {
  static final float DEFAULT_LOAD_FACTOR = 0.75f;
  static final int DEFAULT_CAPACITY = 16;
  private static final int DEFAULT_CONCURRENCY_LEVEL = 16;

  private final ReferenceQueue<K> myReferenceQueue = new ReferenceQueue<>();
  private final ConcurrentMap<Object, V> myMap;
  private final @NotNull HashingStrategy<? super K> myHashingStrategy;
  private final @NotNull ReferenceStrength myReferenceStrength;
  private final @Nullable CollectionFactory.EvictionListener<K, V, ? super V> myEvictionListener;

  enum ReferenceStrength {WEAK, SOFT}

  ConcurrentRefHashMap(int initialCapacity,
                       float loadFactor,
                       @NotNull ReferenceStrength strength,
                       @Nullable HashingStrategy<? super K> hashingStrategy,
                       @Nullable CollectionFactory.EvictionListener<K, V, ? super V> keyEvictionListener) {
    myHashingStrategy = hashingStrategy == null ? this : hashingStrategy;
    myReferenceStrength = strength;
    myEvictionListener = keyEvictionListener;
    myMap = new ConcurrentHashMap<>(initialCapacity, loadFactor, DEFAULT_CONCURRENCY_LEVEL, new HashingStrategy<>() {
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

  private sealed interface KeyReference<K> {
    @Nullable K get();

    @Override
    boolean equals(Object object);

    @Override
    int hashCode();
  }

  private static final class WeakKey<K> extends WeakReference<K> implements KeyReference<K> {
    private final int myHash;

    private WeakKey(@NotNull K key, int hash, @NotNull ReferenceQueue<K> queue) {
      super(key, queue);
      myHash = hash;
    }

    @Override
    public int hashCode() {
      return myHash;
    }
  }

  private static final class SoftKey<K> extends SoftReference<K> implements KeyReference<K> {
    private final int myHash;

    private SoftKey(@NotNull K key, int hash, @NotNull ReferenceQueue<K> queue) {
      super(key, queue);
      myHash = hash;
    }

    @Override
    public int hashCode() {
      return myHash;
    }
  }

  private @NotNull KeyReference<K> createKeyReference(@NotNull K key) {
    int hash = myHashingStrategy.hashCode(key);
    return myReferenceStrength == ReferenceStrength.WEAK
           ? new WeakKey<>(key, hash, myReferenceQueue)
           : new SoftKey<>(key, hash, myReferenceQueue);
  }

  @SuppressWarnings("unchecked")
  private @Nullable K dereference(Object key) {
    return key instanceof KeyReference<?> ? (K)((KeyReference<?>)key).get() : (K)key;
  }

  @Override
  public boolean processQueue() {
    Reference<? extends K> reference;
    boolean processed = false;
    while ((reference = myReferenceQueue.poll()) != null) {
      V value = myMap.remove(reference);
      if (myEvictionListener != null) {
        myEvictionListener.evicted(this, reference.hashCode(), value);
      }
      processed = true;
    }
    return processed;
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
  public boolean containsKey(@NotNull Object key) {
    try {
      return myMap.containsKey(key);
    }
    finally {
      Reference.reachabilityFence(key);
    }
  }

  @Override
  public boolean containsValue(Object value) {
    throw RefValueHashMapUtil.pointlessContainsValue();
  }

  @Override
  public V get(@NotNull Object key) {
    try {
      return myMap.get(key);
    }
    finally {
      Reference.reachabilityFence(key);
    }
  }

  @Override
  public V put(@NotNull K key, @NotNull V value) {
    try {
      return myMap.put(createKeyReference(key), value);
    }
    finally {
      processQueue();
      Reference.reachabilityFence(key);
    }
  }

  @Override
  public V remove(@NotNull Object key) {
    try {
      return myMap.remove(key);
    }
    finally {
      processQueue();
      Reference.reachabilityFence(key);
    }
  }

  @Override
  public void clear() {
    myMap.clear();
    processQueue();
  }

  @SuppressWarnings("ClassCanBeRecord")
  private static final class RefEntry<K, V> implements Map.Entry<K, V> {
    private final Map.Entry<?, V> myEntry;
    private final K myKey;
    private final ConcurrentRefHashMap<K, V> myOwner;

    private RefEntry(@NotNull Map.Entry<?, V> entry, @NotNull K key, @NotNull ConcurrentRefHashMap<K, V> owner) {
      myEntry = entry;
      myKey = key;
      myOwner = owner;
    }

    @Override
    public K getKey() {
      return myKey;
    }

    @Override
    public V getValue() {
      return myEntry.getValue();
    }

    @Override
    public V setValue(@NotNull V value) {
      V previous = myEntry.setValue(value);
      myOwner.processQueue();
      return previous;
    }

    @Override
    public boolean equals(Object object) {
      if (!(object instanceof Map.Entry<?, ?> entry)) {
        return false;
      }
      return Objects.equals(myKey, entry.getKey()) && Objects.equals(getValue(), entry.getValue());
    }

    @Override
    public int hashCode() {
      return myKey.hashCode() ^ Objects.hashCode(getValue());
    }
  }

  private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
    private final Set<Map.Entry<Object, V>> myEntrySet = myMap.entrySet();

    @Override
    public @NotNull Iterator<Map.Entry<K, V>> iterator() {
      return new Iterator<>() {
        private final Iterator<Map.Entry<Object, V>> myIterator = myEntrySet.iterator();
        private RefEntry<K, V> myNext;
        private Object myLastReference;

        @Override
        public boolean hasNext() {
          if (myNext != null) {
            return true;
          }
          while (myIterator.hasNext()) {
            Map.Entry<Object, V> entry = myIterator.next();
            K key = dereference(entry.getKey());
            if (key == null) {
              continue;
            }
            myNext = new RefEntry<>(entry, key, ConcurrentRefHashMap.this);
            return true;
          }
          return false;
        }

        @Override
        public Map.Entry<K, V> next() {
          if (myNext == null && !hasNext()) {
            throw new NoSuchElementException();
          }
          RefEntry<K, V> entry = myNext;
          myNext = null;
          myLastReference = entry.myEntry.getKey();
          return entry;
        }

        @Override
        public void remove() {
          if (myLastReference == null) {
            throw new IllegalStateException();
          }
          myMap.remove(myLastReference);
          processQueue();
          myLastReference = null;
        }
      };
    }

    @Override
    public boolean isEmpty() {
      for (Map.Entry<Object, V> entry : myEntrySet) {
        if (dereference(entry.getKey()) == null) {
          continue;
        }
        return false;
      }
      return true;
    }

    @Override
    public int size() {
      int size = 0;
      for (Iterator<Map.Entry<K, V>> iterator = iterator(); iterator.hasNext(); iterator.next()) size++;
      return size;
    }

    @Override
    public boolean contains(Object object) {
      if (!(object instanceof Map.Entry<?, ?> entry)) {
        return false;
      }
      V value = get(entry.getKey());
      return value != null && value.equals(entry.getValue());
    }

    @Override
    public boolean remove(Object object) {
      if (!(object instanceof Map.Entry<?, ?> entry)) {
        return false;
      }
      return contains(entry) && ConcurrentRefHashMap.this.remove(entry.getKey(), entry.getValue());
    }

    @Override
    public void clear() {
      ConcurrentRefHashMap.this.clear();
    }
  }

  private Set<Map.Entry<K, V>> myEntrySet;

  @Override
  public @NotNull Set<Map.Entry<K, V>> entrySet() {
    Set<Map.Entry<K, V>> entries = myEntrySet;
    if (entries == null) {
      myEntrySet = entries = new EntrySet();
    }
    return entries;
  }

  @Override
  public V putIfAbsent(@NotNull K key, @NotNull V value) {
    try {
      return myMap.putIfAbsent(createKeyReference(key), value);
    }
    finally {
      processQueue();
      Reference.reachabilityFence(key);
    }
  }

  @Override
  public boolean remove(@NotNull Object key, @NotNull Object value) {
    try {
      return myMap.remove(key, value);
    }
    finally {
      processQueue();
      Reference.reachabilityFence(key);
    }
  }

  @Override
  public boolean replace(@NotNull K key, @NotNull V oldValue, @NotNull V newValue) {
    try {
      return myMap.replace(key, oldValue, newValue);
    }
    finally {
      processQueue();
      Reference.reachabilityFence(key);
    }
  }

  @Override
  public V replace(@NotNull K key, @NotNull V value) {
    try {
      return myMap.replace(key, value);
    }
    finally {
      processQueue();
      Reference.reachabilityFence(key);
    }
  }

  private int keyHashCode(@NotNull Object object) {
    //noinspection unchecked
    return object instanceof KeyReference<?>
           ? object.hashCode()
           : myHashingStrategy.hashCode((K)object);
  }

  private boolean keysEqual(Object object1, Object object2) {
    if (object1 == object2) {
      return true;
    }
    K key1 = dereference(object1);
    K key2 = dereference(object2);
    if (key1 == null || key2 == null) {
      return false;
    }
    return key1 == key2 || myHashingStrategy.equals(key1, key2);
  }

  @Override
  public int hashCode(@Nullable K object) {
    int hash = object == null ? 0 : object.hashCode();
    hash += ~(hash << 9);
    hash ^= hash >>> 14;
    hash += hash << 4;
    hash ^= hash >>> 10;
    return hash;
  }

  @Override
  public boolean equals(K object1, K object2) {
    return Objects.equals(object1, object2);
  }
}
