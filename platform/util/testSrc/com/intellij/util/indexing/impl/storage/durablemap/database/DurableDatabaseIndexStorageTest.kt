// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SuspiciousPackagePrivateAccess")

package com.intellij.util.indexing.impl.storage.durablemap.database

import com.intellij.platform.util.io.storages.CommonKeyDescriptors.byteArraySequence
import com.intellij.platform.util.io.storages.CommonKeyDescriptors.integer
import com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8
import com.intellij.platform.util.io.storages.UnsupportedFormatException
import com.intellij.platform.util.io.storages.database.DurableDatabase
import com.intellij.platform.util.io.storages.database.DurableDatabaseFactory
import com.intellij.platform.util.io.storages.database.DurableMapImplementation
import com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_SEPARATELY
import com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_TOGETHER
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.indexing.IdFilter
import com.intellij.util.indexing.IndexId
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.ManyKeysIntegerToIntegerIndexExtension
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.ShardableManyKeysIntegerToIntegerIndexExtension
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.SingleEntryIntegerValueIndexExtension
import com.intellij.util.indexing.VfsAwareIndexStorage
import com.intellij.util.indexing.impl.ValueContainerProcessor
import com.intellij.util.indexing.impl.storage.durablemap.DurableMapIndexStorage
import com.intellij.util.io.EnumeratorStringDescriptor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Checks the database lifecycle through the complete index storage adapter. */
@TestApplication
@Timeout(30)
class DurableDatabaseIndexStorageTest {
  @TempDir
  lateinit var directory: Path

  private lateinit var database: DurableDatabase

  @BeforeEach
  fun openDatabase() {
    database = DurableDatabaseFactory.withDefaults().open(directory.resolve("database"))
  }

