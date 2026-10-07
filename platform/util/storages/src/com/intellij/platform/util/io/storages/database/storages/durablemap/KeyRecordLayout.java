// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT32_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT64_UNALIGNED_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.fieldHandle;

/// Binary layout of one `KEYS` record payload:
/// ```
/// valueRecordRef : int64    // unaligned
/// keyHash        : int32    // cache to avoid deserializing key just to compute it
/// keyPayload     : byte[]
/// ```
/// The caller holds the map lock when it reads or updates `valueRecordRef`.
@ApiStatus.Internal
public final class KeyRecordLayout {
  public static final long NO_VALUE_RECORD_REF = 0;

  public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
    INT64_UNALIGNED_LAYOUT.withName("valueRecordRef"),
    INT32_LAYOUT.withName("keyHash")
  ).withName("KeyRecordLayout");

  //@formatter:off
  public static final VarHandle VALUE_RECORD_REF_HANDLE   = fieldHandle(LAYOUT, "valueRecordRef");
  public static final VarHandle KEY_HASH_HANDLE           = fieldHandle(LAYOUT, "keyHash");

  public static final int  HEADER_SIZE                    = (int)LAYOUT.byteSize();
  //@formatter:on

  public static int recordSize(int keyPayloadSize) {
    if (keyPayloadSize < 0) {
      throw new IllegalArgumentException("keyPayloadSize(=" + keyPayloadSize + ") must not be negative");
    }
    return Math.addExact(HEADER_SIZE, keyPayloadSize);
  }

  /// Initializes a key record without a value.
  public static void write(@NotNull MemorySegment target,
                           int keyHash,
                           @NotNull MemorySegment keyPayload) {
    var expectedSize = recordSize(Math.toIntExact(keyPayload.byteSize()));
    if (target.byteSize() != expectedSize) {
      throw new IllegalArgumentException("Key record size(=" + target.byteSize() + ") must be " + expectedSize);
    }
    VALUE_RECORD_REF_HANDLE.set(target, 0L, NO_VALUE_RECORD_REF);
    KEY_HASH_HANDLE.set(target, 0L, keyHash);
    MemorySegment.copy(keyPayload, 0, target, HEADER_SIZE, keyPayload.byteSize());
  }

  /// Updates the value head while the map lock is held.
  public static void updateValueRecordRef(@NotNull MemorySegment record, long valueRecordRef) throws CorruptedException {
    validate(record);
    requireValidValueRecordRef(valueRecordRef);
    VALUE_RECORD_REF_HANDLE.set(record, 0L, valueRecordRef);
  }

  public static long valueRecordRef(@NotNull MemorySegment record) throws CorruptedException {
    validate(record);
    var valueRecordRef = (long)VALUE_RECORD_REF_HANDLE.get(record, 0L);
    requireValidStoredValueRecordRef(valueRecordRef);
    return valueRecordRef;
  }

  public static int keyHash(@NotNull MemorySegment record) throws CorruptedException {
    validate(record);
    return (int)KEY_HASH_HANDLE.get(record, 0L);
  }

  /// @return a read-only view of the serialized key
  public static @NotNull MemorySegment keyPayload(@NotNull MemorySegment record) throws CorruptedException {
    validate(record);
    return record.asSlice(HEADER_SIZE).asReadOnly();
  }

  private static void validate(@NotNull MemorySegment record) throws CorruptedException {
    if (record.byteSize() < HEADER_SIZE) {
      throw new CorruptedException("Key record size(=" + record.byteSize() + ") must be at least " + HEADER_SIZE);
    }
  }

  private static void requireValidValueRecordRef(long valueRecordRef) {
    if (valueRecordRef < 0) {
      throw new IllegalArgumentException("valueRecordRef(=" + valueRecordRef + ") must not be negative");
    }
  }

  private static void requireValidStoredValueRecordRef(long valueRecordRef) throws CorruptedException {
    if (valueRecordRef < 0) {
      throw new CorruptedException("valueRecordRef(=" + valueRecordRef + ") must not be negative");
    }
  }

  private KeyRecordLayout() { }
}
