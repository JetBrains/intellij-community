// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

/// Bridges synchronous callbacks from old byte buffer backends to FFM serializers without copying data.
@ApiStatus.Internal
public final class ByteBufferFFMBridge {
  private ByteBufferFFMBridge() { }

  /// @return a segment over the buffer's remaining region
  public static @NotNull MemorySegment remainingSegment(@NotNull ByteBuffer buffer) {
    return MemorySegment.ofBuffer(buffer);
  }

  /// Writes one record into the remaining region and advances the buffer position.
  public static @NotNull ByteBuffer write(@NotNull DataExternalizerEx.KnownSizeRecordWriter writer,
                                          @NotNull ByteBuffer buffer) throws IOException {
    int recordSize = writer.recordSize();
    if (recordSize < 0) {
      throw new IllegalArgumentException("recordSize must be non-negative: " + recordSize);
    }
    if (buffer.remaining() < recordSize) {
      throw new BufferOverflowException();
    }

    int position = buffer.position();
    writer.write(MemorySegment.ofBuffer(buffer).asSlice(0, recordSize), 0);
    buffer.position(position + recordSize);
    return buffer;
  }
}
