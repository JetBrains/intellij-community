// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;

import static com.intellij.util.ObjectUtils.NULL;

public abstract class SoftFactoryMap<T,V> {
  private final ConcurrentMap<T, V> myMap = CollectionFactory.createConcurrentWeakKeySoftValueMap();

  protected abstract V create(@NotNull T key);

  public final V get(@NotNull T key) {
    final V v = myMap.get(key);
    if (v != null) {
      return v == NULL ? null : v;
    }

    final V value = create(key);
    //noinspection unchecked
    V toPut = value == null ? (V)NULL : value;
    V prev = myMap.putIfAbsent(key, toPut);
    return prev == null || prev == NULL ? value : prev;
  }

  public void clear() {
    myMap.clear();
  }
}