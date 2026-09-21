// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import com.intellij.platform.util.io.storages.database.storages.appendonlylog.AppendOnlyLogOverBlock;
import com.intellij.platform.util.io.storages.database.storages.appendonlylog.AppendOnlyLogOverBlock.RecordWriter;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.util.io.CorruptedException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

import static com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.DATA;

/// Append-only record storage over _multiple_ durable map `DATA` blocks.
/// Uses [AppendOnlyLogOverBlock] for each block, allocates new block as current one gets exhausted,
/// and keeps filled up blocks connected in a sequence.
@ApiStatus.Internal
public final class RecordStorageOverBlocks {
  /// Routes block access through [DurableMapBlockCatalog] to keep the catalog up to date
  private final @NotNull DurableMapBlockCatalog blockCatalog;

  private final int preferredBlockContentLength;

  private final transient Object logsByBlockIdLock = new Object();
  private final Int2ObjectMap<AppendOnlyLogOverBlock> logsByBlockId = new Int2ObjectOpenHashMap<>();

  // TODO Serializing the complete append operation is not acceptable. Revise the allocation and commit protocol to permit concurrent writers.
  private final transient Object appendLock = new Object();
  private AppendOnlyLogOverBlock currentLog;

  private RecordStorageOverBlocks(@NotNull DurableMapBlockCatalog blockCatalog, int preferredBlockContentLength) {
    this.blockCatalog = blockCatalog;
    this.preferredBlockContentLength = preferredBlockContentLength;
  }

  /// Opens the storage and validates the committed record prefix in each non-retired `DATA` block.
  public static @NotNull RecordStorageOverBlocks open(@NotNull DurableMapBlockCatalog blockCatalog,
                                                      int preferredBlockContentLength) throws IOException {
    var minimumContentLength = AppendOnlyLogOverBlock.minimumBlockContentLengthFor(0);
    if (preferredBlockContentLength < minimumContentLength) {
      throw new IllegalArgumentException(
        "preferredBlockContentLength(=" + preferredBlockContentLength + ") must be at least " +
        minimumContentLength
      );
    }

    var storage = new RecordStorageOverBlocks(blockCatalog, preferredBlockContentLength);
    //restore filled-up and currently appendable blocks:
    for (var block : blockCatalog.blocks(DATA)) {
      if (block.state() == BlocksStore.Block.LifecycleState.RETIRED) {
        continue;
      }

      var log = AppendOnlyLogOverBlock.open(block);
      AppendOnlyLogOverBlock previous = storage.logsByBlockId.putIfAbsent(block.id(), log);
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

  /// @return record for recordRef, _without_ resolving [AppendOnlyLogOverBlock.Record#previousRef()], if record has it
  public @NotNull AppendOnlyLogOverBlock.Record readRecord(long recordRef) throws IOException {
    var blockId = blockId(recordRef);
    AppendOnlyLogOverBlock log;
    synchronized (logsByBlockIdLock) {
      log = logsByBlockId.get(blockId);
    }
    if (log == null) {
      throw new IllegalArgumentException("Unknown DATA block " + blockId);
    }
    return log.readRecord(recordOffset(recordRef));
  }

  /// Processes committed records in block allocation order
  public void forEachCommittedRecord(@NotNull RecordReader reader) throws IOException {
    forEachCommittedRecordWithLinks((recordRef, record) -> reader.process(recordRef, record.payload()));
  }

  public void forEachCommittedRecordWithLinks(@NotNull LinkedRecordReader reader) throws IOException {
    synchronized (appendLock){
      for (var block : blockCatalog.blocks(DATA)) {
        if (block.state() == BlocksStore.Block.LifecycleState.RETIRED) {
          continue;
        }
        AppendOnlyLogOverBlock log;
        synchronized (logsByBlockIdLock) {
          log = logsByBlockId.get(block.id());
        }
        if (log == null) {
          throw new CorruptedException("Missing DATA block " + block.id());
        }
        log.forEachCommittedRecordWithLinks((recordOffset, record) -> reader.process(recordRef(block.id(), recordOffset), record));
      }
    }
  }

  private @NotNull AppendOnlyLogOverBlock allocateLog(int payloadLength, boolean hasLink) throws IOException {
    var minimumContentLength = AppendOnlyLogOverBlock.minimumBlockContentLengthFor(payloadLength, hasLink);
    var contentLength = Math.max(preferredBlockContentLength, minimumContentLength);
    var block = blockCatalog.allocateBlock(DATA, contentLength);
    var log = AppendOnlyLogOverBlock.create(block);
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
    void process(long recordRef, @NotNull MemorySegment payload) throws IOException;
  }

  @FunctionalInterface
  public interface LinkedRecordReader {
    void process(long recordRef, @NotNull AppendOnlyLogOverBlock.Record record) throws IOException;
  }
}
