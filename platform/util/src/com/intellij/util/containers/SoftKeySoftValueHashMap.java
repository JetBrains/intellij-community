/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import java.lang.ref.SoftReference;
import java.util.*;

/**
 * @deprecated use {@link ContainerUtil#createSoftKeySoftValueMap()} instead
 */
@Deprecated
public final class SoftKeySoftValueHashMap<K,V> implements Map<K,V>{
  private final SoftHashMap<K, ValueReference<K,V>> mySoftKeyMap = new SoftHashMap<K, ValueReference<K, V>>();
  private final ReferenceQueue<V> myQueue = new ReferenceQueue<V>();

  private static class ValueReference<K,V> extends SoftReference<V> {
    private final SoftHashMap.Key<K> key;

    private ValueReference(SoftHashMap.Key<K> key, V referent, ReferenceQueue<? super V> q) {
      super(referent, q);
      this.key = key;
    }
  }

  // returns true if some refs were tossed
  boolean processQueue() {
    boolean processed = mySoftKeyMap.processQueue();
    while(true) {
      ValueReference<K,V> ref = (ValueReference<K, V>)myQueue.poll();
      if (ref == null) break;
      SoftHashMap.Key<K> key = ref.key;
      mySoftKeyMap.removeKey(key);
      processed = true;
    }
    return processed;
  }

  @Override
  public V get(Object key) {
    ValueReference<K,V> ref = mySoftKeyMap.get(key);
    return com.intellij.reference.SoftReference.dereference(ref);
  }

  @Override
  public V put(K key, V value) {
    processQueue();
    SoftHashMap.Key<K> softKey = mySoftKeyMap.createKey(key);
    ValueReference<K, V> reference = new ValueReference<K, V>(softKey, value, myQueue);
    ValueReference<K,V> oldRef = mySoftKeyMap.putKey(softKey, reference);
    return com.intellij.reference.SoftReference.dereference(oldRef);
  }

  @Override
  public V remove(Object key) {
    processQueue();
    ValueReference<K,V> ref = mySoftKeyMap.remove(key);
    return com.intellij.reference.SoftReference.dereference(ref);
  }

  @Override
  public void putAll(@NotNull Map<? extends K, ? extends V> t) {
    throw new RuntimeException("method not implemented");
  }

  @Override
  public void clear() {
    mySoftKeyMap.clear();
    processQueue();
  }

  @Override
  public int size() {
    return mySoftKeyMap.size(); //?
  }

  @Override
  public boolean isEmpty() {
    return mySoftKeyMap.isEmpty(); //?
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
    return mySoftKeyMap.keySet();
  }

  @NotNull
  @Override
  public Collection<V> values() {
    List<V> result = new ArrayList<V>();
    final Collection<ValueReference<K, V>> refs = mySoftKeyMap.values();
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
    throw new RuntimeException("method not implemented");
  }
}
