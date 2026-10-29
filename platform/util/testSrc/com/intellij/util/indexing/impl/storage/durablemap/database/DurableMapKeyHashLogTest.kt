// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap.database

import com.intellij.platform.util.io.storages.CommonKeyDescriptors.integer
import com.intellij.platform.util.io.storages.database.DurableDatabase
import com.intellij.platform.util.io.storages.database.DurableDatabaseFactory
import com.intellij.platform.util.io.storages.database.DurableMapImplementation
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.indexing.IdFilter
import com.intellij.util.indexing.IndexId
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.SingleEntryIntegerValueIndexExtension
import com.intellij.util.indexing.StorageException
import com.intellij.util.indexing.VfsAwareIndexStorage
import com.intellij.util.indexing.impl.ChangeTrackingValueContainer
import com.intellij.util.indexing.impl.UpdatableValueContainer
import com.intellij.util.indexing.impl.storage.durablemap.DurableMapIndexStorage
import com.intellij.util.indexing.impl.storage.durablemap.DurableMapIndexStorageFactories
import com.intellij.util.indexing.impl.storage.durablemap.FileChannelKeyHashLogAccessor
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND

/** Checks that the journal can reject keys only while it agrees with the index map. */
@TestApplication
@Timeout(30)
class DurableMapKeyHashLogTest {
  @TempDir
  lateinit var directory: Path

