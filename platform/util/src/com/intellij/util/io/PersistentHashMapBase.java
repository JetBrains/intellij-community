// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.Processor;
import com.intellij.util.io.AppendablePersistentMap.ValueDataAppender;
import org.jetbrains.annotations.*;

import java.io.IOException;
import java.util.Collection;

/**
 * A Base interface for custom PersistentHashMap implementations.
 * It is intentionally made not to extend other interfaces.
 * Wrap this class with {@link PersistentHashMap} if you need it to implement other interfaces
 * @see PersistentHashMap
 */
@ApiStatus.Experimental
public interface PersistentHashMapBase<Key, Value> {
  /**
   * Appends value chunk from specified appender to key's value.
   * Important use note: value externalizer used by this map should process all bytes from DataInput during deserialization and make sure
   * that deserialized value is consistent with value chunks appended.
   * E.g. Value can be Set of String and individual Strings can be appended with this method for particular key, when {@link #get(Object)} will
   * be eventually called for the key, deserializer will read all bytes retrieving Strings and collecting them into Set
   */
  void appendData(Key key, @NotNull ValueDataAppender appender) throws IOException;

  /**
   * Process all keys registered in the map.
   * Note that keys which were removed at some point might be returned as well.
   */
  boolean processKeys(@NotNull Processor<? super Key> processor) throws IOException;

  void remove(Key key) throws IOException;

  boolean containsMapping(Key key) throws IOException;

  boolean isClosed();

  boolean isDirty();

  void markDirty() throws IOException;

  boolean isCorrupted();

  Value get(Key key) throws IOException;

  void put(Key key, Value value) throws IOException;

  void force();

  void close() throws IOException;

  /**
   * Closes the map removing all entries
   */
  void closeAndClean() throws IOException;

  void dropMemoryCaches();

  @NotNull
  Collection<Key> getAllKeysWithExistingMapping() throws IOException;

  boolean processKeysWithExistingMapping(@NotNull Processor<? super Key> processor) throws IOException;
}
