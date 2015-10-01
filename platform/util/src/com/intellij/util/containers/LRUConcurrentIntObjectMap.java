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

import org.jetbrains.annotations.NotNull;

/**
 * An {@code int->V} map with fixed {@code capacity}.
 * When new key->value placed into the map and its size exceeded the capacity,
 * the LRU oldKey->oldValue pair (i.e. the pair which was added into the map a {@code capacity} puts ago) is removed.
 * Thread safe.
 */
public class LRUConcurrentIntObjectMap<V> {
  private static final int TOMB_VALUE = -1;
  /**
   * the keys are stored in the usual ConcurrentIntObjectMap,
   * while their LRU order is maintained in {@link #queue}. As soon as the key is evicted from the queue we remove the key->value pair from the map.
   */
  private final ConcurrentIntObjectMap<V> myMap;
  private final FixedConcurrentIntQueue queue;

  public LRUConcurrentIntObjectMap(int capacity) {
    queue = new FixedConcurrentIntQueue(capacity - capacity / 4 - 2 /* that's more or less the ConcurrentHashMap size which triggers resize*/, TOMB_VALUE);
    myMap = ContainerUtil.createConcurrentIntObjectMap(capacity, 1, Runtime.getRuntime().availableProcessors());
  }

  public V get(int key) {
    return myMap.get(key);
  }

  public interface IntFunction<R> {
    @NotNull
    R apply(int value);
  }

  @NotNull
  public V computeIfAbsent(int key, @NotNull IntFunction<V> function) {
    V v = myMap.get(key);
    if (v != null) {
      return v;
    }

    V newV = function.apply(key);
    V prev = myMap.putIfAbsent(key, newV);
    if (prev == null) {
      addToQueue(key);
      return newV;
    }
    return prev;
  }

  public V put(int key, V value) {
    V prev = myMap.put(key, value);
    if (prev == null) {
      addToQueue(key);
    }
    return prev;
  }

  private void addToQueue(int key) {
    int evicted = queue.push(key);
    if (evicted != TOMB_VALUE) {
      myMap.remove(evicted);
    }
  }
}
