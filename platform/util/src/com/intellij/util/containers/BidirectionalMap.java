// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class BidirectionalMap<K, V> implements Map<K, V> {
  private final Map<K, V> myKeyToValueMap = new HashMap<>();
  private final Map<V, List<K>> myValueToKeysMap = new HashMap<>();

  @Override
  public V put(K key, V value) {
    V oldValue = myKeyToValueMap.put(key, value);
    if (oldValue != null) {
      if (oldValue.equals(value)) {
        return oldValue;
      }
      List<K> array = myValueToKeysMap.get(oldValue);
      array.remove(key);
      if (array.isEmpty()) {
        myValueToKeysMap.remove(oldValue);
      }
    }

    myValueToKeysMap.computeIfAbsent(value, __ -> new SmartList<>()).add(key);
    return oldValue;
  }

  @Override
  public void clear() {
    myKeyToValueMap.clear();
    myValueToKeysMap.clear();
  }

  public @Nullable List<K> getKeysByValue(V value) {
    return myValueToKeysMap.get(value);
  }

  @Override
  public @NotNull Set<K> keySet() {
    return myKeyToValueMap.keySet();
  }

  @Override
  public int size() {
    return myKeyToValueMap.size();
  }

  @Override
  public boolean isEmpty() {
    return myKeyToValueMap.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return myKeyToValueMap.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return myValueToKeysMap.containsKey(value);
  }

  @Override
  public V get(Object key) {
    return myKeyToValueMap.get(key);
  }

  public void removeValue(V v) {
    List<K> ks = myValueToKeysMap.remove(v);
    if (ks != null) {
      for (K k : ks) {
        myKeyToValueMap.remove(k);
      }
    }
  }

  @Override
  public V remove(Object key) {
    final V value = myKeyToValueMap.remove(key);
    final List<K> ks = myValueToKeysMap.get(value);
    if (ks != null) {
      if (ks.size() > 1) {
        ks.remove(key);
      }
      else {
        myValueToKeysMap.remove(value);
      }
    }
    return value;
  }

  @Override
  public void putAll(@NotNull Map<? extends K, ? extends V> t) {
    for (final Entry<? extends K, ? extends V> entry : t.entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public @NotNull Collection<V> values() {
    return myValueToKeysMap.keySet();
  }

  @Override
  public @NotNull Set<Entry<K, V>> entrySet() {
    return myKeyToValueMap.entrySet();
  }

  @Override
  public String toString() {
    return myKeyToValueMap.toString();
  }
}
