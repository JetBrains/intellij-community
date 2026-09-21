// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl.layout;

import com.intellij.platform.util.io.storages.database.spi.StoreMetadata;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.Objects;

import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT32_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.fieldHandle;

/// Payload for `MetadataChange[type:STORE_METADATA_UPDATE]`
/// ```
/// StoreMetadataPayloadLayout[12 bytes] {
///   storeId:               int32
///   storeMetadataVersion:  int32
///   metadataLength:        int32
/// }
/// ```
@ApiStatus.Internal
public final class StoreMetadataPayloadLayout {
  public static final short PAYLOAD_FORMAT_VERSION = 1;

  public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
    INT32_LAYOUT.withName("storeId"),
    INT32_LAYOUT.withName("storeMetadataVersion"),
    INT32_LAYOUT.withName("metadataLength")
  ).withName("StoreMetadataPayloadLayout");

  //@formatter:off
  private static final VarHandle STORE_ID_HANDLE                = fieldHandle(LAYOUT, "storeId");
  private static final VarHandle STORE_METADATA_VERSION_HANDLE  = fieldHandle(LAYOUT, "storeMetadataVersion");
  private static final VarHandle METADATA_LENGTH_HANDLE         = fieldHandle(LAYOUT, "metadataLength");

  public static final int        FIXED_SIZE                     = Math.toIntExact(LAYOUT.byteSize());
  //@formatter:on

  public record StoreMetadataRecordPayload(int storeId,
                                           @NotNull StoreMetadata storeMetadata) {
    public StoreMetadataRecordPayload {
      Objects.requireNonNull(storeMetadata, "storeMetadata");
    }

    public static @NotNull StoreMetadataRecordPayload read(@NotNull ByteBuffer source) throws CorruptedException {
      if (source.remaining() < FIXED_SIZE) {
        throw new CorruptedException("the store metadata payload header is truncated");
      }
      var segment = MemorySegment.ofBuffer(source.slice());
      var storeId = (int)STORE_ID_HANDLE.get(segment, 0L);
      var storeMetadataVersion = (int)STORE_METADATA_VERSION_HANDLE.get(segment, 0L);
      var metadataLength = (int)METADATA_LENGTH_HANDLE.get(segment, 0L);
      if (metadataLength < 0 || metadataLength != source.remaining() - FIXED_SIZE) {
        throw new CorruptedException("the store metadata length does not match the remaining payload");
      }
      var storeMetadata = StoreMetadata.copyOf(storeMetadataVersion, source.slice(FIXED_SIZE, metadataLength));
      return new StoreMetadataRecordPayload(storeId, storeMetadata);
    }

    public int payloadSize() {
      return Math.addExact(FIXED_SIZE, storeMetadata.size());
    }

    public @NotNull ByteBuffer writeTo(@NotNull ByteBuffer target) {
      var segment = MemorySegment.ofBuffer(target.slice());
      STORE_ID_HANDLE.set(segment, 0L, storeId);
      STORE_METADATA_VERSION_HANDLE.set(segment, 0L, storeMetadata.version());
      METADATA_LENGTH_HANDLE.set(segment, 0L, storeMetadata.size());
      target.position(target.position() + FIXED_SIZE);
      target.put(storeMetadata.asReadOnlyBuffer());
      return target;
    }
  }

  private StoreMetadataPayloadLayout() { }
}
