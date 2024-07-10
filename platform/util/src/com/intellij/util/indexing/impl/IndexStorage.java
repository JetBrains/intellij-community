// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public interface IndexStorage<Key, Value> extends Flushable, Closeable {

  void addValue(Key key, int inputId, Value value) throws StorageException;

  //RC: why remove_All_Values? Shouldn't it be <=1 value for a (inputId, key)
  void removeAllValues(@NotNull Key key, int inputId) throws StorageException;

  default void updateValue(Key key, int inputId, Value newValue) throws StorageException {
    removeAllValues(key, inputId);
    addValue(key, inputId, newValue);
  }

  void clear() throws StorageException;

  @NotNull
  ValueContainer<Value> read(Key key) throws StorageException;

  /**
   * Drops (some of) cached data, without touching data that is modified and needs to be persisted.
   * TODO RC: clearCaches() is an ambiguous name, this method is better called 'trimCaches()'
   */
  void clearCaches();

  @Override
  void flush() throws IOException;

  boolean isDirty();

  @Override
  void close() throws IOException;

  @Internal
  boolean isClosed();

  /**
   * Invalidates all cached {@link ValueContainer}s: persists modified containers, and clear all the entries
   * from the cache.
   * TODO RC: most of the time this method is used only for the first of its effects: persisting modified
   * cache contents to underlying storage (=flush). Clearing caching entries then is useless side-effect, that
   * is even harmful for performance. Probably, this method should be split in 2: flush() and invalidateCache(),
   * with the first one only persisting modified ValueContainers, while second one also clearing the cache.
   * TODO RC: in its current version, ChangeTrackingValueContainer can't be flushed without being also evicted
   * from cache, because it doesn't reset its .isDirty() status after being saveTo()/saveDiffTo(). This could be
   * re-implemented in a more correct way, or modified containers could be still evicted from cache on flush(),
   * but un-modified containers could be left in cache.
   */
  @Internal
  default void invalidateCachedMappings() {
    //nothing
  }
}
