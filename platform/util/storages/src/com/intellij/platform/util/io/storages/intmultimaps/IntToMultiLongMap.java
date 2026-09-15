// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/// Maps each `int` key to a **set of unique** `long` values.
/// Thread-safety and durability depend on the implementation.
/// A persistent implementation also implements [Durable].
@ApiStatus.Internal
public interface IntToMultiLongMap {
  /// Represents an absent value.
  /// This value is unsupported by the map as 'value': methods that accept `value` arguments reject this sentinel;
  /// It is only returned/passed to callbacks by 'lookup'-like methods to denote 'value is absent'.
  long NO_VALUE = 0;

  /// Adds the value to the set for the key. This method does not replace other values
  ///
  /// @return `true` if the method added a new key-value mapping, `false` if map already has key-value mapping
  /// @throws IllegalArgumentException if `value` is [NO_VALUE]
  boolean put(int key, long value) throws IOException;

  /// Replaces the old value if the key maps to it.
  /// If the `newValue` already exists in the set of values for the key, this method removes the old value without adding a duplicate.
  ///
  /// @return `true` if the old key-value mapping existed, and was indeed replaced
  /// @throws IllegalArgumentException if `oldValue` or `newValue` is [NO_VALUE]
  boolean replace(int key, long oldValue, long newValue) throws IOException;

  /// @return the first value for the key accepted by valueAcceptor, or [NO_VALUE] if no value was accepted
  long lookup(int key, @NotNull ValueAcceptor valueAcceptor) throws IOException;

  /// Removes the key-value mapping, if it exists
  ///
  /// @return `true` if the mapping existed, and removed, `false` if there is no `(key, value)` pair in the map
  /// @throws IllegalArgumentException if `value` is [NO_VALUE]
  boolean remove(int key, long value) throws IOException;

  /// Passes all key-value mappings to the processor, stops if processor returns `false`.
  ///
  /// @return `true` if the processor processed all mappings (i.e. it never returns false)
  boolean forEach(@NotNull KeyValueProcessor processor) throws IOException;

  /// @return the number of key-value mappings
  int size();

  /// @return `true` if the map contains no mappings
  boolean isEmpty();

  /// Removes all mappings
  void clear() throws IOException;

  /// Selects a value during a lookup
  @FunctionalInterface
  interface ValueAcceptor {
    boolean accept(long value) throws IOException;
  }

  /// Processes a key-value mapping
  @FunctionalInterface
  interface KeyValueProcessor {
    boolean process(int key, long value) throws IOException;
  }
}
