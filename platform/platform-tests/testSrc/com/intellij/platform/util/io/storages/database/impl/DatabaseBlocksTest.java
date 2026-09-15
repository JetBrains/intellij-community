// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.database.impl.layout.ChunkHeaderLayout;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.List;

import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.SEALED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
      var sealed = blocks.allocateBlock(dropped, 0, BLOCK_LENGTH);
      sealed.seal();
      var retired = blocks.allocateBlock(dropped, 0, BLOCK_LENGTH);
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
      lastBlockId = replacementBlock.blockId();
      chunks.fsync();
      metadata.fsync();
    }

    for (var attempt = 0; attempt < 2; attempt++) {
      try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE);
           var chunks = DatabaseChunks.open(databaseDirectory, metadata);
           var blocks = DatabaseBlocks.open(metadata, chunks)) {
        var droppedBlocks = blocks.blocks(droppedStoreId);
        assertEquals(3, droppedBlocks.size(), "Recovery must retain all retired blocks for compaction");
        for (var block : droppedBlocks) {
          assertEquals(BlocksStore.Block.LifecycleState.RETIRED, block.state(), "Recovery must finish the recorded deletion");
        }
        var replacement = metadata.findStore("dropped");
        assertNotNull(replacement);
        for (var block : blocks.blocks(replacement.storeId())) {
          assertEquals(BlocksStore.Block.LifecycleState.ACTIVE, block.state(), "Name reuse must not retire the replacement store");
        }
        var newBlock = blocks.allocateBlock(replacement, 0, BLOCK_LENGTH);
        assertTrue(newBlock.blockId() > lastBlockId, "Recovery must reserve identifiers of retired blocks");
        lastBlockId = newBlock.blockId();
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
      firstBlock.seal();
      firstBlock.retire();

      assertEquals(1, firstStore.storeId(), "The first store must use the first positive logical identifier");
      assertEquals(2, secondStore.storeId(), "A later store must use the next logical identifier");
      assertEquals(1, firstBlock.blockId(), "The first block must use the first positive logical identifier");
      assertEquals(2, secondBlock.blockId(), "A later block must use the next logical identifier");
      assertEquals(
        blocks.findBlock(firstBlock.blockId()).chunkId(),
        blocks.findBlock(secondBlock.blockId()).chunkId(),
        "Blocks of different stores can share one chunk"
      );
      assertEquals(List.of(firstBlock), blocks.blocks(firstStoreId));
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
      var firstBlock = blocks.findBlock(1);
      var secondBlock = blocks.findBlock(2);
      assertNotNull(firstBlock);
      assertNotNull(secondBlock);
      assertEquals(42, firstBlock.contentSegment().get(ValueLayout.JAVA_BYTE, 0));
      assertEquals(BlocksStore.Block.LifecycleState.RETIRED, firstBlock.state(), "Recovery must retain retired blocks for compaction");
      assertEquals(0, firstBlock.role(), "Role zero must remain a valid opaque tag");
      assertEquals(0xFF, secondBlock.role(), "The complete unsigned byte range must remain available");

      var thirdBlock = blocks.allocateBlock(firstStore, 17, BLOCK_LENGTH);
      assertEquals(3, thirdBlock.blockId(), "Recovery must continue logical identifier allocation");
      assertEquals(List.of(firstBlock, thirdBlock), blocks.blocks(firstStore.storeId()));
    }
  }
}
