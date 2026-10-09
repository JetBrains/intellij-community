// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.appenablerecords;

import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState;
import com.intellij.platform.util.io.storages.database.storages.appenablerecords.AppendableRecordsOverBlock.RecordWriter;
import com.intellij.util.io.CorruptedException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

/// Appendable record storage over **multiple** blocks from one [AppendableRecordBlocks] scope.
/// Uses [AppendableRecordsOverBlock] for each block, allocates new block as current one gets exhausted,
/// and keeps filled up blocks connected in a sequence.
///
/// The record storage could work in 2 modes: append-only, or modifiable
/// 1. Append-only: records are only appended, never modified afterward; i.e. this is classical append-only-log structure.
///    In this mode each block is sealed ([BlocksStore.Block#seal]) as soon as it is filled up, so only read access is allowed;
/// 2. Modifiable: simple records store, appended records content could be modified, but records can't be resized;
///    In this mode filled up blocks are not sealed, they remain active so their records could be modified;
///
/// The appendOnly/modifiable mode is defined at runtime, and does NOT persist.
@ApiStatus.Internal
public final class RecordStorageOverBlocks {
  private final @NotNull AppendableRecordBlocks blocks;
  private final boolean appendOnly;

  private final int preferredBlockContentLength;

  private final transient Object recordsByBlockIdLock = new Object();
  private final Int2ObjectMap<AppendableRecordsOverBlock> recordsByBlockId = new Int2ObjectOpenHashMap<>();

  // TODO Serializing the complete append operation is not acceptable. Revise the allocation and commit protocol to permit concurrent writers.
  private final transient Object appendLock = new Object();
  private AppendableRecordsOverBlock currentAppendableBlock;

  private RecordStorageOverBlocks(@NotNull AppendableRecordBlocks blocks,
                                  boolean appendOnly,
                                  int preferredBlockContentLength) {
    this.blocks = blocks;
    this.appendOnly = appendOnly;
    this.preferredBlockContentLength = preferredBlockContentLength;
  }

  /// Opens the storage and validates the committed record prefix in each non-retired block.
  public static @NotNull RecordStorageOverBlocks open(@NotNull AppendableRecordBlocks blocks,
                                                      boolean appendOnly,
                                                      int preferredBlockContentLength) throws IOException {
    var minimumContentLength = AppendableRecordsOverBlock.minimumBlockContentLengthFor(0);
    if (preferredBlockContentLength < minimumContentLength) {
      throw new IllegalArgumentException(
        "preferredBlockContentLength(=" + preferredBlockContentLength + ") must be at least " +
        minimumContentLength
      );
    }

    var storage = new RecordStorageOverBlocks(blocks, appendOnly, preferredBlockContentLength);

    //restore filled-up and currently appendable blocks:
    var blocksInAllocationOrder = blocks.blocksInAllocationOrder();
    AppendableRecordsOverBlock lastActiveBlock = null;
    var activeBlocksCount = 0;
    for (var block : blocksInAllocationOrder) {
      if (block.state() == LifecycleState.RETIRED) {//because state could change concurrently
        continue;
      }
      var recordsBlock = AppendableRecordsOverBlock.open(block);
      AppendableRecordsOverBlock previous = storage.recordsByBlockId.putIfAbsent(block.id(), recordsBlock);
      if (previous != null) {
        throw new CorruptedException("Duplicate block id [" + block.id() + "]: " + previous + " vs " + block);
      }

      if (block.state() == LifecycleState.ACTIVE) {
        activeBlocksCount++;
        lastActiveBlock = recordsBlock;
      }
    }

    if (appendOnly && activeBlocksCount > 1) {
      throw new CorruptedException("Append-only record storage has " + activeBlocksCount + " active blocks");
    }

    if (lastActiveBlock != null) {
      if (lastActiveBlock.hasIncompleteAllocation()) {
        if (appendOnly) {
          lastActiveBlock.block().seal();
        }
        //else: currently in !appendOnly mode we just leave the block with incomplete allocation as-is -- don't append to it anymore,
        //      but still keep its state='active' and readable/writeable. We could do more: 'cancel' the incomplete allocation (zeroize
        //      all the potentially-dirty region, and adjust cursors as-if incomplete allocation never happened), and treat the block as
        //      normal -- but for now it seems unnecessary.
      }
      else {
        storage.currentAppendableBlock = lastActiveBlock;
      }
    }
    return storage;
  }

  /// Appends one payload and returns its stable packed location.
  public long append(int payloadLength,
                     @NotNull RecordWriter writer) throws IOException {
    return append(payloadLength, /*previousRef: */ 0, writer);
  }

