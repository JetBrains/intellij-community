// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentMap;

/**
 * Base interface for concurrent (long key -> V value) map
 * Null values are NOT allowed
 *
 * Methods are adapted from {@link java.util.concurrent.ConcurrentMap} to long keys
 * @see java.util.concurrent.ConcurrentMap
 */
@Debug.Renderer(text = "\"size = \" + size()", hasChildren = "!isEmpty()", childrenArray = "entrySet().toArray()")
public interface ConcurrentLongObjectMap<V> {
  /**
   * @return written value
   */
  @NotNull
  V cacheOrGet(long key, @NotNull V value);
  boolean remove(long key, @NotNull V value);

  /**
   * @see ConcurrentMap#replace(java.lang.Object, java.lang.Object, java.lang.Object)
   * @param key key with which the specified value is associated
   * @param oldValue value expected to be associated with the specified key
   * @param newValue value to be associated with the specified key
   * @return {@code true} if the value was replaced
   */
  boolean replace(long key, @NotNull V oldValue, @NotNull V newValue);

  /**
   * @see ConcurrentMap#replace(java.lang.Object, java.lang.Object)
   * @param key key with which the specified value is associated
   * @param value value to be associated with the specified key
   * @return the previous value associated with the specified key, or
   *         {@code null} if there was no mapping for the key.
   *         (A {@code null} return can also indicate that the map
   *         previously associated {@code null} with the key,
   *         if the implementation supports null values.)
   */
  V replace(long key, @NotNull V value);

  // regular Map methods
  V put(long key, @NotNull V value);
  V get(long key);
  V remove(long key);
  boolean containsKey(long key);
  void clear();
  @NotNull
  Iterable<LongEntry<V>> entries();

  long @NotNull [] keys();

  /**
   * @return Approximate number of elements in the map.
   * The usage is discouraged since
   * First, in concurrent context it doesn't have much sense
   * and Second, for weak- or soft- keyed maps it returns the total number of references
   *         rather than alive values because otherwise it would be too expensive
   */
  int size();

  boolean isEmpty();
  @NotNull
  Enumeration<V> elements();
  @NotNull
  Collection<V> values();
  boolean containsValue(@NotNull V value);
  /**
   * @return the previous value associated with the specified key,
   * or {@code null} if there was no mapping for the key
   */
  V putIfAbsent(long key, @NotNull V value);

  @Debug.Renderer(text = "getKey() + \" -> \\\"\" + getValue() + \"\\\"\"")
  interface LongEntry<V> {
    long getKey();
    @NotNull
    V getValue();
  }
}
