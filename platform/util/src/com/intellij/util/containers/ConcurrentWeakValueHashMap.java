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

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ConcurrentWeakValueHashMap<K,V> implements ConcurrentMap<K,V> {
  private final ConcurrentHashMap<K,MyReference<K,V>> myMap;
  private final ReferenceQueue<V> myQueue = new ReferenceQueue<V>();

  public ConcurrentWeakValueHashMap(final Map<K, V> map) {
    this();
    putAll(map);
  }

  public ConcurrentWeakValueHashMap() {
    myMap = new ConcurrentHashMap<K, MyReference<K,V>>();
  }
  public ConcurrentWeakValueHashMap(int initialCapaciy, float loadFactor, int concurrenycLevel) {
    myMap = new ConcurrentHashMap<K, MyReference<K,V>>(initialCapaciy, loadFactor, concurrenycLevel);
  }

  private static class MyReference<K,T> extends WeakReference<T> {
    private final K key;
    public MyReference(K key, T referent, ReferenceQueue<? super T> q) {
      super(referent, q);
      this.key = key;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final MyReference that = (MyReference)o;

      return key.equals(that.key) && Comparing.equal(get(), that.get());
    }

    public int hashCode() {
      return key.hashCode();
    }
  }

  public void processQueue() {
    while(true){
      MyReference<K,V> ref = (MyReference<K,V>)myQueue.poll();
      if (ref == null) {
        break;
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
    MyReference<K,V> oldRef = myMap.put(key, createRef(key, value));
    return oldRef != null ? oldRef.get() : null;
  }

  private MyReference<K, V> createRef(K key, V value) {
    return new MyReference<K,V>(key, value, myQueue);
  }

  public V putIfAbsent(@NotNull K key, V value) {
    while (true) {
      processQueue();
      MyReference<K, V> newRef = createRef(key, value);
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

  public boolean remove(@NotNull final Object key, final Object value) {
    processQueue();
    return myMap.remove(key, createRef((K)key, (V)value));
  }

  public boolean replace(@NotNull final K key, @NotNull final V oldValue, @NotNull final V newValue) {
    processQueue();
    return myMap.replace(key, createRef(key, oldValue), createRef(key, newValue));
  }

  public V replace(@NotNull final K key, @NotNull final V value) {
    processQueue();
    MyReference<K, V> ref = myMap.replace(key, createRef(key, value));
    return ref == null ? null : ref.get();
  }

  public V remove(Object key) {
    processQueue();
    MyReference<K,V> ref = myMap.remove(key);
    return ref != null ? ref.get() : null;
  }

  public void putAll(Map<? extends K, ? extends V> t) {
    processQueue();
    for (K k : t.keySet()) {
      V v = t.get(k);
      if (v != null) {
        put(k, v);
      }
    }
  }

  public void clear() {
    myMap.clear();
    processQueue();
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
    final Set<K> keys = keySet();
    Set<Entry<K, V>> entries = new HashSet<Entry<K, V>>();

    for (final K key : keys) {
      final V value = get(key);
      if (value != null) {
        entries.add(new Entry<K, V>() {
          public K getKey() {
            return key;
          }

          public V getValue() {
            return value;
          }

          public V setValue(V value) {
            throw new UnsupportedOperationException("setValue is not implemented");
          }
        });
      }
    }

    return entries;
  }

  @Override
  public String toString() {
    String s = "ConcurrentWeakValueHashMap size:" + size() + " [";
    for (K k : myMap.keySet()) {
      Object v = get(k);
      s += "'"+k + "': '" +v+"', ";
    }
    s += "] ";
    return s;
  }
}
