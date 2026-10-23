// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.appenablerecords;

import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.storages.BlockSizingPolicy;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;

import static com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.ENTRIES;
import static com.intellij.util.io.IOUtil.KiB;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies append and recovery for records in durable map ENTRIES blocks. */
public class RecordStorageOverBlocksTest {
  private static final int CHUNK_SIZE = 1024 * 1024;
  private static final int ENTRIES_BLOCK_CONTENT_LENGTH = 64;

  @Test
  public void adaptivePolicyChangesSizeAtEachCapacityThreshold() {
    var policy = RecordStorageOverBlocks.ADAPTIVE_BLOCK_SIZE_POLICY;
    var defaultBlockSize = 64 * KiB;

    assertEquals(defaultBlockSize, policy.preferredBlockSize(128L * defaultBlockSize - 1));
    assertEquals(2 * defaultBlockSize, policy.preferredBlockSize(128L * defaultBlockSize));
    assertEquals(2 * defaultBlockSize, policy.preferredBlockSize(256L * defaultBlockSize - 1));
    assertEquals(4 * defaultBlockSize, policy.preferredBlockSize(256L * defaultBlockSize));
    assertEquals(4 * defaultBlockSize, policy.preferredBlockSize(512L * defaultBlockSize - 1));
    assertEquals(8 * defaultBlockSize, policy.preferredBlockSize(512L * defaultBlockSize));
  }

  @Test
  public void adaptiveSizeUsesSealedBlocksAfterReopen(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    long firstRecordRef;
    var policy = (BlockSizingPolicy)(liveCapacityBytes -> liveCapacityBytes >= 64 * KiB ? 128 * KiB : 64 * KiB);
    try (var database = factory.open(databaseDirectory)) {
      var catalog = DurableMapBlockCatalog.open(database.openStore("map", 1));
      var records = RecordStorageOverBlocks.open(catalog.appendableRecordBlocks(ENTRIES), true, policy);
      firstRecordRef = append(records, new byte[64 * KiB - 100]);
      append(records, new byte[128 * KiB - 100]);
      assertEquals(BlocksStore.Block.LifecycleState.SEALED, catalog.blocks(ENTRIES).getFirst().state());
    }

    try (var database = factory.open(databaseDirectory)) {
      var catalog = DurableMapBlockCatalog.open(database.findStore("map"));
      var records = RecordStorageOverBlocks.open(catalog.appendableRecordBlocks(ENTRIES), true, policy);
      assertEquals(64 * KiB - 100, records.read(firstRecordRef).byteSize(), "The small block must remain readable");
      append(records, new byte[128 * KiB - 100]);
      assertEquals(128 * KiB, catalog.blocks(ENTRIES).getLast().content().byteSize(),
                   "The reopened storage must count the sealed block");
    }
  }

