// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.database.impl.layout.BlockHeaderLayout;
import com.intellij.platform.util.io.storages.database.impl.layout.ChunkHeaderLayout;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.util.io.CorruptedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.nio.ByteOrder.nativeOrder;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("SuspiciousPackagePrivateAccess")
public class DatabaseBlockTest {
  private static final int CHUNK_SIZE = 1024 * 1024;
  private static final long DATABASE_ID = 42;
  private static final int CHUNK_ID = 1;
  private static final int STORE_ID = 7;
  private static final int BLOCK_ID = 37;
  private static final int METADATA_ROLE = 0xFE;
  private static final int VALUE_ROLE = 3;
  private static final int BLOCK_LENGTH = 64 * 1024;

  @Test
  public void blockHeaderSurvivesReopening(@TempDir Path directory) throws Exception {
    var chunkPath = directory.resolve("chunk.dat");
    try (var chunk = DatabaseChunk.create(chunkPath, DATABASE_ID, CHUNK_ID, CHUNK_SIZE)) {
      var block = chunk.allocateBlock(BLOCK_ID, STORE_ID, METADATA_ROLE, BLOCK_LENGTH);

      assertEquals(ChunkHeaderLayout.HEADER_SIZE, block.blockOffset());
      assertEquals(CHUNK_ID, block.chunkId());
      assertEquals(BLOCK_ID, block.blockId());
      assertEquals(BLOCK_LENGTH, block.blockLength());
      assertEquals(STORE_ID, block.storeId());
      assertEquals(METADATA_ROLE, block.role());
      assertEquals(BlocksStore.Block.LifecycleState.ACTIVE, block.state());
      assertEquals(BLOCK_LENGTH - BlockHeaderLayout.HEADER_SIZE, block.contentSegment().byteSize());
      assertEquals(ChunkHeaderLayout.HEADER_SIZE + BLOCK_LENGTH, chunk.committedTail());
      assertEquals(chunk.committedTail(), chunk.allocatedTail());
      chunk.flush();
    }

    try (var chunk = DatabaseChunk.open(chunkPath, DATABASE_ID, CHUNK_ID, CHUNK_SIZE, true)) {
      var block = chunk.blocks().getFirst();
      assertEquals(BLOCK_ID, block.blockId());
      assertEquals(STORE_ID, block.storeId());
      assertEquals(METADATA_ROLE, block.role());
      assertEquals(BlocksStore.Block.LifecycleState.ACTIVE, block.state());
      assertEquals(BLOCK_LENGTH, block.blockLength());
      assertEquals(ChunkHeaderLayout.HEADER_SIZE + BLOCK_LENGTH, chunk.committedTail());
      assertEquals(chunk.committedTail(), chunk.allocatedTail());
    }
  }

  @Test
  public void openingDiscardsUncommittedBlockTail(@TempDir Path directory) throws Exception {
    var chunkPath = directory.resolve("chunk.dat");
    try (var chunk = DatabaseChunk.create(chunkPath, DATABASE_ID, CHUNK_ID, CHUNK_SIZE)) {
      chunk.allocateBlock(BLOCK_ID, STORE_ID, VALUE_ROLE, BLOCK_LENGTH);
      chunk.flush();
    }

    moveCommittedTailToChunkHeader(chunkPath);

    try (var chunk = DatabaseChunk.open(chunkPath, DATABASE_ID, CHUNK_ID, CHUNK_SIZE, true)) {
      assertEquals(0, chunk.blocks().size());
      assertEquals(ChunkHeaderLayout.HEADER_SIZE, chunk.committedTail());
      assertEquals(chunk.committedTail(), chunk.allocatedTail());
    }
  }

  @Test
  public void openingRejectsUnpublishedBlockInsideCommittedPrefix(@TempDir Path directory) throws Exception {
    var chunkPath = directory.resolve("chunk.dat");
    try (var chunk = DatabaseChunk.create(chunkPath, DATABASE_ID, CHUNK_ID, CHUNK_SIZE)) {
      chunk.allocateBlock(BLOCK_ID, STORE_ID, VALUE_ROLE, BLOCK_LENGTH);
      chunk.flush();
    }

    setBlockStateToUnpublished(chunkPath);

    assertThrows(CorruptedException.class, () -> {
      var chunk = DatabaseChunk.open(chunkPath, DATABASE_ID, CHUNK_ID, CHUNK_SIZE, true);
      chunk.close();
    });
  }

