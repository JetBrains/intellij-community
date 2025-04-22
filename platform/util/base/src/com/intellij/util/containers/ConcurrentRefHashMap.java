// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

/**
 * Base class for concurrent (soft/weak) key:K -> strong value:V map
 * Null keys are NOT allowed
 * Null values are NOT allowed
 */
abstract class ConcurrentRefHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V>, HashingStrategy<K> {
  final ReferenceQueue<K> myReferenceQueue = new ReferenceQueue<>();
  private final ConcurrentMap<KeyReference<K>, V> myMap; // hashing strategy must be canonical, we compute corresponding hash codes using our own myHashingStrategy
  private final @NotNull HashingStrategy<? super K> myHashingStrategy;

  static final float DEFAULT_LOAD_FACTOR = 0.75f;
  static final int DEFAULT_CAPACITY = 16;
  static final int DEFAULT_CONCURRENCY_LEVEL = Math.min(Runtime.getRuntime().availableProcessors(), 4);
  private final BiConsumer<? super @NotNull ConcurrentMap<K, V>, ? super V> myEvictionListener;

  ConcurrentRefHashMap(int initialCapacity,
                       float loadFactor,
                       int concurrencyLevel,
                       @Nullable HashingStrategy<? super K> hashingStrategy,
                       @Nullable BiConsumer<? super @NotNull ConcurrentMap<K,V>, ? super V> evictionListener) {
    myHashingStrategy = hashingStrategy == null ? this : hashingStrategy;
    myMap = new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel);
    myEvictionListener = evictionListener;
  }

  @FunctionalInterface
  interface KeyReference<K> {
    K get();

    // In case of gced reference, equality must be identity-based (to be able to remove stale key in processQueue), otherwise it's myHashingStrategy-based
    @Override
    boolean equals(Object o);

    @Override
    int hashCode();
  }

  abstract @NotNull KeyReference<K> createKeyReference(@NotNull K key, @NotNull HashingStrategy<? super K> hashingStrategy);

  private @NotNull KeyReference<K> createKeyReference(@NotNull K key) {
    return createKeyReference(key, myHashingStrategy);
  }

  // returns true if some keys were processed
  private boolean processQueue() {
    KeyReference<K> wk;
    boolean processed = false;
    //noinspection unchecked
    while ((wk = (KeyReference<K>)myReferenceQueue.poll()) != null) {
      V v = myMap.remove(wk);
      if (myEvictionListener != null) {
        myEvictionListener.accept(this, v);
      }
      processed = true;
    }
    return processed;
  }
  private static final HashingStrategy<?> THIS = new HashingStrategy<Object>() {
    @Override
    public int hashCode(Object object) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o1, Object o2) {
      throw new UnsupportedOperationException();
    }
  };

  @Override
  public int size() {
    return entrySet().size();
  }

  @Override
  public boolean isEmpty() {
    // make easier and alloc-free call to myMap first
    return myMap.isEmpty() || entrySet().isEmpty();
  }

  @Override
  public boolean containsKey(@NotNull Object key) {
    if (myMap.isEmpty()) {
      return false;
    }
    HardKey<K> hardKey = createHardKey(key);
    try {
      return myMap.containsKey(hardKey);
    }
    finally {
      hardKey.clear();
    }
  }

  @Override
  public boolean containsValue(Object value) {
    throw RefValueHashMapUtil.pointlessContainsValue();
  }

  private static class HardKey<K> implements KeyReference<K> {
    private K myKey;
    private int myHash;

    void setKey(K key, int hash) {
      myKey = key;
      myHash = hash;
    }

    @Override
    public K get() {
      return myKey;
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
      return o.equals(this); // see com.intellij.util.containers.ConcurrentSoftHashMap.SoftKey or com.intellij.util.containers.ConcurrentWeakHashMap.WeakKey
    }

    @Override
    public int hashCode() {
      return myHash;
    }

    private void clear() {
      setKey(null, 0);
    }
  }
  private static final ThreadLocal<HardKey<?>> HARD_KEY = ThreadLocal.withInitial(() -> new HardKey<>());

  private @NotNull HardKey<K> createHardKey(@NotNull Object o) {
    //noinspection unchecked
    K key = (K)o;
    //noinspection unchecked
    HardKey<K> hardKey = (HardKey<K>)HARD_KEY.get();
    hardKey.setKey(key, myHashingStrategy.hashCode(key));
    return hardKey;
  }

  @Override
  public V get(@NotNull Object key) {
    if (myMap.isEmpty()) return null;
    HardKey<K> hardKey = createHardKey(key);
    try {
      return myMap.get(hardKey);
    }
    finally {
      hardKey.clear();
    }
  }

  @Override
  public V put(@NotNull K key, @NotNull V value) {
    KeyReference<K> weakKey = createKeyReference(key);
    V prev = myMap.put(weakKey, value);
    processQueue();
    return prev;
  }

  @Override
  public V remove(@NotNull Object key) {
    HardKey<?> hardKey = createHardKey(key);
    try {
      return myMap.remove(hardKey);
    }
    finally {
      processQueue();
      hardKey.clear();
    }
  }

  @Override
  public void clear() {
    myMap.clear();
    processQueue();
  }

  private static final class RefEntry<K, V> implements Map.Entry<K, V> {
    private final Map.Entry<?, V> ent;
    private final K key; /* Strong reference to key, so that the GC
                                 will leave it alone as long as this Entry
                                 exists */

    RefEntry(@NotNull Map.Entry<?, V> ent, @Nullable K key) {
      this.ent = ent;
      this.key = key;
    }

    @Override
    public K getKey() {
      return key;
    }

    @Override
    public V getValue() {
      return ent.getValue();
    }

    @Override
    public V setValue(@NotNull V value) {
      return ent.setValue(value);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Map.Entry)) return false;
      //noinspection unchecked
      Map.Entry<K,V> e = (Map.Entry<K,V>)o;
      return Objects.equals(key, e.getKey()) && Objects.equals(getValue(), e.getValue());
    }

    @Override
    public int hashCode() {
      Object v;
      return (key == null ? 0 : key.hashCode()) ^ ((v = getValue()) == null ? 0 : v.hashCode());
    }
  }

  /* Internal class for entry sets */
  private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
    private final Set<Map.Entry<KeyReference<K>, V>> hashEntrySet = myMap.entrySet();

    @Override
    public @NotNull Iterator<Map.Entry<K, V>> iterator() {
      return new Iterator<Map.Entry<K, V>>() {
        private final Iterator<Map.Entry<KeyReference<K>, V>> hashIterator = hashEntrySet.iterator();
        private RefEntry<K, V> next;

        @Override
        public boolean hasNext() {
          while (hashIterator.hasNext()) {
            Map.Entry<KeyReference<K>, V> ent = hashIterator.next();
            KeyReference<K> wk = ent.getKey();
            K k = null;
            if (wk != null && (k = wk.get()) == null) {
              /* Weak key has been cleared by GC */
              continue;
            }
            next = new RefEntry<>(ent, k);
            return true;
          }
          return false;
        }

        @Override
        public Map.Entry<K, V> next() {
          if (next == null && !hasNext()) {
            throw new NoSuchElementException();
          }
          RefEntry<K, V> e = next;
          next = null;
          return e;
        }

        @Override
        public void remove() {
          hashIterator.remove();
        }
      };
    }

    @Override
    public boolean isEmpty() {
      for (Entry<KeyReference<K>, V> ent : hashEntrySet) {
        KeyReference<K> wk = ent.getKey();
        if (wk != null && wk.get() == null) {
          continue;
        }
        return false;
      }
      return true;
    }

    @Override
    public int size() {
      int j = 0;
      for (Iterator<Entry<K, V>> i = iterator(); i.hasNext(); i.next()) j++;
      return j;
    }

    @Override
    public boolean remove(Object o) {
      if (!(o instanceof Map.Entry)) return false;
      //noinspection unchecked
      Map.Entry<K,V> e = (Map.Entry<K,V>)o;
      V ev = e.getValue();
      HardKey<K> key = createHardKey(e.getKey());
      try {
        V hv = myMap.get(key);
        boolean toRemove = hv == null ? ev == null && myMap.containsKey(key) : hv.equals(ev);
        if (toRemove) {
          myMap.remove(key);
        }
        processQueue();
        return toRemove;
      }
      finally {
        key.clear();
      }
    }

    @Override
    public int hashCode() {
      int h = 0;
      for (Map.Entry<KeyReference<K>, V> entry : hashEntrySet) {
        KeyReference<K> wk = entry.getKey();
        if (wk == null) continue;
        Object v;
        h += wk.hashCode() ^ ((v = entry.getValue()) == null ? 0 : v.hashCode());
      }
      return h;
    }
  }

  private Set<Map.Entry<K, V>> entrySet;

  @Override
  public @NotNull Set<Map.Entry<K, V>> entrySet() {
    Set<Entry<K, V>> es = entrySet;
    if (es == null) entrySet = es = new EntrySet();
    return es;
  }

  @Override
  public V putIfAbsent(@NotNull K key, @NotNull V value) {
    V prev = myMap.putIfAbsent(createKeyReference(key), value);
    processQueue();
    return prev;
  }

  @Override
  public boolean remove(@NotNull Object key, @NotNull Object value) {
    HardKey<K> hardKey = createHardKey(key);
    try {
      boolean removed = myMap.remove(hardKey, value);
      processQueue();
      return removed;
    }
    finally {
      hardKey.clear();
    }
  }

  @Override
  public boolean replace(@NotNull K key, @NotNull V oldValue, @NotNull V newValue) {
    HardKey<K> hardKey = createHardKey(key);
    try {
      boolean replaced = myMap.replace(hardKey, oldValue, newValue);
      processQueue();
      return replaced;
    }
    finally {
      hardKey.clear();
    }
  }

  @Override
  public V replace(@NotNull K key, @NotNull V value) {
    HardKey<K> hardKey = createHardKey(key);
    try {
      V replaced = myMap.replace(hardKey, value);
      processQueue();
      return replaced;
    }
    finally {
      hardKey.clear();
    }
  }

  // MAKE SURE IT CONSISTENT WITH com.intellij.util.containers.ConcurrentHashMap
  @Override
  public int hashCode(@Nullable K object) {
    int h = object == null ? 0 : object.hashCode();
    h += ~(h << 9);
    h ^= h >>> 14;
    h += h << 4;
    h ^= h >>> 10;
    return h;
  }

  @Override
  public boolean equals(K o1, K o2) {
    return Objects.equals(o1, o2);
  }
}
