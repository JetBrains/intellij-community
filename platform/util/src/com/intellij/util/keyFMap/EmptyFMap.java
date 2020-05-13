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
import com.intellij.util.containers.UnmodifiableHashMap;
import org.jetbrains.annotations.NotNull;

class EmptyFMap implements KeyFMap {
  private static final Key[] EMPTY_KEYS_ARRAY = {};

  EmptyFMap() {
  }

  @NotNull
  @Override
  public <V> KeyFMap plus(@NotNull Key<V> key, @NotNull V value) {
    return new OneElementFMap<>(key, value);
  }

  @NotNull
  @Override
  public KeyFMap minus(@NotNull Key<?> key) {
    return this;
  }

  @Override
  public <V> V get(@NotNull Key<V> key) {
    return null;
  }

  @Override
  public int size() {
    return 0;
  }

  @Override
  public Key @NotNull [] getKeys() {
    return EMPTY_KEYS_ARRAY;
  }

  @Override
  public String toString() {
    return "{}";
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public int getValueIdentityHashCode() {
    return 0;
  }

  @Override
  public boolean equalsByReference(KeyFMap other) {
    return other == this;
  }

  @Override
  public int hashCode() {
    return 0;
  }
  
  static KeyFMap create() {
    return DebugFMap.DEBUG_FMAP ? new DebugFMap(UnmodifiableHashMap.empty()) : new EmptyFMap();
  }
}
