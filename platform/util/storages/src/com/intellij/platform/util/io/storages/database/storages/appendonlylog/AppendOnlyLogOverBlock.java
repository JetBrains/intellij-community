// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.appendonlylog;

import com.intellij.platform.util.io.storages.database.storages.appendonlylog.RecordHeaderLayout.RecordHeader;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ALLOCATED;

/// Stores append-only records with arbitrary payloads inside one [BlocksStore.Block].
/// [AppendOnlyLogOverBlockHeaderLayout] defines header binary layout.
/// Block selection, chaining and lifecycle transitions belong to the storage that owns this log.
@ApiStatus.Internal
public final class AppendOnlyLogOverBlock {
  private final @NotNull BlocksStore.Block block;
  private final @NotNull MemorySegment content;

  private AppendOnlyLogOverBlock(@NotNull BlocksStore.Block block,
                                 @NotNull MemorySegment content) {
    this.block = block;
    this.content = content;
  }

  public static int minimumBlockContentLengthFor(int payloadLength) {
    return minimumBlockContentLengthFor(payloadLength, /*hasLink: */ false);
  }

  public static int minimumBlockContentLengthFor(int payloadLength, boolean hasLink) {
    return Math.addExact(AppendOnlyLogOverBlockHeaderLayout.HEADER_SIZE, RecordHeader.totalLength(payloadLength, hasLink));
  }

  /// Initializes an empty log header in the block, and activates the block;
  /// The block must be in [ALLOCATED] state;
  public static @NotNull AppendOnlyLogOverBlock create(@NotNull BlocksStore.Block block) {
    var initialState = block.state();
    if (initialState != ALLOCATED) {
      throw new IllegalArgumentException("Block " + block.id() + " must be allocated, but: " + initialState);
    }

    var initialized = false;
    try {
      var content = block.content();
      if (content.byteSize() < minimumBlockContentLengthFor(0)) {
        throw new IllegalArgumentException("Block " + block.id() + " content (=" + content.byteSize() + ") is too small");
      }
      AppendOnlyLogOverBlockHeaderLayout.initializeEmpty(content);
      block.activate();
      initialized = true;
      return new AppendOnlyLogOverBlock(block, content);
    }
    finally {
      if (!initialized && block.state() == ALLOCATED) {
        block.discard();
      }
    }
  }

  /// Opens a log and validates its committed record prefix
  public static @NotNull AppendOnlyLogOverBlock open(@NotNull BlocksStore.Block block) throws IOException {
    var content = block.content();
    var committedTail = AppendOnlyLogOverBlockHeaderLayout.readValidatedCommittedTail(block.id(), content);
    var log = new AppendOnlyLogOverBlock(block, content);
    log.scanCommittedRecords(committedTail, (_, _) -> { });
    return log;
  }

  public @NotNull BlocksStore.Block block() {
    return block;
  }

  @Override
  public String toString() {
    return "AppendOnlyLog[block #" + block.id() + ']';
  }

  public synchronized boolean hasIncompleteAllocation() {
    return AppendOnlyLogOverBlockHeaderLayout.allocatedTail(content) != AppendOnlyLogOverBlockHeaderLayout.committedTail(content);
  }

  public synchronized boolean hasSpaceFor(int payloadLength) {
    return hasSpaceFor(payloadLength, /* hasLink: */ false);
  }

  public synchronized boolean hasSpaceFor(int payloadLength, boolean hasLink) {
    var totalLength = RecordHeader.totalLength(payloadLength, hasLink);
    return content.byteSize() - AppendOnlyLogOverBlockHeaderLayout.allocatedTail(content) >= totalLength;
  }

  /// Appends one payload and returns its offset inside the block
  public synchronized int append(int payloadLength, @NotNull RecordWriter writer) throws IOException {
    return append(payloadLength, /*previousRef: */0, writer);
  }

  /// Appends a payload with an (optional) link to a previous record in chain; zero denotes an absent link.
  public synchronized int append(int payloadLength, long previousRef, @NotNull RecordWriter writer) throws IOException {
    if (block.state() != ACTIVE) {
      throw new IllegalStateException("Block " + block.id() + " is not active");
    }
    var recordOffset = AppendOnlyLogOverBlockHeaderLayout.allocatedTail(content);
    var committedTail = AppendOnlyLogOverBlockHeaderLayout.committedTail(content);
    if (recordOffset != committedTail) {
      throw new IllegalStateException("Block " + block.id() + " has an incomplete allocation");
    }

    boolean hasLink = (previousRef != 0);
    var header = RecordHeader.allocated(payloadLength, hasLink);
    if (content.byteSize() - recordOffset < header.totalLength()) {
      throw new IllegalStateException("Block " + block.id() + " has no space for the record");
    }

    var recordHeader = content.asSlice(recordOffset, RecordHeaderLayout.HEADER_SIZE);
    header.writeAllocatedTo(recordHeader);
    var newTail = Math.addExact(recordOffset, header.totalLength());
    AppendOnlyLogOverBlockHeaderLayout.publishAllocatedTail(content, newTail);

    if (header.hasLink()) {
      //A header is int32, int32-aligned => [link:int64] right after header is _not_ (int64-)aligned:
      content.set(ValueLayout.JAVA_LONG_UNALIGNED, recordOffset + RecordHeaderLayout.HEADER_SIZE, previousRef);
    }
    writer.writeTo(payload(recordOffset, header));

    header.publishCommittedTo(recordHeader);
    AppendOnlyLogOverBlockHeaderLayout.publishCommittedTail(content, newTail);
    return recordOffset;
  }

