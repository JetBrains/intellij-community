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
package com.intellij.util.keyFMap;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

final class PairElementsFMap<V1, V2> implements KeyFMap {
  // invariant: key1.hashCode() < key2.hashCode()
  private final @NotNull Key<V1> key1;
  private final @NotNull Key<V2> key2;
  private final @NotNull V1 value1;
  private final @NotNull V2 value2;

  PairElementsFMap(@NotNull Key<V1> key1, @NotNull V1 value1, @NotNull Key<V2> key2, @NotNull V2 value2) {
    // Key hashCodes are unique and ordered
    int c = Integer.compare(key1.hashCode(), key2.hashCode());
    if (c < 0) {
      this.key1 = key1;
      this.value1 = value1;
      this.key2 = key2;
      this.value2 = value2;
    }
    else if (c > 0) {
      //noinspection unchecked
      this.key1 = (Key<V1>)key2;
      //noinspection unchecked
      this.value1 = (V1)value2;
      //noinspection unchecked
      this.key2 = (Key<V2>)key1;
      //noinspection unchecked
      this.value2 = (V2)value1;
    }
    else {
      throw new IllegalArgumentException("Must not pass equal keys but got: key1: "+key1+":"+value1+"; key2: "+key2+":"+value2);
    }
  }

  @NotNull
  @Override
  public <V> KeyFMap plus(@NotNull Key<V> key, @NotNull V value) {
    if (key == key1) {
      return value == value1 ? this : new PairElementsFMap<>(key, value, key2, value2);
    }
    if (key == key2) {
      return value == value2 ? this : new PairElementsFMap<>(key1, value1, key, value);
    }
    if (key.hashCode() < key1.hashCode()) {
      return new ArrayBackedFMap(new int[]{key.hashCode(), key1.hashCode(), key2.hashCode()}, new Object[]{value, value1, value2});
    }
    if (key.hashCode() < key2.hashCode()) {
      return new ArrayBackedFMap(new int[]{key1.hashCode(), key.hashCode(), key2.hashCode()}, new Object[]{value1, value, value2});
    }
    return new ArrayBackedFMap(new int[]{key1.hashCode(), key2.hashCode(), key.hashCode()}, new Object[]{value1, value2, value});
  }

  @NotNull
  @Override
  public KeyFMap minus(@NotNull Key<?> key) {
    if (key == key1) return new OneElementFMap<>(key2, value2);
    if (key == key2) return new OneElementFMap<>(key1, value1);
    return this;
  }

  @Override
  public <V> V get(@NotNull Key<V> key) {
    //noinspection unchecked
    return key == key1 ? (V)value1 : key == key2 ? (V)value2 : null;
  }

  @Override
  public int size() {
    return 2;
  }

  @Override
  public @NotNull Key @NotNull [] getKeys() {
    return new Key[] { key1, key2 };
  }

  @Override
  public String toString() {
    return "{" + key1 + "=" + value1 + ", " + key2 + "=" + value2 + "}";
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public int getValueIdentityHashCode() {
    int hash = key1.hashCode() * 31 + System.identityHashCode(value1);
    hash = (hash * 31 + key2.hashCode()) * 31 + System.identityHashCode(value2);
    return hash;
  }

  @Override
  public int hashCode() {
    return (key1.hashCode() ^ value1.hashCode()) + (key2.hashCode() ^ value2.hashCode());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PairElementsFMap)) return false;

    PairElementsFMap<?,?> map = (PairElementsFMap<?,?>)o;

    return key1 == map.key1 && value1.equals(map.value1) && key2 == map.key2 && value2.equals(map.value2);
  }

  @Override
  public boolean equalsByReference(@NotNull KeyFMap o) {
    if (this == o) return true;
    if (!(o instanceof PairElementsFMap)) return false;

    PairElementsFMap<?,?> map = (PairElementsFMap<?,?>)o;

    return key1 == map.key1 && value1 == map.value1 && key2 == map.key2 && value2 == map.value2;
  }
}
