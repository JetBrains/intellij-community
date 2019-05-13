/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.containers;

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

  @NotNull
  long[] keys();

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
  public Enumeration<V> elements();
  @NotNull
  Collection<V> values();
  boolean containsValue(@NotNull V value);
  /**
   * @return the previous value associated with the specified key,
   * or {@code null} if there was no mapping for the key
   */
  V putIfAbsent(long key, @NotNull V value);

  interface LongEntry<V> {
    long getKey();
    @NotNull
    V getValue();
  }
}