  private lateinit var database: DurableDatabase
  private val indexId = IndexId.create<Int, String>("key-hash-log-integration")
  private var valueReads = 0
  private var failWrites = false
  private var failFlush = false
  private var failTraversal = false
  private var failClose = false
  private var mapClosed = false
  private val externalizer = object : DataExternalizer<String> {
    override fun save(out: DataOutput, value: String) = EnumeratorStringDescriptor.INSTANCE.save(out, value)

    override fun read(input: DataInput): String {
      valueReads++
      return EnumeratorStringDescriptor.INSTANCE.read(input)
    }
  }

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
  fun filtersWithoutReadingContainersAndTracksChanges(implementation: DurableMapImplementation) {
    openStorage(implementation).use { storage ->
      storage.addValue(11, 1, "first")
      storage.addValue(11, 2, "second")
      storage.addValue(22, 2, "other key")
      storage.flush()
      valueReads = 0

      repeat(2) {
        assertEquals(setOf(11), keys(storage, 1))
        assertEquals(setOf(11, 22), keys(storage, 2))
      }
      assertEquals(0, valueReads, "Filtering must scan keys without loading value containers")
      assertFalse(storage.processKeys({ false }, GlobalSearchScope.EMPTY_SCOPE, filter(1)))

      storage.removeAllValues(11, 1)
      assertEquals(emptySet<Int>(), keys(storage, 1), "A removal must affect the next query")
      assertEquals(setOf(11, 22), keys(storage, 2), "The hash must remain present for another file")
      storage.addValue(11, 1, "added again")
      storage.updateValue(22, 2, "updated")
      assertEquals(setOf(11), keys(storage, 1))
    }
    assertFalse(Files.exists(sessionMarker), "A successful close must finish the journal session")
    openStorage(implementation).use { storage ->
      valueReads = 0
      assertEquals(setOf(11), keys(storage, 1))
      assertEquals(0, valueReads, "A clean reopen must reuse the journal")
    }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun bootstrapsAnExistingMap(implementation: DurableMapImplementation) {
    openStorage(implementation, trace = false).use { storage ->
      storage.addValue(11, 1, "before the journal")
      storage.addValue(22, 2, "another file")
    }
    assertFalse(Files.exists(dataFile), "Disabling tracing must not create journal files")
    valueReads = 0
    openStorage(implementation).use { storage ->
      assertTrue(valueReads > 0, "The first session must recover pairs from existing containers")
      assertEquals(setOf(11), keys(storage, 1))
      storage.removeAllValues(11, 1)
      assertEquals(emptySet<Int>(), keys(storage, 1))
    }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun rebuildsAfterChangesWithTracingDisabled(implementation: DurableMapImplementation) {
    openStorage(implementation).use { it.addValue(11, 1, "initial") }
    openStorage(implementation, trace = false).use { it.addValue(22, 1, "without tracing") }
    openStorage(implementation).use { storage ->
      assertEquals(setOf(11, 22), keys(storage, 1), "Enabling tracing must include changes made without a journal")
    }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun rebuildsAnInterruptedSession(implementation: DurableMapImplementation) {
    openStorage(implementation).use { storage ->
      storage.addValue(11, 1, "committed")
      storage.flush()
    }
    // Emulate a crash during compaction. The journal now misses a pair that the map retains.
    Files.createFile(sessionMarker)
    Files.write(dataFile, byteArrayOf(1, 2, 3))

    openStorage(implementation).use { storage ->
      assertEquals(setOf(11), keys(storage, 1), "A partial journal must never reject a key from the map")
    }
    assertFalse(Files.exists(sessionMarker))
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun rebuildsATruncatedJournalAfterACleanSession(implementation: DurableMapImplementation) {
    openStorage(implementation).use { it.addValue(11, 1, "retained") }
    Files.write(dataFile, byteArrayOf(1), APPEND)
    openStorage(implementation).use { storage ->
      assertEquals(setOf(11), keys(storage, 1), "An incomplete record requires a rebuild")
    }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun failedJournalScanRequiresRecovery(implementation: DurableMapImplementation) {
    openStorage(implementation).use { storage ->
      storage.addValue(11, 1, "retained")
      storage.flush()
      Files.write(dataFile, byteArrayOf())
      assertThrows(StorageException::class.java) { keys(storage, 1) }
      assertEquals(setOf(11), keys(storage, 1), "Later queries must use containers after a failed journal scan")
    }
    assertTrue(Files.exists(sessionMarker), "A failed scan must keep recovery enabled after close")
    openStorage(implementation).use { storage ->
      assertEquals(setOf(11), keys(storage, 1), "An aligned truncated journal must be rebuilt after a failed scan")
    }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun clearResetsTheMapAndTheJournal(implementation: DurableMapImplementation) {
    openStorage(implementation).use { storage ->
      storage.addValue(11, 1, "discarded")
      storage.clear()
      assertEquals(emptySet<Int>(), keys(storage, 1))
      storage.addValue(22, 2, "after clear")
      assertEquals(setOf(22), keys(storage, 2))
    }
    openStorage(implementation).use { storage ->
      assertEquals(emptySet<Int>(), keys(storage, 1))
      assertEquals(setOf(22), keys(storage, 2))
    }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun uniqueKeyUpdatesProduceOneEventEach(implementation: DurableMapImplementation) {
    openStorage(implementation, unique = true).use { storage ->
      storage.addValue(1, 1, "first")
      storage.updateValue(1, 1, "updated")
      storage.flush()
      assertEquals(16L, Files.size(dataFile), "A direct update must append one event")
      assertEquals(setOf(1), keys(storage, 1))
      storage.removeAllValues(1, 1)
      assertEquals(emptySet<Int>(), keys(storage, 1))
    }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun failedMutationDisablesFilteringUntilReopen(implementation: DurableMapImplementation) {
    openStorage(implementation, unique = true).use { storage ->
      storage.addValue(1, 1, "retained")
      failWrites = true
      assertThrows(StorageException::class.java) { storage.updateValue(1, 1, "rejected") }
      failWrites = false
      valueReads = 0
      assertEquals(setOf(1), keys(storage, 1))
      assertTrue(valueReads > 0, "An uncertain session must use the exact container filter")
    }
    assertTrue(Files.exists(sessionMarker), "A failed mutation must require recovery even if close succeeds")
    openStorage(implementation, unique = true).use { storage ->
      assertEquals(setOf(1), keys(storage, 1))
    }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun failedFlushPreservesTheSessionMarker(implementation: DurableMapImplementation) {
    openStorage(implementation).use { storage ->
      storage.addValue(11, 1, "retained")
      failFlush = true
      assertThrows(IOException::class.java) { storage.flush() }
      failFlush = false
    }
    assertTrue(Files.exists(sessionMarker), "A later successful close must not hide a flush failure")
    openStorage(implementation).use { assertEquals(setOf(11), keys(it, 1)) }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun failedRebuildClosesTheMapAndCanRetry(implementation: DurableMapImplementation) {
    openStorage(implementation, trace = false).use { it.addValue(11, 1, "retained") }
    failTraversal = true
    assertThrows(IOException::class.java) { openStorage(implementation) }
    assertTrue(mapClosed, "A failed rebuild must close the map that it opened")
    assertTrue(Files.exists(sessionMarker), "A failed rebuild must leave recovery enabled")
    failTraversal = false
    openStorage(implementation).use { assertEquals(setOf(11), keys(it, 1)) }
  }

  @ParameterizedTest
  @EnumSource(DurableMapImplementation::class)
  fun failedMapClosePreservesTheSessionMarker(implementation: DurableMapImplementation) {
    val storage = openStorage(implementation)
    storage.addValue(11, 1, "retained")
    failClose = true
    assertThrows(IOException::class.java) { storage.close() }
    failClose = false
    assertTrue(Files.exists(sessionMarker), "The journal must not finish before the map closes successfully")
    openStorage(implementation).use { assertEquals(setOf(11), keys(it, 1)) }
  }

  @Test
  fun registrySeparatesIndexesAndRemovesObsoleteShards() {
    val otherIndex = IndexId.create<Int, String>(indexId.name + ":child")
    FileChannelKeyHashLogAccessor.Registry(directory.resolve("logs")).use { registry ->
      val first = registry.accessor(indexId, 9, integer())
      first.open().addKeyHashToVirtualFileMapping(11, 1)
      first.close(true)
      val other = registry.accessor(otherIndex, 9, integer())
      other.open().addKeyHashToVirtualFileMapping(22, 2)
      other.close(true)
      registry.cleanIndexData(indexId)
      registry.cleanIndexData(indexId)

      val removed = registry.accessor(indexId, 9, integer())
      removed.open()
      assertTrue(removed.requiresRebuild(), "Cleanup must remove journals for obsolete shard numbers")
      removed.close(false)
      val retained = registry.accessor(otherIndex, 9, integer())
      val log = retained.open()
      assertFalse(retained.requiresRebuild(), "Cleanup must preserve an index with a common name prefix")
      assertTrue(log.getSuitableKeyHashes(filter(2)).contains(22))
      retained.close(true)
    }
  }

  @Test
  fun providerClosesAnOutstandingJournalAndRebuildsItOnReopen() {
    val databasePath = directory.resolve("provider-database")
    val extension = object : SingleEntryIntegerValueIndexExtension() {
      override fun traceKeyHashToVirtualFileMapping(): Boolean = true
    }
    DurableDatabaseIndexLayoutProvider(databasePath, maintenanceService = { null }).use { provider ->
      val storage = provider.getLayout(extension, emptyList()).openIndexStorage()
      storage.addValue(1, 1, 42)
      storage.flush()
      provider.close()
      storage.close()
    }
    val marker = Files.walk(databasePath.resolve("key-hash-logs")).use { paths ->
      paths.filter { it.fileName.toString().endsWith(".session") }.toList().single()
    }
    assertTrue(Files.exists(marker), "Provider closure must keep recovery enabled for an outstanding storage")
    DurableDatabaseIndexLayoutProvider(databasePath, maintenanceService = { null }).use { provider ->
      (provider.getLayout(extension, emptyList()).openIndexStorage() as VfsAwareIndexStorage<Int, Int>).use { storage ->
        val keys = mutableSetOf<Int>()
        assertTrue(storage.processKeys({ keys.add(it); true }, GlobalSearchScope.EMPTY_SCOPE, filter(1)))
        assertEquals(setOf(1), keys, "The new provider must recover the committed key")
      }
    }
    assertFalse(Files.exists(marker), "A successful recovery must finish the journal session")
  }

  @Test
  fun fileMapOpensAndCleansItsChannelJournal() {
    val file = directory.resolve("file-index")
    fun open(trace: Boolean = true) = DurableMapIndexStorage(
      file, integer(), externalizer, 16, false, trace,
      FileChannelKeyHashLogAccessor(FileChannelKeyHashLogAccessor.baseFileFor(DurableMapIndexStorage.getIndexStorageFile(file)), integer())
    )
    open().use { storage ->
      storage.addValue(11, 1, "retained")
      assertEquals(setOf(11), keys(storage, 1))
    }
    open().use { storage -> assertEquals(setOf(11), keys(storage, 1)) }
    open(trace = false).use { storage ->
      storage.addValue(22, 1, "without tracing")
    }
    open().use { storage ->
      assertEquals(setOf(11, 22), keys(storage, 1), "A file map must also recover changes made without tracing")
    }
    val journal = directory.resolve("file-index.storage.keyhash-channel.project")
    assertTrue(Files.exists(journal), "A file map must use the channel journal")
    DurableMapIndexStorageFactories.fileBasedMapCleaner().cleanData(DurableMapIndexStorage.getIndexStorageFile(file))
    assertFalse(Files.exists(journal), "The map cleaner must also remove its journal")
    open().use { storage -> assertEquals(emptySet<Int>(), keys(storage, 1)) }
  }

  private fun openStorage(
    implementation: DurableMapImplementation,
    trace: Boolean = true,
    unique: Boolean = false,
    accessor: FileChannelKeyHashLogAccessor<Int> = FileChannelKeyHashLogAccessor(baseFile, integer()),
  ): DurableMapIndexStorage<Int, String> {
    val mapAccessor = DurableDatabaseMapAccessors.valueMapAccessor(database, indexId, implementation, integer(), externalizer)
    return DurableMapIndexStorage(
      {
        val map = mapAccessor.open()
        mapClosed = false
        object : PatchableDurableMap<Int, UpdatableValueContainer<String>, ChangeTrackingValueContainer<String>> by map {
          override fun put(key: Int, value: UpdatableValueContainer<String>?) {
            if (failWrites) throw IOException("Injected write failure")
            map.put(key, value)
          }

          override fun force() {
            if (failFlush) throw IOException("Injected flush failure")
            map.force()
          }

          override fun isDirty(): Boolean = failFlush || map.isDirty

          override fun forEachEntry(processor: java.util.function.BiPredicate<in Int, in UpdatableValueContainer<String>>): Boolean {
            if (failTraversal) throw IOException("Injected traversal failure")
            return map.forEachEntry(processor)
          }

          override fun close() {
            map.close()
            mapClosed = true
            if (failClose) throw IOException("Injected close failure")
          }
        }
      },
      integer(), externalizer, 16, unique, trace, accessor,
    )
  }

  private fun keys(storage: DurableMapIndexStorage<Int, String>, inputId: Int): Set<Int> {
    val result = mutableSetOf<Int>()
    assertTrue(storage.processKeys({ result.add(it); true }, GlobalSearchScope.EMPTY_SCOPE, filter(inputId)))
    return result
  }

  private fun filter(inputId: Int): IdFilter = object : IdFilter() {
    override fun containsFileId(id: Int): Boolean = id == inputId
  }

  private val baseFile: Path get() = directory.resolve("key-hashes")
  private val dataFile: Path get() = directory.resolve("key-hashes.project")
  private val sessionMarker: Path get() = directory.resolve("key-hashes.session")
}