  @Test
  public void blockStateTransitionsSurviveReopening(@TempDir Path directory) throws Exception {
    var chunkPath = directory.resolve("chunk.dat");
    try (var chunk = DatabaseChunk.create(chunkPath, DATABASE_ID, CHUNK_ID, CHUNK_SIZE)) {
      var block = chunk.allocateBlock(BLOCK_ID, STORE_ID, VALUE_ROLE, BLOCK_LENGTH);
      block.seal();
      block.retire();
      assertEquals(BlocksStore.Block.LifecycleState.RETIRED, block.state());
      chunk.flush();
    }

    try (var chunk = DatabaseChunk.open(chunkPath, DATABASE_ID, CHUNK_ID, CHUNK_SIZE, true)) {
      var block = chunk.blocks().getFirst();
      assertEquals(BlocksStore.Block.LifecycleState.RETIRED, block.state());
    }
  }

  @Test
  public void blockStateRejectsSkippedAndRepeatedTransitions(@TempDir Path directory) throws Exception {
    var chunkPath = directory.resolve("chunk.dat");
    try (var chunk = DatabaseChunk.create(chunkPath, DATABASE_ID, CHUNK_ID, CHUNK_SIZE)) {
      var block = chunk.allocateBlock(BLOCK_ID, STORE_ID, VALUE_ROLE, BLOCK_LENGTH);

      assertThrows(IllegalStateException.class, block::retire, "An active block must be sealed before retirement");
      block.seal();
      assertThrows(IllegalStateException.class, block::seal, "Sealing must publish exactly one state transition");
      block.retire();
      assertThrows(IllegalStateException.class, block::retire, "Retirement must publish exactly one state transition");
    }
  }

  private static void moveCommittedTailToChunkHeader(Path path) throws Exception {
    var buffer = ByteBuffer.allocate(Long.BYTES).order(nativeOrder()).putLong(ChunkHeaderLayout.HEADER_SIZE).flip();
    try (var channel = FileChannel.open(path, WRITE)) {
      channel.write(buffer, ChunkHeaderLayout.LAYOUT.byteOffset(groupElement("committedTail")));
      channel.force(true);
    }
  }

  @ParameterizedTest
  @EnumSource(BlocksStore.Block.LifecycleState.class)
  public void storeDropRetiresEveryState(BlocksStore.Block.LifecycleState initialState, @TempDir Path directory) throws Exception {
    var chunkPath = directory.resolve("chunk.dat");
    try (var chunk = DatabaseChunk.create(chunkPath, DATABASE_ID, CHUNK_ID, CHUNK_SIZE)) {
      var block = chunk.allocateBlock(BLOCK_ID, STORE_ID, VALUE_ROLE, BLOCK_LENGTH);
      if (initialState != BlocksStore.Block.LifecycleState.ACTIVE) {
        block.seal();
      }
      if (initialState == BlocksStore.Block.LifecycleState.RETIRED) {
        block.retire();
      }

      block.retireForStoreDrop();
      block.retireForStoreDrop();

      assertEquals(BlocksStore.Block.LifecycleState.RETIRED, block.state(), "Recovery must be able to repeat retirement");
      assertThrows(IllegalStateException.class, block::seal, "Dropping a store must not allow a block to become live again");
      chunk.flush();
    }
    try (var chunk = DatabaseChunk.open(chunkPath, DATABASE_ID, CHUNK_ID, CHUNK_SIZE, true)) {
      assertEquals(BlocksStore.Block.LifecycleState.RETIRED, chunk.blocks().getFirst().state());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 99})
  public void storeDropRejectsInvalidBlockState(int stateCode) {
    try (var arena = Arena.ofConfined()) {
      var header = arena.allocate(BlockHeaderLayout.LAYOUT);
      BlockHeaderLayout.STATE_HANDLE.set(header, 0L, stateCode);

      assertThrows(IllegalStateException.class, () -> BlockHeaderLayout.retireForStoreDrop(header));
      assertEquals(stateCode, (int)BlockHeaderLayout.STATE_HANDLE.get(header, 0L), "Retirement must not hide an invalid header");
    }
  }

  private static void setBlockStateToUnpublished(Path path) throws Exception {
    var buffer = ByteBuffer.allocate(Integer.BYTES).order(nativeOrder()).putInt(BlockHeaderLayout.UNPUBLISHED_STATE_CODE).flip();
    try (var channel = FileChannel.open(path, WRITE)) {
      channel.write(buffer, ChunkHeaderLayout.HEADER_SIZE + BlockHeaderLayout.LAYOUT.byteOffset(groupElement("state")));
      channel.force(true);
    }
  }
}
