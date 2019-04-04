// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Debug.Renderer(text = "\"size = \" + myBaseMap.size()", hasChildren = "!isEmpty()", childrenArray = "entrySet().toArray()")
public class MultiValuesMap<K, V>{
  private final Map<K, Collection<V>> myBaseMap;
  private final boolean myOrdered;

  public MultiValuesMap() {
    this(false);
  }

  public MultiValuesMap(boolean ordered) {
    myOrdered = ordered;
    myBaseMap = ordered ? new LinkedHashMap<>() : new HashMap<>();
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
      collection = myOrdered ? new LinkedHashSet<>() : new HashSet<>();
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
    Set<V> result = myOrdered ? new LinkedHashSet<>() : new HashSet<>();
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
