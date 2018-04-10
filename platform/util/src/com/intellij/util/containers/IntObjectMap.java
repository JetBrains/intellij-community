// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface IntObjectMap<V> {
  V put(int key, @NotNull V value);

  V get(int key);

  V remove(int key);

  boolean containsKey(int key);

  void clear();

  @NotNull
  int[] keys();

  int size();

  boolean isEmpty();

  @NotNull
  Collection<V> values();

  boolean containsValue(@NotNull V value);

  interface Entry<V> {
    int getKey();
    @NotNull
    V getValue();
  }

  @NotNull
  Iterable<Entry<V>> entries();
}
