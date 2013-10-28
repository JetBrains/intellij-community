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

import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.HashSet;
import java.util.concurrent.ConcurrentMap;

abstract class ConcurrentRefValueHashMap<K, V> implements ConcurrentMap<K, V> {
  private final ConcurrentHashMap<K, MyValueReference<K, V>> myMap;
  protected final ReferenceQueue<V> myQueue = new ReferenceQueue<V>();

  public ConcurrentRefValueHashMap(@NotNull Map<K, V> map) {
    this();
    putAll(map);
  }

  public ConcurrentRefValueHashMap() {
    myMap = new ConcurrentHashMap<K, MyValueReference<K, V>>();
  }

  public ConcurrentRefValueHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
    myMap = new ConcurrentHashMap<K, MyValueReference<K, V>>(initialCapacity, loadFactor, concurrencyLevel);
  }

  public ConcurrentRefValueHashMap(int initialCapacity,
                                   float loadFactor,
                                   int concurrencyLevel,
                                   @NotNull TObjectHashingStrategy<K> hashingStrategy) {
    myMap = new ConcurrentHashMap<K, MyValueReference<K, V>>(initialCapacity, loadFactor, concurrencyLevel, hashingStrategy);
  }

  protected interface MyValueReference<K, V> {
    @NotNull
    K getKey();

    V get();
  }

  // returns true if some refs were tossed
  boolean processQueue() {
    boolean processed = false;

    while (true) {
      MyValueReference<K, V> ref = (MyValueReference<K, V>)myQueue.poll();
      if (ref == null) break;
      myMap.remove(ref.getKey(), ref);
      processed = true;
    }
    return processed;
  }

  @Override
  public V get(@NotNull Object key) {
    MyValueReference<K, V> ref = myMap.get(key);
    if (ref == null) return null;
    return ref.get();
  }

  @Override
  public V put(@NotNull K key, @NotNull V value) {
    processQueue();
    MyValueReference<K, V> oldRef = myMap.put(key, createRef(key, value));
    return oldRef != null ? oldRef.get() : null;
  }

  protected abstract MyValueReference<K, V> createRef(@NotNull K key, @NotNull V value);

  @Override
  public V putIfAbsent(@NotNull K key, @NotNull V value) {
    MyValueReference<K, V> newRef = createRef(key, value);
    while (true) {
      processQueue();
      MyValueReference<K, V> oldRef = myMap.putIfAbsent(key, newRef);
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

  @Override
  public boolean remove(@NotNull final Object key, @NotNull Object value) {
    processQueue();
    return myMap.remove(key, createRef((K)key, (V)value));
  }

  @Override
  public boolean replace(@NotNull final K key, @NotNull final V oldValue, @NotNull final V newValue) {
    processQueue();
    return myMap.replace(key, createRef(key, oldValue), createRef(key, newValue));
  }

  @Override
  public V replace(@NotNull final K key, @NotNull final V value) {
    processQueue();
    MyValueReference<K, V> ref = myMap.replace(key, createRef(key, value));
    return ref == null ? null : ref.get();
  }

  @Override
  public V remove(Object key) {
    processQueue();
    MyValueReference<K, V> ref = myMap.remove(key);
    return ref == null ? null : ref.get();
  }

  @Override
  public void putAll(@NotNull Map<? extends K, ? extends V> t) {
    processQueue();
    for (K k : t.keySet()) {
      V v = t.get(k);
      if (v != null) {
        put(k, v);
      }
    }
  }

  @Override
  public void clear() {
    myMap.clear();
    processQueue();
  }

  @Override
  public int size() {
    return myMap.size(); //?
  }

  @Override
  public boolean isEmpty() {
    return myMap.isEmpty(); //?
  }

  @Override
  public boolean containsKey(Object key) {
    return get(key) != null;
  }

  @Override
  public boolean containsValue(Object value) {
    throw new RuntimeException("method not implemented");
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    return myMap.keySet();
  }

  @NotNull
  @Override
  public Collection<V> values() {
    List<V> result = new ArrayList<V>();
    final Collection<MyValueReference<K, V>> refs = myMap.values();
    for (MyValueReference<K, V> ref : refs) {
      final V value = ref.get();
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  @NotNull
  @Override
  public Set<Entry<K, V>> entrySet() {
    final Set<K> keys = keySet();
    Set<Entry<K, V>> entries = new HashSet<Entry<K, V>>();

    for (final K key : keys) {
      final V value = get(key);
      if (value != null) {
        entries.add(new Entry<K, V>() {
          @Override
          public K getKey() {
            return key;
          }

          @Override
          public V getValue() {
            return value;
          }

          @Override
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
    @NonNls String s = "map size:" + size() + " [";
    for (K k : myMap.keySet()) {
      Object v = get(k);
      s += "'" + k + "': '" + v + "', ";
    }
    s += "] ";
    return s;
  }

  @TestOnly
  int underlyingMapSize() {
    return myMap.size();
  }
}
