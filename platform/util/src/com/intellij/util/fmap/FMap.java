// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.fmap;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * An immutable map optimized for storing few entries with relatively rare updates.
 * To construct a map, start with {@link FMap#empty()} and call {@link #plus} and {@link #minus},
 * or use {@link #of}
 * <p>
 * This map was adapted from {@link com.intellij.util.keyFMap.KeyFMap}.
 *
 * @param <K> type of keys
 * @param <V> type of values
 */
@Debug.Renderer(
  text = "\"size = \" + size()",
  hasChildren = "!isEmpty()",
  childrenArray = "toMap().entrySet().toArray()"
)
@ApiStatus.NonExtendable
public interface FMap<K, V> {

  /**
   * @return an empty {@code FMap}. No additional instances of empty {@code FMap} should be created as they are indistinguishable
   */
  @SuppressWarnings("unchecked")
  static <K, V> FMap<K, V> empty() {
    return (FMap<K, V>)EmptyFMap.INSTANCE;
  }

  /**
   * @return an {@code FMap} with a single key-value entry
   */
  static <K, V> @NotNull FMap<K, V> of(@NotNull K key, @NotNull V value) {
    return new OneKeyFMap<>(key, value);
  }

  /**
   * @return an {@code FMap} with a two key-value entries
   */
  static <K, V> @NotNull FMap<K, V> of(@NotNull K key1, @NotNull V value1, @NotNull K key2, @NotNull V value2) {
    assert !key1.equals(key2);
    return new TwoKeysFMap<>(key1, value1, key2, value2);
  }

  /**
   * Returns a {@code FMap} which consists of the same elements as this {@code FMap},
   * but the key {@code key} is associated with the supplied {@code value}.
   * May return itself if the {@code key} is already associated with the supplied {@code value}.
   *
   * @param key   a key to add or replace
   * @param value a value to be associated with the key
   * @return an updated {@code FMap} (this or newly created)
   */
  @NotNull FMap<K, V> plus(K key, V value);

  /**
   * Returns a {@code FMap} which consists of the same elements as this FMap,
   * except the supplied key which is removed.
   * May return itself if the supplied key is absent in this map.
   *
   * @param key a key to remove
   * @return an updated {@code FMap} (this or newly created)
   */
  @NotNull FMap<K, V> minus(K key);

  /**
   * Returns a value associated with given key in this {@code FMap}, or {@code null} if no value is associated.
   * Note that unlike {@link HashMap} {@code FMap} cannot hold null values.
   *
   * @param key a key to get the value associated with
   * @return a value associated with a given {@code key} or {@code null} if there's no such value
   */
  @Nullable V get(K key);

  /**
   * @return {@code true} if this {@code FMap} is empty, otherwise {@code false}
   */
  boolean isEmpty();

  /**
   * @return size (number of keys) of this {@code FMap}
   */
  int size();

  /**
   * @return a collection of all keys present in this {@code FMap}, in no particular order
   */
  @NotNull Collection<K> keys();

  @NotNull Map<K, V> toMap();
}
