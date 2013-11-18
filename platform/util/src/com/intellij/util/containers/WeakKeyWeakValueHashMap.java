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

import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

public final class WeakKeyWeakValueHashMap<K,V> implements Map<K,V>{
  private final WeakHashMap<K, MyValueReference<K,V>> myWeakKeyMap = new WeakHashMap<K, MyValueReference<K, V>>();
  private final ReferenceQueue<V> myQueue = new ReferenceQueue<V>();

  private static class MyValueReference<K,V> extends WeakReference<V> {
    private final WeakHashMap.Key<K> key;

    private MyValueReference(WeakHashMap.Key<K> key, V referent, ReferenceQueue<? super V> q) {
      super(referent, q);
      this.key = key;
    }
  }

  // returns true if some refs were tossed
  boolean processQueue() {
    boolean processed = myWeakKeyMap.processQueue();
    while(true) {
      MyValueReference<K,V> ref = (MyValueReference<K, V>)myQueue.poll();
      if (ref == null) break;
      WeakHashMap.Key<K> weakKey = ref.key;
      myWeakKeyMap.removeKey(weakKey);
      processed = true;
    }
    return processed;
  }

  @Override
  public V get(Object key) {
    MyValueReference<K,V> ref = myWeakKeyMap.get(key);
    if (ref == null) return null;
    return ref.get();
  }

  @Override
  public V put(K key, V value) {
    processQueue();
    WeakHashMap.Key<K> weakKey = myWeakKeyMap.createKey(key);
    MyValueReference<K, V> reference = new MyValueReference<K, V>(weakKey, value, myQueue);
    MyValueReference<K,V> oldRef = myWeakKeyMap.putKey(weakKey, reference);
    return oldRef == null ? null : oldRef.get();
  }

  @Override
  public V remove(Object key) {
    processQueue();
    MyValueReference<K,V> ref = myWeakKeyMap.remove(key);
    return ref != null ? ref.get() : null;
  }

  @Override
  public void putAll(@NotNull Map<? extends K, ? extends V> t) {
    throw new RuntimeException("method not implemented");
  }

  @Override
  public void clear() {
    myWeakKeyMap.clear();
    processQueue();
  }

  @Override
  public int size() {
    return myWeakKeyMap.size(); //?
  }

  @Override
  public boolean isEmpty() {
    return myWeakKeyMap.isEmpty(); //?
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
    return myWeakKeyMap.keySet();
  }

  @NotNull
  @Override
  public Collection<V> values() {
    List<V> result = new ArrayList<V>();
    final Collection<MyValueReference<K, V>> refs = myWeakKeyMap.values();
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
    throw new RuntimeException("method not implemented");
  }
}
