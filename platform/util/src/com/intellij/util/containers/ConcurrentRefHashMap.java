/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 18.12.2006
 * Time: 20:18:31
 */
package com.intellij.util.containers;

import com.intellij.util.IncorrectOperationException;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * Fully copied from java.util.WeakHashMap except "get" method optimization.
 */
abstract class ConcurrentRefHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V>, TObjectHashingStrategy<K> {
  protected final ReferenceQueue<K> myReferenceQueue = new ReferenceQueue<K>();
  private final ConcurrentHashMap<Key<K, V>, V> myMap; // hashing strategy must be canonical, we compute corresponding hash codes using our own myHashingStrategy
  @NotNull
  private final TObjectHashingStrategy<K> myHashingStrategy;

  public interface Key<K, V> {
    K get();

    V getValue();

    // MUST work even with gced references for the code in processQueue to work
    boolean equals(Object o);

    int hashCode();
  }

  protected abstract Key<K, V> createKey(@NotNull K key, V value, @NotNull TObjectHashingStrategy<K> hashingStrategy);


  private static class HardKey<K, V> implements Key<K, V> {
    private K myKey;
    private int myHash;

    private void setKey(K key, final int hash) {
      myKey = key;
      myHash = hash;
    }

    @Override
    public K get() {
      return myKey;
    }

    @Override
    public V getValue() {
      return null;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      Object t = get();
      Object u = ((Key)o).get();
      if (t == null || u == null) return false;
      if (t == u) return true;
      return t.equals(u);
    }

    public int hashCode() {
      return myHash;
    }
  }

  private static final Key NULL_KEY = new Key() {
    @Override
    public Object get() {
      return null;
    }

    @Override
    public Object getValue() {
      return null;
    }
  };

  // returns true if some keys were processed
  boolean processQueue() {
    Key<K, V> wk;
    boolean processed = false;
    while ((wk = (Key)myReferenceQueue.poll()) != null) {
      V value = wk.getValue();
      myMap.remove(wk, value);
      processed = true;
    }
    return processed;
  }

  public ConcurrentRefHashMap(Map<? extends K, ? extends V> t) {
    this(Math.max(2 * t.size(), 11), ConcurrentHashMap.DEFAULT_LOAD_FACTOR);
    putAll(t);
  }

  public ConcurrentRefHashMap() {
    this(ConcurrentHashMap.DEFAULT_INITIAL_CAPACITY);
  }

  public ConcurrentRefHashMap(int initialCapacity) {
    this(initialCapacity, ConcurrentHashMap.DEFAULT_LOAD_FACTOR);
  }

  private static final TObjectHashingStrategy THIS = new TObjectHashingStrategy() {
    @Override
    public int computeHashCode(Object object) {
      throw new IncorrectOperationException();
    }

    @Override
    public boolean equals(Object o1, Object o2) {
      throw new IncorrectOperationException();
    }
  };
  public ConcurrentRefHashMap(int initialCapacity, float loadFactor) {
    this(initialCapacity, loadFactor, 4, THIS);
  }

  public ConcurrentRefHashMap(@NotNull final TObjectHashingStrategy<K> hashingStrategy) {
    this(ConcurrentHashMap.DEFAULT_INITIAL_CAPACITY, ConcurrentHashMap.DEFAULT_LOAD_FACTOR, 2, hashingStrategy);
  }

  public ConcurrentRefHashMap(int initialCapacity,
                              float loadFactor,
                              int concurrencyLevel,
                              @NotNull TObjectHashingStrategy<K> hashingStrategy) {
    myHashingStrategy = hashingStrategy == THIS ? this : hashingStrategy;
    myMap = new ConcurrentHashMap<Key<K, V>, V>(initialCapacity, loadFactor, concurrencyLevel, CANONICAL);
  }

  @Override
  public int size() {
    return entrySet().size();
  }

