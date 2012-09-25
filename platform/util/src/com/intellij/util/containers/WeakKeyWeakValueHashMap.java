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
package com.intellij.util.containers;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

public final class WeakKeyWeakValueHashMap<K,V> implements Map<K,V>{
  private final WeakHashMap<K, MyReference<K,V>> myWeakKeyMap = new WeakHashMap<K, MyReference<K, V>>();
  private final ReferenceQueue<V> myQueue = new ReferenceQueue<V>();

  private static class MyReference<K,V> extends WeakReference<V> {
    private final WeakHashMap.Key<K> key;

    private MyReference(WeakHashMap.Key<K> key, V referent, ReferenceQueue<? super V> q) {
      super(referent, q);
      this.key = key;
    }
  }

  private void processQueue() {
    myWeakKeyMap.processQueue();
    while(true) {
      MyReference<K,V> ref = (MyReference<K, V>)myQueue.poll();
      if (ref == null) break;
      WeakHashMap.Key<K> weakKey = ref.key;
      myWeakKeyMap.removeKey(weakKey);
    }
  }

  @Override
  public V get(Object key) {
    MyReference<K,V> ref = myWeakKeyMap.get(key);
    if (ref == null) return null;
    return ref.get();
  }

  @Override
  public V put(K key, V value) {
    processQueue();
    WeakHashMap.Key<K> weakKey = myWeakKeyMap.createKey(key);
    MyReference<K, V> reference = new MyReference<K, V>(weakKey, value, myQueue);
    MyReference<K,V> oldRef = myWeakKeyMap.putKey(weakKey, reference);
    return oldRef == null ? null : oldRef.get();
  }

  @Override
  public V remove(Object key) {
    processQueue();
    MyReference<K,V> ref = myWeakKeyMap.remove(key);
    return ref != null ? ref.get() : null;
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> t) {
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

  @Override
  public Set<K> keySet() {
    return myWeakKeyMap.keySet();
  }

  @Override
  public Collection<V> values() {
    List<V> result = new ArrayList<V>();
    final Collection<MyReference<K, V>> refs = myWeakKeyMap.values();
    for (MyReference<K, V> ref : refs) {
      final V value = ref.get();
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    throw new RuntimeException("method not implemented");
  }
}
