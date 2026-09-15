// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.appendonlylog;

import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AppendOnlyLogOverBlockTest {
  private static final int CHUNK_SIZE = 1024 * 1024;
  private static final int ARBITRARY_BLOCK_ROLE = 42;
  private static final int BLOCK_CONTENT_LENGTH = 64;

  @Test
  public void createActivatesAllocatedBlock() {
    var block = new TestBlock(BLOCK_CONTENT_LENGTH);

    var log = AppendOnlyLogOverBlock.create(block);

    assertSame(block, log.block());
    assertEquals(BlocksStore.Block.LifecycleState.ACTIVE, block.state());
  }

  @Test
  public void failedCreateDiscardsAllocatedBlock() {
    var contentLength = AppendOnlyLogOverBlock.minimumBlockContentLengthFor(0) - 1;
    var block = new TestBlock(contentLength);

    assertThrows(IllegalArgumentException.class, () -> AppendOnlyLogOverBlock.create(block));

    assertEquals(BlocksStore.Block.LifecycleState.RETIRED, block.state());
  }

  @Test
  public void packedHeaderRecoversPayloadLengthForEachPaddingSize(@TempDir Path databaseDirectory) throws Exception {
    assertEquals(Integer.BYTES, RecordHeaderLayout.HEADER_SIZE);
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var store = database.openStore("log", 1);
      var block = store.allocateBlock(ARBITRARY_BLOCK_ROLE, BLOCK_CONTENT_LENGTH);
      var log = AppendOnlyLogOverBlock.create(block);

      for (int payloadLength = 0; payloadLength < RecordHeaderLayout.RECORD_ALIGNMENT; payloadLength++) {
        var recordOffset = log.append(payloadLength, _ -> { });
        assertEquals(payloadLength, log.read(recordOffset).byteSize(), "The packed header must preserve the payload length");
      }
    }
  }

  @Test
  public void packedHeaderUsesTheDocumentedBitFormat() {
    var segment = MemorySegment.ofArray(new int[1]);
    var header = RecordHeaderLayout.RecordHeader.allocated(1);

    assertFalse(header.isCommitted());
    assertFalse(header.isTombstone());
    assertFalse(header.hasLink());
    assertEquals(3, header.paddingSize());
    assertEquals(8, header.totalLength());
    assertEquals(1, header.payloadLength());

    header.writeAllocatedTo(segment);
    assertEquals(0b10_011_000, (int)RecordHeaderLayout.PACKED_LENGTH_AND_FLAGS_HANDLE.get(segment, 0L));

    header.publishCommittedTo(segment);
    assertTrue(header.isCommitted());
    assertEquals(0b10_011_001, (int)RecordHeaderLayout.PACKED_LENGTH_AND_FLAGS_HANDLE.get(segment, 0L));
  }

  @Test
  public void committedPayloadSurvivesReopening(@TempDir Path databaseDirectory) throws Exception {
    var factory = new BlocksDatabaseFactory(CHUNK_SIZE);
    var expected = new byte[]{1, 2, 3, 4, 5};
    int blockId;
    int recordOffset;
    try (var database = factory.open(databaseDirectory)) {
      var store = database.openStore("log", 1);
      var block = store.allocateBlock(ARBITRARY_BLOCK_ROLE, BLOCK_CONTENT_LENGTH);
      var log = AppendOnlyLogOverBlock.create(block);

      recordOffset = log.append(expected.length, payload -> payload.copyFrom(MemorySegment.ofArray(expected)));
      blockId = block.id();
      assertArrayEquals(expected, log.read(recordOffset).toArray(ValueLayout.JAVA_BYTE));
    }

    try (var database = factory.open(databaseDirectory)) {
      var store = database.findStore("log");
      assertNotNull(store);
      var block = store.findBlock(blockId);
      assertNotNull(block);
      var log = AppendOnlyLogOverBlock.open(block);

      assertArrayEquals(expected, log.read(recordOffset).toArray(ValueLayout.JAVA_BYTE));
    }
  }

  @Test
  public void failedAppendLeavesLifecycleDecisionToTheOwner(@TempDir Path databaseDirectory) throws Exception {
    try (var database = new BlocksDatabaseFactory(CHUNK_SIZE).open(databaseDirectory)) {
      var store = database.openStore("log", 1);
      var block = store.allocateBlock(ARBITRARY_BLOCK_ROLE, BLOCK_CONTENT_LENGTH);
      var log = AppendOnlyLogOverBlock.create(block);

      var failure = assertThrows(IOException.class, () -> log.append(8, _ -> {
        throw new IOException("simulated failure");
      }));

      assertEquals("simulated failure", failure.getMessage());
      assertTrue(log.hasIncompleteAllocation());
      assertEquals(BlocksStore.Block.LifecycleState.ACTIVE, block.state(), "The block owner must decide its lifecycle state");
      assertThrows(IllegalStateException.class, () -> log.append(1, _ -> { }));
    }
  }

  private static final class TestBlock implements BlocksStore.Block {
    private final MemorySegment content;
    private LifecycleState state = LifecycleState.ALLOCATED;

    private TestBlock(int contentLength) {
      content = MemorySegment.ofArray(new int[(contentLength + Integer.BYTES - 1) / Integer.BYTES]).asSlice(0, contentLength);
    }

    @Override
    public int id() {
      return 1;
    }

    @Override
    public int role() {
      return ARBITRARY_BLOCK_ROLE;
    }

    @Override
    public @NotNull LifecycleState state() {
      return state;
    }

    @Override
    public @NotNull MemorySegment content() {
      return content;
    }

    @Override
    public void activate() {
      transition(LifecycleState.ALLOCATED, LifecycleState.ACTIVE);
    }

    @Override
    public void discard() {
      transition(LifecycleState.ALLOCATED, LifecycleState.RETIRED);
    }

    @Override
    public void seal() {
      transition(LifecycleState.ACTIVE, LifecycleState.SEALED);
    }

    @Override
    public void retire() {
      transition(LifecycleState.SEALED, LifecycleState.RETIRED);
    }

    private void transition(LifecycleState expected, LifecycleState target) {
      if (state != expected) {
        throw new IllegalStateException("Block state is " + state + ", expected " + expected);
      }
      state = target;
    }
  }
}
