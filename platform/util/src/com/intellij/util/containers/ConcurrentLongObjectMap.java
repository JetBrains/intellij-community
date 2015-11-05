/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
  boolean replace(long key, @NotNull V oldValue, @NotNull V newValue);
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
  V putIfAbsent(long key, @NotNull V value);

  interface LongEntry<V> {
    long getKey();
    @NotNull
    V getValue();
  }
}
