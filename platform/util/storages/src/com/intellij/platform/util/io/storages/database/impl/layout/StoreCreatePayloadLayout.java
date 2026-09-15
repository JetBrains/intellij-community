// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl.layout;

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

/// ```
/// StoreCreatePayloadLayout {
///   storeId:     int32
///   dataVersion: int32
///   nameLength:  int32
///   name:        [nameLength utf8 bytes]
/// }
/// ```
@ApiStatus.Internal
public final class StoreCreatePayloadLayout {
  public static final short PAYLOAD_FORMAT_VERSION = 1;

  public static final MemoryLayout FIXED_LAYOUT = MemoryLayout.structLayout(
    INT32_LAYOUT.withName("storeId"),
    INT32_LAYOUT.withName("dataVersion"),
    INT32_LAYOUT.withName("nameLength")
  ).withName("StoreCreatePayloadLayout");

  //@formatter:off
  public static final VarHandle STORE_ID_HANDLE     = fieldHandle(FIXED_LAYOUT, "storeId");
  public static final VarHandle DATA_VERSION_HANDLE = fieldHandle(FIXED_LAYOUT, "dataVersion");
  public static final VarHandle NAME_LENGTH_HANDLE  = fieldHandle(FIXED_LAYOUT, "nameLength");

  public static final int FIXED_SIZE                = Math.toIntExact(FIXED_LAYOUT.byteSize());
  //@formatter:on

  private StoreCreatePayloadLayout() { }

  /// Contains the persistent identity of a new store.
  public record StoreCreateRecordPayload(int storeId, int dataVersion, @NotNull String name) {
    public StoreCreateRecordPayload {
      Objects.requireNonNull(name, "name");
    }

    /// Reads one complete payload from the current source position.
    public static @NotNull StoreCreatePayloadLayout.StoreCreateRecordPayload read(@NotNull ByteBuffer source) throws CorruptedException {
      if (source.remaining() < FIXED_SIZE) {
        throw new CorruptedException("the store creation payload header is truncated");
      }

      var segment = MemorySegment.ofBuffer(source.slice());
      var storeId = (int)STORE_ID_HANDLE.get(segment, 0L);
      var dataVersion = (int)DATA_VERSION_HANDLE.get(segment, 0L);
      var nameLength = (int)NAME_LENGTH_HANDLE.get(segment, 0L);
      if (nameLength < 0 || nameLength != source.remaining() - FIXED_SIZE) {
        throw new CorruptedException(
          "store name length(=" + nameLength + ") does not match the remaining payload(=" + (source.remaining() - FIXED_SIZE) + ")"
        );
      }

      var decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
      try {
        var name = decoder.decode(source.slice(FIXED_SIZE, nameLength)).toString();
        return new StoreCreateRecordPayload(storeId, dataVersion, name);
      }
      catch (CharacterCodingException e) {
        throw new CorruptedException("the store name is not valid UTF-8", e);
      }
    }

    public int payloadSize() {
      return Math.addExact(FIXED_SIZE, nameBytes().length);
    }

    /// Writes the payload at the current target position.
    public @NotNull ByteBuffer writeTo(@NotNull ByteBuffer target) {
      var nameBytes = nameBytes();
      var segment = MemorySegment.ofBuffer(target.slice());
      STORE_ID_HANDLE.set(segment, 0L, storeId);
      DATA_VERSION_HANDLE.set(segment, 0L, dataVersion);
      NAME_LENGTH_HANDLE.set(segment, 0L, nameBytes.length);
      target.position(target.position() + FIXED_SIZE);
      target.put(nameBytes);
      return target;
    }

    private byte @NotNull [] nameBytes() {
      return name.getBytes(StandardCharsets.UTF_8);
    }
  }
}
