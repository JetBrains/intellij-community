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

import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

/**
 * @see MultiMap#createConcurrentSet()
 * @author peter
 */
public class ConcurrentMultiMap<K,V> extends MultiMap<K,V> {
  public static final int DEFAULT_CAPACITY = 16;
  public static final float DEFAULT_LOAD_FACTOR = 0.75f;
  public static final int DEFAULT_CONCURRENCY_LEVEL = ConcurrentIntObjectHashMap.NCPU;

  public ConcurrentMultiMap() {
    super();
  }

  public ConcurrentMultiMap(int initialCapacity, float loadFactor) {
    super(initialCapacity, loadFactor);
  }

  @NotNull
  @Override
  protected ConcurrentMap<K, Collection<V>> createMap() {
    return createMap(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR);
  }

  @Override
  @NotNull
  protected ConcurrentMap<K, Collection<V>> createMap(int initialCapacity, float loadFactor) {
    return ContainerUtil.newConcurrentMap(initialCapacity, loadFactor, DEFAULT_CONCURRENCY_LEVEL);
  }

  @NotNull
  @Override
  protected Collection<V> createCollection() {
    return ContainerUtil.createLockFreeCopyOnWriteList();
  }

  @Override
  public void putValue(@NotNull K key, V value) {
    Collection<V> collection = myMap.get(key);
    if (collection == null) {
      Collection<V> newCollection = createCollection();
      collection = ConcurrencyUtil.cacheOrGet((ConcurrentMap<K, Collection<V>>)myMap, key, newCollection);
    }
    collection.add(value);
  }
}
