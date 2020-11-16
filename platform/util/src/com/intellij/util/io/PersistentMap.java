package com.intellij.util.io;

import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public interface PersistentMap<K, V> extends KeyValueStore<K, V> {
  /**
   * Process all keys registered in the map.
   * Note that keys which were removed at some point might be returned as well.
   */
  boolean processKeys(@NotNull Processor<? super K> processor) throws IOException;

  void remove(K key) throws IOException;

  boolean containsMapping(K key) throws IOException;

  boolean isClosed();

  boolean isDirty();

  void markDirty() throws IOException;

  boolean isCorrupted();

  //TODO should not be a part of PersistentMap interface
  @ApiStatus.Internal
  void deleteMap();
}
