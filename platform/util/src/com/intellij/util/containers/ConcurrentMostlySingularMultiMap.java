/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class ConcurrentMostlySingularMultiMap<K, V> extends MostlySingularMultiMap<K, V> {
  @NotNull
  @Override
  protected Map<K, Object> createMap() {
    return ContainerUtil.newConcurrentMap();
  }

  @Override
  public void add(@NotNull K key, @NotNull V value) {
    ConcurrentMap<K, Object> map = (ConcurrentMap<K, Object>)myMap;
    while (true) {
      Object current = map.get(key);
      if (current == null) {
        if (ConcurrencyUtil.cacheOrGet(map, key, value) == value) break;
      }
      else if (current instanceof Object[]) {
        Object[] curArr = (Object[])current;
        Object[] newArr = ArrayUtil.append(curArr, value, ArrayUtil.OBJECT_ARRAY_FACTORY);
        if (map.replace(key, curArr, newArr)) break;
      }
      else {
        Object[] newArr = {current, value};
        if (map.replace(key, current, newArr)) break;
      }
    }
  }

  @Override
  public void compact() {
    // not implemented
  }

  public boolean replace(@NotNull K key, @NotNull Collection<V> expectedValue, @NotNull Collection<V> newValue) {
    ConcurrentMap<K, Object> map = (ConcurrentMap<K, Object>)myMap;
    Object[] newArray = ArrayUtil.toObjectArray(newValue);
    Object newValueToPut = newArray.length == 0 ? null : newArray.length == 1 ? newArray[0] : newArray;

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
}
