// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.database.impl.layout.ChunkHeaderLayout;
import com.intellij.util.SystemProperties;
import com.intellij.util.WaitFor;
import com.intellij.util.io.CorruptedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.RETIRED;
import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.SEALED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Verifies full chunk mapping, catalog publication, and file reconciliation. */
@SuppressWarnings("SuspiciousPackagePrivateAccess")
public class DatabaseChunksTest {
  private static final int CHUNK_SIZE = 1024 * 1024;

  /** Creation must save a full chunk before the catalog publishes its identifier. */
  @Test
  public void createdChunkSurvivesReopening(@TempDir Path databaseDirectory) throws Exception {
    var catalogPath = databaseDirectory.resolve("database.meta");
    long databaseId;
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata)) {
      databaseId = metadata.databaseId();
      var chunk = chunks.createChunk();

      assertEquals(1, chunk.chunkId(), "The first chunk file must use the first catalog identifier");
      assertEquals(databaseId, chunk.databaseId(), "The chunk header must identify its database");
      assertEquals(ACTIVE, chunk.state(), "A new chunk must accept allocations");
      assertEquals(ChunkHeaderLayout.HEADER_SIZE, chunk.committedTail(), "A new chunk must have no committed blocks");
      assertEquals(ChunkHeaderLayout.HEADER_SIZE, chunk.allocatedTail(), "A new chunk must have no allocated blocks");
      assertEquals(CHUNK_SIZE, chunk.mappedSize(), "One mmap region must cover the complete chunk");
      assertEquals(CHUNK_SIZE, Files.size(chunk.storagePath()), "Creation must allocate the complete chunk file");
      assertEquals(1, metadata.chunks().size(), "The catalog must publish the initialized chunk");
      chunks.flush();
      metadata.flush();
    }

    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata)) {
      var chunk = chunks.chunks().getFirst();
      assertEquals(databaseId, chunk.databaseId(), "Reopening must validate the persistent database identity");
      assertEquals(CHUNK_SIZE, chunk.mappedSize(), "Reopening must map the complete file once");
    }
  }

  @Test
  public void invalidBlockLengthDoesNotSealActiveChunk(@TempDir Path databaseDirectory) throws Exception {
    try (var catalog = DatabaseCatalogOverAppendOnlyLog.open(databaseDirectory.resolve("database.meta"), CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, catalog)) {
      var chunk = chunks.createChunk();

      for (var blockLength : new int[]{0, -1}) {
        assertThrows(IllegalArgumentException.class, () -> chunks.chunkForAllocation(blockLength));
        assertThrows(IllegalArgumentException.class, () -> chunk.canAllocate(blockLength));
      }

      assertEquals(ACTIVE, chunk.state(), "An invalid block length must not seal the chunk header");
      assertEquals(ACTIVE, catalog.chunks().getFirst().state(), "An invalid block length must not seal the catalog entry");
    }
  }

  @Test
  public void failedLargeAllocationLeavesRoomForSmallerBlock(@TempDir Path databaseDirectory) throws Exception {
    try (var catalog = DatabaseCatalogOverAppendOnlyLog.open(databaseDirectory.resolve("database.meta"), CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, catalog)) {
      var smallLength = DatabaseBlock.blockLengthForContent(32);
      var largeLength = CHUNK_SIZE - ChunkHeaderLayout.HEADER_SIZE - smallLength;
      var first = chunks.chunkForAllocation(largeLength);
      first.allocateBlock(1, 1, 0, largeLength);

      var second = chunks.chunkForAllocation(largeLength);
      assertEquals(2, second.chunkId(), "The large block needs a new chunk");
      assertEquals(ACTIVE, first.state(), "A failed request must leave the first chunk active");
      assertSame(first, chunks.chunkForAllocation(smallLength), "The smaller block must reuse the first chunk's remaining space");
      first.allocateBlock(2, 1, 0, smallLength);
      assertEquals(CHUNK_SIZE, first.committedTail(), "The smaller block must fill the remaining space");
    }
  }

  @Test
  public void thirdActiveChunkSealsOnlyTheOldest(@TempDir Path databaseDirectory) throws Exception {
    try (var catalog = DatabaseCatalogOverAppendOnlyLog.open(databaseDirectory.resolve("database.meta"), CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, catalog)) {
      var blockLength = (CHUNK_SIZE - ChunkHeaderLayout.HEADER_SIZE) / 2 + 8;
      for (int blockId = 1; blockId <= 3; blockId++) {
        var chunk = chunks.chunkForAllocation(blockLength);
        assertEquals(blockId, chunk.chunkId(), "Each block needs a new chunk");
        chunk.allocateBlock(blockId, 1, 0, blockLength);
      }

      var opened = chunks.chunks();
      assertEquals(SEALED, opened.get(0).state(), "The oldest chunk must leave the active queue");
      assertEquals(ACTIVE, opened.get(1).state(), "The second chunk must remain active");
      assertEquals(ACTIVE, opened.get(2).state(), "The newest chunk must remain active");
      assertEquals(SEALED, catalog.findChunk(1).state(), "The catalog must record the eviction");
      assertEquals(1, chunks.metrics(true).sealed(), "The eviction must update the seal counter");
    }
  }

  @Test
  public void openingSealsOldestExcessActiveChunks(@TempDir Path databaseDirectory) throws Exception {
    var catalogPath = databaseDirectory.resolve("database.meta");
    try (var catalog = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE)) {
      for (int chunkId = 1; chunkId <= 4; chunkId++) {
        try (var _ = DatabaseChunk.create(DatabaseChunks.chunkPath(databaseDirectory, chunkId), catalog.databaseId(), chunkId, CHUNK_SIZE)) {
          catalog.registerNewChunk(chunkId);
        }
      }
      catalog.flush();
    }

    try (var catalog = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, catalog)) {
      assertEquals(SEALED, catalog.findChunk(1).state(), "The first excess chunk must be sealed");
      assertEquals(SEALED, catalog.findChunk(2).state(), "The second excess chunk must be sealed");
      assertEquals(ACTIVE, catalog.findChunk(3).state(), "The newest two chunks must remain active");
      assertEquals(ACTIVE, catalog.findChunk(4).state(), "The newest two chunks must remain active");
      assertEquals(3, chunks.chunkForAllocation(DatabaseBlock.blockLengthForContent(32)).chunkId(),
                   "Allocation must use the oldest remaining active chunk");
      assertEquals(2, chunks.metrics(true).sealed(), "Startup must count both seal transitions");
    }
  }

  @Test
  public void openingCompletesInterruptedChunkSeal(@TempDir Path databaseDirectory) throws Exception {
    var catalogPath = databaseDirectory.resolve("database.meta");
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata)) {
      var chunk = chunks.createChunk();
      chunk.seal();
      assertEquals(ACTIVE, metadata.chunks().getFirst().state(), "The setup must leave the catalog transition incomplete");
    }

    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata, true)) {
      assertEquals(SEALED, metadata.chunks().getFirst().state(), "Recovery must complete the catalog transition");
      assertEquals(SEALED, chunks.chunks().getFirst().state(), "The recovered chunk must stay sealed");
      assertEquals(1, chunks.metrics(true).statesReconciled(), "Recovery must report the completed state transition");
    }
  }

  @Test
  public void openingCompletesInterruptedChunkRetirementBeforeDrop(@TempDir Path databaseDirectory) throws Exception {
    var catalogPath = databaseDirectory.resolve("database.meta");
    var chunkPath = DatabaseChunks.chunkPath(databaseDirectory, 1);
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata)) {
      var chunk = chunks.createChunk();
      chunk.seal();
      metadata.markChunkSealed(chunk.chunkId());
      metadata.fsync();
      chunk.retire();
      assertEquals(SEALED, metadata.chunks().getFirst().state(), "The setup must leave the catalog transition incomplete");
    }

    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE);
         var chunks = DatabaseChunks.open(databaseDirectory, metadata, true)) {
      assertEquals(RETIRED, metadata.chunks().getFirst().state(), "Recovery must complete the catalog transition");
      assertTrue(chunks.chunks().isEmpty(), "A recovered retired chunk must not become accessible");
      assertTrue(Files.exists(chunkPath), "Recovery must keep the retired chunk file for startup housekeeping");
      var metrics = chunks.metrics(true);
      assertEquals(1, metrics.statesReconciled(), "Recovery must report the completed state transition");
      assertEquals(0, metrics.filesDeleted(), "Recovery must not report the retired chunk as an orphan file");

      chunks.dropRetiredChunks();
      assertFileDeletedAndReported(chunkPath, chunks, "Startup housekeeping must delete and report the retired chunk file");
    }
  }

  /** Recovery must delete a complete file that the catalog never published. */
  @Test
  public void openingDeletesUnregisteredChunkFile(@TempDir Path databaseDirectory) throws Exception {
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(databaseDirectory.resolve("database.meta"), CHUNK_SIZE)) {
      var chunkId = metadata.nextChunkId();
      var chunkPath = DatabaseChunks.chunkPath(databaseDirectory, chunkId);
      try (var _ = DatabaseChunk.create(chunkPath, metadata.databaseId(), chunkId, CHUNK_SIZE)) {
        assertTrue(Files.exists(chunkPath), "The setup must create an unpublished chunk file");
      }

      try (var chunks = DatabaseChunks.open(databaseDirectory, metadata, true)) {
        assertTrue(chunks.chunks().isEmpty(), "An orphan file must not become a catalog chunk");
        assertFileDeletedAndReported(chunkPath, chunks, "Recovery must delete and report the unpublished chunk file");
      }
    }
  }

  /** Recovery must reject a catalog entry whose file was never initialized. */
  @Test
  public void openingRejectsMissingRegisteredChunk(@TempDir Path databaseDirectory) throws Exception {
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(databaseDirectory.resolve("database.meta"), CHUNK_SIZE)) {
      var chunkId = metadata.nextChunkId();
      metadata.registerNewChunk(chunkId);
      metadata.flush();

      assertThrows(
        CorruptedException.class,
        () -> DatabaseChunks.open(databaseDirectory, metadata),
        "A catalog entry must never hide a missing chunk file"
      );
    }
  }

  @Test
  public void openingChecksRegisteredChunksBeforeDeletingUnusedFiles(@TempDir Path databaseDirectory) throws Exception {
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(databaseDirectory.resolve("database.meta"), CHUNK_SIZE)) {
      metadata.registerNewChunk(metadata.nextChunkId());
      metadata.flush();

      var unusedChunkId = metadata.nextChunkId();
      var unusedChunkPath = DatabaseChunks.chunkPath(databaseDirectory, unusedChunkId);
      try (var _ = DatabaseChunk.create(unusedChunkPath, metadata.databaseId(), unusedChunkId, CHUNK_SIZE)) {
        assertTrue(Files.exists(unusedChunkPath), "The setup must create an unpublished chunk file");
      }

      assertThrows(CorruptedException.class, () -> DatabaseChunks.open(databaseDirectory, metadata));
      assertTrue(Files.exists(unusedChunkPath), "Failed opening must not start secondary cleanup");
    }
  }

  @Test
  public void openingChecksRegisteredChunksBeforeDeletingRetiredFiles(@TempDir Path databaseDirectory) throws Exception {
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(databaseDirectory.resolve("database.meta"), CHUNK_SIZE)) {
      var retiredChunkId = metadata.nextChunkId();
      var retiredChunkPath = DatabaseChunks.chunkPath(databaseDirectory, retiredChunkId);
      try (var _ = DatabaseChunk.create(retiredChunkPath, metadata.databaseId(), retiredChunkId, CHUNK_SIZE)) {
        metadata.registerNewChunk(retiredChunkId);
        metadata.markChunkSealed(retiredChunkId);
        metadata.markChunkRetired(retiredChunkId);
      }
      metadata.registerNewChunk(metadata.nextChunkId());
      metadata.flush();

      assertThrows(CorruptedException.class, () -> DatabaseChunks.open(databaseDirectory, metadata));
      assertTrue(Files.exists(retiredChunkPath), "Failed opening must not delete a retired chunk file");
    }
  }

  @Test
  public void openingKeepsRetiredChunkFilesUntilAsyncDrop(@TempDir Path databaseDirectory) throws Exception {
    assumeTrue(
      SystemProperties.getBooleanProperty("DatabaseChunks.DELETE_FILES_ASYNC", true),
      "Asynchronous chunk file deletion is disabled"
    );
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(databaseDirectory.resolve("database.meta"), CHUNK_SIZE)) {
      var retiredChunkId = metadata.nextChunkId();
      var retiredChunkPath = DatabaseChunks.chunkPath(databaseDirectory, retiredChunkId);
      try (var _ = DatabaseChunk.create(retiredChunkPath, metadata.databaseId(), retiredChunkId, CHUNK_SIZE)) {
        metadata.registerNewChunk(retiredChunkId);
        metadata.markChunkSealed(retiredChunkId);
        metadata.markChunkRetired(retiredChunkId);
      }
      metadata.flush();

      try (var chunks = DatabaseChunks.open(databaseDirectory, metadata)) {
        assertTrue(Files.exists(retiredChunkPath), "Recovery must keep a retired chunk file for startup housekeeping");
        assertTrue(chunks.chunks().isEmpty(), "A retired chunk must not become accessible");
        chunks.dropRetiredChunks();
        assertFileDeletedAndReported(
          retiredChunkPath,
          chunks,
          "Asynchronous startup housekeeping must delete and report the retired chunk file"
        );
      }
    }
  }

  /** Opening must reject a chunk that belongs to another database. */
  @Test
  public void openingRejectsForeignChunk(@TempDir Path databaseDirectory) throws Exception {
    try (var metadata = DatabaseCatalogOverAppendOnlyLog.open(databaseDirectory.resolve("database.meta"), CHUNK_SIZE)) {
      var chunkId = metadata.nextChunkId();
      var foreignDatabaseId = metadata.databaseId() == Long.MIN_VALUE ? 1 : metadata.databaseId() ^ Long.MIN_VALUE;
      try (var _ = DatabaseChunk.create(DatabaseChunks.chunkPath(databaseDirectory, chunkId), foreignDatabaseId, chunkId, CHUNK_SIZE)) {
        metadata.registerNewChunk(chunkId);
        metadata.flush();
      }

      assertThrows(
        CorruptedException.class,
        () -> DatabaseChunks.open(databaseDirectory, metadata),
        "A chunk from another database must not become accessible"
      );
    }
  }

  private static void assertFileDeletedAndReported(Path chunkPath,
                                                   DatabaseChunks chunks,
                                                   String message) {
    assertTrue(new WaitFor(10_000) {
      @Override
      protected boolean condition() {
        return Files.notExists(chunkPath) && chunks.metrics(true).filesDeleted() == 1;
      }
    }.isConditionRealized(), message);
  }
}
