// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.impl;

import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog;
import com.intellij.util.io.CorruptedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ALLOCATED;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.RETIRED;
import static java.lang.foreign.ValueLayout.JAVA_INT;
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
      var entriesBlock = blockCatalog.allocateBlock(DurableMapBlockCatalog.DurableMapBlockRole.ENTRIES, BLOCK_CONTENT_LENGTH);
      entriesBlock.activate();
      var lookupBlock = blockCatalog.allocateBlock(DurableMapBlockCatalog.DurableMapBlockRole.LOOKUP, BLOCK_CONTENT_LENGTH);
      var keysBlock = blockCatalog.allocateBlock(DurableMapBlockCatalog.DurableMapBlockRole.KEYS, BLOCK_CONTENT_LENGTH);
      var valuesBlock = blockCatalog.allocateBlock(DurableMapBlockCatalog.DurableMapBlockRole.VALUES, BLOCK_CONTENT_LENGTH);

      assertEquals(3, DurableMapBlockCatalog.DurableMapBlockRole.KEYS.persistentCode());
      assertEquals(4, DurableMapBlockCatalog.DurableMapBlockRole.VALUES.persistentCode());
      assertEquals(List.of(entriesBlock, lookupBlock, keysBlock, valuesBlock), blockCatalog.blocks());
      assertEquals(List.of(entriesBlock), blockCatalog.blocks(DurableMapBlockCatalog.DurableMapBlockRole.ENTRIES));
      assertEquals(List.of(lookupBlock), blockCatalog.blocks(DurableMapBlockCatalog.DurableMapBlockRole.LOOKUP));
      assertEquals(List.of(keysBlock), blockCatalog.blocks(DurableMapBlockCatalog.DurableMapBlockRole.KEYS));
      assertEquals(List.of(valuesBlock), blockCatalog.blocks(DurableMapBlockCatalog.DurableMapBlockRole.VALUES));
      assertEquals(blockCatalog.blocks(), store.blocks());
    }
  }

  @Test
  public void lookupBlocksAreScopedByImplementationAndGeneration(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    int entriesBlockId;
    int implementationOneGenerationOneBlockId;
    int implementationOneGenerationTwoBlockId;
    int implementationTwoGenerationOneBlockId;
    try (var database = factory.open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      var blockCatalog = DurableMapBlockCatalog.open(store);
      var entriesBlock = blockCatalog.allocateBlock(DurableMapBlockCatalog.DurableMapBlockRole.ENTRIES, BLOCK_CONTENT_LENGTH);
      entriesBlock.activate();
      var implementationOneGenerationOne = blockCatalog.lookupBlocks(1, 1);
      var implementationOneGenerationTwo = blockCatalog.lookupBlocks(1, 2);
      var implementationTwoGenerationOne = blockCatalog.lookupBlocks(2, 1);
      var implementationOneGenerationOneBlock = implementationOneGenerationOne.allocate(BLOCK_CONTENT_LENGTH);
      var implementationOneGenerationTwoBlock = implementationOneGenerationTwo.allocate(BLOCK_CONTENT_LENGTH);
      var implementationTwoGenerationOneBlock = implementationTwoGenerationOne.allocate(BLOCK_CONTENT_LENGTH);

      assertEquals(ALLOCATED, implementationOneGenerationOneBlock.state());

      implementationOneGenerationOneBlock.payload().set(JAVA_INT, 0, 11);
      implementationOneGenerationTwoBlock.payload().set(JAVA_INT, 0, 12);
      implementationTwoGenerationOneBlock.payload().set(JAVA_INT, 0, 21);
      implementationOneGenerationOneBlock.activate();
      implementationOneGenerationTwoBlock.activate();
      implementationTwoGenerationOneBlock.activate();

      assertEquals(List.of(implementationOneGenerationOneBlock), implementationOneGenerationOne.blocks());
      assertEquals(List.of(implementationOneGenerationTwoBlock), implementationOneGenerationTwo.blocks());
      assertEquals(List.of(implementationTwoGenerationOneBlock), implementationTwoGenerationOne.blocks());
      assertEquals(3, blockCatalog.blocks(DurableMapBlockCatalog.DurableMapBlockRole.LOOKUP).size());

      entriesBlockId = entriesBlock.id();
      implementationOneGenerationOneBlockId = implementationOneGenerationOneBlock.id();
      implementationOneGenerationTwoBlockId = implementationOneGenerationTwoBlock.id();
      implementationTwoGenerationOneBlockId = implementationTwoGenerationOneBlock.id();
      implementationOneGenerationOne.flush();
    }

    try (var database = factory.open(databaseDirectory)) {
      var store = database.findStore("map");
      assertNotNull(store);
      var blockCatalog = DurableMapBlockCatalog.open(store);
      var implementationOneGenerationOne = blockCatalog.lookupBlocks(1, 1);
      var implementationOneGenerationTwo = blockCatalog.lookupBlocks(1, 2);
      var implementationTwoGenerationOne = blockCatalog.lookupBlocks(2, 1);

      assertEquals(implementationOneGenerationOneBlockId, implementationOneGenerationOne.blocks().getFirst().id());
      assertEquals(11, implementationOneGenerationOne.blocks().getFirst().payload().get(JAVA_INT, 0));
      assertEquals(implementationOneGenerationTwoBlockId, implementationOneGenerationTwo.blocks().getFirst().id());
      assertEquals(12, implementationOneGenerationTwo.blocks().getFirst().payload().get(JAVA_INT, 0));
      assertEquals(implementationTwoGenerationOneBlockId, implementationTwoGenerationOne.blocks().getFirst().id());
      assertEquals(21, implementationTwoGenerationOne.blocks().getFirst().payload().get(JAVA_INT, 0));

      implementationOneGenerationOne.retireAll();

      assertEquals(entriesBlockId, blockCatalog.blocks(DurableMapBlockCatalog.DurableMapBlockRole.ENTRIES).getFirst().id());
      assertEquals(ACTIVE, blockCatalog.blocks(DurableMapBlockCatalog.DurableMapBlockRole.ENTRIES).getFirst().state(),
                   "Retiring one lookup scope must preserve ENTRIES blocks");
      assertEquals(RETIRED, implementationOneGenerationOne.blocks().getFirst().state());
      assertEquals(ACTIVE, implementationOneGenerationTwo.blocks().getFirst().state(),
                   "Retiring one generation must preserve other generations");
      assertEquals(ACTIVE, implementationTwoGenerationOne.blocks().getFirst().state(),
                   "Retiring one implementation must preserve other implementations");
    }
  }

  @Test
  public void openingMapsPreRenameRoleCodeToEntries(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    var preRenameEntriesRoleCode = 1;
    long entriesBlockId;
    long lookupBlockId;
    try (var database = factory.open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      var entriesBlock = store.allocateBlock(preRenameEntriesRoleCode, BLOCK_CONTENT_LENGTH);
      entriesBlock.activate();
      entriesBlockId = entriesBlock.id();
      var lookupBlock = store.allocateBlock(DurableMapBlockCatalog.DurableMapBlockRole.LOOKUP.persistentCode(), BLOCK_CONTENT_LENGTH);
      lookupBlock.activate();
      lookupBlockId = lookupBlock.id();
      database.flush();
    }

    try (var database = factory.open(databaseDirectory)) {
      var store = database.findStore("map");
      assertNotNull(store);
      var blockCatalog = DurableMapBlockCatalog.open(store);

      assertEquals(entriesBlockId, blockCatalog.blocks().get(0).id());
      assertEquals(lookupBlockId, blockCatalog.blocks().get(1).id());
      assertEquals(entriesBlockId, blockCatalog.blocks(DurableMapBlockCatalog.DurableMapBlockRole.ENTRIES).getFirst().id(),
                   "The pre-rename role code must open as ENTRIES");
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
