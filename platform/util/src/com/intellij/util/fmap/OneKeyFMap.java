// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.fmap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

final class OneKeyFMap<K, V> implements FMap<K, V> {

  private final K myKey;
  private final V myValue;

  OneKeyFMap(@NotNull K key, @NotNull V value) {
    myKey = key;
    myValue = value;
  }

  @Override
  public @NotNull FMap<K, V> plus(K key, V value) {
    if (myKey.equals(key)) {
      if (myValue.equals(value)) {
        return this;
      }
      else {
        return new OneKeyFMap<>(key, value);
      }
    }
    else {
      return new TwoKeysFMap<>(myKey, myValue, key, value);
    }
  }

  @Override
  public @NotNull FMap<K, V> minus(K key) {
    return myKey.equals(key) ? FMap.empty() : this;
  }

  @Override
  public @Nullable V get(K key) {
    return myKey.equals(key) ? myValue : null;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public int size() {
    return 1;
  }

  @Override
  public @NotNull Collection<K> keys() {
    return Collections.singleton(myKey);
  }

  @Override
  public @NotNull Map<K, V> toMap() {
    return Collections.singletonMap(myKey, myValue);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    OneKeyFMap<?, ?> map = (OneKeyFMap<?, ?>)o;
    return myKey.equals(map.myKey) && myValue.equals(map.myValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myKey, myValue);
  }

  @Override
  public String toString() {
    return String.format("[%s: %s,]", myKey, myValue);
  }
}
