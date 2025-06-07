// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.util.Enumeration;

/**
 * Base interface for concurrent int key -> value:V map
 * Null values are NOT allowed
 * <p>
 * Methods are adapted from {@link java.util.concurrent.ConcurrentMap} to integer keys
 *
 * @see java.util.concurrent.ConcurrentMap
 */
public interface ConcurrentIntObjectMap<V> extends IntObjectMap<V> {
  /**
   * @return written value
   */
  @NotNull
  V cacheOrGet(int key, @NotNull V value);

  boolean remove(int key, @NotNull V value);

  boolean replace(int key, @NotNull V oldValue, @NotNull V newValue);

  V replace(int key, @NotNull V oldValue);

  V getOrDefault(int key, V defaultValue);

  @NotNull
  Enumeration<V> elements();

  /**
   * @return Approximate number of elements in the map.
   * The usage is discouraged since
   * First, in concurrent context it doesn't have much sense
   * and Second, for weak- or soft- keyed maps it returns the total number of references
   * rather than alive values because otherwise it would be too expensive
   */
  @Override
  int size();

  /**
   * @return the previous value associated with the specified key,
   * or {@code null} if there was no mapping for the key
   */
  V putIfAbsent(int key, @NotNull V value);
}
