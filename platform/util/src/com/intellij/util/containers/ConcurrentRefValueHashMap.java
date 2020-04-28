// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.containers;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.util.HashSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Base class for concurrent strong key:K -> (soft/weak) value:V map
 * Null keys are NOT allowed
 * Null values are NOT allowed
 */
abstract class ConcurrentRefValueHashMap<K, V> implements ConcurrentMap<K, V> {
  private final ConcurrentMap<K, ValueReference<K, V>> myMap = new ConcurrentHashMap<>();
  protected final ReferenceQueue<V> myQueue = new ReferenceQueue<>();

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

  abstract @NotNull ValueReference<K, V> createValueReference(@NotNull K key, @NotNull V value);

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
  public boolean remove(final @NotNull Object key, @NotNull Object value) {
    processQueue();
    //noinspection unchecked
    return myMap.remove(key, createValueReference((K)key, (V)value));
  }

  @Override
  public boolean replace(final @NotNull K key, final @NotNull V oldValue, final @NotNull V newValue) {
    processQueue();
    return myMap.replace(key, createValueReference(key, oldValue), createValueReference(key, newValue));
  }

  @Override
  public V replace(final @NotNull K key, final @NotNull V value) {
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
    throw RefValueHashMap.pointlessContainsKey();
  }

  @Override
  public boolean containsValue(@NotNull Object value) {
    throw RefValueHashMap.pointlessContainsValue();
  }

  @Override
  public @NotNull Set<K> keySet() {
    return myMap.keySet();
  }

  @Override
  public @NotNull Collection<V> values() {
    Collection<V> result = new ArrayList<>();
    final Collection<ValueReference<K, V>> refs = myMap.values();
    for (ValueReference<K, V> ref : refs) {
      final V value = ref.get();
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  @Override
  public @NotNull Set<Entry<K, V>> entrySet() {
    final Set<K> keys = keySet();
    Set<Entry<K, V>> entries = new HashSet<>();

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
