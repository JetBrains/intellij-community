// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.ObjectUtilsRt;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Base class for (soft/weak) keys -> hard values map
 * Null keys are NOT allowed
 * Null values are allowed
 */
abstract class RefHashMap<K, V> extends AbstractMap<K, V> implements Map<K, V>, ReferenceQueueable {
  private final MyMap myMap;
  private final ReferenceQueue<K> myReferenceQueue = new ReferenceQueue<>();
  private final HardKey myHardKeyInstance = new HardKey(); // "singleton"
  private final @NotNull HashingStrategy<? super K> myStrategy;
  private Set<Entry<K, V>> entrySet;
  private final BiConsumer<? super @NotNull Map<K, V>, ? super V> myEvictionListener;

  RefHashMap(int initialCapacity, float loadFactor, @NotNull HashingStrategy<? super K> strategy) {
    this(initialCapacity, loadFactor, strategy, null);
  }
  RefHashMap(int initialCapacity, float loadFactor, @NotNull HashingStrategy<? super K> strategy, @Nullable BiConsumer<? super @NotNull Map<K, V>, ? super V> evictionListener) {
    myStrategy = strategy;
    myMap = new MyMap(initialCapacity, loadFactor);
    myEvictionListener = evictionListener;
  }

  private RefHashMap(int initialCapacity, float loadFactor) {
    this(initialCapacity, loadFactor, HashingStrategy.canonical());
  }

  RefHashMap(int initialCapacity) {
    this(initialCapacity, 0.8f);
  }

  RefHashMap(@NotNull HashingStrategy<? super K> hashingStrategy) {
    this(4, 0.8f, hashingStrategy);
  }

  static <K> boolean keysEqual(K k1, K k2, @NotNull HashingStrategy<? super K> strategy) {
    return k1 == k2 || strategy.equals(k1, k2);
  }

  private class MyMap extends Object2ObjectOpenHashMap<Key<K>, V> {
    private MyMap(int initialCapacity, float loadFactor) {
      super(initialCapacity, loadFactor);
    }

    @Override
    protected void rehash(int newN) {
      // rehash should discard gced keys
      // because otherwise there is a remote probability of
      // having two (Weak|Soft)Keys with accidentally equal hashCodes and different but gced key values
      assert newN != 0;
      Object[] key = this.key;
      Object[] value = this.value;
      int mask = newN - 1; // Note that this is used by the hashing macro
      //noinspection unchecked
      Key<K>[] newKey = new Key[newN + 1];
      Object[] newValue = new Object[newN + 1];
      int pos;
      int keysToProcess = size;
      for (int i = n; i >= 0 && keysToProcess > 0; i--) {
        //noinspection unchecked
        Key<K> k = (Key<K>)key[i];
        if (k == null) {
          continue;
        }
        keysToProcess--;
        K referent = k.get();
        if (referent == null) {
          size--;
          continue;
        }
        if (!(newKey[pos = HashCommon.mix(k.hashCode()) & mask] == null)) {
          while (!(newKey[pos = (pos + 1) & mask] == null)) ;
        }
        newKey[pos] = k;
        newValue[pos] = value[i];
        // avoid inserting gced keys into new table
        ObjectUtilsRt.reachabilityFence(referent);
      }
      newValue[newN] = value[n];
      n = newN;
      this.mask = mask;
      maxFill = HashCommon.maxFill(n, f);
      this.key = newKey;
      this.value = (V[])newValue;
    }
  }

  @FunctionalInterface
  interface Key<T> {
    T get();
    @Override
    int hashCode();
    @Override
    boolean equals(Object o);
  }

  protected abstract @NotNull <T> Key<T> createKey(@NotNull T k, @NotNull HashingStrategy<? super T> strategy, @NotNull ReferenceQueue<? super T> q);

  private class HardKey implements Key<K> {
    private K myObject;
    private int myHash;

    @Override
    public K get() {
      return myObject;
    }

    private void set(@NotNull K object) {
      myObject = object;
      myHash = myStrategy.hashCode(object);
    }

    private void clear() {
      myObject = null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      K t = myObject;
      //noinspection unchecked
      K u = ((Key<K>)o).get();
      return keysEqual(t, u, myStrategy);
    }

    @Override
    public int hashCode() {
      return myHash;
    }
  }

  // returns true if some refs were tossed
  @Override
  public boolean processQueue() {
    boolean processed = false;
    Key<K> wk;
    //noinspection unchecked
    while ((wk = (Key<K>)myReferenceQueue.poll()) != null) {
      V v = removeKey(wk);
      if (myEvictionListener != null) {
        myEvictionListener.accept(this, v);
      }
      processed = true;
    }
    return processed;
  }

  V removeKey(@NotNull Key<K> key) {
    return myMap.remove(key);
  }

  @NotNull
  Key<K> createKey(@NotNull K key) {
    return createKey(key, myStrategy, myReferenceQueue);
  }

  V putKey(@NotNull Key<K> weakKey, V value) {
    return myMap.put(weakKey, value);
  }

