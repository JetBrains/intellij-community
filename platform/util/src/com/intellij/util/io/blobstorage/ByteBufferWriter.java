// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.blobstorage;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * The specific semantics of {@linkplain #write(ByteBuffer)} should be defined by use-case.
 * Typical semantics:
 * - Target buffer passed in a 'ready to put' state, i.e. `[position..limit]` is the space to put the data in.
 * - A writer fills the data into the target buffer and returns the buffer in `[position=limit]` state
 * - If a writer decides to do no changes, it returns null
 * - If a record size is not strictly defined in advance, and the target buffer is too small -- the writer could
 *   return a newly allocated buffer with the data filled in, instead of the buffer passed in as an argument.
 * It is just a typical semantic, detailed semantics should be given by specific storage.
 */
@ApiStatus.Internal
public interface ByteBufferWriter {
  ByteBuffer write(@NotNull ByteBuffer target) throws IOException;
}
