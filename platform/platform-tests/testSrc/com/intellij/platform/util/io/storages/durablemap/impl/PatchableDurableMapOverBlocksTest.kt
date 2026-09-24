// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.impl

import com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8
import com.intellij.platform.util.io.storages.DataExternalizerEx.KnownSizeRecordWriter
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory
import com.intellij.platform.util.io.storages.database.DurableDatabaseFactory
import com.intellij.platform.util.io.storages.database.spi.BlocksStore
import com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ACTIVE
import com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.RETIRED
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.DATA
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.LOOKUP
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapOverBlocks
import com.intellij.platform.util.io.storages.database.storages.durablemap.RecordStorageOverBlocks
import com.intellij.platform.util.io.storages.database.storages.extendiblehashmap.ExtendibleHashMapStorageOverLookupBlocks
import com.intellij.platform.util.io.storages.durablemap.DefaultEntryExternalizer
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap.PatchableValueExternalizer
import com.intellij.platform.util.io.storages.intmultimaps.InMemoryIntToMultiLongMap
import com.intellij.platform.util.io.storages.intmultimaps.IntToMultiLongMap
import com.intellij.platform.util.io.storages.intmultimaps.RecordRefIndex
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMapInt32ToInt64
import com.intellij.util.io.CorruptedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Exercises patches whose order changes the resulting value. */
@Timeout(30)
class PatchableDurableMapOverBlocksTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun patchesApplyToExistingAndMissingValues() {
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      openMap(database.openStore("map", 1)).use { map ->
        map.patchValue("key", listOf(1, 2))
        map.patchValue("key", listOf(-1, 3))
        map.patchValue("key", listOf(1, -3))
        assertEquals(setOf(1, 2), map.get("key"), "Patches must preserve their application order")
        map.patchValue("empty", listOf(-1))
        assertEquals(emptySet<Int>(), map.get("empty"), "A deletion patch can create a non-null empty value")
        assertTrue(map.containsMapping("empty"))
        map.patchValue("zeroBytes", emptyList())
        map.patchValue("key", emptyList())
        assertEquals(emptySet<Int>(), map.get("zeroBytes"))
        assertEquals(setOf(1, 2), map.get("key"))
        assertEquals(3, map.size())
      }
    }
  }

  @Test
  fun snapshotsAndRemovalsStartIndependentChains() {
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      openMap(database.openStore("map", 1)).use { map ->
        map.put("key", setOf(1))
        map.patchValue("key", listOf(2))
        map.put("key", setOf(3))
        map.patchValue("key", listOf(4))
        assertEquals(setOf(3, 4), map.get("key"))
        map.remove("key")
        assertNull(map.get("key"))
        map.patchValue("key", listOf(5))
        assertEquals(setOf(5), map.get("key"), "A patch after removal must not reuse the old base")
        map.put("key", null)
        map.put("key", setOf(6))
        map.patchValue("key", listOf(7))
        assertEquals(setOf(6, 7), map.get("key"))
      }
    }
  }

  @Test
  fun collisionsKeepIndependentChainsAcrossBlocksAndRecordRefIndexRecovery() {
    assertEquals("FB".hashCode(), "Ea".hashCode())
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      val store = database.openStore("map", 1)
      openMap(store).use { map ->
        map.put("FB", setOf(1))
        map.put("Ea", setOf(2))
        for (i in 3..50) {
          map.patchValue("FB", listOf(i))
          map.patchValue("Ea", listOf(i + 100))
        }
        assertTrue(store.blocks().count { it.role() == DATA.persistentCode() } > 1)
      }
      dropRecordRefIndex(store)
      val codec = SetCodec().apply { failReads = true }
      openMap(store, codec = codec).use { map ->
        assertTrue(map.containsMapping("FB"))
        assertTrue(map.containsMapping("Ea"))
        codec.failReads = false
        assertEquals((1..50).toSet() - 2, map.get("FB"))
        assertEquals((103..150).toSet() + 2, map.get("Ea"))
        val values = mutableMapOf<String, Set<Int>>()
        map.forEachEntry { key, value -> values[key] = value; true }
        assertEquals(setOf("FB", "Ea"), values.keys)
        val keys = mutableSetOf<String>()
        map.processKeys { keys.add(it); true }
        assertEquals(values.keys, keys)
      }
    }
  }

  @Test
  fun replayHandlesReplacementAndDeletionOfPatchedHeads() {
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      val store = database.openStore("map", 1)
      openMap(store).use { map ->
        map.patchValue("FB", listOf(1))
        map.patchValue("FB", listOf(2))
        map.patchValue("Ea", listOf(3))
        map.patchValue("Ea", listOf(4))
        map.remove("FB")
        map.put("Ea", setOf(5))
        map.patchValue("Ea", listOf(6))
        map.patchValue("FB", listOf(7))
      }
      dropRecordRefIndex(store)
      openMap(store).use { map ->
        assertEquals(setOf(7), map.get("FB"))
        assertEquals(setOf(5, 6), map.get("Ea"))
        assertEquals(2, map.size())
      }
    }
  }

  @Test
  fun replayOrdersDataBlocksByLogicalBlockId() {
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      val store = database.openStore("map", 1)
      openMap(store).use { map ->
        map.put("key", setOf(1))
        for (value in 2..50) map.patchValue("key", listOf(value))
      }
      assertTrue(store.blocks().count { it.role() == DATA.persistentCode() } > 1)
      dropRecordRefIndex(store)

      val storeWithReorderedBlocks = object : BlocksStore by store {
        override fun blocks(): List<BlocksStore.Block> = store.blocks().reversed()
      }
      openMap(storeWithReorderedBlocks).use { map ->
        assertEquals((1..50).toSet(), map.get("key"), "Replay must use logical block order after physical block relocation")
      }
    }
  }

  @Test
  fun replaySkipsAnObsoletePatchWhosePredecessorBlockWasRetired() {
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      val store = database.openStore("map", 1)
      openMap(store).use { map ->
        map.put("key", (1..50).toSet())
        map.patchValue("key", listOf(51))
        map.put("key", setOf(1000))
        map.put("filler", (1..50).toSet())
      }
      val dataBlocks = store.blocks().filter { it.role() == DATA.persistentCode() }
      assertTrue(dataBlocks.size >= 3, "The records must span three DATA blocks")
      dataBlocks.first().retire()
      dropRecordRefIndex(store)

      openMap(store).use { map ->
        assertEquals(setOf(1000), map.get("key"), "Replay must ignore the obsolete patch chain")
        assertEquals((1..50).toSet(), map.get("filler"))
      }
    }
  }

  @Test
  fun writesDoNotReadThePreviousValue() {
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      val codec = SetCodec().apply { failReads = true }
      openMap(database.openStore("map", 1), codec = codec).use { map ->
        map.put("key", (1..100).toSet())
        map.patchValue("key", listOf(-50, 101))
        map.patchValue("missing", listOf(7))
        assertTrue(map.containsMapping("key"))
        codec.failReads = false
        assertEquals((1..101).toSet() - 50, map.get("key"))
      }
    }
  }

  @Test
  fun reopeningPreservesPatchedValues() {
    val factory = BlocksDatabaseFactory(CHUNK_SIZE)
    factory.open(directory).use { database ->
      openMap(database.openStore("map", 1)).use { map ->
        map.put("key", setOf(1, 2))
        map.patchValue("key", listOf(-1, 3))
      }
    }
    factory.open(directory).use { database ->
      val codec = SetCodec().apply { failReads = true }
      openMap(database.openStore("map", 1), codec = codec).use { map ->
        assertTrue(map.containsMapping("key"))
        codec.failReads = false
        assertEquals(setOf(2, 3), map.get("key"))
      }
    }
  }

  @Test
  fun aNewPersistentRecordRefIndexReplaysPatchesFromAnInMemoryIndex() {
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      val store = database.openStore("map", 1)
      openMap(store, InMemoryRecordRefIndex(), rebuildIndex = true).use { map ->
        map.patchValue("key", listOf(1))
        map.patchValue("key", listOf(-1, 2))
      }
      openMap(store).use { assertEquals(setOf(2), it.get("key")) }
    }
  }

  @Test
  fun failedPatchLeavesThePreviousValueAndCannotMarkTheRecordRefIndexClean() {
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      val store = database.openStore("map", 1)
      val index = openRecordRefIndex(store)
      val codec = SetCodec()
      openMap(store, index, codec).use { map ->
        map.put("key", setOf(1))
        map.force()
        assertFalse(index.isDirty)
        codec.beforeWrite = { assertTrue(index.isDirty, "The record reference index must be dirty before writing the patch") }
        codec.failWrites = true
        assertThrows(IOException::class.java) { map.patchValue("key", listOf(2, 3)) }
        assertTrue(map.isClosed)
        assertThrows(IllegalStateException::class.java) { map.patchValue("key", listOf(4)) }
      }
      openMap(store).use { map ->
        assertEquals(setOf(1), map.get("key"))
        map.patchValue("key", listOf(5))
        assertEquals(setOf(1, 5), map.get("key"))
      }
    }
  }

  @Test
  fun failedIndexPublicationClosesTheMapAndReplaysTheCommittedPatch() {
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      val store = database.openStore("map", 1)
      val index = FailingRecordRefIndex(openRecordRefIndex(store))
      openMap(store, index).use { map ->
        map.put("key", setOf(1))
        map.force()
        index.failNextModification = true
        assertThrows(IOException::class.java) { map.patchValue("key", listOf(-1, 2)) }
        assertTrue(map.isClosed, "A failed publication must prevent further writes through this instance")
      }
      openMap(store).use { map ->
        assertEquals(setOf(2), map.get("key"), "The committed patch must survive the failed index update")
      }
    }
  }

  @Test
  fun committedPatchesRecoverBeforeAndAfterIndexPublication() {
    for (publishIndex in listOf(false, true)) {
      BlocksDatabaseFactory(CHUNK_SIZE).open(directory.resolve(publishIndex.toString())).use { database ->
        val store = database.openStore("map", 1)
        val index = openRecordRefIndex(store)
        openMap(store, index).use { map ->
          map.put("key", setOf(1))
          map.force()
          var hash = 0
          var oldHead = 0L
          index.forEach { keyHash, recordRef -> hash = keyHash; oldHead = recordRef; true }
          index.markDirty()
          val newHead = records(store).append(12, oldHead) { payload ->
            payload.set(JAVA_LONG_UNALIGNED, 0, oldHead)
            payload.asSlice(8).asByteBuffer().putInt(-1)
          }
          if (publishIndex) index.replace(hash, oldHead, newHead)
          index.closeKeepingDirty()
        }
        openMap(store).use { map ->
          assertEquals(emptySet<Int>(), map.get("key"), "Replay must include the committed patch at either crash boundary")
          map.patchValue("key", listOf(2))
          assertEquals(setOf(2), map.get("key"))
        }
      }
    }
  }

  @Test
  fun replayRejectsAPatchThatBranchesFromAnObsoleteHead() {
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      val store = database.openStore("map", 1)
      openMap(store).use { map ->
        map.patchValue("key", listOf(1))
        map.patchValue("key", listOf(2))
        map.patchValue("key", listOf(3))
      }
      val refs = mutableListOf<Long>()
      records(store).forEachCommittedRecord { ref, _ -> refs.add(ref) }
      val lastRef = refs.last()
      val block = store.blocks().single { it.id() == RecordStorageOverBlocks.blockId(lastRef) }
      block.content().set(JAVA_LONG_UNALIGNED, RecordStorageOverBlocks.recordOffset(lastRef).toLong() + 4, refs.first())
      dropRecordRefIndex(store)
      assertThrows(CorruptedException::class.java) { openMap(store) }
    }
  }

  @Test
  fun invalidLinksAreRejectedByReadsAndReplay() {
    for (corruption in listOf("self link", "missing base", "patch as base")) {
      BlocksDatabaseFactory(CHUNK_SIZE).open(directory.resolve(corruption)).use { database ->
        val store = database.openStore("map", 1)
        openMap(store).use { map ->
          map.patchValue("key", listOf(1))
          map.patchValue("key", listOf(2))
          map.patchValue("key", listOf(3))
        }
        val refs = mutableListOf<Long>()
        records(store).forEachCommittedRecord { ref, _ -> refs.add(ref) }
        val lastRef = refs.last()
        val block = store.blocks().single { it.id() == RecordStorageOverBlocks.blockId(lastRef) }
        val recordOffset = RecordStorageOverBlocks.recordOffset(lastRef).toLong()
        when (corruption) {
          "self link" -> block.content().set(JAVA_LONG_UNALIGNED, recordOffset + 4, lastRef)
          "missing base" -> block.content().set(JAVA_LONG_UNALIGNED, recordOffset + 12, 0)
          "patch as base" -> block.content().set(JAVA_LONG_UNALIGNED, recordOffset + 12, refs[1])
        }
        openMap(store).use { map -> assertThrows(CorruptedException::class.java) { map.get("key") } }
        dropRecordRefIndex(store)
        repeat(2) {
          assertThrows(CorruptedException::class.java) { openMap(store) }
        }
      }
    }
  }

  @Test
  fun namedFactoryReturnsTheTypedCapabilityAndCachesItsInstance() {
    DurableDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      val codec = SetCodec()
      val first = database.openMap("patchable", 1, stringAsUTF8(), codec)
      val second = database.openMap("patchable", 1, stringAsUTF8(), codec)
      assertSame(first, second)
      first.patchValue("key", listOf(1))
      first.patchValue("key", listOf(2))
      assertEquals(setOf(1, 2), second.get("key"))
      first.close()
      database.openMap("patchable", 1, stringAsUTF8(), codec).use { assertEquals(setOf(1, 2), it.get("key")) }
      val ordinary = database.openMap("ordinary", 1, stringAsUTF8(), stringAsUTF8())
      assertFalse(ordinary is PatchableDurableMap<*, *, *>, "Ordinary maps must not advertise patch support")
      assertThrows(IllegalStateException::class.java) { database.openMap("ordinary", 1, stringAsUTF8(), codec) }
    }
  }

  @Test
  fun concurrentPatchesDoNotLoseUpdates() {
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      openMap(database.openStore("map", 1)).use { map ->
        Executors.newFixedThreadPool(4).use { executor ->
          executor.invokeAll((0..3).map { worker ->
            Callable {
              for (i in 1..50) map.patchValue("key", listOf(worker * 50 + i))
            }
          }).forEach { it.get() }
        }
        assertEquals((1..200).toSet(), map.get("key"))
      }
    }
  }

  private fun openMap(
    store: BlocksStore,
    index: RecordRefIndex? = null,
    codec: SetCodec = SetCodec(),
    rebuildIndex: Boolean = false,
  ): PatchableDurableMap<String, Set<Int>, List<Int>> {
    val descriptor = stringAsUTF8()
    val entryExternalizer = DefaultEntryExternalizer(descriptor, codec)
    return if (index == null) {
      DurableMapOverBlocks.openPatchable(store, BLOCK_SIZE, descriptor, null, entryExternalizer, codec)
    }
    else {
      DurableMapOverBlocks.openPatchable(
        store, BLOCK_SIZE, index, rebuildIndex, descriptor, null, entryExternalizer, codec
      )
    }
  }

  private class InMemoryRecordRefIndex(
    private val delegate: InMemoryIntToMultiLongMap = InMemoryIntToMultiLongMap(),
  ) : RecordRefIndex, IntToMultiLongMap by delegate {
    override fun markDirty() = Unit

    override fun flush() = Unit

    override fun close() = clear()

    override fun closeKeepingDirty() = clear()
  }

  private class FailingRecordRefIndex(
    private val delegate: RecordRefIndex,
  ) : RecordRefIndex by delegate {
    var failNextModification = false

    override fun lookupAndModify(key: Int, processor: IntToMultiLongMap.ValueProcessor): Boolean {
      return delegate.lookupAndModify(key) { oldValue, newValueRef ->
        val shouldContinue = processor.process(oldValue, newValueRef)
        if (failNextModification && newValueRef.get() != oldValue) {
          failNextModification = false
          throw IOException("Simulated index update failure")
        }
        shouldContinue
      }
    }
  }

  private fun records(store: BlocksStore): RecordStorageOverBlocks = RecordStorageOverBlocks.open(DurableMapBlockCatalog.open(store), BLOCK_SIZE)

  private fun openRecordRefIndex(store: BlocksStore): ExtendibleHashMapInt32ToInt64 = ExtendibleHashMapInt32ToInt64(
    ExtendibleHashMapStorageOverLookupBlocks(
      DurableMapBlockCatalog.open(store).lookupBlocks(
        ExtendibleHashMapStorageOverLookupBlocks.IMPLEMENTATION_ID,
        ExtendibleHashMapStorageOverLookupBlocks.INITIAL_GENERATION,
      ),
      DurableMapOverBlocks.SEGMENT_SIZE,
    )
  )

  private fun dropRecordRefIndex(store: BlocksStore) {
    for (block in store.blocks()) {
      if (block.role() == LOOKUP.persistentCode() && block.state() != RETIRED) {
        if (block.state() == ACTIVE) block.seal()
        block.retire()
      }
    }
  }

  private class SetCodec : PatchableValueExternalizer<Set<Int>, List<Int>> {
    var failReads = false
    var failWrites = false
    var beforeWrite: () -> Unit = {}

    override fun read(input: ByteBuffer): Set<Int> {
      check(!failReads) { "The operation must not deserialize values" }
      val result = mutableSetOf<Int>()
      while (input.hasRemaining()) {
        val command = input.int
        if (command > 0) result.add(command) else result.remove(-command)
      }
      return result
    }

    override fun writerFor(value: Set<Int>): KnownSizeRecordWriter = writerForPatch(value.toList())

    override fun writerForPatch(patch: List<Int>): KnownSizeRecordWriter = object : KnownSizeRecordWriter {
      override fun recordSize(): Int = patch.size * 4

      override fun write(data: ByteBuffer): ByteBuffer {
        beforeWrite()
        for (command in patch) {
          data.putInt(command)
          if (failWrites) throw IOException("Simulated failure during patch serialization")
        }
        return data
      }
    }
  }

  companion object {
    private const val CHUNK_SIZE = 1024 * 1024
    private const val BLOCK_SIZE = 128
  }
}