  @Override
  public int size() {
    return myMap.size();
  }

  @Override
  public boolean isEmpty() {
    // make easier and alloc-free call to myMap first
    return myMap.isEmpty() || entrySet().isEmpty();
  }

  @Override
  public boolean containsKey(@NotNull Object key) {
    // optimization:
    //noinspection unchecked
    myHardKeyInstance.set((K)key);
    try {
      return myMap.containsKey(myHardKeyInstance);
    }
    finally {
      myHardKeyInstance.clear();
    }
  }

  @Override
  public boolean containsValue(Object value) {
    throw RefValueHashMapUtil.pointlessContainsValue();
  }

  @Override
  public V get(@NotNull Object key) {
    //noinspection unchecked
    myHardKeyInstance.set((K)key);
    try {
      return myMap.get(myHardKeyInstance);
    }
    finally {
      myHardKeyInstance.clear();
    }
  }

  @Override
  public V put(@NotNull K key, V value) {
    processQueue();
    return putKey(createKey(key), value);
  }

  @Override
  public V remove(@NotNull Object key) {
    processQueue();

    // optimization:
    //noinspection unchecked
    myHardKeyInstance.set((K)key);
    try {
      return myMap.remove(myHardKeyInstance);
    }
    finally {
      myHardKeyInstance.clear();
    }
  }

  @Override
  public void clear() {
    processQueue();
    myMap.clear();
  }

  private static final class MyEntry<K, V> implements Entry<K, V> {
    private final Entry<?, V> ent;
    private final K key; // Strong reference to key, so that the GC will leave it alone as long as this Entry exists
    private final int myKeyHashCode;
    private final @NotNull HashingStrategy<? super K> myStrategy;

    private MyEntry(@NotNull Entry<?, V> ent, @NotNull K key, int keyHashCode, @NotNull HashingStrategy<? super K> strategy) {
      this.ent = ent;
      this.key = key;
      myKeyHashCode = keyHashCode;
      myStrategy = strategy;
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
    public V setValue(V value) {
      return ent.setValue(value);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Entry)) return false;
      //noinspection unchecked
      Entry<K,V> e = (Entry<K,V>)o;
      return keysEqual(key, e.getKey(), myStrategy) && Objects.equals(getValue(), e.getValue());
    }

    @Override
    public int hashCode() {
      V v;
      return myKeyHashCode ^ ((v = getValue()) == null ? 0 : v.hashCode());
    }
  }

  /* Internal class for entry sets */
  private class EntrySet extends AbstractSet<Entry<K, V>> {
    private final Set<Entry<Key<K>, V>> hashEntrySet = myMap.entrySet();

    @Override
    public @NotNull Iterator<Entry<K, V>> iterator() {
      return new Iterator<Entry<K, V>>() {
        private final Iterator<Entry<Key<K>, V>> hashIterator = hashEntrySet.iterator();
        private MyEntry<K, V> next;

        @Override
        public boolean hasNext() {
          while (hashIterator.hasNext()) {
            Entry<Key<K>, V> ent = hashIterator.next();
            Key<K> wk = ent.getKey();
            K k;
            if ((k = wk.get()) == null) {
              // weak key has been cleared by GC, ignore
              continue;
            }
            next = new MyEntry<>(ent, k, wk.hashCode(), myStrategy);
            return true;
          }
          return false;
        }

        @Override
        public Entry<K, V> next() {
          if (next == null && !hasNext()) {
            throw new NoSuchElementException();
          }
          Entry<K, V> e = next;
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
      return !iterator().hasNext();
    }

    @Override
    public int size() {
      int j = 0;
      for (Iterator<Entry<K, V>> i = iterator(); i.hasNext(); i.next()) j++;
      return j;
    }

    @Override
    public boolean remove(Object o) {
      processQueue();
      if (!(o instanceof Entry)) return false;
      //noinspection unchecked
      Entry<K, V> e = (Entry<K, V>)o;
      V ev = e.getValue();

      // optimization: do not recreate the key
      HardKey key = myHardKeyInstance;
      boolean toRemove;
      try {
        key.set(e.getKey());

        V hv = myMap.get(key);
        toRemove = hv == null ? ev == null && myMap.containsKey(key) : hv.equals(ev);
        if (toRemove) {
          myMap.remove(key);
        }
      }
      finally {
        key.clear();
      }
      return toRemove;
    }

    @Override
    public int hashCode() {
      int h = 0;
      for (Entry<Key<K>,V> entry : hashEntrySet) {
        Key<K> wk = entry.getKey();
        if (wk == null) continue;
        Object v;
        h += wk.hashCode() ^ ((v = entry.getValue()) == null ? 0 : v.hashCode());
      }
      return h;
    }
  }

  @Override
  public @NotNull Set<Entry<K, V>> entrySet() {
    Set<Entry<K, V>> es = entrySet;
    if (es == null) {
      entrySet = es = new EntrySet();
    }
    return es;
  }
}
