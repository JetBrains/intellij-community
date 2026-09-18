// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.UnsupportedFormatException;
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Verifies store liveness and block ownership. */
@SuppressWarnings("SuspiciousPackagePrivateAccess")
public class BlocksStoreTest {
  private static final int CHUNK_SIZE = 1024 * 1024;
  private static final int BLOCK_CONTENT_LENGTH = 64 * 1024;

  @Test
  public void openStoreReturnsTheCurrentStoreAndValidatesTheDataVersion(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var first = database.openStore("store", 1);
      var block = first.allocateBlock(0, BLOCK_CONTENT_LENGTH);

      var second = database.openStore("store", 1);

      assertEquals(block.id(), second.blocks().getFirst().id());
      var exception = assertThrows(UnsupportedFormatException.class, () -> database.openStore("store", 2));
      assertEquals("store 'store'", exception.subject());
      assertEquals("2", exception.expectedVersion());
      assertEquals("1", exception.actualVersion());
    }
  }

  @Test
  public void storeHandleScopesBlocksAndSurvivesReopening(@TempDir Path databaseDirectory) throws Exception {
    int retiredBlockId;
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    try (var database = factory.open(databaseDirectory)) {
      var words = database.openStore("words", 17);
      var numbers = database.openStore("numbers", 3);
      var wordsBlock = words.allocateBlock(1, BLOCK_CONTENT_LENGTH);
      var numbersBlock = numbers.allocateBlock(2, BLOCK_CONTENT_LENGTH);
      wordsBlock.activate();
      numbersBlock.activate();
      retiredBlockId = wordsBlock.id();

      assertEquals(BLOCK_CONTENT_LENGTH, wordsBlock.content().byteSize());
      assertEquals(List.of(wordsBlock), words.blocks(), "A store handle must expose only its blocks");
      assertEquals(wordsBlock, words.findBlock(wordsBlock.id()));
      assertNull(words.findBlock(Integer.MAX_VALUE), "An unknown block must not resolve through a store handle");
      assertThrows(
        IllegalArgumentException.class,
        () -> words.findBlock(numbersBlock.id()),
        "A store handle must reject a block owned by another store"
      );

      wordsBlock.seal();
      assertTrue(wordsBlock.content().isReadOnly(), "A sealed block must expose read-only content");
      wordsBlock.retire();
      assertThrows(IllegalStateException.class, wordsBlock::content, "A retired block must reject content access");
      database.flush();
    }

    try (var database = factory.open(databaseDirectory)) {
      var words = database.findStore("words");
      assertNotNull(words);
      assertEquals(17, words.dataVersion());
      var retiredBlock = words.findBlock(retiredBlockId);
      assertNotNull(retiredBlock);
      assertEquals(BlocksStore.Block.LifecycleState.RETIRED, retiredBlock.state());
    }
  }

  @Test
  public void activeBlockRejectsAllocationCompletionTransitions(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var store = database.openStore("store", 1);
      var block = store.allocateBlock(0, BLOCK_CONTENT_LENGTH);
      assertEquals(BlocksStore.Block.LifecycleState.ALLOCATED, block.state());
      block.activate();

      assertEquals(BlocksStore.Block.LifecycleState.ACTIVE, block.state());
      assertThrows(IllegalStateException.class, block::activate, "Activation must publish exactly one transition");
      assertThrows(IllegalStateException.class, block::discard, "An active block must not return to allocation cleanup");
    }
  }

  @Test
  public void storeDropInvalidatesOldHandlesAndAllowsNameReuse(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    try (var database = factory.open(databaseDirectory)) {
      var oldStore = database.openStore("store", 1);
      var oldBlock = oldStore.allocateBlock(0, BLOCK_CONTENT_LENGTH);
      var oldBlockId = oldBlock.id();
      oldStore.drop();

      assertNull(database.findStore("store"), "A dropped store must leave the current catalog");
      assertThrows(IllegalStateException.class, oldStore::dataVersion, "A dropped store handle must reject metadata access");
      assertThrows(IllegalStateException.class, oldStore::blocks, "A dropped store handle must reject block access");
      assertThrows(
        IllegalStateException.class,
        () -> oldStore.allocateBlock(0, BLOCK_CONTENT_LENGTH),
        "A dropped store handle must reject block allocation"
      );
      assertEquals(BlocksStore.Block.LifecycleState.RETIRED, oldBlock.state(), "A dropped store block must be ready for compaction");
      assertThrows(IllegalStateException.class, oldBlock::content, "A dropped store block must reject content access");
      assertThrows(IllegalStateException.class, oldBlock::seal, "A dropped store block must reject state changes");

      var replacement = database.openStore("store", 2);
      assertThrows(
        IllegalArgumentException.class,
        () -> replacement.findBlock(oldBlockId),
        "A replacement must not acquire blocks of the dropped store"
      );
    }
  }

  @Test
  public void allocationHidesTheSystemHeaderLength(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    int blockId;
    long contentLength;
    try (var database = factory.open(databaseDirectory)) {
      var store = database.openStore("store", 1);
      var block = store.allocateBlock(0, 1);
      block.activate();
      blockId = block.id();
      contentLength = block.content().byteSize();
      assertTrue(contentLength >= 1);
    }

    try (var database = factory.open(databaseDirectory)) {
      var store = database.findStore("store");
      assertNotNull(store);
      var block = store.findBlock(blockId);
      assertNotNull(block);
      assertEquals(contentLength, block.content().byteSize());
    }
  }

  @Test
  public void dropRetiresBlocksAcrossHandlesAndChunks(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE, false, false).open(databaseDirectory)) {
      var store = database.openStore("store", 1);
      var neighbour = database.openStore("neighbour", 1);
      var neighbourBlock = neighbour.allocateBlock(0, BLOCK_CONTENT_LENGTH);
      neighbourBlock.activate();
      var blocks = new ArrayList<BlocksStore.Block>();
      for (var i = 0; i < 20; i++) {
        var block = store.allocateBlock(0, BLOCK_CONTENT_LENGTH);
        block.activate();
        if (i % 3 != 0) {
          block.seal();
        }
        if (i % 3 == 2) {
          block.retire();
        }
        blocks.add(block);
      }
      var reopenedHandle = database.openStore("store", 1);
      var foundHandle = database.findStore("store");
      assertNotNull(foundHandle);
      var reopenedBlocks = reopenedHandle.blocks();
      var foundBlocks = foundHandle.blocks();

      store.drop();

      for (var handles : List.of(blocks, reopenedBlocks, foundBlocks)) {
        for (var block : handles) {
          assertEquals(BlocksStore.Block.LifecycleState.RETIRED, block.state(), "Every handle must see the completed deletion");
          assertThrows(IllegalStateException.class, block::content);
        }
      }
      assertEquals(BlocksStore.Block.LifecycleState.ACTIVE, neighbourBlock.state(), "A shared chunk must not retire another store");
      var replacement = database.openStore("store", 2);
      assertTrue(replacement.blocks().isEmpty(), "Name reuse must not expose blocks of the dropped store");
      replacement.drop();
      assertNull(database.findStore("store"), "An empty store must also support deletion");
    }
  }

  @Test
  public void dropPersistsMetadataBeforeRetiringBlocks(@TempDir Path databaseDirectory) throws Exception {
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(databaseDirectory.resolve("database.meta"), CHUNK_SIZE, false);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata, false);
         var blocks = DatabaseBlocks.open(metadata, chunks)) {
      var observedMetadata = mock(DatabaseCatalog.class, delegatesTo(metadata));
      try (var database = new BlocksDatabaseImpl(observedMetadata, chunks, blocks, false)) {
        var store = database.openStore("store", 1);
        var block = store.allocateBlock(0, BLOCK_CONTENT_LENGTH);
        block.activate();
        doAnswer(_ -> {
          assertNull(metadata.findStore("store"), "The deletion must be appended before its persistence barrier");
          assertEquals(BlocksStore.Block.LifecycleState.ACTIVE, block.state(), "The header must not change before fsync succeeds");
          metadata.fsync();
          return null;
        }).when(observedMetadata).fsync();

        store.drop();

        verify(observedMetadata).fsync();
        assertEquals(BlocksStore.Block.LifecycleState.RETIRED, block.state());
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void failedDropRetiresBlocksAfterCatalogChange(boolean failFsync, @TempDir Path databaseDirectory) throws Exception {
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(databaseDirectory.resolve("database.meta"), CHUNK_SIZE, false);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata, false);
         var blocks = DatabaseBlocks.open(metadata, chunks)) {
      var failingMetadata = mock(DatabaseCatalog.class, delegatesTo(metadata));
      try (var database = new BlocksDatabaseImpl(failingMetadata, chunks, blocks, false)) {
        var store = database.openStore("store", 1);
        var block = store.allocateBlock(0, BLOCK_CONTENT_LENGTH);
        block.activate();
        var failure = new IOException("The metadata write failed");
        if (failFsync) {
          doThrow(failure).when(failingMetadata).fsync();
        }
        else {
          doThrow(failure).when(failingMetadata).dropStore(metadata.findStore("store").storeId());
        }

        assertEquals(failure, assertThrows(IOException.class, store::drop));
        var expectedState = failFsync ? BlocksStore.Block.LifecycleState.RETIRED : BlocksStore.Block.LifecycleState.ACTIVE;
        assertEquals(expectedState, block.state(), "Blocks must retire only after the deletion enters the catalog");
      }
    }
  }

  @Test
  public void blockStateRejectsAccessAfterClose(@TempDir Path databaseDirectory) throws Exception {
    BlocksStore.Block block;
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      block = database.openStore("store", 1).allocateBlock(0, BLOCK_CONTENT_LENGTH);
      block.activate();
    }
    assertThrows(IllegalStateException.class, block::state, "State access must not read an unmapped block");
  }

  @Test
  public void stateDoesNotWaitForStoreDrop(@TempDir Path databaseDirectory) throws Exception {
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(databaseDirectory.resolve("database.meta"), CHUNK_SIZE, false);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata, false);
         var blocks = DatabaseBlocks.open(metadata, chunks)) {
      var observedMetadata = mock(DatabaseCatalog.class, delegatesTo(metadata));
      try (var database = new BlocksDatabaseImpl(observedMetadata, chunks, blocks, false);
           var executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("Store drop").factory())) {
        var store = database.openStore("store", 1);
        var block = store.allocateBlock(0, BLOCK_CONTENT_LENGTH);
        block.activate();
        var fsyncStarted = new CountDownLatch(1);
        var finishFsync = new CountDownLatch(1);
        doAnswer(_ -> {
          fsyncStarted.countDown();
          assertTrue(finishFsync.await(10, TimeUnit.SECONDS), "The test must release the metadata barrier");
          metadata.fsync();
          return null;
        }).when(observedMetadata).fsync();
        var drop = executor.submit(() -> {
          store.drop();
          return null;
        });
        var readState = new FutureTask<>(block::state);
        var reader = new Thread(readState, "Block state reader");
        try {
          assertTrue(fsyncStarted.await(10, TimeUnit.SECONDS), "Drop must reach the metadata barrier");
          reader.start();
          assertEquals(BlocksStore.Block.LifecycleState.ACTIVE, readState.get(5, TimeUnit.SECONDS),
                       "State must remain readable while drop waits for fsync");
        }
        finally {
          finishFsync.countDown();
          reader.join(TimeUnit.SECONDS.toMillis(10));
        }
        drop.get(10, TimeUnit.SECONDS);
        assertEquals(BlocksStore.Block.LifecycleState.RETIRED, block.state(), "A completed drop must retire the block");
      }
    }
  }

  @Test
  public void stateRemainsReadableUntilCloseUnmapsTheBlock(@TempDir Path databaseDirectory) throws Exception {
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(databaseDirectory.resolve("database.meta"), CHUNK_SIZE, false);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata, false);
         var blocks = DatabaseBlocks.open(metadata, chunks)) {
      var observedMetadata = mock(DatabaseCatalog.class, delegatesTo(metadata));
      try (var database = new BlocksDatabaseImpl(observedMetadata, chunks, blocks, true);
           var executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("Database close").factory())) {
        var block = database.openStore("store", 1).allocateBlock(0, BLOCK_CONTENT_LENGTH);
        block.activate();
        var fsyncStarted = new CountDownLatch(1);
        var finishFsync = new CountDownLatch(1);
        doAnswer(_ -> {
          fsyncStarted.countDown();
          assertTrue(finishFsync.await(10, TimeUnit.SECONDS), "The test must release the metadata barrier");
          metadata.fsync();
          return null;
        }).when(observedMetadata).fsync();
        var close = executor.submit(() -> {
          database.close();
          return null;
        });
        try {
          assertTrue(fsyncStarted.await(10, TimeUnit.SECONDS), "Close must reach fsync before unmapping");
          assertTrue(database.isClosed(), "The database must be logically closed before fsync");
          assertEquals(BlocksStore.Block.LifecycleState.ACTIVE, block.state(), "A live segment remains readable during close");
        }
        finally {
          finishFsync.countDown();
        }
        close.get(10, TimeUnit.SECONDS);
        assertThrows(IllegalStateException.class, block::state, "Closing the arena must invalidate the block segment");
      }
    }
  }
}