  @Test
  public void retirementReducesThePreferredSize(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var catalog = DurableMapBlockCatalog.open(database.openStore("map", 1));
      var policy = (BlockSizingPolicy)(liveCapacityBytes -> liveCapacityBytes >= 3 * 64 * KiB ? 128 * KiB : 64 * KiB);
      var records = RecordStorageOverBlocks.open(catalog.appendableRecordBlocks(ENTRIES), true, policy);
      for (int i = 0; i < 3; i++) {
        append(records, new byte[64 * KiB - 100]);
      }
      append(records, new byte[128 * KiB - 100]);
      assertEquals(128 * KiB, catalog.blocks(ENTRIES).getLast().content().byteSize());

      var blocksToRetire = catalog.blocks(ENTRIES).subList(0, 3);
      for (var block : blocksToRetire) {
        records.retireSealedBlock(block);
      }
      append(records, new byte[64 * KiB - 100]);
      assertEquals(64 * KiB, catalog.blocks(ENTRIES).getLast().content().byteSize(),
                   "The next block must use the lower step after retirement");
    }
  }

  @Test
  public void oversizedRecordContributesAtMost512KiB(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var catalog = DurableMapBlockCatalog.open(database.openStore("map", 1));
      var policy = (BlockSizingPolicy)(liveCapacityBytes -> liveCapacityBytes == 512 * KiB ? 128 * KiB : 64 * KiB);
      var records = RecordStorageOverBlocks.open(catalog.appendableRecordBlocks(ENTRIES), true, policy);

      append(records, new byte[600 * KiB]);
      append(records, new byte[128 * KiB - 100]);

      assertTrue(catalog.blocks(ENTRIES).getFirst().content().byteSize() > 512 * KiB,
                 "The large record must get enough space");
      assertEquals(128 * KiB, catalog.blocks(ENTRIES).getLast().content().byteSize(),
                   "One oversized record must contribute at most 512 KiB");
    }
  }

  @Test
  public void appendReturnsReadablePayload(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var blockCatalog = DurableMapBlockCatalog.open(database.openStore("map", 1));
      var records = openRecords(blockCatalog, /*appendOnly: */ true);
      var expected = "record payload".getBytes(StandardCharsets.UTF_8);

      var pointer = append(records, expected);

      assertArrayEquals(expected, records.read(pointer).toArray(ValueLayout.JAVA_BYTE));
      assertTrue(records.read(pointer).isReadOnly());
    }
  }

  @Test
  public void recordReferencePacksBothInt32Components() {
    var blockId = 0x7654_3210;
    var recordOffset = 0xfedc_ba98;

    var recordRef = RecordStorageOverBlocks.recordRef(blockId, recordOffset);

    assertEquals(blockId, RecordStorageOverBlocks.blockId(recordRef));
    assertEquals(recordOffset, RecordStorageOverBlocks.recordOffset(recordRef));
    assertEquals(0x7654_3210_fedc_ba98L, recordRef);
  }

  @Test
  public void reopeningReadsCommittedRecords(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    var expected = "persistent payload".getBytes(StandardCharsets.UTF_8);
    long recordRef;
    try (var database = factory.open(databaseDirectory)) {
      var blockCatalog = DurableMapBlockCatalog.open(database.openStore("map", 1));
      recordRef = append(
        openRecords(blockCatalog, /*appendOnly: */ true), expected
      );
    }

    try (var database = factory.open(databaseDirectory)) {
      var store = database.findStore("map");
      assertNotNull(store);
      var records = openRecords(DurableMapBlockCatalog.open(store), /*appendOnly: */ true);

      assertArrayEquals(expected, records.read(recordRef).toArray(ValueLayout.JAVA_BYTE));
    }
  }

  @Test
  public void appendUsesANewBlockWhenTheCurrentBlockIsFull(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var blockCatalog = DurableMapBlockCatalog.open(database.openStore("map", 1));
      var records = openRecords(blockCatalog, /*appendOnly: */ true);

      var first = append(records, new byte[40]);
      var second = append(records, new byte[8]);

      assertNotEquals(
        RecordStorageOverBlocks.blockId(first),
        RecordStorageOverBlocks.blockId(second),
        "The full block must not accept the second record: " + first + ", " + second
      );
      assertEquals(2, blockCatalog.blocks(ENTRIES).size());
      assertEquals(BlocksStore.Block.LifecycleState.SEALED, blockCatalog.blocks(ENTRIES).getFirst().state());
      assertEquals(BlocksStore.Block.LifecycleState.ACTIVE, blockCatalog.blocks(ENTRIES).getLast().state());
    }
  }

  @Test
  public void modifiableRecordsRemainActiveAndWritableAfterReopen(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    long firstRecordRef;
    int currentBlockId;
    try (var database = factory.open(databaseDirectory)) {
      var blockCatalog = DurableMapBlockCatalog.open(database.openStore("map", 1));
      var records = openRecords(blockCatalog, /*appendOnly: */ false);

      firstRecordRef = append(records, new byte[40]);
      var secondRecordRef = append(records, new byte[8]);
      currentBlockId = RecordStorageOverBlocks.blockId(secondRecordRef);
      records.updatePayload(firstRecordRef, payload -> payload.set(ValueLayout.JAVA_BYTE, 0, (byte)41));

      assertEquals(2, blockCatalog.blocks(ENTRIES).size());
      for (var block : blockCatalog.blocks(ENTRIES)) {
        assertEquals(BlocksStore.Block.LifecycleState.ACTIVE, block.state());
      }
      assertEquals(41, records.read(firstRecordRef).get(ValueLayout.JAVA_BYTE, 0));
    }

    try (var database = factory.open(databaseDirectory)) {
      var store = database.findStore("map");
      assertNotNull(store);
      var records = openRecords(DurableMapBlockCatalog.open(store), /*appendOnly: */ false);

      records.updatePayload(firstRecordRef, payload -> payload.set(ValueLayout.JAVA_BYTE, 0, (byte)42));
      var appendedAfterReopen = append(records, new byte[]{1});

      assertEquals(42, records.read(firstRecordRef).get(ValueLayout.JAVA_BYTE, 0));
      assertEquals(currentBlockId, RecordStorageOverBlocks.blockId(appendedAfterReopen),
                   "Append after reopen must continue in the newest active block");
    }
  }

  @Test
  public void appendOnlyRecordsRejectPayloadUpdates(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var blockCatalog = DurableMapBlockCatalog.open(database.openStore("map", 1));
      var records = openRecords(blockCatalog, /*appendOnly: */ true);
      var recordRef = append(records, new byte[]{1});

      assertThrows(UnsupportedOperationException.class,
                   () -> records.updatePayload(recordRef, payload -> payload.set(ValueLayout.JAVA_BYTE, 0, (byte)2)));
    }
  }

  @Test
  public void failedAppendDoesNotSealModifiableRecords(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var blockCatalog = DurableMapBlockCatalog.open(database.openStore("map", 1));
      var records = openRecords(blockCatalog, /*appendOnly: */ false);
      var committedRecordRef = append(records, new byte[]{1});

      assertThrows(IOException.class, () -> records.append(16, _ -> {
        throw new IOException("simulated write failure");
      }));
      records.updatePayload(committedRecordRef, payload -> payload.set(ValueLayout.JAVA_BYTE, 0, (byte)2));
      var nextRecordRef = append(records, new byte[]{3});

      assertEquals(BlocksStore.Block.LifecycleState.ACTIVE, blockCatalog.blocks(ENTRIES).getFirst().state(),
                   "A failed append must keep committed records modifiable");
      assertNotEquals(RecordStorageOverBlocks.blockId(committedRecordRef), RecordStorageOverBlocks.blockId(nextRecordRef),
                      "A block with an incomplete allocation must not accept another record");
      assertEquals(2, records.read(committedRecordRef).get(ValueLayout.JAVA_BYTE, 0));
    }
  }

  @Test
  public void recordTraversalStopsBeforeReadingTheNextBlock(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var blockCatalog = DurableMapBlockCatalog.open(database.openStore("map", 1));
      var records = openRecords(blockCatalog, /*appendOnly: */ true);
      var first = append(records, new byte[40]);
      append(records, new byte[40]);

      var visited = new ArrayList<Long>();
      var allRecordsProcessed = records.forEachCommittedRecord((recordRef, _) -> {
        visited.add(recordRef);
        return false;
      });
      assertFalse(allRecordsProcessed);
      assertEquals(List.of(first), visited);
    }
  }

  @Test
  public void readsRecordsWhileNewBlocksAreAllocated(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory);
         var executor = Executors.newFixedThreadPool(2)) {
      var blockCatalog = DurableMapBlockCatalog.open(database.openStore("map", 1));
      var records = openRecords(blockCatalog, /*appendOnly: */ true);
      var recordsToRead = new ArrayBlockingQueue<ExpectedRecord>(1);
      var recordCount = 64;

      var reader = executor.submit(() -> {
        for (int i = 0; i < recordCount; i++) {
          var expected = recordsToRead.take();
          var actual = records.read(expected.pointer()).toArray(ValueLayout.JAVA_BYTE);
          assertArrayEquals(expected.payload(), actual);
        }
        return null;
      });
      var writer = executor.submit(() -> {
        for (int i = 0; i < recordCount; i++) {
          var payload = new byte[40];
          payload[0] = (byte)i;
          recordsToRead.put(new ExpectedRecord(append(records, payload), payload));
        }
        return null;
      });

      writer.get();
      reader.get();
    }
  }

  @Test
  public void reopeningDiscardsAnIncompleteAllocation(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    int incompleteBlockId;
    try (var database = factory.open(databaseDirectory)) {
      var blockCatalog = DurableMapBlockCatalog.open(database.openStore("map", 1));
      var records = openRecords(blockCatalog, /*appendOnly: */ true);
      var failure = assertThrows(IOException.class, () -> records.append(16, payload -> {
        payload.set(ValueLayout.JAVA_BYTE, 0, (byte)1);
        throw new IOException("simulated write failure");
      }));
      assertEquals("simulated write failure", failure.getMessage());
      incompleteBlockId = blockCatalog.blocks(ENTRIES).getFirst().id();
    }

    try (var database = factory.open(databaseDirectory)) {
      var store = database.findStore("map");
      assertNotNull(store);
      var blockCatalog = DurableMapBlockCatalog.open(store);
      var records = openRecords(blockCatalog, /*appendOnly: */ true);

      assertThrows(CorruptedException.class,
                   () -> records.read(RecordStorageOverBlocks.recordRef(incompleteBlockId, 16)));
      var committed = append(records, new byte[]{42});
      assertNotEquals(incompleteBlockId, RecordStorageOverBlocks.blockId(committed));
      assertArrayEquals(new byte[]{42}, records.read(committed).toArray(ValueLayout.JAVA_BYTE));
    }
  }

  private static long append(@NotNull RecordStorageOverBlocks records, byte @NotNull [] payload) throws IOException {
    return records.append(payload.length, target -> target.copyFrom(MemorySegment.ofArray(payload)));
  }

  private static @NotNull RecordStorageOverBlocks openRecords(@NotNull DurableMapBlockCatalog blockCatalog,
                                                               boolean appendOnly) throws IOException {
    return RecordStorageOverBlocks.open(
      blockCatalog.appendableRecordBlocks(ENTRIES), appendOnly, ENTRIES_BLOCK_CONTENT_LENGTH
    );
  }

  private record ExpectedRecord(long pointer, byte @NotNull [] payload) {
  }
}
