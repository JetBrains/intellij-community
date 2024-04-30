// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap;

import com.intellij.platform.util.io.storages.DataExternalizerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Customizes (key,value) entry serialization in {@link DurableMap}
 */
public interface EntryExternalizer<K, V> extends DataExternalizerEx<EntryExternalizer.Entry<K, V>> {

  @Override
  default KnownSizeRecordWriter writerFor(@NotNull Entry<K, V> entry) throws IOException {
    return writerFor(entry.key(), entry.value());
  }

  KnownSizeRecordWriter writerFor(@NotNull K key,
                                  @Nullable V value) throws IOException;

  @Override
  @NotNull
  Entry<K, V> read(@NotNull ByteBuffer input) throws IOException;

  /**
   * @return [key, value] entry read from the input buffer, if entry.key==expectedKey, or null if the entry contains key!=expectedKey
   * I.e. it is just short-circuit version of {@link #read(ByteBuffer)} that checks entry.key.equals(expectedKey) early on,
   * and avoid wasting resources on deserializing the value if key doesn't match.
   */
  @Nullable
  Entry<K, V> readIfKeyMatch(@NotNull ByteBuffer input,
                             @NotNull K expectedKey) throws IOException;

  //TODO boolean isKeyMatch(@NotNull K expectedKey) throws IOException;


  record Entry<K, V>(@NotNull K key,
                     @Nullable V value) {
    boolean isValueVoid() {
      return value == null;
    }
  }
}
