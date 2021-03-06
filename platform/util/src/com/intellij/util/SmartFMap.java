// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.Comparing;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * An immutable map optimized for storing few entries with relatively rare updates.
 *
 * @author peter
 */
public final class SmartFMap<K,V> implements Map<K,V> {
  private static final SmartFMap<?, ?> EMPTY = new SmartFMap<>(ArrayUtilRt.EMPTY_OBJECT_ARRAY);
  private static final int ARRAY_THRESHOLD = 8;
  private final Object myMap; // Object[] for map sizes up to ARRAY_THRESHOLD or Map

  private SmartFMap(Object map) {
    myMap = map;
  }

  public static @NotNull<K, V> SmartFMap<K, V> emptyMap() {
    //noinspection unchecked
    return (SmartFMap<K, V>)EMPTY;
  }

  public @NotNull SmartFMap<K, V> plus(@NotNull K key, V value) {
    return new SmartFMap<>(doPlus(myMap, key, value));
  }

  private static Object doPlus(Object oldMap, Object key, Object value) {
    if (oldMap instanceof Map) {
      //noinspection unchecked
      Map<Object, Object> newMap = new THashMap<>((Map<Object, Object>)oldMap);
      newMap.put(key, value);
      return newMap;
    }

    Object[] array = (Object[])oldMap;
    for (int i = 0; i < array.length; i += 2) {
      if (key.equals(array[i])) {
        Object[] newArray = array.clone();
        newArray[i + 1] = value;
        return newArray;
      }
    }
    if (array.length == 2 * ARRAY_THRESHOLD) {
      Map<Object,Object> map = new HashMap<>();
      for (int i = 0; i < array.length; i += 2) {
        map.put(array[i], array[i + 1]);
      }
      map.put(key, value);
      return map;
    }

    Object[] newArray = Arrays.copyOf(array, array.length + 2);
    newArray[array.length] = key;
    newArray[array.length + 1] = value;
    return newArray;
  }

  public SmartFMap<K, V> minus(@NotNull K key) {
    if (myMap instanceof Map) {
      Map<K, V> newMap = new HashMap<>(asMap());
      newMap.remove(key);
      if (newMap.size() <= ARRAY_THRESHOLD) {
        Object[] newArray = new Object[newMap.size() * 2];
        int i = 0;
        for (K k : newMap.keySet()) {
          newArray[i++] = k;
          newArray[i++] = newMap.get(k);
        }
        return new SmartFMap<>(newArray);
      }

      return new SmartFMap<>(newMap);
    }

    Object[] array = (Object[])myMap;
    for (int i = 0; i < array.length; i += 2) {
      if (key.equals(array[i])) {
        if (size() == 1) {
          return emptyMap();
        }

        Object[] newArray = new Object[array.length - 2];
        System.arraycopy(array, 0, newArray, 0, i);
        System.arraycopy(array, i + 2, newArray, i, array.length - i - 2);
        return new SmartFMap<>(newArray);
      }
    }
    return this;
  }

  public SmartFMap<K, V> plusAll(Map<? extends K, ? extends V> m) {
    SmartFMap<K, V> result = this;
    for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
      result = result.plus(e.getKey(), e.getValue());
    }
    return result;
  }

  public SmartFMap<K, V> minusAll(@NotNull Collection<? extends K> keys) {
    SmartFMap<K, V> result = this;
    for (K key : keys) {
      result = result.minus(key);
    }
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (myMap instanceof Map) {
      return myMap.equals(obj);
    }

    if (!(obj instanceof Map)) {
      return false;
    }

    Map<?, ?> map = (Map<?, ?>)obj;
    if (size() != map.size()) {
      return false;
    }

    Object[] array = (Object[])myMap;
    for (int i = 0; i < array.length; i += 2) {
      if (!Comparing.equal(array[i + 1], map.get(array[i]))) {
        return false;
      }
    }

    return true;
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
      return asMap().containsKey(key);
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
    if (key == null) {
      return null;
    }
    if (myMap instanceof Map) {
      return asMap().get(key);
    }
    Object[] array = (Object[])myMap;
    for (int i = 0; i < array.length; i += 2) {
      if (key.equals(array[i])) {
        //noinspection unchecked
        return (V)array[i + 1];
      }
    }
    return null;
  }

  /**
   * @deprecated not supported, use {@link #plus(Object, Object)}
   */
  @Override
  @Deprecated
  public V put(K key, V value) {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated not supported, use {@link #plusAll(Map)}
   */
  @Override
  @Deprecated
  public void putAll(@NotNull Map<? extends K, ? extends V> m) {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated not supported
   */
  @Override
  @Deprecated
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Set<K> keySet() {
    if (isEmpty()) return Collections.emptySet();

    LinkedHashSet<K> result = new LinkedHashSet<>();
    for (Entry<K, V> entry : entrySet()) {
      result.add(entry.getKey());
    }
    return Collections.unmodifiableSet(result);
  }

  @NotNull
  @Override
  public Collection<V> values() {
    if (isEmpty()) return Collections.emptyList();

    ArrayList<V> result = new ArrayList<>();
    for (Entry<K, V> entry : entrySet()) {
      result.add(entry.getValue());
    }
    return Collections.unmodifiableCollection(result);
  }

  /**
   * @deprecated not supported, use {@link #minus(Object)}
   */
  @Override
  @Deprecated
  public V remove(Object key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int size() {
    if (myMap instanceof Map) {
      return asMap().size();
    }
    return ((Object[])myMap).length >> 1;
  }

  private Map<K, V> asMap() {
    //noinspection unchecked
    return (Map<K, V>)myMap;
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @NotNull
  @Override
  public Set<Entry<K, V>> entrySet() {
    if (isEmpty()) return Collections.emptySet();

    LinkedHashSet<Entry<K, V>> set = new LinkedHashSet<>();
    if (myMap instanceof Map) {
      for (Entry<K, V> entry : asMap().entrySet()) {
        set.add(new AbstractMap.SimpleImmutableEntry<>(entry));
      }
    } else {
      Object[] array = (Object[])myMap;
      for (int i = 0; i < array.length; i += 2) {
        //noinspection unchecked
        set.add(new AbstractMap.SimpleImmutableEntry<>((K)array[i], (V)array[i + 1]));
      }
    }
    return Collections.unmodifiableSet(set);
  }

  @Override
  public void forEach(@NotNull BiConsumer<? super K, ? super V> action) {
    if (myMap instanceof Map) {
      asMap().forEach(action);
    }
    else {
      Object[] array = (Object[])myMap;
      for (int i = 0; i < array.length; i += 2) {
        //noinspection unchecked
        action.accept((K)array[i], (V)array[i + 1]);
      }
    }
  }

  // copied from AbstractMap
  @Override
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
