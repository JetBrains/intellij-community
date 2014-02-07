/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
public class SmartFMap<K,V> implements Map<K,V> {
  private static final SmartFMap EMPTY = new SmartFMap(ArrayUtil.EMPTY_OBJECT_ARRAY);
  private static final int ARRAY_THRESHOLD = 8;
  private final Object myMap;

  private SmartFMap(Object map) {
    myMap = map;
  }

  public static <K,V> SmartFMap<K, V> emptyMap() {
    return EMPTY;
  }

  public SmartFMap<K, V> plus(@NotNull K key, V value) {
    return new SmartFMap<K, V>(doPlus(myMap, key, value, false));
  }

  private static Object doPlus(Object oldMap, Object key, Object value, boolean inPlace) {
    if (oldMap instanceof Map) {
      Map newMap = inPlace ? (Map)oldMap : new THashMap((Map)oldMap);
      newMap.put(key, value);
      return newMap;
    }

    Object[] array = (Object[])oldMap;
    for (int i = 0; i < array.length; i += 2) {
      if (key.equals(array[i])) {
        Object[] newArray = inPlace ? array : new Object[array.length];
        if (!inPlace) {
          System.arraycopy(array, 0, newArray, 0, array.length);
        }
        newArray[i + 1] = value;
        return newArray;
      }
    }
    if (array.length == 2 * ARRAY_THRESHOLD) {
      THashMap map = new THashMap();
      for (int i = 0; i < array.length; i += 2) {
        map.put(array[i], array[i + 1]);
      }
      map.put(key, value);
      return map;
    }

    Object[] newArray = new Object[array.length + 2];
    System.arraycopy(array, 0, newArray, 0, array.length);
    newArray[array.length] = key;
    newArray[array.length + 1] = value;
    return newArray;
  }

  public SmartFMap<K, V> minus(@NotNull K key) {
    if (myMap instanceof Map) {
      THashMap<K, V> newMap = new THashMap<K, V>((Map<K, V>)myMap);
      newMap.remove(key);
      if (newMap.size() <= ARRAY_THRESHOLD) {
        Object[] newArray = new Object[newMap.size() * 2];
        int i = 0;
        for (K k : newMap.keySet()) {
          newArray[i++] = k;
          newArray[i++] = newMap.get(k);
        }
        return new SmartFMap<K, V>(newArray);
      }

      return new SmartFMap<K, V>(newMap);
    }

    Object[] array = (Object[])myMap;
    for (int i = 0; i < array.length; i += 2) {
      if (key.equals(array[i])) {
        if (size() == 1) {
          return EMPTY;
        }

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
  public boolean equals(Object obj) {
    return obj instanceof Map && entrySet().equals(((Map)obj).entrySet());
  }

  @Override
  public int hashCode() {
    return entrySet().hashCode();
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
  public boolean containsValue(Object value) {
    return false;
  }

  @Override
  @Nullable
  public V get(Object key) {
    return (V)doGet(myMap, key);
  }

  @Nullable
  private static Object doGet(Object map, Object key) {
    if (key == null) {
      return null;
    }
    if (map instanceof Map) {
      return ((Map)map).get(key);
    }
    Object[] array = (Object[])map;
    for (int i = 0; i < array.length; i += 2) {
      if (key.equals(array[i])) {
        return array[i + 1];
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
  public void putAll(@NotNull Map<? extends K, ? extends V> m) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    if (isEmpty()) return Collections.emptySet();
    
    LinkedHashSet<K> result = new LinkedHashSet<K>();
    for (Entry<K, V> entry : entrySet()) {
      result.add(entry.getKey());
    }
    return Collections.unmodifiableSet(result);
  }

  @NotNull
  @Override
  public Collection<V> values() {
    if (isEmpty()) return Collections.emptyList();
    
    ArrayList<V> result = new ArrayList<V>();
    for (Entry<K, V> entry : entrySet()) {
      result.add(entry.getValue());
    }
    return Collections.unmodifiableCollection(result);
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
  public boolean isEmpty() {
    return size() == 0;
  }

  @NotNull
  @Override
  public Set<Entry<K, V>> entrySet() {
    if (isEmpty()) return Collections.emptySet();
    
    LinkedHashSet<Entry<K, V>> set = new LinkedHashSet<Entry<K, V>>();
    if (myMap instanceof Map) {
      for (Entry<K, V> entry : ((Map<K, V>)myMap).entrySet()) {
        set.add(new AbstractMap.SimpleImmutableEntry<K, V>(entry));
      }
    } else {
      Object[] array = (Object[])myMap;
      for (int i = 0; i < array.length; i += 2) {
        set.add(new AbstractMap.SimpleImmutableEntry<K, V>((K)array[i], (V)array[i + 1]));
      }
    }
    return Collections.unmodifiableSet(set);
  }

  // copied from AbstractMap
  public String toString() {
    Iterator<Entry<K,V>> i = entrySet().iterator();
    if (! i.hasNext())
      return "{}";

    StringBuilder sb = new StringBuilder();
    sb.append('{');
    while (true) {
      Entry<K, V> e = i.next();
      K key = e.getKey();
      V value = e.getValue();
      sb.append(key == this ? "(this Map)" : key);
      sb.append('=');
      sb.append(value == this ? "(this Map)" : value);
      if (!i.hasNext()) {
        return sb.append('}').toString();
      }
      sb.append(", ");
    }
  }

}
