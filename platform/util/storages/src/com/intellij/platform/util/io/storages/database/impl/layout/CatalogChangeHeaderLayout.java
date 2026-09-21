// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl.layout;

import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;

import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT16_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.fieldHandle;

/// Layout of a _common_ header-part of a catalog (metadata) change entry:
/// ```
/// CatalogChangeHeaderLayout[=4 bytes] {
///   type    : int16  // =ChangeType
///   version : int16  // version of change-specific-header-and-payload binary format
/// }
/// [change-specific-header-and-payload]
/// ```
@ApiStatus.Internal
public final class CatalogChangeHeaderLayout {
  public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
    INT16_LAYOUT.withName("type"),
    INT16_LAYOUT.withName("version")
  ).withName("CatalogChangeHeaderLayout");

  //@formatter:off
  public static final VarHandle TYPE_HANDLE    = fieldHandle(LAYOUT, "type");
  public static final VarHandle VERSION_HANDLE = fieldHandle(LAYOUT, "version");

  public static final int HEADER_SIZE          = Math.toIntExact(LAYOUT.byteSize());
  //@formatter:on

  private CatalogChangeHeaderLayout() { }

  /// Defines the catalog change types stored in the record header.
  public enum ChangeType {
    CHUNK_CREATE(1),
    CHUNK_SEAL(2),
    CHUNK_RETIRE(3),

    STORE_CREATE(4),
    STORE_DROP(5),
    STORE_METADATA_UPDATE(6);

    private final short code;

    ChangeType(int code) { this.code = (short)code; }

    public short code() { return code; }

    /// @return the change type, or `null` if the value is unknown
    public static @Nullable ChangeType fromCode(short code) {
      for (var type : values()) {
        if (type.code == code) {
          return type;
        }
      }
      return null;
    }
  }

  /// Contains the type and payload version of one catalog change record.
  public record CatalogChangeRecordHeader(@NotNull ChangeType type, short version) {
    /// Reads the header from the current source position.
    public static @NotNull CatalogChangeRecordHeader read(@NotNull ByteBuffer source) throws CorruptedException {
      var segment = MemorySegment.ofBuffer(source.slice());
      var typeCode = (short)TYPE_HANDLE.get(segment, 0L);
      var type = ChangeType.fromCode(typeCode);
      if (type == null) {
        throw new CorruptedException("typeCode(=" + typeCode + ") is unknown");
      }
      var version = (short)VERSION_HANDLE.get(segment, 0L);
      return new CatalogChangeRecordHeader(type, version);
    }

    /// Writes the header at the current target position.
    public @NotNull ByteBuffer writeTo(@NotNull ByteBuffer target) {
      var segment = MemorySegment.ofBuffer(target.slice());
      TYPE_HANDLE.set(segment, 0L, type.code());
      VERSION_HANDLE.set(segment, 0L, version);
      target.position(target.position() + HEADER_SIZE);
      return target;
    }
  }
}
