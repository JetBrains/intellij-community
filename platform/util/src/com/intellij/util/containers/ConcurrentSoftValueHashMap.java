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

package com.intellij.util.containers;

import com.intellij.reference.SoftReference;

import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

public final class ConcurrentSoftValueHashMap<K,V> implements ConcurrentMap<K,V> {
  private final ConcurrentMap<K,MyReference<K,V>> myMap = new ConcurrentHashMap<K, MyReference<K,V>>();
  private final ReferenceQueue<MyReference<K,V>> myQueue = new ReferenceQueue<MyReference<K,V>>();

  private static class MyReference<K,V> extends SoftReference<V> {
    final K key;
    public MyReference(K key, V referent, ReferenceQueue q) {
      super(referent, (ReferenceQueue<? super V>)q);
      this.key = key;
    }
  }

  public ConcurrentSoftValueHashMap() {
  }

  private void processQueue() {
    while(true){
      MyReference<K,V> ref = (MyReference<K,V>)myQueue.poll();
      if (ref == null) {
        return;
      }
      if (myMap.get(ref.key) == ref){
        myMap.remove(ref.key);
      }
    }
  }

  public V get(Object key) {
    MyReference<K,V> ref = myMap.get(key);
    if (ref == null) return null;
    return ref.get();
  }

  public V put(K key, V value) {
    processQueue();
    MyReference<K,V> oldRef = myMap.put(key, new MyReference<K,V>(key, value, myQueue));
    return oldRef != null ? oldRef.get() : null;
  }

  public void clear() {
    myMap.clear();
  }

  public int size() {
    return myMap.size(); //?
  }

  public boolean isEmpty() {
    return myMap.isEmpty(); //?
  }

  public boolean containsKey(Object key) {
    return get(key) != null;
  }

  public boolean containsValue(Object value) {
    throw new RuntimeException("method not implemented");
  }

  public Set<K> keySet() {
    return myMap.keySet();
  }

  public Collection<V> values() {
    List<V> result = new ArrayList<V>();
    final Collection<MyReference<K, V>> refs = myMap.values();
    for (MyReference<K, V> ref : refs) {
      final V value = ref.get();
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  public Set<Entry<K, V>> entrySet() {
    return new AbstractSet<Entry<K, V>>() {
      @Override
      public Iterator<Entry<K, V>> iterator() {
        final Iterator<Entry<K,MyReference<K, V>>> refEntries = myMap.entrySet().iterator();
        return new Iterator<Entry<K, V>>() {
          Entry<K, V> next;
          @Override
          public boolean hasNext() {
            while (next == null) {
              if (!refEntries.hasNext()) return false;
              Entry<K, MyReference<K, V>> ref = refEntries.next();
              final K k = ref.getKey();
              V v = ref.getValue().get();
              next = v == null ? null : new AbstractMap.SimpleEntry<K, V>(k,v){
                @Override
                public V setValue(V value) {
                  V old = super.setValue(value);
                  put(k, value);
                  return old;
                }
              };
            }
            return true;
          }

          @Override
          public Entry<K, V> next() {
            if (!hasNext()) throw new NoSuchElementException();
            Entry<K, V> r = next;
            next = null;
            return r;
          }

          @Override
          public void remove() {
            if (next == null) {
              throw new NoSuchElementException();
            }
            ConcurrentSoftValueHashMap.this.remove(next.getKey());
          }
        };
      }

      @Override
      public int size() {
        return myMap.size();
      }

      @Override
      public boolean remove(Object o) {
        return ConcurrentSoftValueHashMap.this.remove(((Entry)o).getKey()) != null;
      }

      @Override
      public void clear() {
        ConcurrentSoftValueHashMap.this.clear();
      }
    };
  }

  public V putIfAbsent(final K key, final V value) {
    while (true) {
      processQueue();
      MyReference<K, V> newRef = new MyReference<K, V>(key, value, myQueue);
      MyReference<K,V> oldRef = myMap.putIfAbsent(key, newRef);
      if (oldRef == null) return null;
      final V oldVal = oldRef.get();
      if (oldVal == null) {
        if (myMap.replace(key, oldRef, newRef)) return null;
      }
      else {
        return oldVal;
      }
    }
  }

  public boolean remove(final Object key, final Object value) {
    processQueue();
    return myMap.remove(key, new MyReference<K,V>((K)key, (V)value, myQueue));
  }

  public boolean replace(final K key, final V oldValue, final V newValue) {
    processQueue();
    return myMap.replace(key, new MyReference<K,V>(key, oldValue, myQueue), new MyReference<K,V>(key, newValue, myQueue));
  }

  public V replace(final K key, final V value) {
    processQueue();
    MyReference<K, V> ref = myMap.replace(key, new MyReference<K, V>(key, value, myQueue));
    return ref == null ? null : ref.get();
  }

  public V remove(Object key) {
    processQueue();
    MyReference<K,V> ref = myMap.remove(key);
    return ref != null ? ref.get() : null;
  }

  public void putAll(Map<? extends K, ? extends V> t) {
    for (Entry<? extends K, ? extends V> entry : t.entrySet()) {
      V v = entry.getValue();
      if (v != null) {
        put(entry.getKey(), v);
      }
    }
  }
}
