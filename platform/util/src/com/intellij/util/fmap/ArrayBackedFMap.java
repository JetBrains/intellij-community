// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.fmap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class ArrayBackedFMap<K, V> implements FMap<K, V> {

  static final int ARRAY_THRESHOLD = 8;

  private final @NotNull Object @NotNull [] myData;

  ArrayBackedFMap(@NotNull Object @NotNull ... data) {
    assert 3 * 2 <= data.length; // at least 3 key-value pairs
    assert data.length <= ARRAY_THRESHOLD * 2; // at most ARRAY_THRESHOLD key-value pairs
    myData = data;
  }

  ArrayBackedFMap(@NotNull Map<K, V> map) {
    assert map.size() <= ARRAY_THRESHOLD;
    Object[] data = new Object[map.size() * 2];
    int i = 0;
    for (Map.Entry<K, V> entry : map.entrySet()) {
      data[i++] = entry.getKey();
      data[i++] = entry.getValue();
    }
    assert i == data.length;
    myData = data;
  }

  @Override
  public @NotNull FMap<K, V> plus(K key, V value) {
    for (int i = 0; i < myData.length; i += 2) {
      if (myData[i].equals(key)) {
        if (myData[i + 1].equals(value)) {
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

  @SuppressWarnings("unchecked")
  @Override
  public @NotNull FMap<K, V> minus(K key) {
    for (int i = 0; i < myData.length; i += 2) {
      if (myData[i].equals(key)) {
        if (size() == 3) {
          if (i == 0) {
            return new TwoKeysFMap<>((K)myData[2], (V)myData[3], (K)myData[4], (V)myData[5]);
          }
          else if (i == 2) {
            return new TwoKeysFMap<>((K)myData[0], (V)myData[1], (K)myData[4], (V)myData[5]);
          }
          else {
            assert i == 4;
            return new TwoKeysFMap<>((K)myData[0], (V)myData[1], (K)myData[2], (V)myData[3]);
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

  @SuppressWarnings("unchecked")
  @Override
  public @Nullable V get(K key) {
    for (int i = 0; i < myData.length; i += 2) {
      if (myData[i].equals(key)) {
        return (V)myData[i + 1];
      }
    }
    return null;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public int size() {
    return myData.length / 2;
  }

  @SuppressWarnings("unchecked")
  @Override
  public @NotNull Collection<K> keys() {
    List<K> result = new ArrayList<>(size());
    for (int i = 0; i < myData.length; i += 2) {
      result.add((K)myData[i]);
    }
    return result;
  }

  @Override
  public @NotNull Map<K, V> toMap() {
    return Collections.unmodifiableMap(toMapInner());
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private Map<K, V> toMapInner() {
    Map<K, V> map = new HashMap<>(size());
    for (int i = 0; i < myData.length; i += 2) {
      map.put((K)myData[i], (V)myData[i + 1]);
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
      keysHash = keysHash ^ myData[i].hashCode();
      valuesHash = valuesHash ^ myData[i + 1].hashCode();
    }
    return keysHash + 31 * valuesHash;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[\n");
    for (int i = 0; i < myData.length; i += 2) {
      sb.append("  ").append(myData[i]).append(": ").append(myData[i + 1]).append(",\n");
    }
    sb.append("]");
    return sb.toString();
  }
}