  @AfterEach
  fun closeDatabase() {
    database.close()
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun valuesSurviveReopenAndFurtherChanges(implementation: DurableMapImplementation) {
    openStorage(implementation).use { storage ->
      assertTrue(Files.isDirectory(directory.resolve("database")))
      storage.addValue("key", 1, "first")
      storage.addValue("key", 2, "second")
      assertTrue(storage.isDirty)
      storage.flush()
      assertFalse(storage.isDirty)
    }

    openStorage(implementation).use { storage ->
      assertEquals(mapOf(1 to "first", 2 to "second"), read(storage))
      storage.updateValue("key", 1, "updated")
      storage.removeAllValues("key", 2)
    }

    openStorage(implementation).use { storage ->
      assertEquals(mapOf(1 to "updated"), read(storage))
    }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun clearCreatesAnEmptyReusableStorage(implementation: DurableMapImplementation) {
    openStorage(implementation).use { storage ->
      storage.addValue("key", 1, "before clear")
      storage.flush()
      storage.clear()
      assertEquals(emptyMap<Int, String>(), read(storage))

      storage.addValue("key", 2, "after clear")
      storage.flush()
      assertEquals(mapOf(2 to "after clear"), read(storage))
    }

    openStorage(implementation).use { storage ->
      assertEquals(mapOf(2 to "after clear"), read(storage))
    }
  }

  @ParameterizedTest(name = "inverted={0}, forward={1}")
  @MethodSource("mapImplementationPairs")
  fun clearIndexDataRecoversAfterOpenFailure(
    invertedMapImplementation: DurableMapImplementation,
    forwardMapImplementation: DurableMapImplementation,
  ) {
    val extension = ManyKeysIntegerToIntegerIndexExtension()
    val mapAccessor = DurableDatabaseMapAccessors.forwardMapAccessor(database, extension.name, forwardMapImplementation)
    mapAccessor.open().close()
    val mapName = database.mapNames().single()
    database.dropMap(mapName)
    database.openMap(mapName, 2, forwardMapImplementation, integer(), integer()).close()

    val failedLayout = DurableDatabaseStorageLayout(database, extension, invertedMapImplementation, forwardMapImplementation)
    failedLayout.openIndexStorage().use {
      assertThrows(IOException::class.java) { failedLayout.openForwardIndex() }
    }
    assertEquals(2, database.mapNames().size, "The partial initialization must preserve both maps for cleanup")

    failedLayout.clearIndexData()
    assertTrue(database.mapNames().isEmpty(), "Cleanup must remove data after a partial open failure")

    val recoveredLayout = DurableDatabaseStorageLayout(database, extension, invertedMapImplementation, forwardMapImplementation)
    recoveredLayout.openIndexStorage().use { }
    recoveredLayout.openForwardIndex().use { }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun differentIndexIdsUseDifferentMaps(implementation: DurableMapImplementation) {
    val firstIndexId = IndexId.create<String, String>("test-values:first")
    val secondIndexId = IndexId.create<String, String>("test-values")

    openStorage(implementation, firstIndexId).use { storage ->
      storage.addValue("key", 1, "first index")
      storage.flush()
    }

    openStorage(implementation, secondIndexId).use { storage ->
      assertEquals(emptyMap<Int, String>(), read(storage), "Each IndexId must address a separate map")
    }
    openStorage(implementation, firstIndexId).use { storage ->
      assertEquals(mapOf(1 to "first index"), read(storage), "Opening another IndexId must preserve the first map")
    }
  }

  @Test
  fun cleaningAMissingAccessorDoesNotCreateAMap() {
    val indexId = IndexId.create<String, String>("missing:index")
    val accessor = DurableDatabaseMapAccessors.valueMapAccessor(
      database,
      indexId,
      KEY_VALUE_SEPARATELY,
      stringAsUTF8(),
      EnumeratorStringDescriptor.INSTANCE,
    )

    accessor.cleanData()

    assertTrue(database.mapNames().isEmpty(), "Cleaning a missing accessor must not register a map")
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun cleaningAnIndexPreservesAnIndexWithACommonNamePrefix(implementation: DurableMapImplementation) {
    val firstIndexId = IndexId.create<String, String>("test:values")
    val secondIndexId = IndexId.create<String, String>("test")
    openStorage(implementation, firstIndexId).use { storage ->
      storage.addValue("key", 1, "first index")
    }
    openStorage(implementation, secondIndexId).use { storage ->
      storage.addValue("key", 2, "second index")
    }

    DurableDatabaseMapAccessors.cleanIndexData(database, firstIndexId)

    openStorage(implementation, firstIndexId).use { storage ->
      assertEquals(emptyMap<Int, String>(), read(storage), "Cleaning must remove the selected index")
    }
    openStorage(implementation, secondIndexId).use { storage ->
      assertEquals(mapOf(2 to "second index"), read(storage), "Cleaning must preserve a different namespace")
    }
  }

  @Test
  fun cleaningAnIndexRemovesObsoleteShardMaps() {
    val indexId = IndexId.create<String, String>("sharded:index")
    DurableDatabaseMapAccessors.valueMapAccessor(
      database,
      indexId,
      7,
      KEY_VALUE_SEPARATELY,
      stringAsUTF8(),
      EnumeratorStringDescriptor.INSTANCE,
    ).open().close()
    DurableDatabaseMapAccessors.forwardMapAccessor(database, indexId, 9, KEY_VALUE_SEPARATELY).open().close()
    assertEquals(2, database.mapNames().size, "The test must create maps outside a current shard range")

    DurableDatabaseMapAccessors.cleanIndexData(database, indexId)
    DurableDatabaseMapAccessors.cleanIndexData(database, indexId)

    assertTrue(database.mapNames().isEmpty(), "Repeated namespace cleanup must not recreate obsolete shard maps")
  }

  @ParameterizedTest(name = "inverted={0}, forward={1}")
  @MethodSource("mapImplementationPairs")
  fun regularLayoutUsesSelectedMapImplementations(
    invertedMapImplementation: DurableMapImplementation,
    forwardMapImplementation: DurableMapImplementation,
  ) {
    val extension = ManyKeysIntegerToIntegerIndexExtension()
    val selectedLayout = DurableDatabaseStorageLayout(database, extension, invertedMapImplementation, forwardMapImplementation)
    selectedLayout.openIndexStorage().use { storage ->
      storage.addValue(1, 1, 42)
    }
    selectedLayout.openForwardIndex().use { }

    val otherInvertedLayout = DurableDatabaseStorageLayout(
      database,
      extension,
      otherImplementation(invertedMapImplementation),
      forwardMapImplementation,
    )
    assertThrows(UnsupportedFormatException::class.java) {
      otherInvertedLayout.openIndexStorage()
    }
    otherInvertedLayout.openForwardIndex().use { }

    val otherForwardLayout = DurableDatabaseStorageLayout(
      database,
      extension,
      invertedMapImplementation,
      otherImplementation(forwardMapImplementation),
    )
    otherForwardLayout.openIndexStorage().use { }
    assertThrows(UnsupportedFormatException::class.java) {
      otherForwardLayout.openForwardIndex()
    }
  }

  @ParameterizedTest(name = "inverted={0}, forward={1}")
  @MethodSource("mapImplementationPairs")
  fun shardedLayoutUsesSelectedMapImplementations(
    invertedMapImplementation: DurableMapImplementation,
    forwardMapImplementation: DurableMapImplementation,
  ) {
    val extension = ShardableManyKeysIntegerToIntegerIndexExtension()
    val selectedLayout = DurableDatabaseShardedStorageLayout(database, extension, invertedMapImplementation, forwardMapImplementation)
    selectedLayout.openIndexStorage().use { storage ->
      storage.addValue(1, 1, 42)
    }
    selectedLayout.openForwardIndex().use { }

    val otherInvertedLayout = DurableDatabaseShardedStorageLayout(
      database,
      extension,
      otherImplementation(invertedMapImplementation),
      forwardMapImplementation,
    )
    assertThrows(UnsupportedFormatException::class.java) {
      otherInvertedLayout.openIndexStorage()
    }
    otherInvertedLayout.openForwardIndex().use { }

    val otherForwardLayout = DurableDatabaseShardedStorageLayout(
      database,
      extension,
      invertedMapImplementation,
      otherImplementation(forwardMapImplementation),
    )
    otherForwardLayout.openIndexStorage().use { }
    assertThrows(UnsupportedFormatException::class.java) {
      otherForwardLayout.openForwardIndex()
    }
  }

  @Test
  fun forwardMapAccessorUsesSelectedImplementation() {
    val indexId = IndexId.create<Int, Int>("forward-map-implementation")
    DurableDatabaseMapAccessors.forwardMapAccessor(database, indexId, KEY_VALUE_TOGETHER).open().close()
    val mapName = database.mapNames().single()

    assertThrows(UnsupportedFormatException::class.java) {
      database.openMap(mapName, 1, KEY_VALUE_SEPARATELY, integer(), byteArraySequence())
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun layoutsApplyKeyFilterOnlyWhenEnabled(traceKeyHashToVirtualFileMapping: Boolean) {
    val regularExtension = object : ManyKeysIntegerToIntegerIndexExtension() {
      override fun traceKeyHashToVirtualFileMapping(): Boolean = traceKeyHashToVirtualFileMapping
    }
    val shardedExtension = object : ShardableManyKeysIntegerToIntegerIndexExtension() {
      override fun traceKeyHashToVirtualFileMapping(): Boolean = traceKeyHashToVirtualFileMapping
    }
    val singleEntryExtension = object : SingleEntryIntegerValueIndexExtension() {
      override fun traceKeyHashToVirtualFileMapping(): Boolean = traceKeyHashToVirtualFileMapping
    }
    val layouts = listOf(
      DurableDatabaseStorageLayout(database, regularExtension, KEY_VALUE_SEPARATELY, KEY_VALUE_SEPARATELY),
      DurableDatabaseShardedStorageLayout(database, shardedExtension, KEY_VALUE_SEPARATELY, KEY_VALUE_SEPARATELY),
      DurableDatabaseSingleEntryStorageLayout(database, singleEntryExtension, KEY_VALUE_SEPARATELY),
    )
    val filter = object : IdFilter() {
      override fun containsFileId(id: Int): Boolean {
        assertTrue(traceKeyHashToVirtualFileMapping, "An index that disables key filtering must not consult the filter")
        return id == 1
      }
    }

    for (layout in layouts) {
      (layout.openIndexStorage() as VfsAwareIndexStorage<Int, Int>).use { storage ->
        storage.addValue(1, 1, 42)
        storage.addValue(2, 2, 43)
        storage.flush()

        val keys = mutableSetOf<Int>()
        assertTrue(storage.processKeys({ keys.add(it); true }, GlobalSearchScope.EMPTY_SCOPE, filter))
        assertEquals(
          if (traceKeyHashToVirtualFileMapping) setOf(1) else setOf(1, 2),
          keys,
          "${layout.javaClass.simpleName} must use the filtering flag from the index extension",
        )
        assertFalse(
          storage.processKeys({ false }, GlobalSearchScope.EMPTY_SCOPE, filter),
          "The processor must still be able to stop the key scan",
        )
      }
    }
  }

  private fun openStorage(
    implementation: DurableMapImplementation,
    indexId: IndexId<*, *> = IndexId.create<String, String>("test-values"),
  ): DurableMapIndexStorage<String, String> {
    val keyDescriptor = stringAsUTF8()
    val valueExternalizer = EnumeratorStringDescriptor.INSTANCE
    val mapAccessor = DurableDatabaseMapAccessors.valueMapAccessor(database, indexId, implementation, keyDescriptor, valueExternalizer)
    return DurableMapIndexStorage(
      mapAccessor::open,
      keyDescriptor,
      valueExternalizer,
      /* cacheSize = */ 16,
      /* keyIsUniqueForIndexedFile = */ false,
      /* traceKeyHashToVirtualFileMapping = */ false,
    )
  }

  private fun read(storage: DurableMapIndexStorage<String, String>): Map<Int, String> {
    val result = mutableMapOf<Int, String>()
    storage.read("key", ValueContainerProcessor<String, RuntimeException> { container ->
      container.forEach { inputId, value ->
        result[inputId] = value
        true
      }
      false
    })
    return result
  }

  private fun otherImplementation(implementation: DurableMapImplementation): DurableMapImplementation =
    when (implementation) {
      KEY_VALUE_TOGETHER -> KEY_VALUE_SEPARATELY
      KEY_VALUE_SEPARATELY -> KEY_VALUE_TOGETHER
    }

  companion object {
    @JvmStatic
    fun mapImplementationPairs(): List<Arguments> =
      DurableMapImplementation.entries.flatMap { invertedImplementation ->
        DurableMapImplementation.entries.map { forwardImplementation ->
          Arguments.of(invertedImplementation, forwardImplementation)
        }
      }
  }
}
