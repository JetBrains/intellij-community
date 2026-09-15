// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.appendonlylog;

import com.intellij.platform.util.io.storages.database.storages.appendonlylog.RecordHeaderLayout.RecordHeader;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

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
    return Math.addExact(AppendOnlyLogOverBlockHeaderLayout.HEADER_SIZE, RecordHeader.allocated(payloadLength).totalLength());
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
    var totalLength = RecordHeader.allocated(payloadLength).totalLength();
    return content.byteSize() - AppendOnlyLogOverBlockHeaderLayout.allocatedTail(content) >= totalLength;
  }

  /// Appends one payload and returns its offset inside the block
  public synchronized int append(int payloadLength, @NotNull RecordWriter writer) throws IOException {
    if (block.state() != ACTIVE) {
      throw new IllegalStateException("Block " + block.id() + " is not active");
    }
    var recordOffset = AppendOnlyLogOverBlockHeaderLayout.allocatedTail(content);
    var committedTail = AppendOnlyLogOverBlockHeaderLayout.committedTail(content);
    if (recordOffset != committedTail) {
      throw new IllegalStateException("Block " + block.id() + " has an incomplete allocation");
    }

    var header = RecordHeader.allocated(payloadLength);
    if (content.byteSize() - recordOffset < header.totalLength()) {
      throw new IllegalStateException("Block " + block.id() + " has no space for the record");
    }

    var recordHeader = content.asSlice(recordOffset, RecordHeaderLayout.HEADER_SIZE);
    header.writeAllocatedTo(recordHeader);
    var newTail = Math.addExact(recordOffset, header.totalLength());
    AppendOnlyLogOverBlockHeaderLayout.publishAllocatedTail(content, newTail);

    writer.writeTo(payload(recordOffset, header.payloadLength()));

    header.publishCommittedTo(recordHeader);
    AppendOnlyLogOverBlockHeaderLayout.publishCommittedTail(content, newTail);
    return recordOffset;
  }

  /// @return a read-only view of a committed payload
  public @NotNull MemorySegment read(int recordOffset) throws CorruptedException {
    if (block.state() == BlocksStore.Block.LifecycleState.RETIRED) {
      throw new IllegalStateException("Block " + block.id() + " is retired");
    }
    var recordHeader = content.asSlice(recordOffset, RecordHeaderLayout.HEADER_SIZE);
    var committedTail = AppendOnlyLogOverBlockHeaderLayout.committedTail(content);
    var header = RecordHeader.readCommitted(block.id(), recordOffset, recordHeader, committedTail);
    return payload(recordOffset, header.payloadLength()).asReadOnly();
  }

  public void forEachCommittedRecord(@NotNull RecordReader reader) throws IOException {
    scanCommittedRecords(AppendOnlyLogOverBlockHeaderLayout.committedTail(content), reader);
  }

  private void scanCommittedRecords(int committedTail, @NotNull RecordReader reader) throws IOException {
    var recordOffset = AppendOnlyLogOverBlockHeaderLayout.HEADER_SIZE;
    while (recordOffset < committedTail) {
      var recordHeader = content.asSlice(recordOffset, RecordHeaderLayout.HEADER_SIZE);
      var header = RecordHeader.readCommitted(block.id(), recordOffset, recordHeader, committedTail);
      reader.process(recordOffset, payload(recordOffset, header.payloadLength()).asReadOnly());
      recordOffset += header.totalLength();
    }
    if (recordOffset != committedTail) {
      throw new CorruptedException(
        "Invalid append-only log block " + block.id() + ": the committed tail does not end at a record boundary"
      );
    }
  }

  private @NotNull MemorySegment payload(int recordOffset, int payloadLength) {
    return content.asSlice(recordOffset + RecordHeaderLayout.HEADER_SIZE, payloadLength);
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
