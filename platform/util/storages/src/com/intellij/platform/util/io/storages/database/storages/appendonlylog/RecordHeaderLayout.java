// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.appendonlylog;

import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT32_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.fieldHandle;

/// Binary layout of one append-only log record header:
/// ```
/// RecordHeaderLayout[=4 bytes] {
///   packedLengthAndFlags : int32   // (length, flags, state) packed into a single int32
/// }
/// ```
@ApiStatus.Internal
public final class RecordHeaderLayout {
  // Bit-level format:
  // - bit      `0`: record state (ALLOCATED|COMMITTED)
  // - bit      `1`: record state (DEAD|PADDING) (reserved for future)
  // - bit      `2`: an int64 link precedes the payload
  // - bits  `3..5`: padding size (= totalRecordSize - payloadSize - headerSize - linkSize), [0..7]
  // - bits `6..31`: full record length, including header and alignment, in int32 units (i.e. up to 2^28-1 ~= 256 MiB).

  //@formatter:off
  public  static final int RECORD_ALIGNMENT              = 4;

  public  static final int COMMITTED_STATE_MASK          = 0b000001;
  private static final int TOMBSTONE_STATE_MASK          = 0b000010; //reserved
  private static final int HAS_LINK_MASK                 = 0b000100;
  public  static final int PADDING_SIZE_MASK             = 0b111000;
  public  static final int PADDING_SIZE_SHIFT            = 3;

  private static final int LENGTH_SHIFT                  = 6;
  private static final int MAX_LENGTH_IN_INT32_UNITS     = (1 << (Integer.SIZE - LENGTH_SHIFT)) - 1;
  //@formatter:on


  public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
    INT32_LAYOUT.withName("packedLengthAndFlags")
  ).withName("RecordHeaderLayout");

  //@formatter:off
  public static final VarHandle PACKED_LENGTH_AND_FLAGS_HANDLE = fieldHandle(LAYOUT, "packedLengthAndFlags");

  public static final int       HEADER_SIZE                    = Math.toIntExact(LAYOUT.byteSize());
  //@formatter:on

  private RecordHeaderLayout() { }

  // TODO Replace this object with static operations over a packed int.

  /// derives all record metadata from packed int32 header
  public static final class RecordHeader {
    private int header;

    private RecordHeader(int header) {
      this.header = header;
    }

    public static @NotNull RecordHeader allocated(int payloadLength) {
      return allocated(payloadLength, false);
    }

    public static @NotNull RecordHeader allocated(int payloadLength, boolean hasLink) {
      var totalLength = totalLength(payloadLength, hasLink);
      var unalignedLength = HEADER_SIZE + (hasLink ? Long.BYTES : 0) + payloadLength;
      var lengthInInt32Units = totalLength / Integer.BYTES;
      var paddingSize = totalLength - unalignedLength;
      int packedHeader = (lengthInInt32Units << LENGTH_SHIFT) | (paddingSize << PADDING_SIZE_SHIFT) | (hasLink ? HAS_LINK_MASK : 0);
      return new RecordHeader(packedHeader);
    }

    /// @return total record length (bytes): `[header] + [payload] + [int32-alignment padding]`
    public static int totalLength(int payloadLength) {
      return totalLength(payloadLength, /*hasLink: */ false);
    }

    /// @return total record length (bytes): `[header] + [(optional) link field] + [payload] + [int32-alignment padding]`
    public static int totalLength(int payloadLength, boolean hasLink) {
      if (payloadLength < 0) {
        throw new IllegalArgumentException("payloadLength(=" + payloadLength + ") must be non-negative");
      }
      var unalignedLength = Math.addExact(HEADER_SIZE + (hasLink ? Long.BYTES : 0), payloadLength);
      var totalLength = Math.toIntExact(((long)unalignedLength + RECORD_ALIGNMENT - 1) & -RECORD_ALIGNMENT);
      var lengthInIn32Units = totalLength / Integer.BYTES;
      if (lengthInIn32Units > MAX_LENGTH_IN_INT32_UNITS) {
        throw new IllegalArgumentException("payloadLength(=" + payloadLength + ") is too large");
      }
      return totalLength;
    }

    public boolean isCommitted() {
      return (header & COMMITTED_STATE_MASK) != 0;
    }

    public boolean isTombstone() {
      return (header & TOMBSTONE_STATE_MASK) != 0;
    }

    public boolean hasLink() {
      return (header & HAS_LINK_MASK) != 0;
    }

    public int paddingSize() {
      return (header & PADDING_SIZE_MASK) >>> PADDING_SIZE_SHIFT;
    }

    public int totalLength() {
      return (header >>> LENGTH_SHIFT) * Integer.BYTES;
    }

    public int payloadLength() {
      return totalLength() - HEADER_SIZE - (hasLink() ? Long.BYTES : 0) - paddingSize();
    }

    /// Writes an allocated header that is not visible to readers.
    public void writeAllocatedTo(@NotNull MemorySegment target) {
      if (isCommitted()) {
        throw new IllegalStateException("The record header is already committed");
      }
      PACKED_LENGTH_AND_FLAGS_HANDLE.set(target, 0L, header);
    }

    /// Publishes this header after the writer finishes the payload.
    public void publishCommittedTo(@NotNull MemorySegment target) {
      var committedHeader = header | COMMITTED_STATE_MASK;
      PACKED_LENGTH_AND_FLAGS_HANDLE.setRelease(target, 0L, committedHeader);
      header = committedHeader;
    }

    public static @NotNull RecordHeader readCommitted(int blockId,
                                                      int recordOffset,
                                                      @NotNull MemorySegment source,
                                                      int committedTail) throws CorruptedException {
      var header = new RecordHeader((int)PACKED_LENGTH_AND_FLAGS_HANDLE.getAcquire(source, 0L));
      if (!header.isCommitted()) {
        throw corrupted(blockId, "record at offset " + recordOffset + " is not committed");
      }
      if (header.isTombstone()) {
        throw corrupted(blockId, "record at offset " + recordOffset + " is dead or padding");
      }
      validate(blockId, recordOffset, header.totalLength(), header.payloadLength(), committedTail);
      return header;
    }

    private static void validate(int blockId,
                                 int recordOffset,
                                 int totalLength,
                                 int payloadLength,
                                 int committedTail) throws CorruptedException {
      if (totalLength < HEADER_SIZE || (totalLength & (RECORD_ALIGNMENT - 1)) != 0) {
        throw corrupted(blockId, "invalid record length " + totalLength + " at offset " + recordOffset);
      }
      if (payloadLength < 0 || payloadLength > totalLength - HEADER_SIZE) {
        throw corrupted(blockId, "invalid payload length " + payloadLength + " at offset " + recordOffset);
      }
      if ((long)recordOffset + totalLength > committedTail) {
        throw corrupted(blockId, "record at offset " + recordOffset + " exceeds the committed tail");
      }
    }
  }

  private static @NotNull CorruptedException corrupted(int blockId, @NotNull String details) {
    return new CorruptedException("Invalid append-only log block " + blockId + ": " + details);
  }
}
