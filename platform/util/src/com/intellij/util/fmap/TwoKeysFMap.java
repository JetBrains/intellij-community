// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.fmap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class TwoKeysFMap<K, V> implements FMap<K, V> {

  private final K myKey1;
  private final K myKey2;
  private final V myValue1;
  private final V myValue2;

  TwoKeysFMap(@NotNull K key1, @NotNull V value1, @NotNull K key2, @NotNull V value2) {
    myKey1 = key1;
    myKey2 = key2;
    myValue1 = value1;
    myValue2 = value2;
  }

  @Override
  public @NotNull FMap<K, V> plus(K key, V value) {
    if (myKey1.equals(key)) {
      if (myValue1.equals(value)) {
        return this;
      }
      else {
        return new TwoKeysFMap<>(key, value, myKey2, myValue2);
      }
    }
    else if (myKey2.equals(key)) {
      if (myValue2.equals(value)) {
        return this;
      }
      else {
        return new TwoKeysFMap<>(myKey1, myValue1, key, value);
      }
    }
    else {
      return new ArrayBackedFMap<>(myKey1, myValue1, myKey2, myValue2, key, value);
    }
  }

  @Override
  public @NotNull FMap<K, V> minus(K key) {
    return myKey1.equals(key)
           ? new OneKeyFMap<>(myKey2, myValue2)
           : myKey2.equals(key)
             ? new OneKeyFMap<>(myKey1, myValue1)
             : this;
  }

  @Override
  public @Nullable V get(K key) {
    return myKey1.equals(key)
           ? myValue1
           : myKey2.equals(key)
             ? myValue2
             : null;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public int size() {
    return 2;
  }

  @Override
  public @NotNull Collection<K> keys() {
    return Arrays.asList(myKey1, myKey2);
  }

  @Override
  public @NotNull Map<K, V> toMap() {
    Map<K, V> map = new HashMap<>(2);
    map.put(myKey1, myValue1);
    map.put(myKey2, myValue2);
    return Collections.unmodifiableMap(map);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TwoKeysFMap<?, ?> map = (TwoKeysFMap<?, ?>)o;
    return myKey1.equals(map.myKey1) && myKey2.equals(map.myKey2) &&
           myValue1.equals(map.myValue1) && myValue2.equals(map.myValue2) ||
           myKey1.equals(map.myKey2) && myKey2.equals(map.myKey1) &&
           myValue1.equals(map.myValue2) && myValue2.equals(map.myValue1);
  }

  @Override
  public int hashCode() {
    return (myKey1.hashCode() ^ myKey2.hashCode())
           + 31 * (myValue1.hashCode() ^ myValue2.hashCode());
  }

  @Override
  public String toString() {
    return String.format("[\n  %s: %s,\n  %s: %s,\n]", myKey1, myValue1, myKey2, myValue2);
  }
}