  /// Appends a payload and an optional reference to its predecessor
  public long append(int payloadLength, long previousRef, @NotNull RecordWriter writer) throws IOException {
    synchronized (appendLock){
      var currentBlock = currentAppendableBlock;
      boolean hasLink = (previousRef != 0);
      if (currentBlock == null || !currentBlock.hasSpaceFor(payloadLength, hasLink)) {
        if (currentBlock != null && appendOnly) {
          currentBlock.block().seal();
        }
        currentBlock = allocateLog(payloadLength, hasLink);
        currentAppendableBlock = currentBlock;
      }

      try {
        int recordOffset = currentBlock.append(payloadLength, previousRef, writer);
        return recordRef(currentBlock.block().id(), recordOffset);
      }
      catch (Throwable failure) {
        if (appendOnly) {
          currentBlock.block().seal();
        }
        currentAppendableBlock = null;
        throw failure;
      }
    }
  }

  /// @return a read-only view of the committed payload
  public @NotNull MemorySegment read(long recordRef) throws IOException {
    return readRecord(recordRef).payload();
  }

  /// @return record for recordRef, _without_ resolving [AppendableRecordsOverBlock.Record#previousRef()], if record has it
  public @NotNull AppendableRecordsOverBlock.Record readRecord(long recordRef) throws IOException {
    var blockId = blockId(recordRef);
    AppendableRecordsOverBlock records;
    synchronized (recordsByBlockIdLock) {
      records = recordsByBlockId.get(blockId);
    }
    if (records == null) {
      throw new IllegalArgumentException("Unknown record block " + blockId);
    }
    return records.readRecord(recordOffset(recordRef));
  }

  /// Updates a committed payload without changing its size or record header.
  public void updatePayload(long recordRef, @NotNull RecordWriter updater) throws IOException {
    if (appendOnly) {
      throw new UnsupportedOperationException("Append-only records cannot be updated");
    }

    var blockId = blockId(recordRef);
    AppendableRecordsOverBlock records;
    synchronized (recordsByBlockIdLock) {
      records = recordsByBlockId.get(blockId);
    }
    if (records == null) {
      throw new IllegalArgumentException("Unknown record block " + blockId);
    }
    records.updatePayload(recordOffset(recordRef), updater);
  }

  /// Processes committed records in block allocation order
  /// @return `true` if all records were processed, or `false` if the reader stopped the traversal.
  public boolean forEachCommittedRecord(@NotNull RecordReader reader) throws IOException {
    return forEachCommittedRecordWithLinks(
      (recordRef, record) -> reader.process(recordRef, record.payload())
    );
  }

  /// @return `true` if all records were processed, or `false` if the reader stopped the traversal.
  public boolean forEachCommittedRecordWithLinks(@NotNull LinkedRecordReader reader) throws IOException {
    synchronized (appendLock){
      var blocksInAllocationOrder = blocks.blocksInAllocationOrder();
      for (var block : blocksInAllocationOrder) {
        if (block.state() == LifecycleState.RETIRED) {
          continue;
        }
        AppendableRecordsOverBlock records;
        //noinspection NestedSynchronizedStatement
        synchronized (recordsByBlockIdLock) {
          records = recordsByBlockId.get(block.id());
        }
        if (records == null) {
          throw new CorruptedException("Missing record block " + block.id());
        }
        var blockId = block.id();
        var allBlockRecordsProcessed = records.forEachCommittedRecordWithLinks(
          (recordOffset, record) -> reader.process(recordRef(blockId, recordOffset), record)
        );
        if (!allBlockRecordsProcessed) {
          return false;
        }
      }
      return true;
    }
  }

  private @NotNull AppendableRecordsOverBlock allocateLog(int payloadLength, boolean hasLink) throws IOException {
    var minimumContentLength = AppendableRecordsOverBlock.minimumBlockContentLengthFor(payloadLength, hasLink);
    var contentLength = Math.max(preferredBlockContentLength, minimumContentLength);
    var block = blocks.allocateBlock(contentLength);
    var records = AppendableRecordsOverBlock.create(block);
    synchronized (recordsByBlockIdLock) {
      recordsByBlockId.put(block.id(), records);
    }
    return records;
  }

  /// Packs a stable block identifier and a block-relative offset into one record reference.
  public static long recordRef(int blockId, int recordOffset) {
    return ((long)blockId << Integer.SIZE) | Integer.toUnsignedLong(recordOffset);
  }

  public static int blockId(long recordRef) {
    return (int)(recordRef >>> Integer.SIZE);
  }

  public static int recordOffset(long recordRef) {
    return (int)recordRef;
  }

  public boolean containsRecordBlock(long recordRef) {
    synchronized (recordsByBlockIdLock) {
      return recordsByBlockId.containsKey(blockId(recordRef));
    }
  }

  /// Processes one committed record.
  @FunctionalInterface
  public interface RecordReader {
    /// @return `true` to process the next record, or `false` to stop the traversal.
    boolean process(long recordRef, @NotNull MemorySegment payload) throws IOException;
  }

  @FunctionalInterface
  public interface LinkedRecordReader {
    /// @return `true` to process the next record, or `false` to stop the traversal.
    boolean process(long recordRef, @NotNull AppendableRecordsOverBlock.Record record) throws IOException;
  }
}
