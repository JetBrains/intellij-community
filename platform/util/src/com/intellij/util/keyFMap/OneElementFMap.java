// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.keyFMap;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

final class OneElementFMap<V> implements KeyFMap {
  private final Key<V> myKey;
  private final V myValue;

  OneElementFMap(@NotNull Key<V> key, @NotNull V value) {
    myKey = key;
    myValue = value;
  }

  @Override
  public @NotNull <T> KeyFMap plus(@NotNull Key<T> key, @NotNull T value) {
    if (myKey == key) {
      return value == myValue ? this : new OneElementFMap<>(key, value);
    }
    return new PairElementsFMap<>(myKey, myValue, key, value);
  }

  @Override
  public @NotNull KeyFMap minus(@NotNull Key<?> key) {
    return key == myKey ? KeyFMap.EMPTY_MAP : this;
  }

  @Override
  public <T> T get(@NotNull Key<T> key) {
    //noinspection unchecked
    return myKey == key ? (T)myValue : null;
  }

  @Override
  public int size() {
    return 1;
  }

  @Override
  public @NotNull Key @NotNull [] getKeys() {
    return new Key[] { myKey };
  }

  @Override
  public String toString() {
    return "{" + myKey + "=" + myValue + "}";
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public int getValueIdentityHashCode() {
    return myKey.hashCode() * 31 + System.identityHashCode(myValue);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OneElementFMap)) return false;

    OneElementFMap<?> map = (OneElementFMap<?>)o;
    return myKey == map.myKey && myValue.equals(map.myValue);
  }

  @Override
  public boolean equalsByReference(@NotNull KeyFMap o) {
    if (this == o) return true;
    if (!(o instanceof OneElementFMap)) return false;

    OneElementFMap<?> map = (OneElementFMap<?>)o;
    return myKey == map.myKey && myValue == map.myValue;
  }

  @Override
  public int hashCode() {
    return myKey.hashCode() ^ myValue.hashCode();
  }
}
