// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

@Debug.Renderer(text = "\"size = \" + size()", hasChildren = "!isEmpty()", childrenArray = "entrySet().toArray()")
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

  @Debug.Renderer(text = "getKey() + \" -> \\\"\" + getValue() + \"\\\"\"")
  interface Entry<V> {
    int getKey();
    @NotNull
    V getValue();
  }

  @NotNull
  Set<Entry<V>> entrySet();
}
