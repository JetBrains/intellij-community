// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import com.intellij.platform.util.io.storages.database.storages.appenablerecords.AppendableRecordsOverBlock;
import com.intellij.platform.util.io.storages.database.storages.appenablerecords.AppendableRecordsOverBlock.RecordWriter;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole;
import com.intellij.util.io.CorruptedException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.Comparator;

import static com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.ENTRIES;

/// Append-only record storage over multiple durable map blocks with one record role.
/// Uses [AppendableRecordsOverBlock] for each block, allocates new block as current one gets exhausted,
/// and keeps filled up blocks connected in a sequence.
@ApiStatus.Internal
public final class RecordStorageOverBlocks {
  /// Routes block access through [DurableMapBlockCatalog] to keep the catalog up to date
  private final @NotNull DurableMapBlockCatalog blockCatalog;
  private final @NotNull DurableMapBlockRole recordBlockRole;

  private final int preferredBlockContentLength;

  private final transient Object logsByBlockIdLock = new Object();
  private final Int2ObjectMap<AppendableRecordsOverBlock> logsByBlockId = new Int2ObjectOpenHashMap<>();

  // TODO Serializing the complete append operation is not acceptable. Revise the allocation and commit protocol to permit concurrent writers.
  private final transient Object appendLock = new Object();
  private AppendableRecordsOverBlock currentLog;

  private RecordStorageOverBlocks(@NotNull DurableMapBlockCatalog blockCatalog,
                                  @NotNull DurableMapBlockRole recordBlockRole,
                                  int preferredBlockContentLength) {
    this.blockCatalog = blockCatalog;
    this.recordBlockRole = recordBlockRole;
    this.preferredBlockContentLength = preferredBlockContentLength;
  }

  /// Opens the storage and validates the committed record prefix in each non-retired `ENTRIES` block.
  public static @NotNull RecordStorageOverBlocks open(@NotNull DurableMapBlockCatalog blockCatalog,
                                                      int preferredBlockContentLength) throws IOException {
    return open(blockCatalog, ENTRIES, preferredBlockContentLength);
  }

  /// Opens the storage for one record block role and validates each committed record prefix.
  public static @NotNull RecordStorageOverBlocks open(@NotNull DurableMapBlockCatalog blockCatalog,
                                                      @NotNull DurableMapBlockRole recordBlockRole,
                                                      int preferredBlockContentLength) throws IOException {
    switch (recordBlockRole) {
      case ENTRIES, KEYS, VALUES -> { }
      case LOOKUP -> throw new IllegalArgumentException("LOOKUP blocks do not store append-only records");
    }
    var minimumContentLength = AppendableRecordsOverBlock.minimumBlockContentLengthFor(0);
    if (preferredBlockContentLength < minimumContentLength) {
      throw new IllegalArgumentException(
        "preferredBlockContentLength(=" + preferredBlockContentLength + ") must be at least " +
        minimumContentLength
      );
    }

    var storage = new RecordStorageOverBlocks(blockCatalog, recordBlockRole, preferredBlockContentLength);
    //restore filled-up and currently appendable blocks:
    for (var block : blockCatalog.blocks(recordBlockRole)) {
      if (block.state() == BlocksStore.Block.LifecycleState.RETIRED) {
        continue;
      }

      var log = AppendableRecordsOverBlock.open(block);
      AppendableRecordsOverBlock previous = storage.logsByBlockId.putIfAbsent(block.id(), log);
      if (previous != null) {
        throw new CorruptedException("Duplicate block id[" + block.id() + "]: " + previous + " vs " + block);
      }

      if (block.state() == BlocksStore.Block.LifecycleState.ACTIVE) {
        if (log.hasIncompleteAllocation()) {
          block.seal();
        }
        else {
          storage.currentLog = log;
        }
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
      var log = currentLog;
      boolean hasLink = (previousRef != 0);
      if (log == null || !log.hasSpaceFor(payloadLength, hasLink)) {
        if (log != null) {
          log.block().seal();
        }
        log = allocateLog(payloadLength, hasLink);
        currentLog = log;
      }

      try {
        int recordOffset = log.append(payloadLength, previousRef, writer);
        return recordRef(log.block().id(), recordOffset);
      }
      catch (IOException | RuntimeException | Error failure) {
        log.block().seal();
        currentLog = null;
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
    AppendableRecordsOverBlock log;
    synchronized (logsByBlockIdLock) {
      log = logsByBlockId.get(blockId);
    }
    if (log == null) {
      throw new IllegalArgumentException("Unknown " + recordBlockRole + " block " + blockId);
    }
    return log.readRecord(recordOffset(recordRef));
  }

  /// Updates a committed payload without changing its size or record header.
  public void updatePayload(long recordRef, @NotNull RecordWriter updater) throws IOException {
    var blockId = blockId(recordRef);
    AppendableRecordsOverBlock log;
    synchronized (logsByBlockIdLock) {
      log = logsByBlockId.get(blockId);
    }
    if (log == null) {
      throw new IllegalArgumentException("Unknown " + recordBlockRole + " block " + blockId);
    }
    log.updatePayload(recordOffset(recordRef), updater);
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
      //actual order in blockCatalog could be changed by evacuations
      var blocksInAllocationOrder = blockCatalog.blocks(recordBlockRole).stream()
        .sorted(Comparator.comparingInt(BlocksStore.Block::id))
        .toList();
      for (var block : blocksInAllocationOrder) {
        if (block.state() == BlocksStore.Block.LifecycleState.RETIRED) {
          continue;
        }
        AppendableRecordsOverBlock log;
        //noinspection NestedSynchronizedStatement
        synchronized (logsByBlockIdLock) {
          log = logsByBlockId.get(block.id());
        }
        if (log == null) {
          throw new CorruptedException("Missing " + recordBlockRole + " block " + block.id());
        }
        var blockId = block.id();
        var allBlockRecordsProcessed = log.forEachCommittedRecordWithLinks(
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
    var block = blockCatalog.allocateBlock(recordBlockRole, contentLength);
    var log = AppendableRecordsOverBlock.create(block);
    synchronized (logsByBlockIdLock) {
      logsByBlockId.put(block.id(), log);
    }
    return log;
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

  boolean containsRecordBlock(long recordRef) {
    synchronized (logsByBlockIdLock) {
      return logsByBlockId.containsKey(blockId(recordRef));
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