  /// @return a read-only view of a committed payload
  public @NotNull MemorySegment read(int recordOffset) throws CorruptedException {
    return readRecord(recordOffset).payload();
  }

  public @NotNull Record readRecord(int recordOffset) throws CorruptedException {
    if (block.state() == BlocksStore.Block.LifecycleState.RETIRED) {
      throw new IllegalStateException("Block " + block.id() + " is retired");
    }
    var committedTail = AppendOnlyLogOverBlockHeaderLayout.committedTail(content);
    if (recordOffset < AppendOnlyLogOverBlockHeaderLayout.HEADER_SIZE ||
        recordOffset % RecordHeaderLayout.RECORD_ALIGNMENT != 0 ||
        (long)recordOffset + RecordHeaderLayout.HEADER_SIZE > committedTail) {
      throw new CorruptedException("Invalid record offset " + recordOffset + " in block " + block.id());
    }
    var recordHeader = content.asSlice(recordOffset, RecordHeaderLayout.HEADER_SIZE);
    var header = RecordHeader.readCommitted(block.id(), recordOffset, recordHeader, committedTail);
    return record(recordOffset, header);
  }

  public void forEachCommittedRecord(@NotNull RecordReader reader) throws IOException {
    forEachCommittedRecordWithLinks((offset, record) -> reader.process(offset, record.payload()));
  }

  public void forEachCommittedRecordWithLinks(@NotNull LinkedRecordReader reader) throws IOException {
    scanCommittedRecords(AppendOnlyLogOverBlockHeaderLayout.committedTail(content), reader);
  }

  private void scanCommittedRecords(int committedTail, @NotNull LinkedRecordReader reader) throws IOException {
    var recordOffset = AppendOnlyLogOverBlockHeaderLayout.HEADER_SIZE;
    while (recordOffset < committedTail) {
      var recordHeader = content.asSlice(recordOffset, RecordHeaderLayout.HEADER_SIZE);
      var header = RecordHeader.readCommitted(block.id(), recordOffset, recordHeader, committedTail);
      reader.process(recordOffset, record(recordOffset, header));
      recordOffset += header.totalLength();
    }
    if (recordOffset != committedTail) {
      throw new CorruptedException(
        "Invalid append-only log block " + block.id() + ": the committed tail does not end at a record boundary"
      );
    }
  }

  private @NotNull MemorySegment payload(int recordOffset, @NotNull RecordHeader header) {
    return content.asSlice(recordOffset + RecordHeaderLayout.HEADER_SIZE + (header.hasLink() ? Long.BYTES : 0), header.payloadLength());
  }

  private @NotNull Record record(int recordOffset, @NotNull RecordHeader header) throws CorruptedException {
    long previousRef = 0;
    if (header.hasLink()) {
      previousRef = content.get(ValueLayout.JAVA_LONG_UNALIGNED, recordOffset + RecordHeaderLayout.HEADER_SIZE);
      if (previousRef == 0) {
        throw new CorruptedException("Missing record link at offset " + recordOffset + " in block " + block.id());
      }
    }
    return new Record(previousRef, payload(recordOffset, header).asReadOnly());
  }

  /// The [payload] excludes the (optional) [previousRef].
  /// `previousRef=0` denotes an absent ref.
  public record Record(long previousRef, @NotNull MemorySegment payload) {
    @Override
    public String toString() {
      return "Record[previousRef=" + previousRef + ", payloadSize=" + payload.byteSize() + ']';
    }
  }

  @FunctionalInterface
  public interface LinkedRecordReader {
    void process(int recordOffset, @NotNull Record record) throws IOException;
  }

  /// Writes one payload into a segment with the requested payload length
  @FunctionalInterface
  public interface RecordWriter {
    void writeTo(@NotNull MemorySegment payload) throws IOException;
  }

  /// Processes one committed payload
  @FunctionalInterface
  public interface RecordReader {
    void process(int recordOffset, @NotNull MemorySegment payload) throws IOException;
  }
}
