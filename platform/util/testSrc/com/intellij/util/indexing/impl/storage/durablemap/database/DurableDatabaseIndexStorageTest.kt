// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SuspiciousPackagePrivateAccess")

package com.intellij.util.indexing.impl.storage.durablemap.database

import com.intellij.platform.util.io.storages.CommonKeyDescriptors.integer
import com.intellij.platform.util.io.storages.CommonKeyDescriptors.stringAsUTF8
import com.intellij.platform.util.io.storages.database.DurableDatabase
import com.intellij.platform.util.io.storages.database.DurableDatabaseFactory
import com.intellij.util.indexing.IndexId
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.ManyKeysIntegerToIntegerIndexExtension
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
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Checks the database lifecycle through the complete index storage adapter. */
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

  @Test
  fun valuesSurviveReopenAndFurtherChanges() {
    openStorage().use { storage ->
      assertTrue(Files.isDirectory(directory.resolve("database")))
      storage.addValue("key", 1, "first")
      storage.addValue("key", 2, "second")
      assertTrue(storage.isDirty)
      storage.flush()
      assertFalse(storage.isDirty)
    }

    openStorage().use { storage ->
      assertEquals(mapOf(1 to "first", 2 to "second"), read(storage))
      storage.updateValue("key", 1, "updated")
      storage.removeAllValues("key", 2)
    }

    openStorage().use { storage ->
      assertEquals(mapOf(1 to "updated"), read(storage))
    }
  }

  @Test
  fun clearCreatesAnEmptyReusableStorage() {
    openStorage().use { storage ->
      storage.addValue("key", 1, "before clear")
      storage.flush()
      storage.clear()
      assertEquals(emptyMap<Int, String>(), read(storage))

      storage.addValue("key", 2, "after clear")
      storage.flush()
      assertEquals(mapOf(2 to "after clear"), read(storage))
    }

    openStorage().use { storage ->
      assertEquals(mapOf(2 to "after clear"), read(storage))
    }
  }

  @Test
  fun clearIndexDataRecoversAfterOpenFailure() {
    val extension = ManyKeysIntegerToIntegerIndexExtension()
    val mapAccessor = DurableDatabaseMapAccessors.forwardMapAccessor(database, extension.name)
    mapAccessor.open().close()
    val mapName = database.mapNames().single()
    database.dropMap(mapName)
    database.openMap(mapName, 2, integer(), integer()).close()

    val failedLayout = DurableDatabaseStorageLayout(database, extension)
    failedLayout.openIndexStorage().use {
      assertThrows(IOException::class.java) { failedLayout.openForwardIndex() }
    }
    assertEquals(2, database.mapNames().size, "The partial initialization must preserve both maps for cleanup")

    failedLayout.clearIndexData()
    assertTrue(database.mapNames().isEmpty(), "Cleanup must remove data after a partial open failure")

    val recoveredLayout = DurableDatabaseStorageLayout(database, extension)
    recoveredLayout.openIndexStorage().use { }
    recoveredLayout.openForwardIndex().use { }
  }

  @Test
  fun differentIndexIdsUseDifferentMaps() {
    val firstIndexId = IndexId.create<String, String>("test-values:first")
    val secondIndexId = IndexId.create<String, String>("test-values")

    openStorage(firstIndexId).use { storage ->
      storage.addValue("key", 1, "first index")
      storage.flush()
    }

    openStorage(secondIndexId).use { storage ->
      assertEquals(emptyMap<Int, String>(), read(storage), "Each IndexId must address a separate map")
    }
    openStorage(firstIndexId).use { storage ->
      assertEquals(mapOf(1 to "first index"), read(storage), "Opening another IndexId must preserve the first map")
    }
  }

  @Test
  fun cleaningAMissingAccessorDoesNotCreateAMap() {
    val indexId = IndexId.create<String, String>("missing:index")
    val accessor = DurableDatabaseMapAccessors.valueMapAccessor(
      database,
      indexId,
      stringAsUTF8(),
      EnumeratorStringDescriptor.INSTANCE,
    )

    accessor.cleanData()

    assertTrue(database.mapNames().isEmpty(), "Cleaning a missing accessor must not register a map")
  }

  @Test
  fun cleaningAnIndexPreservesAnIndexWithACommonNamePrefix() {
    val firstIndexId = IndexId.create<String, String>("test:values")
    val secondIndexId = IndexId.create<String, String>("test")
    openStorage(firstIndexId).use { storage ->
      storage.addValue("key", 1, "first index")
    }
    openStorage(secondIndexId).use { storage ->
      storage.addValue("key", 2, "second index")
    }

    DurableDatabaseMapAccessors.cleanIndexData(database, firstIndexId)

    openStorage(firstIndexId).use { storage ->
      assertEquals(emptyMap<Int, String>(), read(storage), "Cleaning must remove the selected index")
    }
    openStorage(secondIndexId).use { storage ->
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
      stringAsUTF8(),
      EnumeratorStringDescriptor.INSTANCE,
    ).open().close()
    DurableDatabaseMapAccessors.forwardMapAccessor(database, indexId, 9).open().close()
    assertEquals(2, database.mapNames().size, "The test must create maps outside a current shard range")

    DurableDatabaseMapAccessors.cleanIndexData(database, indexId)
    DurableDatabaseMapAccessors.cleanIndexData(database, indexId)

    assertTrue(database.mapNames().isEmpty(), "Repeated namespace cleanup must not recreate obsolete shard maps")
  }

  private fun openStorage(indexId: IndexId<*, *> = IndexId.create<String, String>("test-values")): DurableMapIndexStorage<String, String> {
    val keyDescriptor = stringAsUTF8()
    val valueExternalizer = EnumeratorStringDescriptor.INSTANCE
    val mapAccessor = DurableDatabaseMapAccessors.valueMapAccessor(database, indexId, keyDescriptor, valueExternalizer)
    return DurableMapIndexStorage(
      mapAccessor::open,
      keyDescriptor,
      valueExternalizer,
      /* cacheSize = */ 16,
      /* keyIsUniqueForIndexedFile = */ false,
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
}
