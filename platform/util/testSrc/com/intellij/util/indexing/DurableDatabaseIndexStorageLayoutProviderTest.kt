// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.PathManager
import com.intellij.platform.util.io.storages.UnsupportedFormatException
import com.intellij.platform.util.io.storages.database.DurableMapImplementation
import com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_SEPARATELY
import com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_TOGETHER
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

  @AfterEach
  fun closeProvider() {
    storageLayoutProviderToTest.closeAndClearData()
  }

  @Test
  fun providerIsRegisteredByPlatform() {
    val providerBean = IndexStorageLayoutLocator.supportedLayoutProviders.single { it.id == PROVIDER_ID }
    assertEquals(DurableDatabaseIndexLayoutProvider::class.java.name, providerBean.providerClass)
  }

  @Test
  fun databaseOpensAgainAfterDataIsCleared() {
    val databasePath = tempDir.resolve("indexing-database")
    val extension = SingleEntryIntegerValueIndexExtension()
    DurableDatabaseIndexLayoutProvider(
      databasePath,
      forwardMapImplementation,
      invertedMapImplementation,
    ).use { provider ->
      provider.getLayout(extension, emptyList()).openIndexStorage().use { it.addValue(1, 1, 42) }
      assertTrue(databasePath.exists(), "The first index must create the database")

      provider.closeAndClearData()
      assertFalse(databasePath.exists(), "The provider must remove its data after it closes the database")

      provider.getLayout(extension, emptyList()).openIndexStorage().use { it.addValue(1, 1, 43) }
      assertTrue(databasePath.exists(), "The next index must open a new database")
    }
  }

  @Test
  fun providerPassesSelectedImplementationsToBothMapRoles() {
    val databasePath = tempDir.resolve("selected-implementation")
    val extension = ManyKeysIntegerToIntegerIndexExtension()
    DurableDatabaseIndexLayoutProvider(databasePath, KEY_VALUE_SEPARATELY, KEY_VALUE_TOGETHER).use { provider ->
      val layout = provider.getLayout(extension, emptyList())
      layout.openIndexStorage().use { it.addValue(1, 1, 42) }
      layout.openForwardIndex().use { }
    }

    DurableDatabaseIndexLayoutProvider(databasePath, KEY_VALUE_SEPARATELY, KEY_VALUE_SEPARATELY).use { provider ->
      val layout = provider.getLayout(extension, emptyList())
      assertThrows(UnsupportedFormatException::class.java) {
        layout.openIndexStorage()
      }
      layout.openForwardIndex().use { }
    }

    DurableDatabaseIndexLayoutProvider(databasePath, KEY_VALUE_TOGETHER, KEY_VALUE_TOGETHER).use { provider ->
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
