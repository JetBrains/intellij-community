// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class SLRUMap<K,V> {
  private final LinkedHashMap<K,V> myProtectedQueue;
  private final LinkedHashMap<K,V> myProbationalQueue;

  private final int myProtectedQueueSize;
  private final int myProbationalQueueSize;

  private int probationalHits;
  private int protectedHits;
  private int misses;
  private static final int FACTOR = Integer.getInteger("idea.slru.factor", 1);

  public SLRUMap(final int protectedQueueSize, final int probationalQueueSize) {
    this(protectedQueueSize, probationalQueueSize, (EqualityPolicy<? super K>)EqualityPolicy.CANONICAL);
  }

  public SLRUMap(final int protectedQueueSize, final int probationalQueueSize, @NotNull EqualityPolicy<? super K> hashingStrategy) {
    myProtectedQueueSize = protectedQueueSize * FACTOR;
    myProbationalQueueSize = probationalQueueSize * FACTOR;

    myProtectedQueue = new LinkedHashMap<K,V>(10, 0.6f, hashingStrategy, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<K, V> eldest, K key, V value) {
        if (size() > myProtectedQueueSize) {
          myProbationalQueue.put(key, value);
          return true;
        }

        return false;
      }
    };

    myProbationalQueue = new LinkedHashMap<K,V>(10, 0.6f, hashingStrategy, true) {
      @Override
      protected boolean removeEldestEntry(final Map.Entry<K, V> eldest, K key, V value) {
        if (size() > myProbationalQueueSize) {
          onDropFromCache(key, value);
          return true;
        }
        return false;
      }
    };
  }

  public @Nullable V get(K key) {
    V value = myProtectedQueue.get(key);
    if (value != null) {
      protectedHits++;
      return value;
    }

    value = myProbationalQueue.remove(key);
    if (value != null) {
      probationalHits++;
      putToProtectedQueue(key, value);
      return value;
    }

    misses++;
    return null;
  }

  protected void putToProtectedQueue(K key, @NotNull V value) {
    myProtectedQueue.put(getStableKey(key), value);
  }

  public void put(K key, @NotNull V value) {
    V oldValue = myProtectedQueue.remove(key);
    if (oldValue != null) {
      onDropFromCache(key, oldValue);
    }

    oldValue = myProbationalQueue.put(getStableKey(key), value);
    if (oldValue != null) {
      onDropFromCache(key, oldValue);
    }
  }

  protected void onDropFromCache(K key, @NotNull V value) {}

  public boolean remove(K key) {
    V value = myProtectedQueue.remove(key);
    if (value != null) {
      onDropFromCache(key, value);
      return true;
    }

    value = myProbationalQueue.remove(key);
    if (value != null) {
      onDropFromCache(key, value);
      return true;
    }

    return false;
  }

  public void iterateKeys(@NotNull Consumer<? super K> keyConsumer) {
    //RC: same key could be reported more than once to the consumer -- is it OK?
    myProtectedQueue.keySet().forEach(keyConsumer);
    myProbationalQueue.keySet().forEach(keyConsumer);
  }

  public @NotNull Set<Map.Entry<K, V>> entrySet() {
    Set<Map.Entry<K, V>> set = new HashSet<>(myProtectedQueue.entrySet());
    set.addAll(myProbationalQueue.entrySet());
    return set;
  }

  public @NotNull Set<V> values() {
    Set<V> set = new HashSet<>(myProtectedQueue.values());
    set.addAll(myProbationalQueue.values());
    return set;
  }

  public void clear() {
    try {
      if (!myProtectedQueue.isEmpty()) {
        for (Map.Entry<K, V> entry : myProtectedQueue.entrySet()) {
          onDropFromCache(entry.getKey(), entry.getValue());
        }
      }

      if (!myProbationalQueue.isEmpty()) {
        for (Map.Entry<K, V> entry : myProbationalQueue.entrySet()) {
          onDropFromCache(entry.getKey(), entry.getValue());
        }
      }
    }
    finally {
      myProtectedQueue.clear();
      myProbationalQueue.clear();
    }
  }

  private K getStableKey(K key) {
    if (key instanceof ShareableKey) {
      return (K)((ShareableKey)key).getStableCopy();
    }

    return key;
  }

  public @NotNull String dumpStats() {
    return "probational hits = " + probationalHits + ", protected hits = " + protectedHits + ", misses = " + misses;
  }
}
