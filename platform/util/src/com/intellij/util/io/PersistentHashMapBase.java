// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.Processor;
import org.jetbrains.annotations.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * A Base interface for custom PersistentHashMap implementations
 */
public interface PersistentHashMapBase<Key, Value> {
  boolean isCorrupted();

  void dropMemoryCaches();

  void deleteMap();

  void put(Key key, Value value) throws IOException;

  /**
   * @deprecated hash map is not an enumerator
   */
  @Deprecated
  int enumerate(Key name) throws IOException;

  /**
   * Appends value chunk from specified appender to key's value.
   * Important use note: value externalizer used by this map should process all bytes from DataInput during deserialization and make sure
   * that deserialized value is consistent with value chunks appended.
   * E.g. Value can be Set of String and individual Strings can be appended with this method for particular key, when {@link #get(Object)} will
   * be eventually called for the key, deserializer will read all bytes retrieving Strings and collecting them into Set
   */
  void appendData(Key key, @NotNull AppendablePersistentMap.ValueDataAppender appender) throws IOException;

  /**
   * Process all keys registered in the map. Note that keys which were removed after {@link #compact()} call will be processed as well. Use
   * {@link #processKeysWithExistingMapping(Processor)} to process only keys with existing mappings
   */
  boolean processKeys(@NotNull Processor<? super Key> processor) throws IOException;

  boolean isClosed();

  boolean isDirty();

  void markDirty() throws IOException;

  @NotNull
  Collection<Key> getAllKeysWithExistingMapping() throws IOException;

  boolean processKeysWithExistingMapping(@NotNull Processor<? super Key> processor) throws IOException;

  Value get(Key key) throws IOException;

  boolean containsMapping(Key key) throws IOException;

  void remove(Key key) throws IOException;

  void force();

  void close() throws IOException;

  // make it visible for tests
  @ApiStatus.Internal
  void compact() throws IOException;

  @ApiStatus.Internal
  boolean isCompactionSupported();
}
