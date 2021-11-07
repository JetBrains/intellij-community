// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.util.*;

abstract class RefKeyRefValueHashMap<K,V> implements Map<K,V>{
  private final RefHashMap<K, ValueReference<K,V>> myMap;
  private final ReferenceQueue<V> myQueue = new ReferenceQueue<>();

  RefKeyRefValueHashMap(@NotNull RefHashMap<K, ValueReference<K, V>> weakKeyMap) {
    myMap = weakKeyMap;
  }

  protected interface ValueReference<K, V> {
    @NotNull
    RefHashMap.Key<K> getKey();

    V get();
  }
  protected V dereference(ValueReference<K, ? extends V> reference) {
    return reference == null ? null : reference.get();
  }

  @NotNull
  protected abstract ValueReference<K,V> createValueReference(@NotNull RefHashMap.Key<K> key, V referent, ReferenceQueue<? super V> q);

  // returns true if some refs were tossed
  boolean processQueue() {
    boolean processed = myMap.processQueue();
    while(true) {
      ValueReference<K,V> ref = (ValueReference<K, V>)myQueue.poll();
      if (ref == null) break;
      RefHashMap.Key<K> weakKey = ref.getKey();
      myMap.removeKey(weakKey);
      processed = true;
    }
    return processed;
  }

  @Override
  public V get(@NotNull Object key) {
    ValueReference<K,V> ref = myMap.get(key);
    return dereference(ref);
  }

  @Override
  public V put(@NotNull K key, V value) {
    processQueue();
    RefHashMap.Key<K> weakKey = myMap.createKey(key);
    ValueReference<K, V> reference = createValueReference(weakKey, value, myQueue);
    ValueReference<K,V> oldRef = myMap.putKey(weakKey, reference);
    return dereference(oldRef);
  }

  @Override
  public V remove(@NotNull Object key) {
    processQueue();
    ValueReference<K,V> ref = myMap.remove(key);
    return dereference(ref);
  }

  @Override
  public void putAll(@NotNull Map<? extends K, ? extends V> t) {
    throw new UnsupportedOperationException();
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
    return myMap.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    throw RefValueHashMapUtil.pointlessContainsKey();
  }

  @Override
  public boolean containsValue(Object value) {
    throw RefValueHashMapUtil.pointlessContainsValue();
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    return myMap.keySet();
  }

  @NotNull
  @Override
  public Collection<V> values() {
    List<V> result = new ArrayList<>();
    final Collection<ValueReference<K, V>> refs = myMap.values();
    for (ValueReference<K, V> ref : refs) {
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
    throw new UnsupportedOperationException();
  }
}
