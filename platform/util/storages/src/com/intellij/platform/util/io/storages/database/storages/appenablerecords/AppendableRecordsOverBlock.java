// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.appenablerecords;

import com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState;
import com.intellij.platform.util.io.storages.database.storages.appenablerecords.RecordHeaderLayout.RecordHeader;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ALLOCATED;

/// Appendable records with arbitrary payloads, organized inside a single [BlocksStore.Block].
/// [AppendableRecordsOverBlockHeaderLayout] defines header binary layout.
/// Block selection, chaining and lifecycle transitions belong to the storage that owns this log.
/// Individual records header is [RecordHeaderLayout].
///
/// Records could be treated as *append-only*, or *modifiable* after appended -- this class supports
/// both policies, it is up to caller to decide.
@ApiStatus.Internal
public final class AppendableRecordsOverBlock {
  private final @NotNull BlocksStore.Block block;
  private final @NotNull MemorySegment content;

  private AppendableRecordsOverBlock(@NotNull BlocksStore.Block block,
                                     @NotNull MemorySegment content) {
    this.block = block;
    this.content = content;
  }

  public static int minimumBlockContentLengthFor(int payloadLength) {
    return minimumBlockContentLengthFor(payloadLength, /* hasLink: */ false);
  }

  /// @return block content length that fits a record with `payloadLength` with/without a link
  ///         i.e. it should fit an appendable-log header ([AppendableRecordsOverBlockHeaderLayout]), a record header,
  ///         an (optional) link, and a given payload
  public static int minimumBlockContentLengthFor(int payloadLength, boolean hasLink) {
    return Math.addExact(AppendableRecordsOverBlockHeaderLayout.HEADER_SIZE, RecordHeader.totalLength(payloadLength, hasLink));
  }

  /// Initializes an empty log header in the block, and activates the block;
  /// The block must be in [LifecycleState#ALLOCATED] state;
  public static @NotNull AppendableRecordsOverBlock create(@NotNull BlocksStore.Block block) {
    var initialState = block.state();
    if (initialState != ALLOCATED) {
      throw new IllegalArgumentException("Block " + block.id() + " must be allocated, but: " + initialState);
    }

    try {
      var content = block.content();
      if (content.byteSize() < minimumBlockContentLengthFor(/* payloadLength: */ 0)) {
        throw new IllegalArgumentException("Block " + block.id() + " content (=" + content.byteSize() + ") is too small");
      }
      AppendableRecordsOverBlockHeaderLayout.initializeEmpty(content);
      block.activate();
      return new AppendableRecordsOverBlock(block, content);
    }
    catch (Throwable failure) {
      if (block.state() == ALLOCATED) {
        //Not strictly required: ALLOCATED blocks are discarded on recovery phase of DB startup
        // but why not do it right here, if we can?
        block.discard();
      }
      throw failure;
    }
  }

  /// Opens a log and validates its committed record prefix
  public static @NotNull AppendableRecordsOverBlock open(@NotNull BlocksStore.Block block) throws IOException {
    var content = block.content();
    var committedTail = AppendableRecordsOverBlockHeaderLayout.readValidatedCommittedTail(block.id(), content);
    var log = new AppendableRecordsOverBlock(block, content);
    //TODO RC: why to do that? it is costly
    log.scanCommittedRecords(committedTail, (_, _) -> true);
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
    return AppendableRecordsOverBlockHeaderLayout.allocatedTail(content) != AppendableRecordsOverBlockHeaderLayout.committedTail(content);
  }

  public synchronized boolean hasSpaceFor(int payloadLength) {
    return hasSpaceFor(payloadLength, /* hasLink: */ false);
  }

  public synchronized boolean hasSpaceFor(int payloadLength, boolean hasLink) {
    var totalLength = RecordHeader.totalLength(payloadLength, hasLink);
    return content.byteSize() - AppendableRecordsOverBlockHeaderLayout.allocatedTail(content) >= totalLength;
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
    var recordOffset = AppendableRecordsOverBlockHeaderLayout.allocatedTail(content);
    var committedTail = AppendableRecordsOverBlockHeaderLayout.committedTail(content);
    if (recordOffset != committedTail) {
      throw new IllegalStateException("Block " + block.id() + " has an incomplete allocation");
    }

    boolean hasLink = (previousRef != 0);
    var header = RecordHeader.allocated(payloadLength, hasLink);
    if (content.byteSize() - recordOffset < header.totalLength()) {
      throw new IllegalStateException("Block " + block.id() + " has no space for the record");
    }

    header.writeAllocatedTo(content, recordOffset);
    var newTail = Math.addExact(recordOffset, header.totalLength());
    AppendableRecordsOverBlockHeaderLayout.publishAllocatedTail(content, newTail);

    if (header.hasLink()) {
      //A header is int32, int32-aligned => [link:int64] right after header is _not_ (int64-)aligned:
      content.set(ValueLayout.JAVA_LONG_UNALIGNED, recordOffset + RecordHeaderLayout.HEADER_SIZE, previousRef);
    }
    writer.writeTo(payload(recordOffset, header));

    header.publishCommittedTo(content, recordOffset);
    AppendableRecordsOverBlockHeaderLayout.publishCommittedTail(content, newTail);
    return recordOffset;
  }

