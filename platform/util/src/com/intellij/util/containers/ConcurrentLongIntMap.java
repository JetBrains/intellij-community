// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.function.LongToIntFunction;

/**
 * A concurrent map from {@code long} keys to {@code int} values.
 * Methods that return a mapped value use the configured default value when no mapping exists.
 * The map cannot store its default value.
 */
@Debug.Renderer(text = "\"size = \" + size()", hasChildren = "!isEmpty()", childrenArray = "entrySet().toArray()")
public interface ConcurrentLongIntMap {
  int size();

  boolean isEmpty();

  int get(long key);

  int getOrDefault(long key, int defaultValue);

  boolean containsKey(long key);

  boolean containsValue(int value);

  int put(long key, int value);

  int putIfAbsent(long key, int value);

  int remove(long key);

  boolean remove(long key, int value);

  int replace(long key, int value);

  boolean replace(long key, int oldValue, int newValue);

  void clear();

  @NotNull
  Set<Entry> entrySet();

  void forEach(@NotNull LongIntConsumer action);

  void replaceAll(@NotNull LongIntToIntFunction function);

  int computeIfAbsent(long key, @NotNull LongToIntFunction mappingFunction);

  int computeIfPresent(long key, @NotNull LongIntToIntFunction remappingFunction);

  int compute(long key, @NotNull LongIntToIntFunction remappingFunction);

  int merge(long key, int value, @NotNull IntIntToIntFunction remappingFunction);

  long mappingCount();

  @Debug.Renderer(text = "getKey() + \" -> \\\"\" + getValue() + \"\\\"\"")
  interface Entry {
    long getKey();

    int getValue();
  }

  @FunctionalInterface
  interface LongIntToIntFunction {
    int apply(long value, int value2);
  }

  @FunctionalInterface
  interface IntIntToIntFunction {
    int apply(int value, int value2);
  }

  @FunctionalInterface
  interface LongIntConsumer {
    void accept(long key, int value);
  }
}
