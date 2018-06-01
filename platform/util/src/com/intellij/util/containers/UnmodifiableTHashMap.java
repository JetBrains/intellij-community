// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import gnu.trove.THashMap;
import gnu.trove.TObjectFunction;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectObjectProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class UnmodifiableTHashMap<K,V> extends THashMap<K,V> {
  public UnmodifiableTHashMap(@NotNull Map<? extends K, ? extends V> map) {
    //noinspection unchecked
    this(CANONICAL, map);
  }

  public UnmodifiableTHashMap(@NotNull TObjectHashingStrategy<K> strategy,
                              @NotNull Map<? extends K, ? extends V> map) {
    super(map.size(), strategy);
    for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
      super.put(e.getKey(), e.getValue());
    }
  }
  public UnmodifiableTHashMap(@NotNull TObjectHashingStrategy<K> strategy,
                              @NotNull Map<? extends K, ? extends V> map, K additionalKey, V additionalValue) {
    this(strategy, map);
    super.put(additionalKey, additionalValue);
  }

  @Override
  public V put(K key, V value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainEntries(TObjectObjectProcedure<K, V> procedure) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void transformValues(TObjectFunction<V, V> function) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public V remove(Object key) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void removeAt(int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Collection<V> values() {
    return Collections.unmodifiableCollection(super.values());
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    return Collections.unmodifiableSet(super.keySet());
  }

  @NotNull
  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    return Collections.unmodifiableSet(super.entrySet());
  }
}
