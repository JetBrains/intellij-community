/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * Base class for concurrent (soft/weak) key:K -> strong value:V map
 * Null keys are allowed
 * Null values are NOT allowed
 */
abstract class ConcurrentRefHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V>, TObjectHashingStrategy<K> {
  final ReferenceQueue<K> myReferenceQueue = new ReferenceQueue<K>();
  private final ConcurrentMap<KeyReference<K, V>, V> myMap; // hashing strategy must be canonical, we compute corresponding hash codes using our own myHashingStrategy
  @NotNull
  private final TObjectHashingStrategy<K> myHashingStrategy;

  interface KeyReference<K, V> {
    K get();

    @NotNull
    V getValue();

    // MUST work even with gced references for the code in processQueue to work
    boolean equals(Object o);

    int hashCode();
  }

  @NotNull
  abstract KeyReference<K, V> createKeyReference(@NotNull K key, @NotNull V value, @NotNull TObjectHashingStrategy<K> hashingStrategy);

  private static final HardKey NULL_KEY = new HardKey() {
    @Override
    public Object get() {
      return null;
    }

    @Override
    void setKey(Object key, int hash) {
    }
  };

  @NotNull
  private KeyReference<K, V> createKeyReference(@Nullable K key, @NotNull V value) {
    if (key == null) {
      //noinspection unchecked
      return NULL_KEY;
    }
    return createKeyReference(key, value, myHashingStrategy);
  }

  // returns true if some keys were processed
  boolean processQueue() {
    KeyReference<K, V> wk;
    boolean processed = false;
    while ((wk = (KeyReference)myReferenceQueue.poll()) != null) {
      V value = wk.getValue();
      myMap.remove(wk, value);
      processed = true;
    }
    return processed;
  }
  private static final float LOAD_FACTOR = 0.75f;
  private static final int DEFAULT_CAPACITY = 16;

  ConcurrentRefHashMap() {
    this(DEFAULT_CAPACITY);
  }

  ConcurrentRefHashMap(int initialCapacity) {
    this(initialCapacity, LOAD_FACTOR);
  }

