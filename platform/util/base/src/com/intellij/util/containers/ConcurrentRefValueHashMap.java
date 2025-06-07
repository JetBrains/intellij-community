// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.containers;

import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.lang.ref.ReferenceQueue;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

/**
 * Base class for concurrent strong key:K -> (soft/weak) value:V map
 * Null keys are NOT allowed
 * Null values are NOT allowed
 */
@ApiStatus.Internal
public abstract class ConcurrentRefValueHashMap<K, V> implements ConcurrentMap<K, V>, ReferenceQueueable {

  private final ConcurrentMap<K, ValueReference<K, V>> myMap = new ConcurrentHashMap<>();
  private final BiConsumer<? super @NotNull ConcurrentMap<K, V>, ? super K> myEvictionListener;
  protected final ReferenceQueue<V> myQueue = new ReferenceQueue<>();

  ConcurrentRefValueHashMap(@Nullable BiConsumer<? super @NotNull ConcurrentMap<K,V>, ? super K> evictionListener) {
    myEvictionListener = evictionListener;
  }

  interface ValueReference<K, V> {
    @NotNull
    K getKey();

    V get();
  }

  // returns true if some refs were tossed
  @Override
  @ApiStatus.Internal
  @VisibleForTesting
  public boolean processQueue() {
    boolean processed = false;

    while (true) {
      //noinspection unchecked
      ValueReference<K, V> ref = (ValueReference<K, V>)myQueue.poll();
      if (ref == null) break;
      K key = ref.getKey();
      if (myMap.remove(key, ref) && myEvictionListener != null) {
        myEvictionListener.accept(this, key);
      }
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
    ValueReference<K, V> oldRef = myMap.put(key, createValueReference(key, value));
    processQueue();
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
  public boolean remove(@NotNull Object key, @NotNull Object value) {
    //noinspection unchecked
    boolean removed = myMap.remove(key, createValueReference((K)key, (V)value));
    processQueue();
    return removed;
  }

  @Override
  public boolean replace(@NotNull K key, @NotNull V oldValue, @NotNull V newValue) {
    boolean replaced = myMap.replace(key, createValueReference(key, oldValue), createValueReference(key, newValue));
    processQueue();
    return replaced;
  }

  @Override
  public V replace(@NotNull K key, @NotNull V value) {
    ValueReference<K, V> ref = myMap.replace(key, createValueReference(key, value));
    processQueue();
    return ref == null ? null : ref.get();
  }

  @Override
  public V remove(@NotNull Object key) {
    ValueReference<K, V> ref = myMap.remove(key);
    processQueue();
    return ref == null ? null : ref.get();
  }

  @Override
  public void putAll(@NotNull Map<? extends K, ? extends V> t) {
    for (Entry<? extends K, ? extends V> entry : t.entrySet()) {
      V v = entry.getValue();
      if (v != null) {
        K key = entry.getKey();
        put(key, v);
      }
    }
    processQueue();
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
    throw RefValueHashMapUtil.pointlessContainsKey();
  }

  @Override
  public boolean containsValue(@NotNull Object value) {
    throw RefValueHashMapUtil.pointlessContainsValue();
  }

  @Override
  public @NotNull Set<K> keySet() {
    return myMap.keySet();
  }

  @Override
  public @NotNull Collection<V> values() {
    Collection<V> result = new ArrayList<>();
    Collection<ValueReference<K, V>> refs = myMap.values();
    for (ValueReference<K, V> ref : refs) {
      V value = ref.get();
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  @Override
  public @NotNull Set<Entry<K, V>> entrySet() {
    Set<K> keys = keySet();
    Set<Entry<K, V>> entries = new HashSet<>();

    for (K key : keys) {
      V value = get(key);
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
    return "map size:" + size() + " [" + Strings.join(entrySet(), ",") + "]";
  }
}
