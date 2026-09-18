// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.extendiblehashmap;

import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole;
import com.intellij.platform.util.io.storages.database.storages.durablemap.LookupBlocks;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMapInt32ToInt64;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ALLOCATED;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.RETIRED;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtendibleHashMapStorageOverLookupBlocksTest {
  private static final int CHUNK_SIZE = 1024 * 1024;
  private static final int SEGMENT_SIZE = 1024;
  private static final int LOOKUP_ROLE = DurableMapBlockRole.LOOKUP.persistentCode();

  @Test
  void logicalSegmentIndexes_SurviveReopen(@TempDir @NotNull Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      var lookupBlocks = lookupBlocks(store);
      try (var storage = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, SEGMENT_SIZE)) {
        assertTrue(storage.isEmpty());

        var headerSegment = storage.allocateSegment(0);
        var dataSegment = storage.allocateSegment(7);
        headerSegment.set(JAVA_INT, 0, 42);
        dataSegment.set(JAVA_INT, 0, 73);

        var blocks = lookupBlocks.blocks();
        assertEquals(0, ExtendibleHashMapSegmentBlockLayout.segmentIndex(blocks.getFirst().payload()));
        assertEquals(7, ExtendibleHashMapSegmentBlockLayout.segmentIndex(blocks.getLast().payload()));
      }

      try (var reopened = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks(store), SEGMENT_SIZE)) {
        assertFalse(reopened.isEmpty());
        assertEquals(42, reopened.segment(0).get(JAVA_INT, 0));
        assertEquals(73, reopened.segment(7).get(JAVA_INT, 0));
      }
      assertEquals(2, store.blocks().size());
      for (var block : store.blocks()) {
        assertEquals(LOOKUP_ROLE, block.role());
      }
    }
  }

  @Test
  void extendibleHashMap_SurvivesSplits_AndDatabaseReopen(@TempDir @NotNull Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    try (var database = factory.open(databaseDirectory);
         var map = openMap(database.openStore("map", 1))) {
      for (int key = 1; key <= 4_000; key++) {
        assertTrue(map.put(key, valueForKey(key)));
      }
      assertEquals(4_000, map.size());
    }

    try (var database = factory.open(databaseDirectory)) {
      var store = database.findStore("map");
      assertNotNull(store);
      try (var map = openMap(store)) {
        for (int key = 1; key <= 4_000; key++) {
          long expectedValue = valueForKey(key);
          assertEquals(expectedValue, map.lookup(key, value -> value == expectedValue));
        }
      }
    }
  }

  @Test
  void extendibleHashMap_ClearSurvivesDatabaseReopen(@TempDir @NotNull Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    try (var database = factory.open(databaseDirectory);
         var map = openMap(database.openStore("map", 1))) {
      assertTrue(map.put(1, 2));
      map.clear();
      assertTrue(map.isEmpty());
    }

    try (var database = factory.open(databaseDirectory)) {
      var store = database.findStore("map");
      assertNotNull(store);
      try (var map = openMap(store)) {
        assertTrue(map.isEmpty());
        assertTrue(map.put(3, 4));
      }
    }
  }

  @Test
  void allocatedLookupBlock_IsRetiredAndHiddenAfterReopen(@TempDir @NotNull Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    int allocatedBlockId;
    try (var database = factory.open(databaseDirectory)) {
      var lookupBlocks = lookupBlocks(database.openStore("map", 1));
      var block = lookupBlocks.allocate(SEGMENT_SIZE + ExtendibleHashMapSegmentBlockLayout.HEADER_SIZE);
      allocatedBlockId = block.id();
      assertEquals(ALLOCATED, block.state());
      lookupBlocks.flush();
    }

    try (var database = factory.open(databaseDirectory)) {
      var store = database.findStore("map");
      assertNotNull(store);
      var recoveredBlock = store.findBlock(allocatedBlockId);
      assertNotNull(recoveredBlock);
      assertEquals(RETIRED, recoveredBlock.state());

      var lookupBlocks = lookupBlocks(store);
      assertTrue(lookupBlocks.blocks().isEmpty(), "The provider must not see the incomplete block");
      try (var storage = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, SEGMENT_SIZE)) {
        assertTrue(storage.isEmpty());
        storage.allocateSegment(0);
        var replacement = lookupBlocks.blocks().getFirst();
        assertEquals(ACTIVE, replacement.state());
        assertTrue(replacement.id() > allocatedBlockId, "The replacement must not reuse the retired identifier");
      }
    }
  }

  @Test
  void duplicateLogicalSegment_IsRejected(@TempDir @NotNull Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      var lookupBlocks = lookupBlocks(store);
      allocatePublishedSegmentThreeBlock(lookupBlocks);
      allocatePublishedSegmentThreeBlock(lookupBlocks);

      assertThrows(CorruptedException.class, () -> {
        try (var storage = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, SEGMENT_SIZE)) {
          assertNotNull(storage);
        }
      });
    }
  }

  @Test
  void clear_RetiresOnlyOwnedRoleBlocks(@TempDir @NotNull Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      var dataBlock = store.allocateBlock(DurableMapBlockRole.DATA.persistentCode(), SEGMENT_SIZE);
      dataBlock.activate();
      try (var storage = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks(store), SEGMENT_SIZE)) {
        storage.allocateSegment(0);
        var lookupBlock = store.blocks().getLast();

        storage.clear();

        assertTrue(storage.isEmpty());
        assertEquals(ACTIVE, dataBlock.state());
        assertEquals(RETIRED, lookupBlock.state());
      }
    }
  }

  private static @NotNull ExtendibleHashMapInt32ToInt64 openMap(@NotNull BlocksStore store) throws Exception {
    return new ExtendibleHashMapInt32ToInt64(
      new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks(store), SEGMENT_SIZE)
    );
  }

  private static @NotNull LookupBlocks lookupBlocks(@NotNull BlocksStore store) throws Exception {
    return DurableMapBlockCatalog.open(store).lookupBlocks(
      ExtendibleHashMapStorageOverLookupBlocks.IMPLEMENTATION_ID,
      ExtendibleHashMapStorageOverLookupBlocks.INITIAL_GENERATION
    );
  }

  private static void allocatePublishedSegmentThreeBlock(@NotNull LookupBlocks lookupBlocks) throws Exception {
    var block = lookupBlocks.allocate(SEGMENT_SIZE + ExtendibleHashMapSegmentBlockLayout.HEADER_SIZE);
    ExtendibleHashMapSegmentBlockLayout.initializeSegmentIndex(block.payload(), 3);
    block.activate();
  }

  private static long valueForKey(int key) {
    return ((long)key << Integer.SIZE) | Integer.toUnsignedLong(~key);
  }
}
