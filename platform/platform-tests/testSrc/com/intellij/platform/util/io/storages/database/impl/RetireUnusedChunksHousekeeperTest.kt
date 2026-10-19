// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl

import com.intellij.platform.util.io.storages.database.impl.layout.BlockHeaderLayout
import com.intellij.platform.util.io.storages.database.impl.layout.ChunkHeaderLayout
import com.intellij.util.WaitFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@Suppress("SuspiciousPackagePrivateAccess")
class RetireUnusedChunksHousekeeperTest {
  @Test
  fun `housekeeping retires a sealed chunk with only persisted retired blocks`(@TempDir directory: Path) {
    val firstChunkPath = DatabaseChunks.chunkPath(directory, 1)
    var retiredBlockId = 0
    BlocksDatabaseImpl.open(directory, CHUNK_SIZE, true, false).use { database ->
      val store = database.openStore("store", 1)
      val retiredBlock = store.allocateBlock(1, MAX_BLOCK_CONTENT_SIZE).also { it.activate() }
      retiredBlock.seal()
      retiredBlock.retire()
      retiredBlockId = retiredBlock.id()
      store.allocateBlock(1, MAX_BLOCK_CONTENT_SIZE).activate()
      store.allocateBlock(1, MAX_BLOCK_CONTENT_SIZE).activate()
      database.flush()
    }

    BlocksDatabaseImpl.open(directory, CHUNK_SIZE, true, false).use { database ->
      val store = requireNotNull(database.findStore("store"))
      assertNull(store.findBlock(retiredBlockId), "The store must not retain the persisted retired block")
      val retiredOnlyChunk = database.sealedChunks().single { it.chunkId() == 1 }
      assertTrue(retiredOnlyChunk.blocks().isEmpty(), "The chunk must not retain the persisted retired block")
      assertEquals(1, database.metrics(true).blocks().retiredCurrent(), "Metrics must include the persisted retired block")

      RetireUnusedChunksHousekeeper(database).runHousekeeping { false }

      assertTrue(database.sealedChunks().none { it.chunkId() == 1 }, "Housekeeping must retire the chunk without live blocks")
      assertEquals(0, database.metrics(true).blocks().retiredCurrent(), "Chunk retirement must remove the physical block metric")
      assertTrue(Files.exists(firstChunkPath), "Regular housekeeping must keep the retired chunk file")
    }
  }

  @Test
  fun `housekeeping keeps an active chunk with only retired blocks`(@TempDir directory: Path) {
    val firstChunkPath = DatabaseChunks.chunkPath(directory, 1)
    val secondChunkPath = DatabaseChunks.chunkPath(directory, 2)

    BlocksDatabaseImpl.open(directory, CHUNK_SIZE, true, false).use { blocksDatabase ->
      DurableDatabaseImpl(blocksDatabase).use {
        val store = blocksDatabase.openStore("store", 1)
        val retiredBlock = store.allocateBlock(1, 1)
        retiredBlock.activate()
        retiredBlock.seal()
        retiredBlock.retire()

        RetireUnusedChunksHousekeeper(blocksDatabase).runHousekeeping { false }

        assertTrue(Files.exists(firstChunkPath), "Housekeeping must keep an active chunk")
        assertNull(store.findBlock(retiredBlock.id()), "The store must not find a retired block")

        val nextBlock = store.allocateBlock(1, 1)
        nextBlock.activate()
        assertFalse(Files.exists(secondChunkPath), "The next block must use free space in the active chunk")
      }
    }
  }

