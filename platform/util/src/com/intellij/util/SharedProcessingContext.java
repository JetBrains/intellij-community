// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @see ProcessingContext
 */
public final class SharedProcessingContext {
  private final Map<Object, Object> myMap = new ConcurrentHashMap<>();

  public Object get(final @NonNls @NotNull String key) {
    return myMap.get(key);
  }

  public void put(final @NonNls @NotNull String key, final @NotNull Object value) {
    myMap.put(key, value);
  }

  public <T> void put(Key<T> key, T value) {
    myMap.put(key, value);
  }

  public <T> T get(Key<T> key) {
    return (T)myMap.get(key);
  }

  public @Nullable <T> T get(@NotNull Key<T> key, Object element) {
    Map map = (Map)myMap.get(key);
    if (map == null) {
      return null;
    }
    else {
      return (T)map.get(element);
    }
  }

  public <T> void put(@NotNull Key<T> key, Object element, T value) {
    //noinspection unchecked
    ((Map)myMap.computeIfAbsent(key, __ -> new HashMap<>())).put(element, value);
  }
}