  private static final TObjectHashingStrategy THIS = new TObjectHashingStrategy() {
    @Override
    public int computeHashCode(Object object) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o1, Object o2) {
      throw new UnsupportedOperationException();
    }
  };
  private ConcurrentRefHashMap(int initialCapacity, float loadFactor) {
    this(initialCapacity, loadFactor, 4, THIS);
  }

  ConcurrentRefHashMap(@NotNull final TObjectHashingStrategy<K> hashingStrategy) {
    this(DEFAULT_CAPACITY, LOAD_FACTOR, 2, hashingStrategy);
  }

  ConcurrentRefHashMap(int initialCapacity,
                              float loadFactor,
                              int concurrencyLevel,
                              @NotNull TObjectHashingStrategy<K> hashingStrategy) {
    myHashingStrategy = hashingStrategy == THIS ? this : hashingStrategy;
    myMap = ContainerUtil.newConcurrentMap(initialCapacity, loadFactor, concurrencyLevel);
  }

  @Override
  public int size() {
    return entrySet().size();
  }

  @Override
  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  @Override
  public boolean containsKey(@Nullable Object key) {
    HardKey<K, V> hardKey = createHardKey(key);
    boolean result = myMap.containsKey(hardKey);
    releaseHardKey(hardKey);
    return result;
  }

  private static class HardKey<K, V> implements KeyReference<K, V> {
    private K myKey;
    private int myHash;

    void setKey(K key, final int hash) {
      myKey = key;
      myHash = hash;
    }

    @Override
    public K get() {
      return myKey;
    }

    @NotNull
    @Override
    public V getValue() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
      return o.equals(this); // see com.intellij.util.containers.ConcurrentSoftHashMap.SoftKey or com.intellij.util.containers.ConcurrentWeakHashMap.WeakKey
    }

    public int hashCode() {
      return myHash;
    }
  }
  private static final ThreadLocal<HardKey> HARD_KEY = new ThreadLocal<HardKey>() {
    @Override
    protected HardKey initialValue() {
      return new HardKey();
    }
  };

  @NotNull
  private HardKey<K, V> createHardKey(@Nullable Object o) {
    if (o == null) {
      //noinspection unchecked
      return NULL_KEY;
    }
    @SuppressWarnings("unchecked") K key = (K)o;
    @SuppressWarnings("unchecked") HardKey<K,V> hardKey = HARD_KEY.get();
    hardKey.setKey(key, myHashingStrategy.computeHashCode(key));
    return hardKey;
  }

  private static void releaseHardKey(@NotNull HardKey<?,?> key) {
    key.setKey(null, 0);
  }

  @Override
  public V get(@Nullable Object key) {
    HardKey<K, V> hardKey = createHardKey(key);
    V result = myMap.get(hardKey);
    releaseHardKey(hardKey);
    return result;
  }

  @Override
  public V put(@Nullable K key, @NotNull V value) {
    processQueue();
    KeyReference<K, V> weakKey = createKeyReference(key, value);
    return myMap.put(weakKey, value);
  }

  @Override
  public V remove(@Nullable Object key) {
    processQueue();

    HardKey hardKey = createHardKey(key);
    V result = myMap.remove(hardKey);
    releaseHardKey(hardKey);
    return result;
  }

  @Override
  public void clear() {
    processQueue();
    myMap.clear();
  }

  private static class RefEntry<K, V> implements Map.Entry<K, V> {
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

    private static boolean valEquals(Object o1, Object o2) {
      return o1 == null ? o2 == null : o1.equals(o2);
    }

    public boolean equals(Object o) {
      if (!(o instanceof Map.Entry)) return false;
      Map.Entry e = (Map.Entry)o;
      return valEquals(key, e.getKey()) && valEquals(getValue(), e.getValue());
    }

    public int hashCode() {
      Object v;
      return (key == null ? 0 : key.hashCode()) ^ ((v = getValue()) == null ? 0 : v.hashCode());
    }
  }

  /* Internal class for entry sets */
  private class EntrySet extends AbstractSet<Map.Entry<K, V>> {
    Set<Map.Entry<KeyReference<K, V>, V>> hashEntrySet = myMap.entrySet();

    @NotNull
    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
      return new Iterator<Map.Entry<K, V>>() {
        private final Iterator<Map.Entry<KeyReference<K, V>, V>> hashIterator = hashEntrySet.iterator();
        private RefEntry<K, V> next;

        @Override
        public boolean hasNext() {
          while (hashIterator.hasNext()) {
            Map.Entry<KeyReference<K, V>, V> ent = hashIterator.next();
            KeyReference<K, V> wk = ent.getKey();
            K k = null;
            if (wk != null && (k = wk.get()) == null && wk != NULL_KEY) {
              /* Weak key has been cleared by GC */
              continue;
            }
            next = new RefEntry<K, V>(ent, k);
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
      return !iterator().hasNext();
    }

    @Override
    public int size() {
      int j = 0;
      for (Iterator i = iterator(); i.hasNext(); i.next()) j++;
      return j;
    }

    @Override
    public boolean remove(Object o) {
      processQueue();
      if (!(o instanceof Map.Entry)) return false;
      Map.Entry<K,V> e = (Map.Entry)o;
      V ev = e.getValue();

      HardKey key = createHardKey(e.getKey());

      V hv = myMap.get(key);
      boolean toRemove = hv == null ? ev == null && myMap.containsKey(key) : hv.equals(ev);
      if (toRemove) {
        myMap.remove(key);
      }

      releaseHardKey(key);
      return toRemove;
    }

    public int hashCode() {
      int h = 0;
      for (Object aHashEntrySet : hashEntrySet) {
        Map.Entry ent = (Map.Entry)aHashEntrySet;
        KeyReference wk = (KeyReference)ent.getKey();
        if (wk == null) continue;
        Object v;
        h += wk.hashCode() ^ ((v = ent.getValue()) == null ? 0 : v.hashCode());
      }
      return h;
    }
  }

  private Set<Map.Entry<K, V>> entrySet;

  @NotNull
  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    Set<Entry<K, V>> es = entrySet;
    if (es == null) entrySet = es = new EntrySet();
    return es;
  }

  @Override
  public V putIfAbsent(@Nullable final K key, @NotNull V value) {
    processQueue();
    return myMap.putIfAbsent(createKeyReference(key, value), value);
  }

  @Override
  public boolean remove(@Nullable final Object key, @NotNull Object value) {
    processQueue();
    return myMap.remove(createKeyReference((K)key, (V)value), value);
  }

  @Override
  public boolean replace(@Nullable final K key, @NotNull final V oldValue, @NotNull final V newValue) {
    processQueue();
    return myMap.replace(createKeyReference(key, oldValue), oldValue, newValue);
  }

  @Override
  public V replace(@Nullable final K key, @NotNull final V value) {
    processQueue();
    return myMap.replace(createKeyReference(key, value), value);
  }

  // MAKE SURE IT CONSISTENT WITH com.intellij.util.containers.ConcurrentHashMap
  @Override
  public int computeHashCode(final K object) {
    int h = object.hashCode();
    h += ~(h << 9);
    h ^= h >>> 14;
    h += h << 4;
    h ^= h >>> 10;
    return h;
  }

  @Override
  public boolean equals(final K o1, final K o2) {
    return o1.equals(o2);
  }
}
