// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;

/**
 * Interface for the maps with collection-like values, there new item could be appended to the end
 * of the already serialized binary form cheaper than with naive way
 * {@code values=get(key); put(key, values+newItem)}
 *
 */
public interface AppendablePersistentMap<K, V> extends PersistentMap<K, V> {
  /**
   * Appends value chunk from specified appender to key's value.
   * <p>
   * This is an alternative, more optimal way to do {@code value=get(key); put(key, value+appendix)}: on
   * appending there is no real need to deserialize current value, one need to just write appendix bytes
   * to the end of current value bytes.
   * ValueDataAppender implementation should be consistent with {@link DataExternalizer value externalizer}
   * used by this map -- i.e. value externalizer should be able to read all the values appended by {@link ValueDataAppender}.
   * <p>
   * Important use note: value externalizer used by this map should process all bytes from DataInput during deserialization and make sure
   * that deserialized value is consistent with value chunks appended.
   * E.g. Value can be Set of String and individual Strings can be appended with this method for particular key, when {@link #get(Object)} will
   * be eventually called for the key, deserializer will read all bytes retrieving Strings and collecting them into Set
   */
  void appendData(K key, @NotNull ValueDataAppender appender) throws IOException;

  interface ValueDataAppender {
    void append(@NotNull DataOutput out) throws IOException;
  }
}
