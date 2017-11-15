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

import java.util.Set;

public interface ObjectIntMap<K> {
  int get(@NotNull K key);

  int put(@NotNull K key, int value);

  int remove(@NotNull K key);

  boolean containsKey(@NotNull K key);

  void clear();

  @NotNull
  Set<K> keySet();

  int size();

  boolean isEmpty();

  @NotNull
  int[] values();

  boolean containsValue(int value);

  interface Entry<K> {
    @NotNull
    K getKey();
    
    int getValue();
  }

  @NotNull
  Iterable<Entry<K>> entries();
}