  @Test
  fun `housekeeping retires a chunk and startup housekeeping deletes it`(@TempDir directory: Path) {
    val firstChunkPath = DatabaseChunks.chunkPath(directory, 1)
    val secondChunkPath = DatabaseChunks.chunkPath(directory, 2)
    var liveBlockId = 0
    var evictionBlockId = 0

    BlocksDatabaseImpl.open(directory, CHUNK_SIZE, true, false).use { blocksDatabase ->
      DurableDatabaseImpl(blocksDatabase).use {
        val store = blocksDatabase.openStore("store", 1)
        val retiredBlock = store.allocateBlock(1, MAX_BLOCK_CONTENT_SIZE)
        val retiredContent = retiredBlock.content()
        retiredBlock.activate()
        retiredBlock.seal()
        retiredBlock.retire()

        val liveBlock = store.allocateBlock(1, MAX_BLOCK_CONTENT_SIZE)
        liveBlock.activate()
        liveBlockId = liveBlock.id()
        val evictionBlock = store.allocateBlock(1, MAX_BLOCK_CONTENT_SIZE)
        evictionBlock.activate()
        evictionBlockId = evictionBlock.id()

        assertTrue(Files.exists(firstChunkPath), "The retired block must occupy the first chunk")
        assertTrue(Files.exists(secondChunkPath), "The live block must occupy the second chunk")

        RetireUnusedChunksHousekeeper(blocksDatabase).runHousekeeping { true }
        assertTrue(Files.exists(firstChunkPath), "Cancellation before the run must prevent chunk retirement")
        assertNull(store.findBlock(retiredBlock.id()), "The store must hide a retired block before chunk retirement")

        RetireUnusedChunksHousekeeper(blocksDatabase).runHousekeeping { false }

        assertTrue(Files.exists(firstChunkPath), "Regular housekeeping must keep the retired chunk file")
        assertTrue(retiredContent.scope().isAlive, "Regular housekeeping must keep the retired chunk mapping")
        assertTrue(Files.exists(secondChunkPath), "Housekeeping must keep the live chunk")
        assertNull(store.findBlock(retiredBlock.id()), "A retired chunk block must leave the block catalog")
        assertEquals(liveBlockId, store.findBlock(liveBlockId)?.id())
      }
    }

    BlocksDatabaseImpl.open(directory, CHUNK_SIZE, true, false).use { blocksDatabase ->
      val store = blocksDatabase.findStore("store")
      assertEquals(listOf(liveBlockId, evictionBlockId), store?.blocks()?.map { it.id() })
      assertTrue(Files.exists(firstChunkPath), "Opening without drop housekeeping must keep the retired chunk file")
      assertTrue(Files.exists(secondChunkPath), "The live chunk must survive reopening")
    }

    BlocksDatabaseImpl.open(
      directory, CHUNK_SIZE, true, false, listOf(DropRetiredChunksHousekeeper()),
    ).use {
      assertTrue(object : WaitFor(10_000) {
        override fun condition(): Boolean = Files.notExists(firstChunkPath)
      }.isConditionRealized, "Startup housekeeping must delete the retired chunk file")
      assertTrue(Files.exists(secondChunkPath), "Startup housekeeping must keep the live chunk file")
    }
  }

  @Test
  fun `housekeeping checks one chunk at a time and wraps to the smallest candidate chunk id`(@TempDir directory: Path) {
    val chunkPaths = (1..5).associateWith { DatabaseChunks.chunkPath(directory, it) }

    BlocksDatabaseImpl.open(directory, CHUNK_SIZE, true, false).use { blocksDatabase ->
      DurableDatabaseImpl(blocksDatabase).use {
        val store = blocksDatabase.openStore("store", 1)
        val firstRetiredBlock = store.allocateBlock(1, MAX_BLOCK_CONTENT_SIZE).also { it.activate() }
        firstRetiredBlock.seal()
        firstRetiredBlock.retire()
        val secondLiveBlock = store.allocateBlock(1, MAX_BLOCK_CONTENT_SIZE).also { it.activate() }
        val thirdRetiredBlock = store.allocateBlock(1, MAX_BLOCK_CONTENT_SIZE).also { it.activate() }
        thirdRetiredBlock.seal()
        thirdRetiredBlock.retire()
        store.allocateBlock(1, MAX_BLOCK_CONTENT_SIZE).activate()
        store.allocateBlock(1, MAX_BLOCK_CONTENT_SIZE).activate()
        val housekeeper = RetireUnusedChunksHousekeeper(blocksDatabase)

        housekeeper.runHousekeeping { false }
        assertTrue(blocksDatabase.sealedChunks().none { it.chunkId() == 1 }, "The first round must retire the first chunk")
        assertTrue(Files.exists(chunkPaths.getValue(3)), "The first round must not inspect another chunk")

        housekeeper.runHousekeeping { false }
        assertTrue(Files.exists(chunkPaths.getValue(3)), "The second round must inspect the second chunk")

        housekeeper.runHousekeeping { false }
        assertTrue(blocksDatabase.sealedChunks().none { it.chunkId() == 3 }, "The third round must retire the third chunk")

        secondLiveBlock.seal()
        secondLiveBlock.retire()
        housekeeper.runHousekeeping { false }
        assertTrue(blocksDatabase.sealedChunks().none { it.chunkId() == 2 },
                   "After wrapping, the fourth round must inspect the smallest candidate chunk id")
        assertTrue(chunkPaths.values.all { Files.exists(it) }, "Regular housekeeping must not delete chunk files")
      }
    }
  }

  companion object {
    private const val CHUNK_SIZE = 1024
    private val MAX_BLOCK_CONTENT_SIZE = CHUNK_SIZE - ChunkHeaderLayout.HEADER_SIZE - BlockHeaderLayout.HEADER_SIZE
  }
}
