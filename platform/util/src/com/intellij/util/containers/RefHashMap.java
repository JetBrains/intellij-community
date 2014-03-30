/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.util.*;

abstract class RefHashMap<K, V> extends AbstractMap<K, V> implements Map<K, V> {
  private final MyMap myMap;
  private final ReferenceQueue<K> myReferenceQueue = new ReferenceQueue<K>();
  private final HardKey<K> myHardKeyInstance = new HardKey<K>(); // "singleton"
  private Set<Entry<K, V>> entrySet = null;
  private boolean processingQueue;

  public RefHashMap(int initialCapacity, float loadFactor, @NotNull TObjectHashingStrategy<Key<K>> strategy) {
    myMap = new MyMap(initialCapacity, loadFactor, strategy);
  }

  public RefHashMap(int initialCapacity, float loadFactor) {
    this(initialCapacity, loadFactor, ContainerUtil.<Key<K>>canonicalStrategy());
  }

  public RefHashMap(int initialCapacity) {
    this(initialCapacity, 0.8f);
  }

  public RefHashMap() {
    this(4);
  }

  public RefHashMap(@NotNull Map<K, V> t) {
    this(Math.max(2 * t.size(), 11), 0.75f);
    putAll(t);
  }

  public RefHashMap(@NotNull final TObjectHashingStrategy<K> hashingStrategy) {
    this(4, 0.8f, new TObjectHashingStrategy<Key<K>>() {
      @Override
      public int computeHashCode(final Key<K> object) {
        return hashingStrategy.computeHashCode(object.get());
      }

      @Override
      public boolean equals(final Key<K> o1, final Key<K> o2) {
        return hashingStrategy.equals(o1.get(), o2.get());
      }
    });
  }

  private class MyMap extends THashMap<Key<K>, V> {
    private MyMap(int initialCapacity, float loadFactor, @NotNull TObjectHashingStrategy<Key<K>> strategy) {
      super(initialCapacity, loadFactor, strategy);
    }

    @Override
    public void compact() {
      // do not compact the map during many gced references removal because it's bad for performance
      if (!processingQueue) {
        super.compact();
      }
    }

    private void compactIfNecessary() {
      if (_deadkeys > _size && capacity() > 42) {
        // Compact if more than 50% of all keys are dead. Also, don't trash small maps
        compact();
      }
    }

    @Override
    protected void rehash(int newCapacity) {
      // rehash should discard gced keys
      // because otherwise there is a remote probability of
      // having two (Weak|Soft)Keys with accidentally equal hashCodes and different but gced key values
      int oldCapacity = _set.length;
      Object[] oldKeys = _set;
      V[] oldVals = _values;

      _set = new Object[newCapacity];
      _values = (V[])new Object[newCapacity];

      for (int i = oldCapacity; i-- > 0; ) {
        Object o = oldKeys[i];
        if (o == null || o == REMOVED) continue;
        Key<K> k = (Key<K>)o;
        K key = k.get();
        if (key == null) continue;
        int index = insertionIndex(k);
        if (index < 0) {
          // make 'key' alive till this point to not allow 'o.referent' to be gced
          throwObjectContractViolation(_set[-index - 1], o + "; key: " + key);
        }
        _set[index] = o;
        _values[index] = oldVals[i];
      }
    }
  }

  protected interface Key<T> {
    T get();
  }

  protected abstract <T> Key<T> createKey(@NotNull T k, @NotNull ReferenceQueue<? super T> q);

  private static class HardKey<T> implements Key<T> {
    private T myObject;
    private int myHash;

    @Override
    public T get() {
      return myObject;
    }

    private void set(@NotNull T object) {
      myObject = object;
      myHash = object.hashCode();
    }

    private void clear() {
      myObject = null;
      myHash = 0;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      T t = myObject;
      T u = ((Key<T>)o).get();
      return t == u || t.equals(u);
    }

    public int hashCode() {
      return myHash;
    }
  }

