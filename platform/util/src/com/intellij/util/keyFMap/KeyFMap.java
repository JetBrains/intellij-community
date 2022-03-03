// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.keyFMap;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An immutable map optimized for storing few {@link Key} entries with relatively rare updates.
 * To construct a map, start with {@link KeyFMap#EMPTY_MAP} and call {@link #plus} and {@link #minus}.
 *
 * <p>
 *   The hashCode() contract conforms to the hashCode() contract of the {@link java.util.Map} interface:
 *   it's the sum of hash codes of its entries, which in turn is calculated as {@code key.hashCode() ^ value.hashCode()}
 * </p>
 *
 * @author peter
 */
@Debug.Renderer(text = "\"size = \" + size()", hasChildren = "!isEmpty()", childrenArray = "KeyFMapRenderer.childrenArray(this)")
public interface KeyFMap {
  /**
   * An empty {@code KeyFMap}. No additional instances of empty {@code KeyFMap} should be created as they are indistinguishable.
   */
  KeyFMap EMPTY_MAP = EmptyFMap.create();

  /**
   * Returns a {@code KeyFMap} which consists of the same elements as this {@code KeyFMap}, but
   * the key {@code key} is associated with the supplied {@code value}. May return itself if the {@code key}
   * is already associated with the supplied {@code value}.
   *
   * @param key a key to add or replace
   * @param value a value to be associated with the key
   * @param <V> a type of the value
   * @return an updated {@code KeyFMap} (this or newly created)
   */
  @NotNull
  <V> KeyFMap plus(@NotNull Key<V> key, @NotNull V value);

  /**
   * Returns a KeyFMap which consists of the same elements as this KeyFMap, except
   * the supplied key which is removed. May return itself if the supplied key is absent
   * in this map.
   *
   * @param key a key to remove
   * @return an updated KeyFMap
   */
  @NotNull
  KeyFMap minus(@NotNull Key<?> key);

  /**
   * Returns a value associated with given key in this {@code KeyFMap}, or null if no value is associated.
   * Note that unlike {@link java.util.HashMap} {@code KeyFMap} cannot hold null values.
   *
   * @param key a key to get the value associated with
   * @param <V> a type of the value
   * @return a value associated with given key or null if there's no such value
   */
  @Nullable
  <V> V get(@NotNull Key<V> key);

  /**
   * @return size (number of keys) of this {@code KeyFMap}.
   */
  int size();

  /**
   * @return an array of all keys present in this {@code KeyFMap}, in no particular order. The length of the array equals to KeyFMap size.
   */
  @NotNull Key<?> @NotNull [] getKeys();

  /**
   * @return true if this {@code KeyFMap} is empty.
   */
  boolean isEmpty();

  /**
   * @return a hashCode function for this map which uses {@link System#identityHashCode(Object)} for values.
   */
  int getValueIdentityHashCode();

  /**
   * Checks if other {@code KeyFMap} equals to this, assuming reference equality for the values
   *
   * @param other {@code KeyFMap} to compare with
   * @return true if other map equals to this map.
   */
  boolean equalsByReference(@NotNull KeyFMap other);
}
