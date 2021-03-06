// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ConcurrentMostlySingularMultiMap<K, V> extends MostlySingularMultiMap<K, V> {
  public ConcurrentMostlySingularMultiMap() {
    super(new ConcurrentHashMap<>());
  }

  @Override
  public void add(@NotNull K key, @NotNull V value) {
    ConcurrentMap<K, Object> map = (ConcurrentMap<K, Object>)myMap;
    while (true) {
      Object current = map.get(key);
      if (current == null) {
        if (ConcurrencyUtil.cacheOrGet(map, key, value) == value) {
          break;
        }
      }
      else if (current instanceof MostlySingularMultiMap.ValueList) {
        List<?> curList = (ValueList<?>)current;
        List<Object> newList = new ValueList<>(curList.size() + 1);
        newList.addAll(curList);
        newList.add(value);
        if (map.replace(key, curList, newList)) break;
      }
      else {
        List<Object> newList = new ValueList<>(2);
        newList.add(current);
        newList.add(value);
        if (map.replace(key, current, newList)) break;
      }
    }
  }

  @Override
  public void compact() {
    // not implemented
  }

  public boolean replace(@NotNull K key, @NotNull Collection<? extends V> expectedValue, @NotNull Collection<? extends V> newValue) {
    ConcurrentMap<K, Object> map = (ConcurrentMap<K, Object>)myMap;
    Object newValueToPut = newValue.isEmpty() ? null : newValue.size() == 1 ? newValue.iterator().next() : new ValueList<Object>(newValue);

    Object oldValue = map.get(key);
    List<V> oldCollection = rawValueToCollection(oldValue);
    if (!oldCollection.equals(expectedValue)) return false;

    if (oldValue == null) {
      return newValueToPut == null || map.putIfAbsent(key, newValueToPut) == null;
    }
    if (newValueToPut == null) {
      return map.remove(key, oldValue);
    }
    return map.replace(key, oldValue, newValueToPut);
  }

  @Override
  public void addAll(@NotNull MostlySingularMultiMap<K, V> other) {
    throw new AbstractMethodError("Not yet re-implemented for concurrency");
  }

  @Override
  public boolean remove(@NotNull K key, @NotNull V value) {
    throw new AbstractMethodError("Not yet re-implemented for concurrency");
  }
}
