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
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MultiValuesMap<K, V>{
  private final Map<K, Collection<V>> myBaseMap;
  private final boolean myOrdered;

  public MultiValuesMap() {
    this(false);
  }

  public MultiValuesMap(boolean ordered) {
    myOrdered = ordered;
    myBaseMap = ordered ? new LinkedHashMap<K, Collection<V>>() : new HashMap<K, Collection<V>>();
  }

  public void putAll(K key, @NotNull Collection<V> values) {
    for (V value : values) {
      put(key, value);
    }
  }

  public void putAll(K key, @NotNull V... values) {
    for (V value : values) {
      put(key, value);
    }
  }

  public void put(K key, V value) {
    Collection<V> collection = myBaseMap.get(key);
    if (collection == null) {
      collection = myOrdered ? new LinkedHashSet<V>() : new HashSet<V>();
      myBaseMap.put(key, collection);
    }

    collection.add(value);
  }

  public Collection<V> get(K key){
    return myBaseMap.get(key);
  }

  @NotNull
  public Set<K> keySet() {
    return myBaseMap.keySet();
  }

  @NotNull
  public Collection<V> values() {
    Set<V> result = myOrdered ? new LinkedHashSet<V>() : new HashSet<V>();
    for (final Collection<V> values : myBaseMap.values()) {
      result.addAll(values);
    }

    return result;
  }

  public void remove(K key, V value) {
    if (!myBaseMap.containsKey(key)) return;
    final Collection<V> values = myBaseMap.get(key);
    values.remove(value);
    if (values.isEmpty()) {
      myBaseMap.remove(key);
    }
  }

  public void clear() {
    myBaseMap.clear();
  }

  @Nullable 
  public Collection<V> removeAll(final K key) {
    return myBaseMap.remove(key);
  }

  @NotNull
  public Set<Map.Entry<K, Collection<V>>> entrySet() {
    return myBaseMap.entrySet();
  }

  public boolean isEmpty() {
    return myBaseMap.isEmpty();
  }

  public boolean containsKey(final K key) {
    return myBaseMap.containsKey(key);
  }

  @Nullable
  public V getFirst(final K key) {
    Collection<V> values = myBaseMap.get(key);
    return values == null || values.isEmpty() ? null : values.iterator().next();
  }


}
