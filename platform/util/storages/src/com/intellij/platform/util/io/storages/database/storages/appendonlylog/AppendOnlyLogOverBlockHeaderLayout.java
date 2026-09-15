// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.appendonlylog;

import com.intellij.platform.util.io.storages.UnsupportedFormatException;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT16_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT32_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.fieldHandle;
import static com.intellij.platform.util.io.storages.database.storages.appendonlylog.RecordHeaderLayout.RECORD_ALIGNMENT;

/// Binary layout of an append-only-log header inside block:
/// ```
/// AppendOnlyLogOverBlockHeaderLayout[=12 bytes] {
///   formatVersion : int16
///   [padding]     : int16
///   allocatedTail : int32
///   committedTail : int32
/// }
/// ```
@ApiStatus.Internal
public final class AppendOnlyLogOverBlockHeaderLayout {
  public static final short FORMAT_VERSION = 1;

  public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
    INT16_LAYOUT.withName("formatVersion"),
    MemoryLayout.paddingLayout(Short.BYTES), // so allocatedTail is on 32-aligned offset
    INT32_LAYOUT.withName("allocatedTail"),
    INT32_LAYOUT.withName("committedTail")
  ).withName("AppendOnlyLogOverBlockHeaderLayout");

  //@formatter:off
  public static final VarHandle FORMAT_VERSION_HANDLE = fieldHandle(LAYOUT, "formatVersion");
  public static final VarHandle ALLOCATED_TAIL_HANDLE = fieldHandle(LAYOUT, "allocatedTail");
  public static final VarHandle COMMITTED_TAIL_HANDLE = fieldHandle(LAYOUT, "committedTail");

  public static final int HEADER_SIZE                 = Math.toIntExact(LAYOUT.byteSize());
  //@formatter:on

  /// Initializes the header of a new, empty log
  public static void initializeEmpty(@NotNull MemorySegment target) {
    FORMAT_VERSION_HANDLE.set(target, 0L, FORMAT_VERSION);
    ALLOCATED_TAIL_HANDLE.set(target, 0L, HEADER_SIZE);
    COMMITTED_TAIL_HANDLE.set(target, 0L, HEADER_SIZE);
  }

  public static int readValidatedCommittedTail(int blockId, @NotNull MemorySegment source) throws IOException {
    if (source.byteSize() < HEADER_SIZE + RecordHeaderLayout.HEADER_SIZE) {
      throw corrupted(blockId, "the content is too small");
    }
    var formatVersion = (short)FORMAT_VERSION_HANDLE.get(source, 0L);
    if (formatVersion != FORMAT_VERSION) {
      throw new UnsupportedFormatException(
        "append-only log in block " + blockId,
        Short.toUnsignedInt(FORMAT_VERSION),
        Short.toUnsignedInt(formatVersion)
      );
    }

    var allocatedTail = allocatedTail(source);
    var committedTail = committedTail(source);
    validateTails(blockId, source.byteSize(), allocatedTail, committedTail);
    return committedTail;
  }

  public static void publishAllocatedTail(@NotNull MemorySegment target, int value) {
    ALLOCATED_TAIL_HANDLE.setRelease(target, 0L, value);
  }

  public static int allocatedTail(@NotNull MemorySegment source) {
    return (int)ALLOCATED_TAIL_HANDLE.getAcquire(source, 0L);
  }

  public static void publishCommittedTail(@NotNull MemorySegment target, int value) {
    COMMITTED_TAIL_HANDLE.setRelease(target, 0L, value);
  }

  public static int committedTail(@NotNull MemorySegment source) {
    return (int)COMMITTED_TAIL_HANDLE.getAcquire(source, 0L);
  }

  private static void validateTails(int blockId,
                                    long contentLength,
                                    int allocatedTail,
                                    int committedTail) throws CorruptedException {
    if (committedTail < HEADER_SIZE || committedTail > allocatedTail || allocatedTail > contentLength) {
      throw corrupted(blockId,
                      "expected " + HEADER_SIZE + " <= committedTail(=" + committedTail +
                      ") <= allocatedTail(=" + allocatedTail + ") <= contentLength(=" + contentLength + ")"
      );
    }
    if (((committedTail - HEADER_SIZE) & (RECORD_ALIGNMENT - 1)) != 0 ||
        ((allocatedTail - HEADER_SIZE) & (RECORD_ALIGNMENT - 1)) != 0) {
      throw corrupted(blockId, "block tails must be " + RECORD_ALIGNMENT + "-byte aligned");
    }
  }

  private static @NotNull CorruptedException corrupted(int blockId, @NotNull String details) {
    return new CorruptedException("Invalid append-only log block " + blockId + ": " + details);
  }

  private AppendOnlyLogOverBlockHeaderLayout() { }
}
