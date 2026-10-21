// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.PathManager
import com.intellij.platform.util.io.storages.UnsupportedFormatException
import com.intellij.platform.util.io.storages.database.DurableMapImplementation
import com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_SEPARATELY
import com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_TOGETHER
import com.intellij.platform.util.io.storages.database.spi.metrics.DatabaseMetrics
import com.intellij.util.indexing.impl.storage.IndexStorageLayoutLocator
import com.intellij.util.indexing.impl.storage.durablemap.database.DurableDatabaseIndexLayoutProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.io.path.exists

@ParameterizedClass(name = "forward={0}, inverted={1}")
@MethodSource("mapImplementations")
class DurableDatabaseIndexStorageLayoutProviderTest(
  private val forwardMapImplementation: DurableMapImplementation,
  private val invertedMapImplementation: DurableMapImplementation,
) : IndexStorageLayoutProviderTestBase(
  DurableDatabaseIndexLayoutProvider(
    databasePath = PathManager.getIndexRoot().resolve("test-indexing-database-$forwardMapImplementation-$invertedMapImplementation"),
    forwardMapImplementation = forwardMapImplementation,
    invertedMapImplementation = invertedMapImplementation,
  ),
  INPUTS_COUNT_TO_TEST_WITH,
) {
  @TempDir
  private lateinit var tempDir: Path

  private val providersToClear = ArrayList<DurableDatabaseIndexLayoutProvider>()

  @AfterEach
  fun closeProvider() {
    storageLayoutProviderToTest.closeAndClearData()
    providersToClear.forEach { it.closeAndClearData() }
  }

  @Test
  fun providerIsRegisteredByPlatform() {
    val providerBean = IndexStorageLayoutLocator.supportedLayoutProviders.single { it.id == PROVIDER_ID }
    assertEquals(DurableDatabaseIndexLayoutProvider::class.java.name, providerBean.providerClass)
  }

  @Test
  fun newProviderOpensDatabaseAfterPreviousProviderCloses() {
    val databasePath = tempDir.resolve("reopened-database")
    val extension = SingleEntryIntegerValueIndexExtension()
    newProvider(databasePath).use { provider ->
      provider.getLayout(extension, emptyList()).openIndexStorage().use { it.addValue(1, 1, 42) }

      provider.close()
      provider.close()
      assertTrue(databasePath.exists(), "Closing the provider must preserve the database files")
      assertEquals(DatabaseMetrics.DUMMY, provider.databaseMetrics(), "A closed provider must detach the database")
      assertThrows(IllegalStateException::class.java) { provider.getLayout(extension, emptyList()) }
    }

    newProvider(databasePath).use { provider ->
      provider.getLayout(extension, emptyList()).openIndexStorage().use { storage ->
        var found = false
        storage.read<RuntimeException>(1) { container ->
          found = contains(container, 42, 1)
          false
        }
        assertTrue(found, "A new provider must preserve the stored value")
      }
    }
  }

  @Test
  fun newProviderOpensEmptyDatabaseAfterDataIsCleared() {
    val databasePath = tempDir.resolve("indexing-database")
    val extension = SingleEntryIntegerValueIndexExtension()
    newProvider(databasePath).use { provider ->
      provider.getLayout(extension, emptyList()).openIndexStorage().use { it.addValue(1, 1, 42) }
      assertTrue(databasePath.exists(), "The first index must create the database")

      provider.closeAndClearData()
      provider.closeAndClearData()
      assertFalse(databasePath.exists(), "The provider must remove its data after it closes the database")
      assertThrows(IllegalStateException::class.java) { provider.getLayout(extension, emptyList()) }
    }

    newProvider(databasePath).use { provider ->
      provider.getLayout(extension, emptyList()).openIndexStorage().use { storage ->
        var found = false
        storage.read<RuntimeException>(1) { container ->
          found = contains(container, 42, 1)
          false
        }
        assertFalse(found, "A new provider must not find the deleted value")
        storage.addValue(1, 1, 43)
      }
      assertTrue(databasePath.exists(), "A new provider must create the database again")
    }
  }

  @Test
  fun closedProviderCannotOpenDatabaseForTheFirstTime() {
    val databasePath = tempDir.resolve("unopened-database")
    val provider = newProvider(databasePath)
    provider.close()
    provider.close()

    assertThrows(IllegalStateException::class.java) {
      provider.getLayout(SingleEntryIntegerValueIndexExtension(), emptyList())
    }
    assertFalse(databasePath.exists(), "A closed provider must not create database files")
  }

  @Test
  fun dataCanBeClearedAfterProviderCloses() {
    val databasePath = tempDir.resolve("closed-database")
    val provider = newProvider(databasePath)
    provider.use {
      it.getLayout(SingleEntryIntegerValueIndexExtension(), emptyList()).openIndexStorage().use { storage ->
        storage.addValue(1, 1, 42)
      }
    }

    provider.closeAndClearData()
    assertFalse(databasePath.exists(), "A closed provider must still delete its data")
  }

  private fun newProvider(databasePath: Path): DurableDatabaseIndexLayoutProvider =
    DurableDatabaseIndexLayoutProvider(databasePath, forwardMapImplementation, invertedMapImplementation).also { providersToClear.add(it) }

  @Test
  fun providerPassesSelectedImplementationsToBothMapRoles() {
    val databasePath = tempDir.resolve("selected-implementation")
    val extension = ManyKeysIntegerToIntegerIndexExtension()
    DurableDatabaseIndexLayoutProvider(databasePath, KEY_VALUE_SEPARATELY, KEY_VALUE_TOGETHER).also { providersToClear.add(it) }.use { provider ->
      val layout = provider.getLayout(extension, emptyList())
      layout.openIndexStorage().use { it.addValue(1, 1, 42) }
      layout.openForwardIndex().use { }
    }

    DurableDatabaseIndexLayoutProvider(databasePath, KEY_VALUE_SEPARATELY, KEY_VALUE_SEPARATELY).also { providersToClear.add(it) }.use { provider ->
      val layout = provider.getLayout(extension, emptyList())
      assertThrows(UnsupportedFormatException::class.java) {
        layout.openIndexStorage()
      }
      layout.openForwardIndex().use { }
    }

    DurableDatabaseIndexLayoutProvider(databasePath, KEY_VALUE_TOGETHER, KEY_VALUE_TOGETHER).also { providersToClear.add(it) }.use { provider ->
      val layout = provider.getLayout(extension, emptyList())
      layout.openIndexStorage().use { }
      assertThrows(UnsupportedFormatException::class.java) { layout.openForwardIndex() }
    }
  }

  companion object {
    private const val PROVIDER_ID = "memory-mapped-database-impl"
    private const val INPUTS_COUNT_TO_TEST_WITH = 30_000

    @JvmStatic
    fun mapImplementations(): List<Arguments> =
      DurableMapImplementation.entries.flatMap { forwardImplementation ->
        DurableMapImplementation.entries.map { invertedImplementation ->
          Arguments.of(forwardImplementation, invertedImplementation)
        }
      }
  }
}
