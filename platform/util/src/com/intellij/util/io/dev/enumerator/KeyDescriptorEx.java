// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.enumerator;

import com.intellij.util.io.dev.appendonlylog.AppendOnlyLog;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Full analog of {@link KeyDescriptor}, but with {@link ByteBuffer} instead of {@link java.io.InputStream} and
 * {@link java.io.OutputStream}
 */
@ApiStatus.Internal
public interface KeyDescriptorEx<K> {
  int hashCodeOf(K value);

  boolean areEqual(K key1,
                   K key2);


  K read(@NotNull ByteBuffer input) throws IOException;

  //TODO RC: this is quite troubling API choice: we need to know the size of key binary
  //         representation to allocate room for the record in append-log. But for many
  //         types K the only way to know the size is to actually serialize the object
  //         -- hence API basically forces to do it twice: first time to assess the size,
  //         second time to actually write the object into ByteBuffer. This is dummy.

  default long saveToLog(@NotNull K key,
                         @NotNull AppendOnlyLog log) throws IOException {
    int recordSize = sizeOfSerialized(key);
    return log.append(buffer -> {
      save(buffer, key);
      return buffer;
    }, recordSize);
  }

  int sizeOfSerialized(K key) throws IOException;

  void save(@NotNull ByteBuffer output,
            K key) throws IOException;
}
