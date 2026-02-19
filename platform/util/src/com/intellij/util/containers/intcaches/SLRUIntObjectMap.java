// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.intcaches;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Specialization of {@link com.intellij.util.containers.SLRUMap} for int keys */
@ApiStatus.Internal
public final class SLRUIntObjectMap<V> {
  private static final int QUEUES_SIZE_SCALING = Integer.getInteger("idea.slru.factor", 1);

  private final LinkedCustomIntObjectHashMap<V> protectedQueue;
  private final LinkedCustomIntObjectHashMap<V> probationalQueue;

  private final @NotNull EvictionCallback<? super V> evictionCallback;

  private final int protectedQueueSize;
  private final int probationalQueueSize;

  private int probationalHits;
  private int protectedHits;
  private int misses;

  public SLRUIntObjectMap(int protectedQueueSize,
                          int probationalQueueSize,
                          @NotNull EvictionCallback<? super V> evictionCallback) {
    this.protectedQueueSize = protectedQueueSize * QUEUES_SIZE_SCALING;
    this.probationalQueueSize = probationalQueueSize * QUEUES_SIZE_SCALING;

    this.evictionCallback = evictionCallback;

    probationalQueue = new LinkedCustomIntObjectHashMap<>((size, key, value) -> {
      if (size > this.probationalQueueSize) {
        onEvict(key, value);
        return true;
      }
      return false;
    });

    protectedQueue = new LinkedCustomIntObjectHashMap<>((size, key, value) -> {
      if (size > this.protectedQueueSize) {
        probationalQueue.put(key, value);
        return true;
      }
      return false;
    });
  }

  public @Nullable V get(int key) {
    //MAYBE RC: instead of moving `value` between probation and protected maps, it may be better to move Entry<V> -- both
    //          maps contain an Entry-es internally, so it may be more optimal to just move a whole Entry, and cut off on
    //          allocation?
    V value = protectedQueue.get(key);
    if (value != null) {
      protectedHits++;
      return value;
    }

    value = probationalQueue.remove(key);
    if (value != null) {
      probationalHits++;

      protectedQueue.put(key, value);
      return value;
    }

    misses++;
    return null;
  }

  public void put(int key, @NotNull V value) {
    V oldValue = protectedQueue.remove(key);
    if (oldValue != null) {
      onEvict(key, oldValue);
    }

    oldValue = probationalQueue.put(key, value);
    if (oldValue != null) {
      onEvict(key, oldValue);
    }
  }

  private void onEvict(int key, @NotNull V value) {
    evictionCallback.evicted(key, value);
  }

  public boolean remove(int key) {
    V value = protectedQueue.remove(key);
    if (value != null) {
      onEvict(key, value);
      return true;
    }

    value = probationalQueue.remove(key);
    if (value != null) {
      onEvict(key, value);
      return true;
    }

    return false;
  }

  public void iterateKeys(@NotNull Consumer<? super Integer> keyConsumer) {
    protectedQueue.keySet().forEach(keyConsumer);
    probationalQueue.keySet().forEach(keyConsumer);
  }

  public @NotNull Set<Map.Entry<Integer, V>> entrySet() {
    Set<Map.Entry<Integer, V>> set = new HashSet<>(protectedQueue.entrySet());
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
   * through {@link #onEvict(int, Object)} first, quite important side effect to consider -- 'drain'
   * would be a better name.
   */
  public void clear() {
    try {
      if (!protectedQueue.isEmpty()) {
        for (LinkedCustomIntObjectHashMap.Entry<V> entry : protectedQueue.entrySet()) {
          onEvict(entry.key(), entry.getValue());
        }
      }

      if (!probationalQueue.isEmpty()) {
        for (LinkedCustomIntObjectHashMap.Entry<V> entry : probationalQueue.entrySet()) {
          onEvict(entry.key(), entry.getValue());
        }
      }
    }
    finally {
      protectedQueue.clear();
      probationalQueue.clear();
    }
  }

  public @NotNull String dumpStats() {
    return "probational hits = " + probationalHits + ", protected hits = " + protectedHits + ", misses = " + misses;
  }

  public interface EvictionCallback<V> {
    void evicted(int key, V value);
  }
}
