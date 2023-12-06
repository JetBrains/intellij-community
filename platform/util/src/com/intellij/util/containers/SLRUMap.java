// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.containers.hash.EqualityPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class SLRUMap<K, V> {
  private static final int FACTOR = Integer.getInteger("idea.slru.factor", 1);

  private final LinkedCustomHashMap<K, V> protectedQueue;
  private final LinkedCustomHashMap<K, V> probationalQueue;

  private final int protectedQueueSize;
  private final int probationalQueueSize;

  private int probationalHits;
  private int protectedHits;
  private int misses;

  public SLRUMap(int protectedQueueSize, int probationalQueueSize) {
    //noinspection unchecked
    this(protectedQueueSize, probationalQueueSize, (EqualityPolicy<? super K>)EqualityPolicy.CANONICAL);
  }

  public SLRUMap(int protectedQueueSize, int probationalQueueSize, @NotNull EqualityPolicy<? super K> hashingStrategy) {
    this.protectedQueueSize = protectedQueueSize * FACTOR;
    this.probationalQueueSize = probationalQueueSize * FACTOR;

    probationalQueue = new LinkedCustomHashMap<>(hashingStrategy, (size, eldest, key, value) -> {
      if (size > this.probationalQueueSize) {
        onDropFromCache(key, value);
        return true;
      }
      return false;
    });

    protectedQueue = new LinkedCustomHashMap<>(hashingStrategy, (size, eldest, key, value) -> {
      if (size > this.protectedQueueSize) {
        probationalQueue.put(key, value);
        return true;
      }
      return false;
    });
  }

  public @Nullable V get(K key) {
    V value = protectedQueue.get(key);
    if (value != null) {
      protectedHits++;
      return value;
    }

    value = probationalQueue.remove(key);
    if (value != null) {
      probationalHits++;
      putToProtectedQueue(key, value);
      return value;
    }

    misses++;
    return null;
  }

  protected void putToProtectedQueue(K key, @NotNull V value) {
    protectedQueue.put(getStableKey(key), value);
  }

  public void put(K key, @NotNull V value) {
    V oldValue = protectedQueue.remove(key);
    if (oldValue != null) {
      onDropFromCache(key, oldValue);
    }

    oldValue = probationalQueue.put(getStableKey(key), value);
    if (oldValue != null) {
      onDropFromCache(key, oldValue);
    }
  }

  protected void onDropFromCache(K key, @NotNull V value) { }

  public boolean remove(K key) {
    V value = protectedQueue.remove(key);
    if (value != null) {
      onDropFromCache(key, value);
      return true;
    }

    value = probationalQueue.remove(key);
    if (value != null) {
      onDropFromCache(key, value);
      return true;
    }

    return false;
  }

  public void iterateKeys(@NotNull Consumer<? super K> keyConsumer) {
    //RC: same key could be reported more than once to the consumer -- is it OK?
    protectedQueue.keySet().forEach(keyConsumer);
    probationalQueue.keySet().forEach(keyConsumer);
  }

  public @NotNull Set<Map.Entry<K, V>> entrySet() {
    Set<Map.Entry<K, V>> set = new HashSet<>(protectedQueue.entrySet());
    set.addAll(probationalQueue.entrySet());
    return set;
  }

  public @NotNull Set<V> values() {
    Set<V> set = new HashSet<>(protectedQueue.values());
    set.addAll(probationalQueue.values());
    return set;
  }

  /**
   * 'clear' may be a bit misleading: this method indeed makes the cache empty, but all the current entries go
   * through {@link #onDropFromCache(Object, Object)} first, quite important side effect to consider -- 'drain'
   * would be a better name.
   */
  public void clear() {
    try {
      if (!protectedQueue.isEmpty()) {
        for (Map.Entry<K, V> entry : protectedQueue.entrySet()) {
          onDropFromCache(entry.getKey(), entry.getValue());
        }
      }

      if (!probationalQueue.isEmpty()) {
        for (Map.Entry<K, V> entry : probationalQueue.entrySet()) {
          onDropFromCache(entry.getKey(), entry.getValue());
        }
      }
    }
    finally {
      protectedQueue.clear();
      probationalQueue.clear();
    }
  }

  private K getStableKey(K key) {
    if (key instanceof ShareableKey) {
      //noinspection unchecked
      return (K)((ShareableKey)key).getStableCopy();
    }
    return key;
  }

  public @NotNull String dumpStats() {
    return "probational hits = " + probationalHits + ", protected hits = " + protectedHits + ", misses = " + misses;
  }
}
