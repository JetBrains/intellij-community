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


  //====== Below are short-circuit/partial versions of 2 main methods above: ======================== //


  /**
   * Assuming that entry binary format is [header][key][value], this method returns a writer for first part of
   * the entry, [header][key] -- i.e. [value] should be appended afterwards.
   */
  KnownSizeRecordWriter writerForEntryHeader(@NotNull K key) throws IOException;


  //MAYBE RC: instead of series of 'short-circuit' methods -- make Entry an interface with lazy .key/.value/.isValueVoid()
  //          evaluation? I.e. keep reference to ByteBuffer slice, and deserialize key/value only as needed?
  //          But overhead could be quite pronounced? Also I don't want references to a ByteBuffer (which is actually a mmapped
  //          ByteBuffer) to drift far away from the storage itself, since it may cause JVM crashes on unmapping

  /**
   * @return [key, value] entry read from the input buffer, if entry.key==expectedKey, or null if the entry contains key!=expectedKey
   * I.e. it is just short-circuit version of {@link #read(ByteBuffer)} that checks entry.key.equals(expectedKey) early on,
   * and avoid wasting resources on deserializing the value if key doesn't match.
   */
  @Nullable
  Entry<K, V> readIfKeyMatch(@NotNull ByteBuffer input,
                             @NotNull K expectedKey) throws IOException;

  /**
   * @return key from the record, or null, if record is 'deleted'
   * I.e. it is another short-circuit for
   * {@code entry=read(input); if(entry.isValueVoid()) {null}else{entry.key()}}
   */
  @Nullable
  K readKey(@NotNull ByteBuffer input) throws IOException;

  //MAYBE RC: boolean isKeyMatch(@NotNull K expectedKey) throws IOException;
  //          //match the key against ByteBuffer, without instantiating the K object?


  record Entry<K, V>(@NotNull K key,
                     @Nullable V value) {
    public boolean isValueVoid() {
      return value == null;
    }
  }
}
