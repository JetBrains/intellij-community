// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.impl

import com.intellij.platform.util.io.storages.CommonKeyDescriptors.integer
import com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8
import com.intellij.platform.util.io.storages.DataExternalizerEx.KnownSizeRecordWriter
import com.intellij.platform.util.io.storages.InlineKeyDescriptorEx
import com.intellij.platform.util.io.storages.KeyDescriptorEx
import com.intellij.platform.util.io.storages.database.DurableMapImplementation
import com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_SEPARATELY
import com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_TOGETHER
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory
import com.intellij.platform.util.io.storages.database.spi.BlocksStore
import com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ACTIVE
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.LOOKUP
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapBlockCatalog.DurableMapBlockRole.VALUES
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapOverBlocks
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapWithKeyCatalogOverBlocks
import com.intellij.platform.util.io.storages.durablemap.DefaultEntryExternalizer
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap.PatchableValueExternalizer
import com.intellij.util.io.InlineKeyDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.nio.file.Path

/** Checks that inline traversal reads records only for the ambiguous lookup hash */
class InlineKeyDurableMapTest {
  @TempDir
  lateinit var directory: Path

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun inlineTraversalWorksWithInaccessibleRecordMemory(implementation: DurableMapImplementation) {
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      GuardedStore(database.openStore("map", 1)).use { store ->
        openMap(store, implementation).use { map ->
          val codes = setOf(Int.MIN_VALUE, Int.MAX_VALUE, -17, 1, 42)
          for (code in codes) {
            map.put(code, "base")
            map.patchValue(code, "+patch")
          }
          map.remove(42)
          val expected = if (implementation == KEY_VALUE_SEPARATELY) codes else codes - 42

          store.forbidRecordAccess()

          assertKeys(map, expected)
          var calls = 0
          assertFalse(map.processKeys { calls++; false }, "The processor must stop the lookup traversal")
          assertEquals(1, calls)
        }
      }
    }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun specialHashReadsOnlyZeroAndMinusOne(implementation: DurableMapImplementation) {
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      for ((index, specialCodes) in listOf(emptySet(), setOf(0), setOf(-1), setOf(0, -1)).withIndex()) {
        val descriptor = ReadCheckingDescriptor()
        GuardedStore(database.openStore("map-$index", 1)).use { store ->
          openMap(store, implementation, descriptor).use { map ->
            val codes = setOf(Int.MIN_VALUE, Int.MAX_VALUE, 17) + specialCodes
            for (code in codes) {
              map.put(code, "base")
              map.patchValue(code, "+patch")
            }
            descriptor.allowedReads = specialCodes
            descriptor.readCodes.clear()

            assertKeys(map, codes)
            assertEquals(specialCodes, descriptor.readCodes.toSet(), "Only ambiguous keys need their original records")
            assertEquals(specialCodes.size, descriptor.readCodes.size, "Each special key must be read once")

            if (specialCodes.isNotEmpty()) {
              descriptor.allowedReads = null
              for (code in specialCodes) map.remove(code)
              descriptor.allowedReads = specialCodes
            }
            store.forbidValueAccess()
            assertKeys(map, if (implementation == KEY_VALUE_SEPARATELY) codes else codes - specialCodes)
          }
        }
      }
    }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun inlineDescriptorReopensTheExistingFormat(implementation: DurableMapImplementation) {
    val factory = BlocksDatabaseFactory(CHUNK_SIZE)
    val oldDescriptor = object : KeyDescriptorEx<Int> by integer() {}
    val codes = setOf(0, -1, 17, Int.MIN_VALUE, Int.MAX_VALUE)
    factory.open(directory).use { database ->
      openMap(database.openStore("map", 1), implementation, oldDescriptor).use { map ->
        for (code in codes) map.put(code, "old")
        map.put(0, "zero")
        map.remove(-1)
        map.put(-1, "restored")
      }
    }

    factory.open(directory).use { database ->
      GuardedStore(requireNotNull(database.findStore("map"))).use { store ->
        val descriptor = ReadCheckingDescriptor()
        openMap(store, implementation, descriptor).use { map ->
          assertEquals("zero", map.get(0))
          assertEquals("restored", map.get(-1))
          descriptor.allowedReads = setOf(0, -1)
          descriptor.readCodes.clear()
          store.forbidValueAccess()

          assertKeys(map, codes)
          assertEquals(setOf(0, -1), descriptor.readCodes.toSet())
        }
      }
    }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun dirtyRecoveryPreservesInlineTraversal(implementation: DurableMapImplementation) {
    val factory = BlocksDatabaseFactory(CHUNK_SIZE)
    val database = factory.open(directory)
    val map = openMap(database.openStore("map", 1), implementation)
    map.put(17, "base")
    map.patchValue(17, "+patch")
    map.put(42, "removed")
    map.remove(42)
    database.close() // Leave the lookup dirty so reopening must recover the committed records.

    factory.open(directory).use { reopened ->
      GuardedStore(requireNotNull(reopened.findStore("map"))).use { store ->
        openMap(store, implementation).use { recovered ->
          assertEquals("base+patch", recovered.get(17))
          assertNull(recovered.get(42))
          store.forbidRecordAccess()

          assertKeys(recovered, if (implementation == KEY_VALUE_SEPARATELY) setOf(17, 42) else setOf(17))
        }
      }
    }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun missingLookupStillFindsCommittedInlineKeys(implementation: DurableMapImplementation) {
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      val originalStore = database.openStore("map", 1)
      openMap(originalStore, implementation).use { map ->
        map.put(17, "value")
      }
      for (block in DurableMapBlockCatalog.open(originalStore).blocks(LOOKUP)) {
        if (block.state() == ACTIVE) block.seal()
        block.retire()
      }

      GuardedStore(originalStore).use { store ->
        openMap(store, implementation).use { recovered ->
          assertEquals("value", recovered.get(17), "An empty lookup must trigger a scan of the source records")
          store.forbidRecordAccess()
          assertKeys(recovered, setOf(17))
        }
      }
    }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun adaptedInlineKeysAreDecodedAsTheirOriginalType(implementation: DurableMapImplementation) {
    data class Key(val code: Int)
    val descriptor = KeyDescriptorEx.adapt(object : InlineKeyDescriptor<Key>() {
      override fun toInt(key: Key): Int = key.code
      override fun fromInt(code: Int): Key = Key(code)
    })
    BlocksDatabaseFactory(CHUNK_SIZE).open(directory).use { database ->
      GuardedStore(database.openStore("map", 1)).use { store ->
        openMap(store, implementation, descriptor).use { map ->
          val keys = setOf(Key(17), Key(Int.MIN_VALUE), Key(Int.MAX_VALUE))
          for (key in keys) map.put(key, "value")
          store.forbidRecordAccess()

          assertKeys(map, keys)
        }
      }
    }
  }

  private fun openMap(store: BlocksStore, implementation: DurableMapImplementation): PatchableDurableMap<Int, String, String> =
    openMap(store, implementation, integer())

  private fun <K> openMap(
    store: BlocksStore,
    implementation: DurableMapImplementation,
    descriptor: KeyDescriptorEx<K>,
  ): PatchableDurableMap<K, String, String> = when (implementation) {
    KEY_VALUE_TOGETHER -> DurableMapOverBlocks.openPatchable(
      store, BLOCK_SIZE, descriptor, null, DefaultEntryExternalizer(descriptor, AppendStrings), AppendStrings,
    )
    KEY_VALUE_SEPARATELY -> DurableMapWithKeyCatalogOverBlocks.openPatchable(store, BLOCK_SIZE, descriptor, null, AppendStrings)
  }

  private fun <K> assertKeys(map: PatchableDurableMap<K, String, String>, expected: Set<K>) {
    val keys = ArrayList<K>()
    assertTrue(map.processKeys(keys::add))
    assertEquals(expected, keys.toSet())
    assertEquals(expected.size, keys.size, "Traversal must return each key once")
  }

  private class ReadCheckingDescriptor : InlineKeyDescriptorEx<Int> {
    var allowedReads: Set<Int>? = null
    val readCodes = ArrayList<Int>()

    override fun getHashCode(value: Int): Int = value
    override fun isEqual(key1: Int, key2: Int): Boolean = key1 == key2
    override fun toInt(key: Int): Int = key
    override fun fromInt(code: Int): Int = code

    override fun read(input: MemorySegment): Int {
      val code = integer().read(input)
      assertTrue(allowedReads?.contains(code) ?: true, "The lookup must supply the unambiguous key $code")
      readCodes.add(code)
      return code
    }

    override fun writerFor(value: Int): KnownSizeRecordWriter = integer().writerFor(value)
    override fun recordSizeIfConstant(): Int = Int.SIZE_BYTES
  }

  private object AppendStrings : PatchableValueExternalizer<String, String> {
    override fun read(input: MemorySegment): String = stringAsUTF8().read(input)
    override fun writerFor(value: String): KnownSizeRecordWriter = stringAsUTF8().writerFor(value)
    override fun writerForPatch(patch: String): KnownSizeRecordWriter = writerFor(patch)
  }

  /** Separate scopes also guard segments that the record storage cached before the traversal */
  private class GuardedStore(private val delegate: BlocksStore) : BlocksStore by delegate, AutoCloseable {
    private val blocks = HashMap<Int, GuardedBlock>()

    override fun blocks(): List<BlocksStore.Block> = delegate.blocks().map(::wrap)
    override fun findBlock(blockId: Int): BlocksStore.Block? = delegate.findBlock(blockId)?.let(::wrap)
    override fun allocateBlock(role: Int, minimumContentLength: Int): BlocksStore.Block = wrap(
      delegate.allocateBlock(role, minimumContentLength),
    )

    fun forbidRecordAccess() {
      for (block in blocks.values) {
        if (block.role() != LOOKUP.persistentCode()) block.close()
      }
    }

    fun forbidValueAccess() {
      for (block in blocks.values) {
        if (block.role() == VALUES.persistentCode()) block.close()
      }
    }

    override fun close() {
      for (block in blocks.values) block.close()
    }

    private fun wrap(block: BlocksStore.Block): GuardedBlock = blocks.getOrPut(block.id()) { GuardedBlock(block) }
  }

  private class GuardedBlock(private val delegate: BlocksStore.Block) : BlocksStore.Block by delegate, AutoCloseable {
    private val arena = Arena.ofShared()

    override fun content(): MemorySegment {
      // The original segment owns the mapping. This alias only restricts when the test can access it.
      return delegate.content().reinterpret(arena, null)
    }

    override fun close() {
      if (arena.scope().isAlive) arena.close()
    }
  }

  companion object {
    private const val CHUNK_SIZE = 1024 * 1024
    private const val BLOCK_SIZE = 128
  }
}