  // returns true if some refs were tossed
  boolean processQueue() {
    boolean processed = false;
    try {
      processingQueue = true;
      Key<K> wk;
      while ((wk = (Key<K>)myReferenceQueue.poll()) != null) {
        myMap.remove(wk);
        processed = true;
      }
    }
    finally {
      processingQueue = false;
    }
    myMap.compactIfNecessary();
    return processed;
  }

  V removeKey(@NotNull Key<K> key) {
    return myMap.remove(key);
  }

  @NotNull
  Key<K> createKey(@NotNull K key) {
    return createKey(key, myReferenceQueue);
  }

  V putKey(@NotNull Key<K> weakKey, V value) {
    return myMap.put(weakKey, value);
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
    if (key == null) return false;
    // optimization:
    myHardKeyInstance.set((K)key);
    boolean result = myMap.containsKey(myHardKeyInstance);
    myHardKeyInstance.clear();
    return result;
  }

  @Override
  public V get(Object key) {
    if (key == null) return null;
    myHardKeyInstance.set((K)key);
    V result = myMap.get(myHardKeyInstance);
    myHardKeyInstance.clear();
    return result;
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
    myHardKeyInstance.set((K)key);
    V result = myMap.remove(myHardKeyInstance);
    myHardKeyInstance.clear();
    return result;
  }

  @Override
  public void clear() {
    processQueue();
    myMap.clear();
  }

  private static class MyEntry<K, V> implements Entry<K, V> {
    private final Entry<?, V> ent;
    private final K key; /* Strong reference to key, so that the GC
                                 will leave it alone as long as this Entry
                                 exists */

    private MyEntry(@NotNull Entry<?, V> ent, K key) {
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
      if (!(o instanceof Entry)) return false;
      Entry e = (Entry)o;
      return valEquals(key, e.getKey()) && valEquals(getValue(), e.getValue());
    }

    public int hashCode() {
      V v;
      return (key == null ? 0 : key.hashCode())
             ^ ((v = getValue()) == null ? 0 : v.hashCode());
    }
  }

  /* Internal class for entry sets */
  private class EntrySet extends AbstractSet<Entry<K, V>> {
    private final Set<Entry<Key<K>, V>> hashEntrySet = myMap.entrySet();

    @NotNull
    @Override
    public Iterator<Entry<K, V>> iterator() {
      return new Iterator<Entry<K, V>>() {
        private final Iterator<Entry<Key<K>, V>> hashIterator = hashEntrySet.iterator();
        private MyEntry<K, V> next = null;

        @Override
        public boolean hasNext() {
          while (hashIterator.hasNext()) {
            Entry<Key<K>, V> ent = hashIterator.next();
            Key<K> wk = ent.getKey();
            K k = null;
            if (wk != null && (k = wk.get()) == null) {
              /* Weak key has been cleared by GC */
              continue;
            }
            next = new MyEntry<K, V>(ent, k);
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
      for (Iterator i = iterator(); i.hasNext(); i.next()) j++;
      return j;
    }

    @Override
    public boolean remove(Object o) {
      processQueue();
      if (!(o instanceof Entry)) return false;
      Entry<K, V> e = (Entry<K, V>)o;
      V ev = e.getValue();

      // optimization: do not recreate the key
      myHardKeyInstance.set(e.getKey());
      Key<K> key = myHardKeyInstance;

      V hv = myMap.get(key);
      boolean toRemove = hv == null ? ev == null && myMap.containsKey(key) : hv.equals(ev);
      if (toRemove) {
        myMap.remove(key);
      }
      myHardKeyInstance.clear();
      return toRemove;
    }

    public int hashCode() {
      int h = 0;
      for (Entry entry : hashEntrySet) {
        Key wk = (Key)entry.getKey();
        if (wk == null) continue;
        Object v;
        h += wk.hashCode() ^ ((v = entry.getValue()) == null ? 0 : v.hashCode());
      }
      return h;
    }
  }


  @NotNull
  @Override
  public Set<Entry<K, V>> entrySet() {
    if (entrySet == null) entrySet = new EntrySet();
    return entrySet;
  }
}
