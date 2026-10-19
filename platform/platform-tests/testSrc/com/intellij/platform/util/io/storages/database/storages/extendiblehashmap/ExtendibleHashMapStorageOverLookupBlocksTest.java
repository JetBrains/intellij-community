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

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.List;

import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ALLOCATED;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.RETIRED;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtendibleHashMapStorageOverLookupBlocksTest {
  private static final int CHUNK_SIZE = 1024 * 1024;
  private static final int SEGMENT_SIZE = 1024;
  private static final int MAP_HEADER_SIZE = 76;
  private static final int LOOKUP_ROLE = DurableMapBlockRole.LOOKUP.persistentCode();

  @Test
  void logicalSegmentIndexes_SurviveReopen(@TempDir @NotNull Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      var lookupBlocks = lookupBlocks(store);
      try (var storage = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, MAP_HEADER_SIZE, SEGMENT_SIZE)) {
        assertTrue(storage.isEmpty());

        var header = storage.header();
        var segmentsTable = storage.segmentsTable();
        var dataSegment = storage.allocateSegment(7);
        header.set(JAVA_INT, 0, 42);
        segmentsTable.set(JAVA_INT, 0, 84);
        dataSegment.set(JAVA_INT, 0, 73);

        var blocks = lookupBlocks.blocks();
        assertEquals(2, blocks.size());
        assertEquals(ExtendibleHashMapBlockLayout.HEADER_AND_SEGMENTS_TABLE_BLOCK_MARKER,
                     ExtendibleHashMapBlockLayout.segmentIndex(blocks.getFirst().payload()));
        assertEquals(7, ExtendibleHashMapBlockLayout.segmentIndex(blocks.getLast().payload()));
      }

      try (var reopened = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks(store), MAP_HEADER_SIZE, SEGMENT_SIZE)) {
        assertFalse(reopened.isEmpty());
        assertEquals(42, reopened.header().get(JAVA_INT, 0));
        assertEquals(84, reopened.segmentsTable().get(JAVA_INT, 0));
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
      var block = lookupBlocks.allocate(SEGMENT_SIZE + ExtendibleHashMapBlockLayout.BLOCK_HEADER_SIZE);
      allocatedBlockId = block.id();
      assertEquals(ALLOCATED, block.state());
      lookupBlocks.flush();
    }

    try (var database = factory.open(databaseDirectory)) {
      var store = database.findStore("map");
      assertNotNull(store);
      assertNull(store.findBlock(allocatedBlockId), "The store must not find the discarded block");

      var lookupBlocks = lookupBlocks(store);
      assertTrue(lookupBlocks.blocks().isEmpty(), "The provider must not see the incomplete block");
      try (var storage = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, MAP_HEADER_SIZE, SEGMENT_SIZE)) {
        assertTrue(storage.isEmpty());
        storage.header();
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
        try (var storage = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, MAP_HEADER_SIZE, SEGMENT_SIZE)) {
          assertNotNull(storage);
        }
      });
    }
  }

  @Test
  void headerSizeIsFixedAtConstruction(@TempDir @NotNull Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var lookupBlocks = lookupBlocks(database.openStore("map", 1));
      assertThrows(IllegalArgumentException.class, () -> {
        try (var invalid = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, 0, SEGMENT_SIZE)) {
          assertNotNull(invalid);
        }
      });
      assertThrows(IllegalArgumentException.class, () -> {
        try (var invalid = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, SEGMENT_SIZE + 1, SEGMENT_SIZE)) {
          assertNotNull(invalid);
        }
      });

      try (var tooSmall = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, MAP_HEADER_SIZE - 1, SEGMENT_SIZE)) {
        assertThrows(IllegalArgumentException.class, () -> {
          try (var map = new ExtendibleHashMapInt32ToInt64(tooSmall)) {
            assertNotNull(map);
          }
        });
        assertTrue(tooSmall.isEmpty(), "An incompatible map must not allocate the header");
      }

      try (var larger = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, MAP_HEADER_SIZE + Integer.BYTES, SEGMENT_SIZE);
           var map = new ExtendibleHashMapInt32ToInt64(larger)) {
        assertTrue(map.isEmpty(), "The map must accept a header that is larger than its required fields");
        map.closeAndClean();
      }

      try (var storage = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, MAP_HEADER_SIZE, SEGMENT_SIZE)) {
        assertEquals(MAP_HEADER_SIZE, storage.headerSize());
        var header = storage.header();
        assertEquals(MAP_HEADER_SIZE, header.byteSize());
        header.set(JAVA_INT, 0, 42);
        assertEquals(42, storage.header().get(JAVA_INT, 0));

        storage.clear();
        assertTrue(storage.isEmpty());
        assertEquals(MAP_HEADER_SIZE, storage.header().byteSize());
      }
    }
  }

  @Test
  void storageDataRequiresHeader(@TempDir @NotNull Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var lookupBlocks = lookupBlocks(database.openStore("map", 1));
      try (var storage = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, MAP_HEADER_SIZE, SEGMENT_SIZE)) {
        assertThrows(IllegalStateException.class, storage::segmentsTable);
        assertThrows(IllegalStateException.class, () -> storage.segment(0));
        assertThrows(IllegalStateException.class, () -> storage.allocateSegment(0));

        storage.header().set(JAVA_INT, 0, 42);
        storage.segmentsTable().set(JAVA_INT, 0, 84);
        storage.allocateSegment(0).set(JAVA_INT, 0, 73);
        assertEquals(2, lookupBlocks.blocks().size());
      }
      try (var reopened = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, MAP_HEADER_SIZE, SEGMENT_SIZE)) {
        assertEquals(42, reopened.header().get(JAVA_INT, 0));
        assertEquals(84, reopened.segmentsTable().get(JAVA_INT, 0));
        assertEquals(73, reopened.segment(0).get(JAVA_INT, 0));
      }
    }
  }

  @Test
  void headerAndSegmentsTable_UseOneBlock(@TempDir @NotNull Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var lookupBlocks = lookupBlocks(database.openStore("map", 1));
      try (var storage = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, MAP_HEADER_SIZE, SEGMENT_SIZE)) {
        storage.header();
      }
      try (var reopened = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, MAP_HEADER_SIZE, SEGMENT_SIZE)) {
        reopened.header();
        reopened.segmentsTable().set(JAVA_INT, 0, 84);
        assertEquals(1, lookupBlocks.blocks().size(), "The header and segments table must use one block");
      }
      try (var reopened = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, MAP_HEADER_SIZE, SEGMENT_SIZE)) {
        reopened.header();
        assertEquals(84, reopened.segmentsTable().get(JAVA_INT, 0));
      }
    }
  }

  @Test
  void segmentsWithoutHeader_AreRejected(@TempDir @NotNull Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var lookupBlocks = lookupBlocks(database.openStore("map", 1));
      allocatePublishedSegmentThreeBlock(lookupBlocks);
      assertThrows(CorruptedException.class, () -> {
        try (var storage = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, MAP_HEADER_SIZE, SEGMENT_SIZE)) {
          assertNotNull(storage);
        }
      });
    }
  }

  @Test
  void duplicateMetadataBlock_IsRejected(@TempDir @NotNull Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var lookupBlocks = lookupBlocks(database.openStore("map", 1));
      allocatePublishedMetadataBlock(lookupBlocks);
      allocatePublishedMetadataBlock(lookupBlocks);
      assertThrows(CorruptedException.class, () -> {
        try (var storage = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, MAP_HEADER_SIZE, SEGMENT_SIZE)) {
          assertNotNull(storage);
        }
      });
    }
  }

  @Test
  void shortMetadataPayload_IsRejected(@TempDir @NotNull Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var lookupBlocks = lookupBlocks(database.openStore("map", 1));
      var block = lookupBlocks.allocate(ExtendibleHashMapBlockLayout.BLOCK_HEADER_SIZE + Integer.BYTES);
      ExtendibleHashMapBlockLayout.initializeHeaderAndSegmentsTable(block.payload());
      block.activate();
      assertThrows(CorruptedException.class, () -> {
        try (var storage = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, MAP_HEADER_SIZE, SEGMENT_SIZE)) {
          assertNotNull(storage);
        }
      });
    }
  }

  @Test
  void failedMetadataAllocationDiscardsTheBlock() throws Exception {
    LookupBlocks.Block block = new LookupBlocks.Block() {
      private BlocksStore.Block.LifecycleState state = ALLOCATED;
      private final MemorySegment content = MemorySegment.ofArray(new int[1]);

      @Override public int id() { return 1; }
      @Override public @NotNull BlocksStore.Block.LifecycleState state() { return state; }
      @Override public @NotNull MemorySegment payload() { return content; }
      @Override public void activate() { state = ACTIVE; }
      @Override public void discard() { state = RETIRED; }
      @Override public void seal() { }
      @Override public void retire() { state = RETIRED; }
    };
    LookupBlocks lookupBlocks = new LookupBlocks() {
      @Override public @NotNull List<Block> blocks() { return List.of(); }
      @Override public @NotNull Block allocate(int minimumPayloadLength) { return block; }
      @Override public void retireAll() { }
      @Override public void flush() { }
    };

    try (var storage = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks, MAP_HEADER_SIZE, SEGMENT_SIZE)) {
      assertThrows(CorruptedException.class, () -> storage.header());
      assertEquals(RETIRED, block.state(), "An incomplete header block must be discarded");
      assertTrue(storage.isEmpty());
    }
  }

  @Test
  void clear_RetiresOnlyOwnedRoleBlocks(@TempDir @NotNull Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var store = database.openStore("map", 1);
      var entriesBlock = store.allocateBlock(DurableMapBlockRole.ENTRIES.persistentCode(), SEGMENT_SIZE);
      entriesBlock.activate();
      try (var storage = new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks(store), MAP_HEADER_SIZE, SEGMENT_SIZE)) {
        storage.header();
        storage.segmentsTable();
        var lookupBlock = store.blocks().getLast();

        storage.clear();

        assertTrue(storage.isEmpty());
        assertEquals(ACTIVE, entriesBlock.state());
        assertEquals(RETIRED, lookupBlock.state());
      }
    }
  }

  private static @NotNull ExtendibleHashMapInt32ToInt64 openMap(@NotNull BlocksStore store) throws Exception {
    return new ExtendibleHashMapInt32ToInt64(
      new ExtendibleHashMapStorageOverLookupBlocks(lookupBlocks(store), MAP_HEADER_SIZE, SEGMENT_SIZE)
    );
  }

  private static @NotNull LookupBlocks lookupBlocks(@NotNull BlocksStore store) throws Exception {
    return DurableMapBlockCatalog.open(store).lookupBlocks(
      ExtendibleHashMapStorageOverLookupBlocks.IMPLEMENTATION_ID,
      ExtendibleHashMapStorageOverLookupBlocks.INITIAL_GENERATION
    );
  }

  private static void allocatePublishedSegmentThreeBlock(@NotNull LookupBlocks lookupBlocks) throws Exception {
    var block = lookupBlocks.allocate(SEGMENT_SIZE + ExtendibleHashMapBlockLayout.BLOCK_HEADER_SIZE);
    ExtendibleHashMapBlockLayout.initializeSegmentIndex(block.payload(), 3);
    block.activate();
  }

  private static void allocatePublishedMetadataBlock(@NotNull LookupBlocks lookupBlocks) throws Exception {
    var block = lookupBlocks.allocate(
      ExtendibleHashMapBlockLayout.headerAndSegmentsTableBlockPayloadSize(MAP_HEADER_SIZE, SEGMENT_SIZE)
    );
    ExtendibleHashMapBlockLayout.initializeHeaderAndSegmentsTable(block.payload());
    block.activate();
  }

  private static long valueForKey(int key) {
    return ((long)key << Integer.SIZE) | Integer.toUnsignedLong(~key);
  }
}
