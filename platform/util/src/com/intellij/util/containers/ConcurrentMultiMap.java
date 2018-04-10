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
  @NotNull
  @Override
  protected ConcurrentMap<K, Collection<V>> createMap() {
    return ContainerUtil.newConcurrentMap();
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
