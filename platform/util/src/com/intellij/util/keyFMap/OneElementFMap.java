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

final class OneElementFMap<V> implements KeyFMap {
  private final Key<V> myKey;
  private final V myValue;

  OneElementFMap(@NotNull Key<V> key, @NotNull V value) {
    myKey = key;
    myValue = value;
  }

  @NotNull
  @Override
  public <T> KeyFMap plus(@NotNull Key<T> key, @NotNull T value) {
    if (myKey == key) {
      return value == myValue ? this : new OneElementFMap<>(key, value);
    }
    return new PairElementsFMap<>(myKey, myValue, key, value);
  }

  @NotNull
  @Override
  public KeyFMap minus(@NotNull Key<?> key) {
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
