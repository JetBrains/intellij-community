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
package com.intellij.util;

import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * An immutable map optimized for storing few entries with relatively rare updates
 *
 * @author peter
 */
@SuppressWarnings("unchecked")
public class SmartFMap<K,V> extends AbstractMap<K,V> {
  private static final SmartFMap EMPTY = new SmartFMap(ArrayUtil.EMPTY_OBJECT_ARRAY);
  private static final int ARRAY_THRESHOLD = 5;
  private final Object myMap;

  private SmartFMap(Object map) {
    myMap = map;
  }

  public static <K,V> SmartFMap<K, V> emptyMap() {
    return EMPTY;
  }

  public SmartFMap<K, V> plus(@NotNull K key, V value) {
    if (myMap instanceof Map) {
      THashMap<K, V> newMap = new THashMap<K, V>((Map<K, V>)myMap);
      newMap.put(key, value);
      return new SmartFMap<K, V>(newMap);
    }

    Object[] array = (Object[])myMap;
    for (int i = 0; i < array.length; i += 2) {
      if (key.equals(array[i])) {
        Object[] newArray = new Object[array.length];
        System.arraycopy(array, 0, newArray, 0, array.length);
        newArray[i + 1] = value;
        return new SmartFMap<K, V>(newArray);
      }
    }
    if (array.length == 2 * ARRAY_THRESHOLD) {
      THashMap<K, V> map = new THashMap<K, V>();
      for (int i = 0; i < array.length; i += 2) {
        map.put((K)array[i], (V)array[i + 1]);
      }
      return new SmartFMap<K, V>(map);
    }

    Object[] newArray = new Object[array.length + 2];
    System.arraycopy(array, 0, newArray, 0, array.length);
    newArray[array.length] = key;
    newArray[array.length + 1] = value;
    return new SmartFMap<K, V>(newArray);
  }

  public SmartFMap<K, V> minus(@NotNull K key) {
    if (myMap instanceof Map) {
      THashMap<K, V> newMap = new THashMap<K, V>((Map<K, V>)myMap);
      newMap.remove(key);
      return new SmartFMap<K, V>(newMap);
    }

    Object[] array = (Object[])myMap;
    for (int i = 0; i < array.length; i += 2) {
      if (key.equals(array[i])) {
        Object[] newArray = new Object[array.length - 2];
        System.arraycopy(array, 0, newArray, 0, i);
        System.arraycopy(array, i + 2, newArray, i, array.length - i - 2);
        return new SmartFMap<K, V>(newArray);
      }
    }
    return this;
  }

  public SmartFMap<K, V> plusAll(Map<K, V> m) {
    SmartFMap<K, V> result = this;
    for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
      result = result.plus(e.getKey(), e.getValue());
    }
    return result;
  }

  public SmartFMap<K, V> minusAll(@NotNull Collection<K> keys) {
    SmartFMap<K, V> result = this;
    for (K key : keys) {
      result = result.minus(key);
    }
    return result;
  }

  @Override
  public boolean containsKey(Object key) {
    if (key == null) {
      return false;
    }
    if (myMap instanceof Map) {
      return ((Map<K, V>)myMap).containsKey(key);
    }
    Object[] array = (Object[])myMap;
    for (int i = 0; i < array.length; i += 2) {
      if (key.equals(array[i])) {
        return true;
      }
    }
    return false;
  }

  @Override
  @Nullable
  public V get(Object key) {
    if (key == null) {
      return null;
    }
    if (myMap instanceof Map) {
      return ((Map<K, V>)myMap).get(key);
    }
    Object[] array = (Object[])myMap;
    for (int i = 0; i < array.length; i += 2) {
      if (key.equals(array[i])) {
        return (V)array[i + 1];
      }
    }
    return null;
  }

  @Override
  @Deprecated
  public V put(K key, V value) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public void putAll(Map<? extends K, ? extends V> m) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public V remove(Object key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int size() {
    if (myMap instanceof Map) {
      return ((Map<K, V>)myMap).size();
    }
    return ((Object[])myMap).length >> 1;
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    LinkedHashSet<Entry<K, V>> set = new LinkedHashSet<Entry<K, V>>();
    if (myMap instanceof Map) {
      for (Entry<K, V> entry : ((Map<K, V>)myMap).entrySet()) {
        set.add(new SimpleImmutableEntry<K, V>(entry));
      }
    } else {
      Object[] array = (Object[])myMap;
      for (int i = 0; i < array.length; i += 2) {
        set.add(new SimpleImmutableEntry<K, V>((K)array[i], (V)array[i + 1]));
      }
    }
    return Collections.unmodifiableSet(set);
  }

}
