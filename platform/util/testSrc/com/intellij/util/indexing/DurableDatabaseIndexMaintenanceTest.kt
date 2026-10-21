// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.indexing.IndexStorageLayoutProviderTestBase.SingleEntryIntegerValueIndexExtension
import com.intellij.util.indexing.impl.storage.IndexMaintenance
import com.intellij.util.indexing.impl.storage.durablemap.database.DurableDatabaseIndexLayoutProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
internal class DurableDatabaseIndexMaintenanceTest {
  @TempDir
  private lateinit var tempDir: Path

  @Test
  fun `the first layout registers its provider and closing releases it once`() {
    var registrations = 0
    var releases = 0
    var registeredProvider: DurableDatabaseIndexLayoutProvider? = null
    val maintenance = object : IndexMaintenance<DurableDatabaseIndexLayoutProvider> {
      override fun registerForMaintenance(provider: DurableDatabaseIndexLayoutProvider): AutoCloseable {
        registrations++
        registeredProvider = provider
        return AutoCloseable { releases++ }
      }
    }
    DurableDatabaseIndexLayoutProvider(tempDir.resolve("database"), maintenanceService = { maintenance }).use { provider ->
      assertEquals(0, registrations, "Constructing a provider must not start maintenance")
      provider.isSupported()
      provider.isApplicable(SingleEntryIntegerValueIndexExtension())
      assertEquals(0, registrations, "Selecting a provider must not start maintenance")

      provider.getLayout(SingleEntryIntegerValueIndexExtension(), emptyList())
      provider.getLayout(SingleEntryIntegerValueIndexExtension(), emptyList())
      assertEquals(1, registrations, "All layouts of one provider must share a registration")
      assertSame(provider, registeredProvider, "Maintenance must receive the provider for future metric reads")

      provider.close()
      provider.closeAndClearData()
      assertEquals(1, releases, "Repeated closing and data removal must release the registration once")
    }
    assertEquals(1, releases)
  }

  @Test
  fun `a database open failure does not request maintenance`() {
    val blockedPath = tempDir.resolve("file")
    Files.writeString(blockedPath, "Not a directory")
    var maintenanceRequested = false
    DurableDatabaseIndexLayoutProvider(blockedPath.resolve("database"), maintenanceService = {
      maintenanceRequested = true
      null
    }).use { provider ->
      assertThrows(Exception::class.java) { provider.getLayout(SingleEntryIntegerValueIndexExtension(), emptyList()) }
      assertFalse(maintenanceRequested, "A failed database must not enable maintenance")
    }
  }

  @Test
  fun `a registration close failure still closes the database`() {
    val databasePath = tempDir.resolve("database")
    var releases = 0
    val maintenance = object : IndexMaintenance<DurableDatabaseIndexLayoutProvider> {
      override fun registerForMaintenance(provider: DurableDatabaseIndexLayoutProvider): AutoCloseable = AutoCloseable {
        releases++
        throw IOException("Cannot release the registration")
      }
    }
    DurableDatabaseIndexLayoutProvider(databasePath, maintenanceService = { maintenance }).use { provider ->
      provider.getLayout(SingleEntryIntegerValueIndexExtension(), emptyList())
      assertThrows(IOException::class.java) { provider.close() }
      provider.close()
      assertEquals(1, releases, "A failed release must not leave the handle attached to the provider")

      DurableDatabaseIndexLayoutProvider(databasePath, maintenanceService = { null }).use { replacement ->
        replacement.getLayout(SingleEntryIntegerValueIndexExtension(), emptyList())
      }
    }
  }

  @Test
  fun `the provider works without a maintenance service`() {
    DurableDatabaseIndexLayoutProvider(tempDir.resolve("database"), maintenanceService = { null }).use { provider ->
      provider.getLayout(SingleEntryIntegerValueIndexExtension(), emptyList()).openIndexStorage().use { storage ->
        storage.addValue(1, 1, 42)
      }
    }
  }

  @Test
  fun `the default lookup disables maintenance in tests`() {
    assertNull(IndexMaintenance.getInstance<DurableDatabaseIndexLayoutProvider>(), "Tests must not start the production observer")
  }
}
