/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * Fully copied from java.util.WeakHashMap except "get" method optimization.
 */
public final class ConcurrentWeakHashMap<K,V> extends AbstractMap<K,V> implements ConcurrentMap<K,V> {
  private interface Key<K>{
    K get();
  }

  private static class WeakKey<K> extends WeakReference<K> implements Key<K> {
    private final int myHash;	/* Hashcode of key, stored here since the key may be tossed by the GC */

    private WeakKey(K k) {
      super(k);
      myHash = k.hashCode();
    }

    public static <K> WeakKey<K> create(K k) {
      return k == null ? null : new WeakKey<K>(k);
    }

    private WeakKey(K k, ReferenceQueue<K> q) {
      super(k, q);
      myHash = k.hashCode();
    }

    private static <K> WeakKey<K> create(K k, ReferenceQueue<K> q) {
      return k == null ? null : new WeakKey<K>(k, q);
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

  private static class HardKey implements Key {
    private Object myObject;
    private int myHash;

    public HardKey(Object object) {
      setObject(object);
    }

    private void setObject(final Object object) {
      myObject = object;
      myHash = object != null ? object.hashCode() : 0;
    }

    public Object get() {
      return myObject;
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

  private final ConcurrentMap<Key<K>, V> myMap;
  private static final Key NULL_KEY = new Key() {
    @Override
    public Object get() {
      return null;
    }
  };

  private final ReferenceQueue<K> myReferenceQueue = new ReferenceQueue<K>();

  private void processQueue() {
    WeakKey wk;
    while((wk = (WeakKey)myReferenceQueue.poll()) != null){
      myMap.remove(wk);
    }
  }

  public ConcurrentWeakHashMap(int initialCapacity, float loadFactor) {
    myMap = new ConcurrentHashMap<Key<K>, V>(initialCapacity, loadFactor, 4);
  }

  public ConcurrentWeakHashMap(int initialCapacity) {
    myMap = new ConcurrentHashMap<Key<K>, V>(initialCapacity);
  }

  public ConcurrentWeakHashMap() {
    myMap = new ConcurrentHashMap<Key<K>, V>();
  }

  public ConcurrentWeakHashMap(int initialCapacity, float loadFactor, int concurrencyLevel, TObjectHashingStrategy<Key<K>> hashingStrategy) {
    myMap = new ConcurrentHashMap<Key<K>, V>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  public ConcurrentWeakHashMap(Map<? extends K, ? extends V> t) {
    this(Math.max(2 * t.size(), 11), 0.75f);
    putAll(t);
  }

  public ConcurrentWeakHashMap(final TObjectHashingStrategy<K> hashingStrategy) {
    myMap = new ConcurrentHashMap<Key<K>, V>(new TObjectHashingStrategy<Key<K>>() {
      public int computeHashCode(final Key<K> object) {
        return hashingStrategy.computeHashCode(object.get());
      }

      public boolean equals(final Key<K> o1, final Key<K> o2) {
        return hashingStrategy.equals(o1.get(), o2.get());
      }
    } );
  }


  public int size() {
    return entrySet().size();
  }

  public boolean isEmpty() {
    return entrySet().isEmpty();
  }

  public boolean containsKey(Object key) {
    // optimization:
    if (key == null){
      return myMap.containsKey(NULL_KEY);
    }
    else{
      HardKey hardKey = createHardKey(key);
      boolean result = myMap.containsKey(hardKey);
      releaseHardKey(hardKey);
      return result;
    }
    //return myMap.containsKey(WeakKey.create(key));
  }

  private static final ThreadLocal<HardKey> myHardKey = new ThreadLocal<HardKey>(){
    @Override
    protected HardKey initialValue() {
      return new HardKey(null);
    }
  };

  private static HardKey createHardKey(final Object key) {
    HardKey hardKey = myHardKey.get();
    hardKey.setObject(key);
    return hardKey;
  }

  private static void releaseHardKey(HardKey key) {
    key.setObject(null);
  }

  public V get(Object key) {
    //return myMap.get(WeakKey.create(key));
    // optimization:
    if (key == null){
      return myMap.get(NULL_KEY);
    }
    else{
      HardKey hardKey = createHardKey(key);
      V result = myMap.get(hardKey);
      releaseHardKey(hardKey);
      return result;
    }
  }

  public V put(K key, V value) {
    processQueue();
    Key<K> weakKey = WeakKey.create(key, myReferenceQueue);
    Key<K> o = weakKey == null ? NULL_KEY : weakKey;
    return myMap.put(o, value);
  }

  public V remove(Object key) {
    processQueue();

    // optimization:
    if (key == null){
      return myMap.remove(NULL_KEY);
    }
    else{
      HardKey hardKey = createHardKey(key);
      V result = myMap.remove(hardKey);
      releaseHardKey(hardKey);
      return result;
    }
  }

  public void clear() {
    processQueue();
    myMap.clear();
  }

  private static class Entry<K,V> implements Map.Entry<K,V> {
    private final Map.Entry<?,V> ent;
    private final K key;	/* Strong reference to key, so that the GC
                                 will leave it alone as long as this Entry
                                 exists */

    Entry(Map.Entry<?,V> ent, K key) {
      this.ent = ent;
      this.key = key;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return ent.getValue();
    }

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
  private class EntrySet extends AbstractSet<Map.Entry<K,V>> {
    Set<Map.Entry<Key<K>,V>> hashEntrySet = myMap.entrySet();

    public Iterator<Map.Entry<K,V>> iterator() {
      return new Iterator<Map.Entry<K,V>>() {
        Iterator<Map.Entry<Key<K>,V>> hashIterator = hashEntrySet.iterator();
        Entry<K,V> next = null;

        public boolean hasNext() {
          while(hashIterator.hasNext()){
            Map.Entry<Key<K>, V> ent = hashIterator.next();
            WeakKey<K> wk = (WeakKey<K>)ent.getKey();
            K k = null;
            if (wk != null && (k = wk.get()) == null){
              /* Weak key has been cleared by GC */
              continue;
            }
            next = new Entry<K,V>(ent, k);
            return true;
          }
          return false;
        }

        public Map.Entry<K, V> next() {
          if (next == null && !hasNext()) {
            throw new NoSuchElementException();
          }
          Entry<K,V> e = next;
          next = null;
          return e;
        }

        public void remove() {
          hashIterator.remove();
        }
      };
    }

    public boolean isEmpty() {
      return !iterator().hasNext();
    }

    public int size() {
      int j = 0;
      for(Iterator i = iterator(); i.hasNext(); i.next()) j++;
      return j;
    }

    public boolean remove(Object o) {
      processQueue();
      if (!(o instanceof Map.Entry)) return false;
      Map.Entry e = (Map.Entry)o;
      Object ev = e.getValue();

      HardKey key = createHardKey(o);

      V hv = myMap.get(key);
      boolean toRemove = hv == null ? ev == null && myMap.containsKey(key) : hv.equals(ev);
      if (toRemove){
        myMap.remove(key);
      }

      releaseHardKey(key);
      return toRemove;
    }

    public int hashCode() {
      int h = 0;
      for (Object aHashEntrySet : hashEntrySet) {
        Map.Entry ent = (Map.Entry)aHashEntrySet;
        WeakKey wk = (WeakKey)ent.getKey();
        if (wk == null) continue;
        Object v;
        h += wk.hashCode() ^ ((v = ent.getValue()) == null ? 0 : v.hashCode());
      }
      return h;
    }

  }

  private Set<Map.Entry<K,V>> entrySet = null;

  public Set<Map.Entry<K,V>> entrySet() {
    if (entrySet == null) entrySet = new EntrySet();
    return entrySet;
  }

  public V putIfAbsent(@NotNull final K key, final V value) {
    processQueue();
    return myMap.putIfAbsent(WeakKey.create(key, myReferenceQueue), value);
  }

  public boolean remove(@NotNull final Object key, final Object value) {
    processQueue();
    return myMap.remove(WeakKey.create((K)key, myReferenceQueue), value);
  }

  public boolean replace(@NotNull final K key, @NotNull final V oldValue, @NotNull final V newValue) {
    processQueue();
    return myMap.replace(WeakKey.create(key, myReferenceQueue), oldValue,newValue);
  }

  public V replace(@NotNull final K key, @NotNull final V value) {
    processQueue();
    return myMap.replace(WeakKey.create(key, myReferenceQueue), value);
  }
}
