// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.PathManager
import com.intellij.util.indexing.impl.storage.IndexStorageLayoutLocator
import com.intellij.util.indexing.impl.storage.durablemap.database.DurableDatabaseIndexLayoutProvider
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DurableDatabaseIndexStorageLayoutProviderTest : IndexStorageLayoutProviderTestBase(
  PROVIDER,
  INPUTS_COUNT_TO_TEST_WITH,
) {
  @Test
  fun providerIsRegisteredByPlatform() {
    val providerBean = IndexStorageLayoutLocator.supportedLayoutProviders.single { it.id == PROVIDER_ID }
    assertEquals(DurableDatabaseIndexLayoutProvider::class.java.name, providerBean.providerClass)
  }

  companion object {
    private const val PROVIDER_ID = "memory-mapped-database-impl"
    private const val INPUTS_COUNT_TO_TEST_WITH = 30_000
    private val PROVIDER = DurableDatabaseIndexLayoutProvider(
      databasePath = PathManager.getIndexRoot().resolve("test-indexing-database")
    )

    @JvmStatic
    @AfterAll
    fun closeProvider() {
      PROVIDER.close()
    }
  }
}
