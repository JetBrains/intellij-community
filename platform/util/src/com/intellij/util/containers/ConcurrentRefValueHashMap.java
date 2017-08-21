/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Base class for concurrent strong key:K -> (soft/weak) value:V map
 * Null keys are NOT allowed
 * Null values are NOT allowed
 */
abstract class ConcurrentRefValueHashMap<K, V> implements ConcurrentMap<K, V> {
  private final ConcurrentMap<K, ValueReference<K, V>> myMap = ContainerUtil.newConcurrentMap();
  protected final ReferenceQueue<V> myQueue = new ReferenceQueue<V>();

  interface ValueReference<K, V> {
    @NotNull
    K getKey();

    V get();
  }

  // returns true if some refs were tossed
  boolean processQueue() {
    boolean processed = false;

    while (true) {
      @SuppressWarnings("unchecked")
      ValueReference<K, V> ref = (ValueReference<K, V>)myQueue.poll();
      if (ref == null) break;
      myMap.remove(ref.getKey(), ref);
      processed = true;
    }
    return processed;
  }

  @Override
  public V get(@NotNull Object key) {
    ValueReference<K, V> ref = myMap.get(key);
    if (ref == null) return null;
    return ref.get();
  }

  @Override
  public V put(@NotNull K key, @NotNull V value) {
    processQueue();
    ValueReference<K, V> oldRef = myMap.put(key, createValueReference(key, value));
    return oldRef != null ? oldRef.get() : null;
  }

  @NotNull
  abstract ValueReference<K, V> createValueReference(@NotNull K key, @NotNull V value);

  @Override
  public V putIfAbsent(@NotNull K key, @NotNull V value) {
    ValueReference<K, V> newRef = createValueReference(key, value);
    while (true) {
      processQueue();
      ValueReference<K, V> oldRef = myMap.putIfAbsent(key, newRef);
      if (oldRef == null) return null;
      V oldVal = oldRef.get();
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
    //noinspection unchecked
    return myMap.remove(key, createValueReference((K)key, (V)value));
  }

  @Override
  public boolean replace(@NotNull final K key, @NotNull final V oldValue, @NotNull final V newValue) {
    processQueue();
    return myMap.replace(key, createValueReference(key, oldValue), createValueReference(key, newValue));
  }

  @Override
  public V replace(@NotNull final K key, @NotNull final V value) {
    processQueue();
    ValueReference<K, V> ref = myMap.replace(key, createValueReference(key, value));
    return ref == null ? null : ref.get();
  }

  @Override
  public V remove(@NotNull Object key) {
    processQueue();
    ValueReference<K, V> ref = myMap.remove(key);
    return ref == null ? null : ref.get();
  }

  @Override
  public void putAll(@NotNull Map<? extends K, ? extends V> t) {
    processQueue();
    for (Entry<? extends K, ? extends V> entry : t.entrySet()) {
      V v = entry.getValue();
      if (v != null) {
        K key = entry.getKey();
        put(key, v);
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
    processQueue();
    return myMap.size();
  }

  @Override
  public boolean isEmpty() {
    processQueue();
    return myMap.isEmpty();
  }

  @Override
  public boolean containsKey(@NotNull Object key) {
    return get(key) != null;
  }

  @Override
  public boolean containsValue(@NotNull Object value) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    return myMap.keySet();
  }

  @NotNull
  @Override
  public Collection<V> values() {
    Collection<V> result = new ArrayList<V>();
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
          public V setValue(@NotNull V value) {
            throw new UnsupportedOperationException("setValue is not implemented");
          }

          @Override
          public String toString() {
            return "(" + getKey() + " : " + getValue() + ")";
          }
        });
      }
    }

    return entries;
  }

  @Override
  public String toString() {
    return "map size:" + size() + " [" + StringUtil.join(entrySet(), ",") + "]";
  }
}
