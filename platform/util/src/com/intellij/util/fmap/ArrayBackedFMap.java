// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.fmap;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
public final class ArrayBackedFMap<K, V> implements FMap<K, V> {
  public static final int ARRAY_THRESHOLD = 8;

  private final @NotNull Object @NotNull [] myData; // array of alternating pairs: K,V,K,V...

  ArrayBackedFMap(@NotNull Object @NotNull ... data) {
    assert 3 * 2 <= data.length; // at least 3 key-value pairs
    assert data.length <= ARRAY_THRESHOLD * 2; // at most ARRAY_THRESHOLD key-value pairs
    myData = data;
  }

  ArrayBackedFMap(@NotNull Map<? extends K, ? extends V> map) {
    assert map.size() <= ARRAY_THRESHOLD;
    Object[] data = new Object[map.size() * 2];
    int i = 0;
    for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
      data[i++] = entry.getKey();
      data[i++] = entry.getValue();
    }
    assert i == data.length;
    myData = data;
  }

  @Override
  public @NotNull FMap<K, V> plus(@NotNull K key, @NotNull V value) {
    for (int i = 0; i < myData.length; i += 2) {
      if (asKey(i).equals(key)) {
        if (asValue(i + 1).equals(value)) {
          return this;
        }
        else {
          Object[] newData = myData.clone();
          newData[i + 1] = value;
          return new ArrayBackedFMap<>(newData);
        }
      }
    }

    if (size() < ARRAY_THRESHOLD) {
      int length = myData.length;
      Object[] newData = Arrays.copyOf(myData, length + 2);
      newData[length] = key;
      newData[length + 1] = value;
      return new ArrayBackedFMap<>(newData);
    }

    Map<K, V> map = toMapInner();
    map.put(key, value);
    return new MapBackedFMap<>(map);
  }

  @Override
  public @NotNull FMap<K, V> minus(@NotNull K key) {
    for (int i = 0; i < myData.length; i += 2) {
      if (asKey(i).equals(key)) {
        if (size() == 3) {
          if (i == 0) {
            return new TwoKeysFMap<>(asKey(2), asValue(3), asKey(4), asValue(5));
          }
          else if (i == 2) {
            return new TwoKeysFMap<>(asKey(0), asValue(1), asKey(4), asValue(5));
          }
          else {
            assert i == 4;
            return new TwoKeysFMap<>(asKey(0), asValue(1), asKey(2), asValue(3));
          }
        }
        else {
          Object[] newData = new Object[myData.length - 2];
          System.arraycopy(myData, 0, newData, 0, i);
          System.arraycopy(myData, i + 2, newData, i, myData.length - 2 - i);
          return new ArrayBackedFMap<>(newData);
        }
      }
    }
    return this;
  }

  @Override
  public @Nullable V get(@NotNull K key) {
    for (int i = 0; i < myData.length; i += 2) {
      if (asKey(i).equals(key)) {
        return asValue(i + 1);
      }
    }
    return null;
  }

  private K asKey(int i) {
    //noinspection unchecked
    return (K)myData[i];
  }
  private V asValue(int i) {
    //noinspection unchecked
    return (V)myData[i];
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public int size() {
    return myData.length / 2;
  }

  @Override
  public @NotNull Collection<K> keys() {
    List<K> result = new ArrayList<>(size());
    for (int i = 0; i < myData.length; i += 2) {
      result.add(asKey(i));
    }
    return result;
  }

  @Override
  public @NotNull Map<K, V> toMap() {
    return Collections.unmodifiableMap(toMapInner());
  }

  private @NotNull Map<K, V> toMapInner() {
    Map<K, V> map = new HashMap<>(size());
    for (int i = 0; i < myData.length; i += 2) {
      map.put(asKey(i), asValue(i + 1));
    }
    return map;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ArrayBackedFMap<?, ?> map = (ArrayBackedFMap<?, ?>)o;
    if (size() != map.size()) return false;
    return toMapInner().equals(map.toMapInner());
  }

  @Override
  public int hashCode() {
    int keysHash = 0;
    int valuesHash = 0;
    for (int i = 0; i < myData.length; i += 2) {
      keysHash = keysHash ^ asKey(i).hashCode();
      valuesHash = valuesHash ^ asValue(i + 1).hashCode();
    }
    return keysHash + 31 * valuesHash;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[\n");
    for (int i = 0; i < myData.length; i += 2) {
      sb.append("  ").append(asKey(i)).append(": ").append(asValue(i + 1)).append(",\n");
    }
    sb.append("]");
    return sb.toString();
  }
}
