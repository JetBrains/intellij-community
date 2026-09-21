// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.database.spi.StoreMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.ReadOnlyBufferException;
import java.util.List;

import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.RETIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies that the catalog log restores the catalog and enforces its lifecycle. */
@SuppressWarnings("SuspiciousPackagePrivateAccess")
public class DatabaseCatalogOverAppendOnlyLogTest {
  private static final int CHUNK_SIZE = 1024 * 1024;

  /** Replay must restore states and continue identifier allocation after retired chunks. */
  @Test
  public void chunkCatalogSurvivesReopening(@TempDir Path tempDirectory) throws Exception {
    var catalogPath = tempDirectory.resolve("database.meta");
    try (var catalog = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE)) {
      var firstChunkId = catalog.nextChunkId();
      catalog.registerNewChunk(firstChunkId);
      var secondChunkId = catalog.nextChunkId();
      catalog.registerNewChunk(secondChunkId);
      catalog.markChunkSealed(firstChunkId);
      catalog.markChunkRetired(firstChunkId);

      assertEquals(1, firstChunkId, "The first chunk must use the first positive identifier");
      assertEquals(2, secondChunkId, "A later chunk must use the next identifier");
      assertEquals(
        List.of(
          new DatabaseCatalog.ChunkInfo(firstChunkId, RETIRED),
          new DatabaseCatalog.ChunkInfo(secondChunkId, ACTIVE)
        ),
        catalog.chunks(),
        "The live catalog must publish each completed state change"
      );
      assertFalse(catalog.isDirty(), "Mapped catalog must not require an explicit flush");
      catalog.flush();
    }

