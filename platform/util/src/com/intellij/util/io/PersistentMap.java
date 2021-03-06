// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  /**
   * Closes the map removing all entries
   */
  @ApiStatus.Experimental
  default void closeAndClean() throws IOException {
    close();
  }
}
