// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.impl;

import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog;
import com.intellij.platform.util.io.storages.database.storages.durablemap.RecordStorageOverBlocks;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;

import static com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.DATA;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies append and recovery for records in durable map DATA blocks. */
public class RecordStorageOverBlocksTest {
  private static final int CHUNK_SIZE = 1024 * 1024;
  private static final int DATA_BLOCK_CONTENT_LENGTH = 64;

  @Test
  public void appendReturnsReadablePayload(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var blockCatalog = DurableMapBlockCatalog.open(database.openStore("map", 1));
      var records = RecordStorageOverBlocks.open(blockCatalog, DATA_BLOCK_CONTENT_LENGTH);
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
      recordRef = append(RecordStorageOverBlocks.open(blockCatalog, DATA_BLOCK_CONTENT_LENGTH), expected);
    }

    try (var database = factory.open(databaseDirectory)) {
      var store = database.findStore("map");
      assertNotNull(store);
      var records = RecordStorageOverBlocks.open(DurableMapBlockCatalog.open(store), DATA_BLOCK_CONTENT_LENGTH);

      assertArrayEquals(expected, records.read(recordRef).toArray(ValueLayout.JAVA_BYTE));
    }
  }

  @Test
  public void appendUsesANewBlockWhenTheCurrentBlockIsFull(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var blockCatalog = DurableMapBlockCatalog.open(database.openStore("map", 1));
      var records = RecordStorageOverBlocks.open(blockCatalog, DATA_BLOCK_CONTENT_LENGTH);

      var first = append(records, new byte[40]);
      var second = append(records, new byte[8]);

      assertNotEquals(
        RecordStorageOverBlocks.blockId(first),
        RecordStorageOverBlocks.blockId(second),
        "The full block must not accept the second record: " + first + ", " + second
      );
      assertEquals(2, blockCatalog.blocks(DATA).size());
      assertEquals(BlocksStore.Block.LifecycleState.SEALED, blockCatalog.blocks(DATA).getFirst().state());
      assertEquals(BlocksStore.Block.LifecycleState.ACTIVE, blockCatalog.blocks(DATA).getLast().state());
    }
  }

  @Test
  public void readsRecordsWhileNewBlocksAreAllocated(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory);
         var executor = Executors.newFixedThreadPool(2)) {
      var blockCatalog = DurableMapBlockCatalog.open(database.openStore("map", 1));
      var records = RecordStorageOverBlocks.open(blockCatalog, DATA_BLOCK_CONTENT_LENGTH);
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
      var records = RecordStorageOverBlocks.open(blockCatalog, DATA_BLOCK_CONTENT_LENGTH);
      var failure = assertThrows(IOException.class, () -> records.append(16, payload -> {
        payload.set(ValueLayout.JAVA_BYTE, 0, (byte)1);
        throw new IOException("simulated write failure");
      }));
      assertEquals("simulated write failure", failure.getMessage());
      incompleteBlockId = blockCatalog.blocks(DATA).getFirst().id();
    }

    try (var database = factory.open(databaseDirectory)) {
      var store = database.findStore("map");
      assertNotNull(store);
      var blockCatalog = DurableMapBlockCatalog.open(store);
      var records = RecordStorageOverBlocks.open(blockCatalog, DATA_BLOCK_CONTENT_LENGTH);

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

  private record ExpectedRecord(long pointer, byte @NotNull [] payload) {
  }
}
