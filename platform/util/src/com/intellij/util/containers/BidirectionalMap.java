// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.*;

@ApiStatus.NonExtendable
public class BidirectionalMap<K, V> implements Map<K, V> {
  private final Map<K, V> myKeyToValueMap = new HashMap<>();
  private final Map<V, List<K>> myValueToKeysMap = new HashMap<>();

  @Override
  public final V put(K key, V value){
    V oldValue = myKeyToValueMap.put(key, value);
    if (oldValue != null){
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
  public final void clear() {
    myKeyToValueMap.clear();
    myValueToKeysMap.clear();
  }

  @Nullable
  public final List<K> getKeysByValue(V value){
    return myValueToKeysMap.get(value);
  }

  @NotNull
  @Override
  public final Set<K> keySet() {
    return myKeyToValueMap.keySet();
  }

  @Override
  public final int size(){
    return myKeyToValueMap.size();
  }

  @Override
  public final boolean isEmpty(){
    return myKeyToValueMap.isEmpty();
  }

  @Override
  public final boolean containsKey(Object key){
    return myKeyToValueMap.containsKey(key);
  }

  @Override
  @SuppressWarnings("SuspiciousMethodCalls")
  public final boolean containsValue(Object value){
    return myValueToKeysMap.containsKey(value);
  }

  @Override
  public final V get(Object key) {
    return myKeyToValueMap.get(key);
  }

  public final void removeValue(V v) {
    List<K> ks = myValueToKeysMap.remove(v);
    if (ks != null) {
      for (K k : ks) {
        myKeyToValueMap.remove(k);
      }
    }
  }

  @Override
  @SuppressWarnings("SuspiciousMethodCalls")
  public final V remove(Object key) {
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
  public final void putAll(@NotNull Map<? extends K, ? extends V> t){
    for (final Entry<? extends K, ? extends V> entry : t.entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  @NotNull
  @Override
  public final Collection<V> values(){
    return myValueToKeysMap.keySet();
  }

  @NotNull
  @Override
  public final Set<Entry<K, V>> entrySet(){
    return myKeyToValueMap.entrySet();
  }

  @Override
  public final String toString() {
    return myKeyToValueMap.toString();
  }
}