  @Override
  public boolean isEmpty() {
    return entrySet().isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    // optimization:
    if (key == null) {
      return myMap.containsKey(NULL_KEY);
    }
    HardKey<K, V> hardKey = createHardKey((K)key);
    boolean result = myMap.containsKey(hardKey);
    releaseHardKey(hardKey);
    return result;
  }

  private static final ThreadLocal<HardKey> HARD_KEY = new ThreadLocal<HardKey>() {
    @Override
    protected HardKey initialValue() {
      return new HardKey();
    }
  };

  private HardKey<K, V> createHardKey(K key) {
    HardKey hardKey = HARD_KEY.get();
    hardKey.setKey(key, myHashingStrategy.computeHashCode(key));
    return hardKey;
  }

  private static void releaseHardKey(HardKey key) {
    key.setKey(null, 0);
  }

  @Override
  public V get(Object key) {
    //return myMap.get(WeakKey.create(key));
    // optimization:
    if (key == null) {
      return myMap.get(NULL_KEY);
    }
    HardKey<K, V> hardKey = createHardKey((K)key);
    V result = myMap.get(hardKey);
    releaseHardKey(hardKey);
    return result;
  }

  @Override
  public V put(K key, V value) {
    processQueue();
    Key<K, V> weakKey = key == null ? NULL_KEY : createKey(key, value, myHashingStrategy);
    return myMap.put(weakKey, value);
  }

  @Override
  public V remove(Object key) {
    processQueue();

    // optimization:
    if (key == null) {
      return myMap.remove(NULL_KEY);
    }
    HardKey hardKey = createHardKey((K)key);
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

    RefEntry(Map.Entry<?, V> ent, K key) {
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
    public V setValue(V value) {
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
    Set<Map.Entry<Key<K, V>, V>> hashEntrySet = myMap.entrySet();

    @NotNull
    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
      return new Iterator<Map.Entry<K, V>>() {
        Iterator<Map.Entry<Key<K, V>, V>> hashIterator = hashEntrySet.iterator();
        RefEntry<K, V> next = null;

        @Override
        public boolean hasNext() {
          while (hashIterator.hasNext()) {
            Map.Entry<Key<K, V>, V> ent = hashIterator.next();
            Key<K, V> wk = ent.getKey();
            K k = null;
            if (wk != null && (k = wk.get()) == null) {
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
        Key wk = (Key)ent.getKey();
        if (wk == null) continue;
        Object v;
        h += wk.hashCode() ^ ((v = ent.getValue()) == null ? 0 : v.hashCode());
      }
      return h;
    }
  }

  private Set<Map.Entry<K, V>> entrySet = null;

  @NotNull
  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    if (entrySet == null) entrySet = new EntrySet();
    return entrySet;
  }

  @Override
  public V putIfAbsent(@NotNull final K key, final V value) {
    processQueue();
    return myMap.putIfAbsent(createKey(key, value, myHashingStrategy), value);
  }

  @Override
  public boolean remove(@NotNull final Object key, final Object value) {
    processQueue();
    return myMap.remove(createKey((K)key, (V)value, myHashingStrategy), value);
  }

  @Override
  public boolean replace(@NotNull final K key, @NotNull final V oldValue, @NotNull final V newValue) {
    processQueue();
    return myMap.replace(createKey(key, oldValue, myHashingStrategy), oldValue, newValue);
  }

  @Override
  public V replace(@NotNull final K key, @NotNull final V value) {
    processQueue();
    return myMap.replace(createKey(key, value, myHashingStrategy), value);
  }

  // MAKE SURE IT CONSISTENT WITH com.intellij.util.containers.ConcurrentHashMap
  @Override
  public int computeHashCode(final K object) {
    int h = object.hashCode();
    h += ~(h << 9);
    h ^= (h >>> 14);
    h += (h << 4);
    h ^= (h >>> 10);
    return h;
  }

  @Override
  public boolean equals(final K o1, final K o2) {
    return o1.equals(o2);
  }

  @TestOnly
  int underlyingMapSize() {
    return myMap.size();
  }
}
