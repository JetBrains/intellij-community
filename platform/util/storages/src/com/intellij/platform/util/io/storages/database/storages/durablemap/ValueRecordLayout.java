// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT64_UNALIGNED_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT8_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.fieldHandle;

/// Binary layout of one `VALUES` record payload:
/// ```
/// reference : int64     // unaligned
/// kind      : int8      // (SNAPSHOT | PATCH | TOMBSTONE)
/// payload   : byte[]
/// ```
@ApiStatus.Internal
public final class ValueRecordLayout {
  public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
    INT64_UNALIGNED_LAYOUT.withName("reference"),
    INT8_LAYOUT.withName("kind")
  ).withName("ValueRecordLayout");

  //@formatter:off
  public static final VarHandle REFERENCE_HANDLE = fieldHandle(LAYOUT, "reference");
  public static final VarHandle KIND_HANDLE      = fieldHandle(LAYOUT, "kind");

  public static final int HEADER_SIZE            = (int)LAYOUT.byteSize();
  //@formatter:on

  public static int recordSize(int payloadSize) {
    if (payloadSize < 0) {
      throw new IllegalArgumentException("payloadSize(=" + payloadSize + ") must not be negative");
    }
    return Math.addExact(HEADER_SIZE, payloadSize);
  }

  public static void writeSnapshot(@NotNull MemorySegment target,
                                   long keyRecordRef,
                                   @NotNull MemorySegment payload) {
    write(target, Kind.SNAPSHOT, keyRecordRef, payload);
  }

  public static void writePatch(@NotNull MemorySegment target,
                                long previousValueRef,
                                @NotNull MemorySegment payload) {
    write(target, Kind.PATCH, previousValueRef, payload);
  }

  public static void writeTombstone(@NotNull MemorySegment target, long keyRecordRef) {
    write(target, Kind.TOMBSTONE, keyRecordRef, MemorySegment.NULL);
  }

  public static @NotNull ValueRecord read(@NotNull MemorySegment record) throws CorruptedException {
    if (record.byteSize() < HEADER_SIZE) {
      throw new CorruptedException("Value record size(=" + record.byteSize() + ") must be at least " + HEADER_SIZE);
    }
    var kind = Kind.fromPersistentCode((byte)KIND_HANDLE.get(record, 0L));
    var reference = (long)REFERENCE_HANDLE.get(record, 0L);
    if (reference <= 0) {
      throw new CorruptedException("Value record reference(=" + reference + ") must be positive");
    }
    var payload = record.asSlice(HEADER_SIZE).asReadOnly();
    if (kind == Kind.TOMBSTONE && payload.byteSize() != 0) {
      throw new CorruptedException("TOMBSTONE value record must not have a payload");
    }
    return new ValueRecord(kind, reference, payload);
  }

  private static void write(@NotNull MemorySegment target,
                            @NotNull Kind kind,
                            long reference,
                            @NotNull MemorySegment payload) {
    if (reference <= 0) {
      throw new IllegalArgumentException("reference(=" + reference + ") must be positive");
    }
    switch (kind) {
      case SNAPSHOT, PATCH -> {
      }
      case TOMBSTONE -> {
        if (payload.byteSize() != 0) {
          throw new IllegalArgumentException("TOMBSTONE value record must not have a payload");
        }
      }
    }
    var expectedSize = recordSize(Math.toIntExact(payload.byteSize()));
    if (target.byteSize() != expectedSize) {
      throw new IllegalArgumentException("Value record size(=" + target.byteSize() + ") must be " + expectedSize);
    }
    KIND_HANDLE.set(target, 0L, kind.persistentCode);
    REFERENCE_HANDLE.set(target, 0L, reference);
    MemorySegment.copy(payload, 0, target, HEADER_SIZE, payload.byteSize());
  }

  public record ValueRecord(@NotNull Kind kind,
                            long reference,
                            @NotNull MemorySegment payload) { }

  public enum Kind {
    SNAPSHOT((byte)1),
    PATCH((byte)2),
    TOMBSTONE((byte)3);

    private final byte persistentCode;

    Kind(byte persistentCode) {
      this.persistentCode = persistentCode;
    }

    public byte persistentCode() {
      return persistentCode;
    }

    private static @NotNull Kind fromPersistentCode(byte persistentCode) throws CorruptedException {
      return switch (persistentCode) {
        case 1 -> SNAPSHOT;
        case 2 -> PATCH;
        case 3 -> TOMBSTONE;
        default -> throw new CorruptedException("Unknown value record kind " + persistentCode);
      };
    }
  }

  private ValueRecordLayout() { }
}
