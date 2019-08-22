// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @deprecated use {@link com.intellij.util.containers.MultiMap} directly
 */
@Debug.Renderer(text = "\"size = \" + myBaseMap.size()", hasChildren = "!isEmpty()", childrenArray = "entrySet().toArray()")
@Deprecated
public class MultiValuesMap<K, V>{
  private final MultiMap<K, V> myDelegate;

  /**
   * @deprecated Use {@link MultiMap#createSet()}
   */
  @Deprecated
  public MultiValuesMap() {
    this(false);
  }

  public MultiValuesMap(boolean ordered) {
    if (ordered) {
      myDelegate = MultiMap.createLinkedSet();
    }
    else {
      myDelegate = MultiMap.createSet();
    }
  }

  public void putAll(K key, @NotNull Collection<? extends V> values) {
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
    myDelegate.putValue(key, value);
  }

  public Collection<V> get(K key){
    return myDelegate.get(key);
  }

  @NotNull
  public Set<K> keySet() {
    return myDelegate.keySet();
  }

  @NotNull
  public Collection<V> values() {
    return (Collection<V>)myDelegate.values();
  }

  public void remove(K key, V value) {
    myDelegate.remove(key, value);
  }

  public void clear() {
    myDelegate.clear();
  }

  @Nullable
  public Collection<V> removeAll(final K key) {
    return myDelegate.remove(key);
  }

  @NotNull
  public Set<Map.Entry<K, Collection<V>>> entrySet() {
    return myDelegate.entrySet();
  }

  public boolean isEmpty() {
    return myDelegate.isEmpty();
  }

  public boolean containsKey(final K key) {
    return myDelegate.containsKey(key);
  }

  @Nullable
  public V getFirst(final K key) {
    Collection<V> values = myDelegate.get(key);
    return values.isEmpty() ? null : values.iterator().next();
  }
}