  /// @return a read-only view of a committed payload
  public @NotNull MemorySegment read(int recordOffset) throws CorruptedException {
    return readRecord(recordOffset).payload();
  }

  public @NotNull Record readRecord(int recordOffset) throws CorruptedException {
    if (block.state() == LifecycleState.RETIRED) {
      throw new IllegalStateException("Block " + block.id() + " is retired");
    }
    var committedTail = AppendableRecordsOverBlockHeaderLayout.committedTail(content);
    var header = readCommittedHeader(recordOffset, committedTail);
    return record(recordOffset, header);
  }

  /// Updates a committed payload without changing its size or record header.
  ///
  /// **BEWARE**: updating records denies the guarantees append-only-log normally gives -- i.e. that committed records
  /// are, in a way, crash-safe.
  public void updatePayload(int recordOffset, @NotNull RecordWriter updater) throws IOException {
    if (block.state() != ACTIVE) {
      throw new IllegalStateException("Block " + block.id() + " is not active");
    }

    var committedTail = AppendableRecordsOverBlockHeaderLayout.committedTail(content);
    var header = readCommittedHeader(recordOffset, committedTail);
    updater.writeTo(payload(recordOffset, header));
  }

  private @NotNull RecordHeader readCommittedHeader(int recordOffset, int committedTail) throws CorruptedException {
    if (recordOffset < AppendableRecordsOverBlockHeaderLayout.HEADER_SIZE ||
        recordOffset % RecordHeaderLayout.RECORD_ALIGNMENT != 0 ||
        (long)recordOffset + RecordHeaderLayout.HEADER_SIZE > committedTail) {
      throw new CorruptedException("Invalid record offset " + recordOffset + " in block " + block.id());
    }
    return RecordHeader.readIfCommitted(block.id(), recordOffset, content, committedTail);
  }

  /// @return `true` if all records were processed, or `false` if the reader stopped the traversal.
  public boolean forEachCommittedRecord(@NotNull RecordReader reader) throws IOException {
    return forEachCommittedRecordWithLinks((offset, record) -> reader.process(offset, record.payload()));
  }

  /// @return `true` if all records were processed, or `false` if the reader stopped the traversal.
  public boolean forEachCommittedRecordWithLinks(@NotNull LinkedRecordReader reader) throws IOException {
    return scanCommittedRecords(AppendableRecordsOverBlockHeaderLayout.committedTail(content), reader);
  }

  private boolean scanCommittedRecords(int committedTail, @NotNull LinkedRecordReader reader) throws IOException {
    var recordOffset = AppendableRecordsOverBlockHeaderLayout.HEADER_SIZE;
    while (recordOffset < committedTail) {
      RecordHeader header = RecordHeader.readIfCommitted(block.id(), recordOffset, content, committedTail);
      Record record = record(recordOffset, header);
      if (!reader.process(recordOffset, record)) {
        return false;
      }
      recordOffset += header.totalLength();
    }
    if (recordOffset != committedTail) {
      throw new CorruptedException(
        "Invalid append-only log block " + block.id() + ": the committed tail does not end at a record boundary"
      );
    }
    return true;
  }

  private @NotNull MemorySegment payload(int recordOffset, @NotNull RecordHeader header) {
    int payloadOffset = recordOffset + RecordHeaderLayout.HEADER_SIZE + (header.hasLink() ? Long.BYTES : 0);
    return content.asSlice(payloadOffset, header.payloadLength());
  }

  private @NotNull Record record(int recordOffset, @NotNull RecordHeader header) throws CorruptedException {
    long previousRef = 0;
    if (header.hasLink()) {
      previousRef = content.get(ValueLayout.JAVA_LONG_UNALIGNED, recordOffset + RecordHeaderLayout.HEADER_SIZE);
      if (previousRef == 0) {
        throw new CorruptedException("Missing record link at offset " + recordOffset + " in block " + block.id());
      }
    }
    MemorySegment payload = payload(recordOffset, header).asReadOnly();
    return new Record(previousRef, payload);
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
    /// @return `true` to process the next record, or `false` to stop the traversal.
    boolean process(int recordOffset, @NotNull Record record) throws IOException;
  }

  /// Writes one payload into a segment with the requested payload length
  @FunctionalInterface
  public interface RecordWriter {
    void writeTo(@NotNull MemorySegment payload) throws IOException;
  }

  /// Processes one committed payload
  @FunctionalInterface
  public interface RecordReader {
    /// @return `true` to process the next record, or `false` to stop the traversal.
    boolean process(int recordOffset, @NotNull MemorySegment payload) throws IOException;
  }
}
