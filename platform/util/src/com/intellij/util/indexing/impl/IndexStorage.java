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
 * Storage of inverted index data
 * Thread-safety is up to implementation
 *
 * @author Eugene Zhuravlev
 */
public interface IndexStorage<Key, Value> extends Flushable, Closeable {

  void addValue(Key key, int inputId, Value value) throws StorageException;

  //RC: why remove_All_Values? Shouldn't it be <=1 value for a (inputId, key)
  void removeAllValues(@NotNull Key key, int inputId) throws StorageException;

  void updateValue(Key key, int inputId, Value newValue) throws StorageException;

  void clear() throws StorageException;


  //TODO RC: this method better replaced with read(key, lambda), because otherwise it is hard to ensure thread-safety
  //         (i.e. use-site must be wrapped in read lock)
  @Deprecated
  @NotNull ValueContainer<Value> read(Key key) throws StorageException;


  /**
   * The processor will be invoked on {@link ValueContainer}s corresponding to the given key.
   * <b>NOTE</b>: it could be more than one container associated with a single key, e.g. if an
   * actual storage is sharded, hence the processor could be invoked <b>more than once</b>
   * so the processor code must be ready to aggregate results of >1 container passed in.
   * <p>
   * The processing should be as fast, as possible, because the storage may hold some locks
   * during the processing to ensure data consistency, which may delay concurrent index update,
   * if any
   *
   * @return true if all data was processed, false if processing was stopped prematurely because
   * processor returns false at some point
   */
  <E extends Exception> boolean read(Key key,
                                     @NotNull ValueContainerProcessor<Value, E> processor) throws StorageException, E;

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
