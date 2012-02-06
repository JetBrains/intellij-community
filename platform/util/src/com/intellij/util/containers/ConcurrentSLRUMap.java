/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.util.containers;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentSLRUMap<K,V> {
  protected final ConcurrentMap<K,V> myProtectedQueue;
  protected final ConcurrentMap<K,V> myProbationalQueue;

  private final AtomicInteger probationalHits = new AtomicInteger();
  private final AtomicInteger protectedHits = new AtomicInteger();
  private final AtomicInteger misses = new AtomicInteger();

  public ConcurrentSLRUMap(final int protectedQueueSize, final int probationalQueueSize) {
    myProtectedQueue = CacheBuilder.newBuilder().concurrencyLevel(4).removalListener(new RemovalListener<K, V>() {
      @Override
      public void onRemoval(RemovalNotification<K, V> notification) {
        myProbationalQueue.put(notification.getKey(), notification.getValue());
      }
    }).initialCapacity(10).maximumSize(protectedQueueSize).<K, V>build().asMap();

    myProbationalQueue = CacheBuilder.newBuilder().concurrencyLevel(4).removalListener(new RemovalListener<K, V>() {
      @Override
      public void onRemoval(RemovalNotification<K, V> notification) {
        onDropFromCache(notification.getKey(), notification.getValue());
      }
    }).initialCapacity(10).maximumSize(probationalQueueSize).<K, V>build().asMap();
  }

  @Nullable
  public V get(K key) {
    V value = myProtectedQueue.get(key);
    if (value != null) {
      protectedHits.incrementAndGet();
      return value;
    }

    value = myProbationalQueue.remove(key);
    if (value != null) {
      probationalHits.incrementAndGet();
      myProtectedQueue.put(key, value);

      return value;
    }

    misses.incrementAndGet();
    return null;
  }

  public void put(K key, V value) {
    V oldValue = myProtectedQueue.remove(key);
    if (oldValue != null) {
      onDropFromCache(key, oldValue);
    }

    oldValue = myProbationalQueue.put(key, value);
    if (oldValue != null) {
      onDropFromCache(key, oldValue);
    }
  }

  protected void onDropFromCache(K key, V value) {}

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

  public Set<Map.Entry<K, V>> entrySet() {
    Set<Map.Entry<K, V>> set = new HashSet<Map.Entry<K,V>>(myProtectedQueue.entrySet());
    set.addAll(myProbationalQueue.entrySet());
    return set;
  }

  public void clear() {
    for (Map.Entry<K, V> entry : myProtectedQueue.entrySet()) {
      onDropFromCache(entry.getKey(), entry.getValue());
    }
    myProtectedQueue.clear();

    for (Map.Entry<K, V> entry : myProbationalQueue.entrySet()) {
      onDropFromCache(entry.getKey(), entry.getValue());
    }
    myProbationalQueue.clear();
  }
}
