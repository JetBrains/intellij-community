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

  long saveToLog(@NotNull K key,
                 @NotNull AppendOnlyLog log) throws IOException;
}
