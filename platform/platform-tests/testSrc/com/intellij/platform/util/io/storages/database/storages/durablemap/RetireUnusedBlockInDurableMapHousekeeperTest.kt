// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap

import com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory
import com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ACTIVE
import com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.RETIRED
import com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.SEALED
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.DATA
import com.intellij.platform.util.io.storages.durablemap.DefaultEntryExternalizer
import com.intellij.platform.util.io.storages.intmultimaps.InMemoryIntToMultiLongMap
import com.intellij.platform.util.io.storages.intmultimaps.IntToMultiLongMap
import com.intellij.platform.util.io.storages.intmultimaps.RecordRefIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED
import java.nio.file.Path

class RetireUnusedBlockInDurableMapHousekeeperTest {
  @Test
  fun `sweep keeps every block in a live patch chain`(@TempDir directory: Path) {
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      val store = database.openStore("map", 1)
      val records = RecordStorageOverBlocks.open(DurableMapBlockCatalog.open(store), DATA_BLOCK_SIZE)
      records.append(2 * DATA_BLOCK_SIZE) { payload -> payload.fill(0) }

      val descriptor = stringAsUTF8()
      val entryExternalizer = DefaultEntryExternalizer(descriptor, descriptor)
      val baseWriter = entryExternalizer.writerFor("key", "value")
      val baseRef = records.append(baseWriter.recordSize()) { payload -> baseWriter.write(payload.asByteBuffer()) }
      var headRef = baseRef
      repeat(5) {
        headRef = records.append(DATA_BLOCK_SIZE, headRef) { payload ->
          payload.fill(0)
          payload.set(JAVA_LONG_UNALIGNED, 0, baseRef)
        }
      }

      val index = InMemoryRecordRefIndex()
      index.put(descriptor.getHashCode("key"), headRef)
      DurableMapOverBlocks.open(store, DATA_BLOCK_SIZE, index, false, descriptor, entryExternalizer).use { map ->
        val dataBlocks = store.blocks().filter { it.role() == DATA.persistentCode() }
        assertTrue(dataBlocks.count { it.state() == SEALED } > 2, "The chain must span sealed DATA blocks")

        val housekeeper = RetireUnusedBlockInDurableMapHousekeeper(map)
        val statesBeforeSweep = dataBlocks.map { it.state() }
        housekeeper.runHousekeeping { true }
        assertEquals(statesBeforeSweep, dataBlocks.map { it.state() }, "Cancellation before the run must prevent retirement")

        housekeeper.runHousekeeping { false }

        assertEquals(RETIRED, dataBlocks.first().state(), "The sweep must retire the unreachable block")
        assertTrue(dataBlocks.drop(1).all { it.state() == SEALED || it.state() == ACTIVE },
                   "The sweep must retain every block in the live patch chain")
      }
    }
  }

  private class InMemoryRecordRefIndex : RecordRefIndex {
    private val delegate = InMemoryIntToMultiLongMap()

    override fun put(key: Int, value: Long): Boolean = delegate.put(key, value)

    override fun replace(key: Int, oldValue: Long, newValue: Long): Boolean = delegate.replace(key, oldValue, newValue)

    override fun lookup(key: Int, valueAcceptor: IntToMultiLongMap.ValueAcceptor): Long = delegate.lookup(key, valueAcceptor)

    override fun remove(key: Int, value: Long): Boolean = delegate.remove(key, value)

    override fun forEach(processor: IntToMultiLongMap.KeyValueProcessor): Boolean = delegate.forEach(processor)

    override fun size(): Int = delegate.size()

    override fun isEmpty(): Boolean = delegate.isEmpty

    override fun clear() = delegate.clear()

    override fun markDirty() = Unit

    override fun flush() = Unit

    override fun close() = Unit

    override fun closeKeepingDirty() = Unit
  }

  companion object {
    private const val CHUNK_SIZE = 1024 * 1024
    private const val DATA_BLOCK_SIZE = 128
  }
}
