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

class OneElementFMap<V> implements KeyFMap {
  private final int myKeyCode;
  private final V myValue;

  OneElementFMap(int keyCode, @NotNull V value) {
    myKeyCode = keyCode;
    myValue = value;
  }

  @NotNull
  @Override
  public <V> KeyFMap plus(@NotNull Key<V> key, @NotNull V value) {
    int keyCode = key.hashCode();
    if (myKeyCode == keyCode) return new OneElementFMap<V>(keyCode, value);
    return new PairElementsFMap(myKeyCode, myValue, keyCode, value);
  }

  @NotNull
  @Override
  public KeyFMap minus(@NotNull Key<?> key) {
    if (key.hashCode() == myKeyCode) {
      return KeyFMap.EMPTY_MAP;
    }
    return this;
  }

  @Override
  public <V> V get(@NotNull Key<V> key) {
    //noinspection unchecked
    return myKeyCode == key.hashCode() ? (V)myValue : null;
  }

  @Override
  public String toString() {
    return "<"+Key.getKeyByIndex(myKeyCode) + " -> " + myValue+">";
  }

  @Override
  public boolean isEmpty() {
    return false;
  }
}
