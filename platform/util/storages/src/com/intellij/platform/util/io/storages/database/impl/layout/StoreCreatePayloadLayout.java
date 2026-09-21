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
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT32_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.fieldHandle;

/// Payload for `MetadataChange[type:STORE_CREATE]`
/// ```
/// StoreCreatePayloadLayout {
///   storeId:              int32
///   dataVersion:          int32
///   storeMetadataVersion: int32
///   nameLength:           int32
///   metadataLength:       int32
///   name:                 byte[nameLength]      // utf8 bytes
///   storeMetadata:        byte[metadataLength]  // arbitrary store-specific bytes
/// }
/// ```
@ApiStatus.Internal
public final class StoreCreatePayloadLayout {
  public static final short PAYLOAD_FORMAT_VERSION = 1;

  public static final MemoryLayout FIXED_LAYOUT = MemoryLayout.structLayout(
    INT32_LAYOUT.withName("storeId"),
    INT32_LAYOUT.withName("dataVersion"),
    INT32_LAYOUT.withName("storeMetadataVersion"),
    INT32_LAYOUT.withName("nameLength"),
    INT32_LAYOUT.withName("metadataLength")
  ).withName("StoreCreatePayloadLayout");

  //@formatter:off
  public static final VarHandle STORE_ID_HANDLE               = fieldHandle(FIXED_LAYOUT, "storeId");
  public static final VarHandle DATA_VERSION_HANDLE           = fieldHandle(FIXED_LAYOUT, "dataVersion");
  public static final VarHandle STORE_METADATA_VERSION_HANDLE = fieldHandle(FIXED_LAYOUT, "storeMetadataVersion");
  public static final VarHandle NAME_LENGTH_HANDLE            = fieldHandle(FIXED_LAYOUT, "nameLength");
  public static final VarHandle METADATA_LENGTH_HANDLE        = fieldHandle(FIXED_LAYOUT, "metadataLength");

  public static final int       FIXED_SIZE                    = Math.toIntExact(FIXED_LAYOUT.byteSize());
  //@formatter:on

  /// Persistent identity of a new store
  public record StoreCreateRecordPayload(int storeId,
                                         int dataVersion,
                                         @NotNull String name,
                                         @NotNull StoreMetadata storeMetadata) {
    public StoreCreateRecordPayload {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(storeMetadata, "storeMetadata");
    }

    /// Reads one complete payload from the current source position
    public static @NotNull StoreCreatePayloadLayout.StoreCreateRecordPayload read(@NotNull ByteBuffer source) throws CorruptedException {
      if (source.remaining() < FIXED_SIZE) {
        throw new CorruptedException("the store creation payload header is truncated");
      }

      var segment = MemorySegment.ofBuffer(source.slice());
      var storeId = (int)STORE_ID_HANDLE.get(segment, 0L);
      var dataVersion = (int)DATA_VERSION_HANDLE.get(segment, 0L);
      var storeMetadataVersion = (int)STORE_METADATA_VERSION_HANDLE.get(segment, 0L);
      var nameLength = (int)NAME_LENGTH_HANDLE.get(segment, 0L);
      var metadataLength = (int)METADATA_LENGTH_HANDLE.get(segment, 0L);
      if (metadataLength < 0 || nameLength < 0 ||
          (long)metadataLength + nameLength != source.remaining() - FIXED_SIZE) {
        throw new CorruptedException(
          "store metadata and name lengths do not match the remaining payload"
        );
      }

      var decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
      String name;
      try {
        name = decoder.decode(source.slice(FIXED_SIZE, nameLength)).toString();
      }
      catch (CharacterCodingException e) {
        throw new CorruptedException("the store name is not valid UTF-8", e);
      }

      var storeMetadata = StoreMetadata.copyOf(
        storeMetadataVersion,
        source.slice(FIXED_SIZE + nameLength, metadataLength)
      );

      return new StoreCreateRecordPayload(storeId, dataVersion, name, storeMetadata);
    }

    public int payloadSize() {
      return Math.addExact(Math.addExact(FIXED_SIZE, storeMetadata.size()), nameBytes().length);
    }

    /// Writes the payload at the current target position.
    public @NotNull ByteBuffer writeTo(@NotNull ByteBuffer target) {
      var nameBytes = nameBytes();
      var segment = MemorySegment.ofBuffer(target.slice());
      STORE_ID_HANDLE.set(segment, 0L, storeId);
      DATA_VERSION_HANDLE.set(segment, 0L, dataVersion);
      STORE_METADATA_VERSION_HANDLE.set(segment, 0L, storeMetadata.version());
      NAME_LENGTH_HANDLE.set(segment, 0L, nameBytes.length);
      METADATA_LENGTH_HANDLE.set(segment, 0L, storeMetadata.size());
      target.position(target.position() + FIXED_SIZE);
      target.put(nameBytes);
      target.put(storeMetadata.asReadOnlyBuffer());
      return target;
    }

    private byte @NotNull [] nameBytes() {
      return name.getBytes(StandardCharsets.UTF_8);
    }
  }

  private StoreCreatePayloadLayout() { }
}