    try (var catalog = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE)) {
      assertEquals(
        List.of(
          new DatabaseCatalog.ChunkInfo(1, RETIRED),
          new DatabaseCatalog.ChunkInfo(2, ACTIVE)
        ),
        catalog.chunks(),
        "Replay must restore retired and active chunks"
      );
      assertEquals(3, catalog.nextChunkId(), "Recovery must not reuse a retired chunk identifier");
    }
  }

  /** Invalid transitions must fail before they add a catalog record. */
  @Test
  public void chunkCatalogRejectsInvalidTransitions(@TempDir Path tempDirectory) throws Exception {
    try (var catalog = DatabaseCatalogOverAppendOnlyLog.open(tempDirectory.resolve("database.meta"), CHUNK_SIZE)) {
      var chunkId = catalog.nextChunkId();
      catalog.registerNewChunk(chunkId);
      catalog.flush();

      assertThrows(
        IllegalStateException.class,
        () -> catalog.markChunkRetired(chunkId),
        "An active chunk must become sealed before it becomes retired"
      );
      assertThrows(
        IllegalArgumentException.class,
        () -> catalog.markChunkSealed(chunkId + 1),
        "The catalog must reject a state change for an unknown chunk"
      );
      assertFalse(catalog.isDirty(), "Rejected transitions must not change the catalog dirty state");
    }
  }

  /** Replay must restore current stores and keep identifiers of dropped stores reserved. */
  @Test
  public void storeCatalogSurvivesReopeningAndNameReuse(@TempDir Path tempDirectory) throws Exception {
    var catalogPath = tempDirectory.resolve("database.meta");
    try (var catalog = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE)) {
      var firstStoreId = catalog.nextStoreId();
      catalog.registerNewStore(firstStoreId, "words", 17);
      var secondStoreId = catalog.nextStoreId();
      catalog.registerNewStore(secondStoreId, "идентификаторы", 5);
      catalog.dropStore(firstStoreId);

      assertNull(catalog.findStore("words"), "A dropped store must not remain current");
      var replacementStoreId = catalog.nextStoreId();
      catalog.registerNewStore(replacementStoreId, "words", 18);

      assertEquals(1, firstStoreId, "The first store must use the first positive identifier");
      assertEquals(2, secondStoreId, "A later store must use the next identifier");
      assertEquals(3, replacementStoreId, "A replacement store must not reuse the dropped identifier");
      assertEquals(
        List.of(
          new DatabaseCatalog.StoreInfo(secondStoreId, "идентификаторы", 5),
          new DatabaseCatalog.StoreInfo(replacementStoreId, "words", 18)
        ),
        catalog.stores(),
        "The current catalog must contain the replacement and other current stores"
      );
      catalog.flush();
    }

    try (var catalog = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE)) {
      assertEquals(
        List.of(
          new DatabaseCatalog.StoreInfo(2, "идентификаторы", 5),
          new DatabaseCatalog.StoreInfo(3, "words", 18)
        ),
        catalog.stores(),
        "Replay must restore current stores in identifier order"
      );
      assertEquals(new DatabaseCatalog.StoreInfo(3, "words", 18), catalog.findStore("words"));
      assertEquals(4, catalog.nextStoreId(), "Recovery must not reuse a dropped store identifier");
    }
  }

  /** Rejected store changes must not add catalog records. */
  @Test
  public void storeCatalogRejectsInvalidChangesBeforeAppend(@TempDir Path tempDirectory) throws Exception {
    var catalogPath = tempDirectory.resolve("database.meta");
    try (var catalog = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE)) {
      catalog.registerNewStore(catalog.nextStoreId(), "store", 1);

      assertThrows(
        IllegalStateException.class,
        () -> catalog.registerNewStore(catalog.nextStoreId(), "store", 2),
        "The catalog must reject a duplicate current name"
      );
      assertThrows(
        IllegalArgumentException.class,
        () -> catalog.registerNewStore(catalog.nextStoreId() + 1, "another-store", 1),
        "The catalog must only accept the next store identifier"
      );
      assertThrows(
        IllegalArgumentException.class,
        () -> catalog.dropStore(catalog.nextStoreId()),
        "The catalog must reject an unknown store identifier"
      );
      assertEquals(2, catalog.nextStoreId(), "Rejected changes must not reserve a store identifier");
      catalog.flush();
    }

    try (var catalog = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE)) {
      assertEquals(List.of(new DatabaseCatalog.StoreInfo(1, "store", 1)), catalog.stores());
      assertEquals(2, catalog.nextStoreId(), "Replay must not observe rejected changes");
    }
  }

  /** Replay must apply the last store catalog update. */
  @Test
  public void storeMetadataSurvivesReopening(@TempDir Path tempDirectory) throws Exception {
    var catalogPath = tempDirectory.resolve("database.meta");
    try (var catalog = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE)) {
      var storeId = catalog.nextStoreId();
      var initialBytes = new byte[]{1, 4, 2};
      catalog.registerNewStore(storeId, "map", 3, StoreMetadata.copyOf(1, initialBytes));
      initialBytes[0] = 0;
      assertEquals(StoreMetadata.copyOf(1, new byte[]{1, 4, 2}), catalog.findStore("map").storeMetadata());
      catalog.updateStoreMetadata(storeId, StoreMetadata.copyOf(7, new byte[]{7, 8, 9}));

      var expectedStore = new DatabaseCatalog.StoreInfo(storeId, "map", 3, StoreMetadata.copyOf(7, new byte[]{7, 8, 9}));
      assertEquals(expectedStore, catalog.findStore("map"));
      assertThrows(
        IllegalArgumentException.class,
        () -> catalog.updateStoreMetadata(storeId + 1, StoreMetadata.copyOf(1, new byte[]{1})),
        "The catalog must reject catalog for an unknown store"
      );
      catalog.flush();
    }

    try (var catalog = DatabaseCatalogOverAppendOnlyLog.open(catalogPath, CHUNK_SIZE)) {
      var store = catalog.findStore("map");
      var expectedStore = new DatabaseCatalog.StoreInfo(1, "map", 3, StoreMetadata.copyOf(7, new byte[]{7, 8, 9}));
      assertEquals(expectedStore, store);
      assertThrows(ReadOnlyBufferException.class, () -> store.storeMetadata().asReadOnlyBuffer().put(0, (byte)0));
      assertEquals(expectedStore, catalog.findStore("map"));
    }
  }
}
