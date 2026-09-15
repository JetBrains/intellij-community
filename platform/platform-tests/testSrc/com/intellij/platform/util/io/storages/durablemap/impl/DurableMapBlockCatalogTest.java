// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.impl;

import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog;
import com.intellij.util.io.CorruptedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the in-memory durable map block catalog */
public class DurableMapBlockCatalogTest {
  private static final int CHUNK_SIZE = 1024 * 1024;
  private static final int BLOCK_CONTENT_LENGTH = 64 * 1024;

  @Test
  public void openingAnEmptyStoreDoesNotAllocateMetadataBlock(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      var blockCatalog = DurableMapBlockCatalog.open(store);

      assertTrue(blockCatalog.blocks().isEmpty());
      assertTrue(store.blocks().isEmpty());
    }
  }

  @Test
  public void allocationUpdatesTheInMemoryCatalog(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      var blockCatalog = DurableMapBlockCatalog.open(store);
      var dataBlock = blockCatalog.allocateBlock(DurableMapBlockCatalog.DurableMapBlockRole.DATA, BLOCK_CONTENT_LENGTH);
      dataBlock.activate();
      var lookupBlock = blockCatalog.allocateBlock(DurableMapBlockCatalog.DurableMapBlockRole.LOOKUP, BLOCK_CONTENT_LENGTH);

      assertEquals(List.of(dataBlock, lookupBlock), blockCatalog.blocks());
      assertEquals(List.of(dataBlock), blockCatalog.blocks(DurableMapBlockCatalog.DurableMapBlockRole.DATA));
      assertEquals(List.of(lookupBlock), blockCatalog.blocks(DurableMapBlockCatalog.DurableMapBlockRole.LOOKUP));
      assertEquals(blockCatalog.blocks(), store.blocks());
    }
  }

  @Test
  public void openingRebuildsTheCatalogFromStoreBlocks(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    long dataBlockId;
    long lookupBlockId;
    try (var database = factory.open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      var dataBlock = store.allocateBlock(DurableMapBlockCatalog.DurableMapBlockRole.DATA.persistentCode(), BLOCK_CONTENT_LENGTH);
      dataBlock.activate();
      dataBlockId = dataBlock.id();
      var lookupBlock = store.allocateBlock(DurableMapBlockCatalog.DurableMapBlockRole.LOOKUP.persistentCode(), BLOCK_CONTENT_LENGTH);
      lookupBlock.activate();
      lookupBlockId = lookupBlock.id();
      database.flush();
    }

    try (var database = factory.open(databaseDirectory)) {
      var store = database.findStore("map");
      assertNotNull(store);
      var blockCatalog = DurableMapBlockCatalog.open(store);

      assertEquals(dataBlockId, blockCatalog.blocks().get(0).id());
      assertEquals(lookupBlockId, blockCatalog.blocks().get(1).id());
    }
  }

  @Test
  public void openingRejectsAnUnknownBlockRole(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      store.allocateBlock(99, BLOCK_CONTENT_LENGTH);

      assertThrows(CorruptedException.class, () -> DurableMapBlockCatalog.open(store));
    }
  }
}
