// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl.layout;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;

import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT32_LAYOUT;

/// Binary layout of a chunk change _payload_ part:
/// ```
/// ChunkChangePayloadLayout[=4 bytes] {
///   chunkId : int32
/// }
/// ```
@ApiStatus.Internal
public final class ChunkChangePayloadLayout {
  public static final short PAYLOAD_FORMAT_VERSION = 0;

  public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
    INT32_LAYOUT.withName("chunkId")
  ).withName("ChunkChangePayloadLayout");

  //@formatter:off
  public static final VarHandle CHUNK_ID_HANDLE = LayoutUtils.fieldHandle(LAYOUT, "chunkId");

  public static final int PAYLOAD_SIZE          = Math.toIntExact(LAYOUT.byteSize());
  //@formatter:on

  private ChunkChangePayloadLayout() { }

  /// Contains the chunk identifier affected by a catalog change
  public record ChunkChangeRecordPayload(int chunkId) {

    /// Reads the payload from the current source position
    public static @NotNull ChunkChangeRecordPayload read(@NotNull ByteBuffer source) {
      var segment = MemorySegment.ofBuffer(source.slice());
      return new ChunkChangeRecordPayload((int)CHUNK_ID_HANDLE.get(segment, 0L));
    }

    /// Writes the payload at the current target position
    public @NotNull ByteBuffer writeTo(@NotNull ByteBuffer target) {
      var segment = MemorySegment.ofBuffer(target.slice());
      CHUNK_ID_HANDLE.set(segment, 0L, chunkId);
      target.position(target.position() + PAYLOAD_SIZE);
      return target;
    }
  }
}
