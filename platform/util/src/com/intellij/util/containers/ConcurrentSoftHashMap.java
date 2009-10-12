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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * Fully copied from java.util.SoftHashMap except "get" method optimization.
 */
public final class ConcurrentSoftHashMap<K,V> extends AbstractMap<K,V> implements ConcurrentMap<K,V> {

  private static interface Key<K>{
    public K get();
  }

  private static class SoftKey extends SoftReference implements Key{
    private final int myHash;	/* Hashcode of key, stored here since the key may be tossed by the GC */

    private SoftKey(Object k) {
      super(k);
      myHash = k.hashCode();
    }

    public static SoftKey create(Object k) {
      return k != null ? new SoftKey(k) : null;
    }

    private SoftKey(Object k, ReferenceQueue q) {
      super(k, q);
      myHash = k.hashCode();
    }

    private static SoftKey create(Object k, ReferenceQueue q) {
      return k != null ? new SoftKey(k, q) : null;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      Object t = this.get();
      Object u = ((Key)o).get();
      if ((t == null) || (u == null)) return false;
      if (t == u) return true;
      return t.equals(u);
    }

    public int hashCode() {
      return myHash;
    }
  }

  private static class HardKey implements Key{
    private final Object myObject;
    private final int myHash;

    public HardKey(Object object) {
      myObject = object;
      myHash = object.hashCode();
    }

    public Object get() {
      return myObject;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      Object t = this.get();
      Object u = ((Key)o).get();
      if ((t == null) || (u == null)) return false;
      if (t == u) return true;
      return t.equals(u);
    }

    public int hashCode() {
      return myHash;
    }
  }

  private final ConcurrentMap myMap;
  private static final Object NULL_KEY = new Object();

  private final ReferenceQueue myReferenceQueue = new ReferenceQueue();

  private void processQueue() {
    SoftKey wk;
    while((wk = (SoftKey)myReferenceQueue.poll()) != null){
      myMap.remove(wk);
    }
  }

  public ConcurrentSoftHashMap(int initialCapacity, float loadFactor) {
    myMap = new ConcurrentHashMap(initialCapacity, loadFactor, 4);
  }

  public ConcurrentSoftHashMap(int initialCapacity) {
    myMap = new ConcurrentHashMap(initialCapacity);
  }

  public ConcurrentSoftHashMap() {
    myMap = new ConcurrentHashMap();
  }

  public ConcurrentSoftHashMap(Map t) {
    this(Math.max(2 * t.size(), 11), 0.75f);
    putAll(t);
  }

  public ConcurrentSoftHashMap(final TObjectHashingStrategy<K> hashingStrategy) {
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
      HardKey hardKey = new HardKey(key);
      boolean result = myMap.containsKey(hardKey);
      return result;
    }
    //return myMap.containsKey(SoftKey.create(key));
  }

  public V get(Object key) {
    //return myMap.get(SoftKey.create(key));
    // optimization:
    if (key == null){
      return (V)myMap.get(NULL_KEY);
    }
    else{
      HardKey hardKey = new HardKey(key);
      Object result = myMap.get(hardKey);
      return (V)result;
    }
  }

  public V put(K key, V value) {
    processQueue();
    SoftKey softKey = SoftKey.create(key, myReferenceQueue);
    return (V)myMap.put(softKey == null ? NULL_KEY : softKey, value);
  }

  public V remove(Object key) {
    processQueue();

    // optimization:
    if (key == null){
      return (V)myMap.remove(NULL_KEY);
    }
    else{
      HardKey hardKey = new HardKey(key);
      Object result = myMap.remove(hardKey);
      return (V)result;
    }
    //return myMap.remove(SoftKey.create(key));
  }

  public void clear() {
    processQueue();
    myMap.clear();
  }

  static private class Entry implements Map.Entry {
    private final Map.Entry ent;
    private final Object key;	/* Strong reference to key, so that the GC
                                 will leave it alone as long as this Entry
                                 exists */

    Entry(Map.Entry ent, Object key) {
      this.ent = ent;
      this.key = key;
    }

    public Object getKey() {
      return key;
    }

    public Object getValue() {
      return ent.getValue();
    }

    public Object setValue(Object value) {
      return ent.setValue(value);
    }

    private static boolean valEquals(Object o1, Object o2) {
      return (o1 == null) ? (o2 == null) : o1.equals(o2);
    }

    public boolean equals(Object o) {
      if (!(o instanceof Map.Entry)) return false;
      Map.Entry e = (Map.Entry)o;
      return (valEquals(key, e.getKey())
        && valEquals(getValue(), e.getValue()));
    }

    public int hashCode() {
      Object v;
      return (((key == null) ? 0 : key.hashCode())
        ^ (((v = getValue()) == null) ? 0 : v.hashCode()));
    }

  }

  /* Internal class for entry sets */
  private class EntrySet extends AbstractSet {
    Set hashEntrySet = myMap.entrySet();

    public Iterator iterator() {

      return new Iterator() {
        Iterator hashIterator = hashEntrySet.iterator();
        Entry next = null;

        public boolean hasNext() {
          while(hashIterator.hasNext()){
            Map.Entry ent = (Map.Entry)hashIterator.next();
            SoftKey wk = (SoftKey)ent.getKey();
            Object k = null;
            if ((wk != null) && ((k = wk.get()) == null)){
              /* Soft key has been cleared by GC */
              continue;
            }
            next = new Entry(ent, k);
            return true;
          }
          return false;
        }

        public Object next() {
          if ((next == null) && !hasNext())
            throw new NoSuchElementException();
          Entry e = next;
          next = null;
          return e;
        }

        public void remove() {
          hashIterator.remove();
        }

      };
    }

    public boolean isEmpty() {
      return !(iterator().hasNext());
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

      // optimization:
      HardKey key = new HardKey(o);
      //SoftKey key = SoftKey.create(e.getKey());

      Object hv = myMap.get(key);
      boolean toRemove = hv == null ? (ev == null && myMap.containsKey(key)) : hv.equals(ev);
      if (toRemove){
        myMap.remove(key);
      }
      return toRemove;
    }

    public int hashCode() {
      int h = 0;
      for(Iterator i = hashEntrySet.iterator(); i.hasNext();){
        Map.Entry ent = (Map.Entry)i.next();
        SoftKey wk = (SoftKey)ent.getKey();
        Object v;
        if (wk == null) continue;
        h += (wk.hashCode()
          ^ (((v = ent.getValue()) == null) ? 0 : v.hashCode()));
      }
      return h;
    }

  }

  private Set entrySet = null;

  public Set<Map.Entry<K,V>> entrySet() {
    if (entrySet == null) entrySet = new EntrySet();
    return entrySet;
  }

  public V putIfAbsent(final K key, final V value) {
    processQueue();
    return (V)myMap.putIfAbsent(SoftKey.create(key, myReferenceQueue), value);
  }

  public boolean remove(final Object key, final Object value) {
    processQueue();
    return myMap.remove(SoftKey.create(key, myReferenceQueue), value);
  }

  public boolean replace(final K key, final V oldValue, final V newValue) {
    processQueue();
    return myMap.replace(SoftKey.create(key, myReferenceQueue), oldValue,newValue);
  }

  public V replace(final K key, final V value) {
    processQueue();
    return (V)myMap.replace(SoftKey.create(key, myReferenceQueue), value);
  }
}
