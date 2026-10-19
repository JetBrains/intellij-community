// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.database.impl.layout.ChunkHeaderLayout;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.List;

import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.SEALED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies logical block allocation and recovery across database chunks. */
@SuppressWarnings("SuspiciousPackagePrivateAccess")
public class DatabaseBlocksTest {
  private static final int CHUNK_SIZE = 1024 * 1024;
  private static final int BLOCK_LENGTH = 64 * 1024;

  @Test
  public void allocationSealsTheOldestChunkWhenTheQueueFills(@TempDir Path databaseDirectory) throws Exception {
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(databaseDirectory.resolve("database.meta"), CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata);
         var blocks = DatabaseBlocks.open(metadata, chunks)) {
      var storeId = metadata.nextStoreId();
      metadata.registerNewStore(storeId, "store", 1);
      var store = metadata.findStore("store");
      assertNotNull(store);

      blocks.allocateBlock(store, 0, CHUNK_SIZE - ChunkHeaderLayout.HEADER_SIZE);
      blocks.allocateBlock(store, 0, CHUNK_SIZE - ChunkHeaderLayout.HEADER_SIZE);
      blocks.allocateBlock(store, 0, BLOCK_LENGTH);

      assertEquals(SEALED, chunks.chunks().get(0).state(), "The oldest chunk must leave the active queue");
      assertEquals(SEALED, metadata.chunks().getFirst().state(), "The catalog must publish the sealed state");
      assertEquals(ACTIVE, chunks.chunks().get(1).state(), "Allocation must continue in an active chunk");
      assertEquals(ACTIVE, chunks.chunks().get(2).state(), "The newest chunk must remain active");
      assertEquals(3, chunks.metrics(true).created(), "Each allocation target must record its chunk creation");
      assertEquals(1, chunks.metrics(true).sealed(), "The evicted chunk must record its seal event");
      assertEquals(3, blocks.metrics(true).allocated(), "Each successful allocation must update the block counter");
    }
  }

  @Test
  public void recoveryDiscardsAllocatedBlock(@TempDir Path databaseDirectory) throws Exception {
    var catalogPath = databaseDirectory.resolve("database.meta");
    int storeId;
    int allocatedBlockId;
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata);
         var blocks = DatabaseBlocks.open(metadata, chunks)) {
      storeId = metadata.nextStoreId();
      metadata.registerNewStore(storeId, "store", 1);
      var store = metadata.findStore("store");
      assertNotNull(store);
      var block = blocks.allocateBlock(store, 0, BLOCK_LENGTH);
      allocatedBlockId = block.blockId();
      chunks.fsync();
      metadata.fsync();
    }

    var lastBlockId = allocatedBlockId;
    for (var attempt = 0; attempt < 2; attempt++) {
      try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE);
           var chunks = DatabaseChunks.open(databaseDirectory, metadata);
           var blocks = DatabaseBlocks.open(metadata, chunks)) {
        assertNull(blocks.findBlock(allocatedBlockId), "The logical directory must hide the discarded block");
        assertEquals(
          List.of(),
          blocks.blocks(storeId).stream().filter(block -> block.blockId() == allocatedBlockId).toList(),
          "The logical directory must not list the discarded block"
        );
        var recoveredBlocks = chunks.chunks().stream()
          .flatMap(chunk -> chunk.blocks().stream())
          .filter(block -> block.blockId() == allocatedBlockId)
          .toList();
        assertEquals(attempt == 0 ? 1 : 0, recoveredBlocks.size(),
                     "Opening must not retain a persisted retired block");
        if (!recoveredBlocks.isEmpty()) {
          assertEquals(BlocksStore.Block.LifecycleState.RETIRED, recoveredBlocks.getFirst().state());
        }
        assertEquals(attempt + 1, blocks.metrics(true).total(), "Recovery must count each logical block once");
        assertEquals(1, blocks.metrics(true).retiredCurrent(), "Recovery must count the discarded block as retired");
        assertEquals(attempt == 0 ? 1 : 0, blocks.metrics(true).incompleteBlocksDiscarded(),
                     "Recovery must report only the first repair of the incomplete block");

        var store = metadata.findStore("store");
        assertNotNull(store);
        var newBlock = blocks.allocateBlock(store, 0, BLOCK_LENGTH);
        assertTrue(newBlock.blockId() > lastBlockId, "Recovery must reserve the discarded block identifier");
        lastBlockId = newBlock.blockId();
        newBlock.activate();
        chunks.fsync();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void recoveryCompletesStoreDrop(boolean partiallyRetired, @TempDir Path databaseDirectory) throws Exception {
    var catalogPath = databaseDirectory.resolve("database.meta");
    int droppedStoreId;
    int lastBlockId;
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata);
         var blocks = DatabaseBlocks.open(metadata, chunks)) {
      droppedStoreId = metadata.nextStoreId();
      metadata.registerNewStore(droppedStoreId, "dropped", 1);
      var dropped = metadata.findStore("dropped");
      assertNotNull(dropped);
      var active = blocks.allocateBlock(dropped, 0, BLOCK_LENGTH);
      active.activate();
      var sealed = blocks.allocateBlock(dropped, 0, BLOCK_LENGTH);
      sealed.activate();
      sealed.seal();
      var retired = blocks.allocateBlock(dropped, 0, BLOCK_LENGTH);
      retired.activate();
      retired.seal();
      retired.retire();
      chunks.fsync();
      metadata.dropStore(droppedStoreId);
      metadata.fsync();
      if (partiallyRetired) {
        active.retireForStoreDrop();
      }

      metadata.registerNewStore(metadata.nextStoreId(), "dropped", 2);
      var replacement = metadata.findStore("dropped");
      assertNotNull(replacement);
      var replacementBlock = blocks.allocateBlock(replacement, 0, BLOCK_LENGTH);
      replacementBlock.activate();
      lastBlockId = replacementBlock.blockId();
      chunks.fsync();
      metadata.fsync();
    }

    for (var attempt = 0; attempt < 2; attempt++) {
      try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE);
           var chunks = DatabaseChunks.open(databaseDirectory, metadata);
           var blocks = DatabaseBlocks.open(metadata, chunks)) {
        var droppedBlocks = chunks.chunks().stream()
          .flatMap(chunk -> chunk.blocks().stream())
          .filter(block -> block.storeId() == droppedStoreId)
          .toList();
        var expectedBlockObjects = attempt == 0 ? (partiallyRetired ? 1 : 2) : 0;
        assertEquals(expectedBlockObjects, droppedBlocks.size(),
                     "Opening must retain only blocks that require recovery");
        for (var block : droppedBlocks) {
          assertEquals(BlocksStore.Block.LifecycleState.RETIRED, block.state(), "Recovery must finish the recorded deletion");
        }
        assertTrue(blocks.blocks(droppedStoreId).isEmpty(), "The logical directory must hide blocks of the dropped store");
        var expectedRepairs = attempt == 0 ? (partiallyRetired ? 1 : 2) : 0;
        assertEquals(expectedRepairs, blocks.metrics(true).droppedStoreBlocksRetired(),
                     "Recovery must report only blocks whose persisted state changed");
        var replacement = metadata.findStore("dropped");
        assertNotNull(replacement);
        for (var block : blocks.blocks(replacement.storeId())) {
          assertEquals(BlocksStore.Block.LifecycleState.ACTIVE, block.state(), "Name reuse must not retire the replacement store");
        }
        var newBlock = blocks.allocateBlock(replacement, 0, BLOCK_LENGTH);
        assertTrue(newBlock.blockId() > lastBlockId, "Recovery must reserve identifiers of retired blocks");
        lastBlockId = newBlock.blockId();
        newBlock.activate();
        chunks.fsync();
      }
    }
  }

  @Test
  public void logicalBlockDirectorySurvivesReopening(@TempDir Path databaseDirectory) throws Exception {
    var catalogPath = databaseDirectory.resolve("database.meta");
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata)) {
      var blocks = DatabaseBlocks.open(metadata, chunks);
      var firstStoreId = metadata.nextStoreId();
      var firstStore = new DatabaseCatalog.StoreInfo(firstStoreId, "first", 1);
      metadata.registerNewStore(firstStoreId, firstStore.name(), firstStore.dataVersion());
      var secondStoreId = metadata.nextStoreId();
      var secondStore = new DatabaseCatalog.StoreInfo(secondStoreId, "second", 1);
      metadata.registerNewStore(secondStoreId, secondStore.name(), secondStore.dataVersion());
      var firstBlock = blocks.allocateBlock(firstStore, 0, BLOCK_LENGTH);
      var secondBlock = blocks.allocateBlock(secondStore, 0xFF, BLOCK_LENGTH);
      firstBlock.contentSegment().set(ValueLayout.JAVA_BYTE, 0, (byte)42);
      firstBlock.activate();
      firstBlock.seal();
      firstBlock.retire();
      secondBlock.activate();

      assertEquals(1, firstStore.storeId(), "The first store must use the first positive logical identifier");
      assertEquals(2, secondStore.storeId(), "A later store must use the next logical identifier");
      assertEquals(1, firstBlock.blockId(), "The first block must use the first positive logical identifier");
      assertEquals(2, secondBlock.blockId(), "A later block must use the next logical identifier");
      assertEquals(firstBlock.chunkId(), secondBlock.chunkId(), "Blocks of different stores can share one chunk");
      assertNull(blocks.findBlock(firstBlock.blockId()), "The logical directory must not find a retired block");
      assertTrue(blocks.blocks(firstStoreId).isEmpty(), "The logical directory must not list a retired block");
      assertEquals(List.of(secondBlock), blocks.blocks(secondStoreId));
      assertNull(blocks.findBlock(3), "An unallocated identifier must not resolve to a block");
      chunks.flush();
      metadata.flush();
    }

    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata)) {
      var blocks = DatabaseBlocks.open(metadata, chunks);
      var firstStore = metadata.findStore("first");
      assertNotNull(firstStore);
      var secondBlock = blocks.findBlock(2);
      assertNotNull(secondBlock);
      assertNull(blocks.findBlock(1), "The logical directory must not find a recovered retired block");
      var firstBlocks = chunks.chunks().stream()
        .flatMap(chunk -> chunk.blocks().stream())
        .filter(block -> block.blockId() == 1)
        .toList();
      assertTrue(firstBlocks.isEmpty(), "Opening must not retain a persisted retired block");
      assertEquals(0xFF, secondBlock.role(), "The complete unsigned byte range must remain available");

      var thirdBlock = blocks.allocateBlock(firstStore, 17, BLOCK_LENGTH);
      assertEquals(3, thirdBlock.blockId(), "Recovery must continue logical identifier allocation");
      assertEquals(List.of(thirdBlock), blocks.blocks(firstStore.storeId()));
    }
  }

  @Test
  public void evacuationRemovesTheSourceCopyFromRuntimeCatalogs(@TempDir Path databaseDirectory) throws Exception {
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(databaseDirectory.resolve("database.meta"), CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata);
         var blocks = DatabaseBlocks.open(metadata, chunks)) {
      var storeId = metadata.nextStoreId();
      metadata.registerNewStore(storeId, "store", 1);
      var store = metadata.findStore("store");
      assertNotNull(store);

      var origin = blocks.allocateBlock(store, 17, BLOCK_LENGTH);
      origin.activate();
      var firstFiller = blocks.allocateBlock(store, 0, CHUNK_SIZE - ChunkHeaderLayout.HEADER_SIZE - BLOCK_LENGTH);
      firstFiller.activate();
      var secondFiller = blocks.allocateBlock(store, 0, CHUNK_SIZE - ChunkHeaderLayout.HEADER_SIZE);
      secondFiller.activate();
      var trigger = blocks.allocateBlock(store, 0, BLOCK_LENGTH);
      trigger.activate();

      var sourceChunk = chunks.chunks().getFirst();
      assertEquals(SEALED, sourceChunk.state(), "The source chunk must be sealed before evacuation");
      blocks.evacuateBlock(sourceChunk, origin);

      var current = blocks.findBlock(origin.blockId());
      assertNotNull(current);
      assertNotSame(origin, current, "Evacuation must publish a new physical copy");
      assertEquals(List.of(firstFiller), sourceChunk.blocks(), "The source chunk must not retain the retired copy");
      assertFalse(storedBlocks(blocks, storeId).contains(origin), "The store catalog must not retain the retired copy");
      assertTrue(storedBlocks(blocks, storeId).contains(current), "The store catalog must retain the current copy");
      assertEquals(4, blocks.metrics(true).total(), "Evacuation must not change the logical block count");
      assertEquals(1, blocks.metrics(true).evacuated(), "Evacuation must record the copied block once");
    }
  }

  @ParameterizedTest
  @EnumSource(value = BlocksStore.Block.LifecycleState.class, names = {"ACTIVE", "SEALED"})
  public void recoverySelectsTheNewestEvacuatedCopy(BlocksStore.Block.LifecycleState state,
                                                    @TempDir Path databaseDirectory) throws Exception {
    var catalogPath = databaseDirectory.resolve("database.meta");
    int storeId;
    int blockId;
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata);
         var blocks = DatabaseBlocks.open(metadata, chunks)) {
      storeId = metadata.nextStoreId();
      metadata.registerNewStore(storeId, "store", 1);
      var store = metadata.findStore("store");
      assertNotNull(store);

      var origin = blocks.allocateBlock(store, 17, BLOCK_LENGTH);
      blockId = origin.blockId();
      origin.contentSegment().set(ValueLayout.JAVA_INT, 0, 42);
      origin.activate();
      if (state == BlocksStore.Block.LifecycleState.SEALED) {
        origin.seal();
      }

      var fillerLength = CHUNK_SIZE - ChunkHeaderLayout.HEADER_SIZE - BLOCK_LENGTH;
      var filler = blocks.allocateBlock(store, 0, fillerLength);
      filler.activate();
      var trigger = blocks.allocateBlock(store, 0, BLOCK_LENGTH);
      trigger.activate();

      var targetChunk = chunks.chunkForAllocation(origin.blockLength());
      var copy = targetChunk.copyBlock(origin);
      assertTrue(copy.chunkId() > origin.chunkId());
      chunks.flush();
      metadata.flush();
    }

    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata);
         var blocks = DatabaseBlocks.open(metadata, chunks)) {
      var current = blocks.findBlock(blockId);
      assertNotNull(current);
      assertEquals(2, current.chunkId(), "The copy from the newest chunk must become current");
      assertEquals(state, current.state());
      assertEquals(42, current.contentSegment().get(ValueLayout.JAVA_INT, 0));

      assertEquals(
        List.of(current),
        blocks.blocks(storeId).stream().filter(block -> block.blockId() == blockId).toList(),
        "The logical directory must expose only the current copy"
      );
      var copies = chunks.chunks().stream()
        .flatMap(chunk -> chunk.blocks().stream())
        .filter(block -> block.blockId() == blockId)
        .toList();
      assertEquals(2, copies.size());
      assertEquals(BlocksStore.Block.LifecycleState.RETIRED, copies.getFirst().state());
      assertEquals(state, copies.getLast().state());
      assertEquals(3, blocks.metrics(true).total(), "Recovery must count the evacuated block once");
      assertEquals(0, blocks.metrics(true).retiredCurrent(), "The stale retired copy must not affect current metrics");
      assertEquals(1, blocks.metrics(true).staleBlockCopiesRetired(), "Recovery must report the completed block evacuation");
    }
  }

  @SuppressWarnings("unchecked")
  private static List<DatabaseBlock> storedBlocks(DatabaseBlocks blocks, int storeId) throws ReflectiveOperationException {
    var field = DatabaseBlocks.class.getDeclaredField("blocksByStoreId");
    field.setAccessible(true);
    var blocksByStoreId = (Int2ObjectMap<List<DatabaseBlock>>)field.get(blocks);
    return blocksByStoreId.get(storeId);
  }

}
