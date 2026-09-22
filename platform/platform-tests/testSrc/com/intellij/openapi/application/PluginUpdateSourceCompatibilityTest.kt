// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.application.UpdateCheckerTestBase.Companion.CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY
import com.intellij.openapi.updateSettings.impl.PluginUpdateSource
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceService
import com.intellij.openapi.updateSettings.impl.createNightlyAndMarketplacePluginUpdateSourceId
import com.intellij.openapi.updateSettings.impl.createNightlyPluginUpdateSourceId
import com.intellij.testFramework.PlatformTestUtil.withSystemProperty
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey(key = "update.source.initialization.enabled", value = "true")
internal class PluginUpdateSourceCompatibilityTest {

  @Test
  fun `plugin update sources allow updates from compatible source type`() {
    val service = PluginUpdateSourceService.getInstance()
    val firstMarketplace = service.createMarketplacePluginUpdateSourceId()
    val secondMarketplace = service.createMarketplacePluginUpdateSourceId()
    val firstNightlyRepositoryUrl = "https://nightly.example.com"
    val secondNightlyRepositoryUrl = "https://nightly2.example.com"

    withSystemProperty<RuntimeException>(CUSTOM_BUILT_IN_PLUGIN_REPOSITORY_PROPERTY,
                                         "$firstNightlyRepositoryUrl,$secondNightlyRepositoryUrl") {
      val firstNightlyRepository = service.createCustomRepositoryPluginUpdateSourceId(firstNightlyRepositoryUrl)
      val secondNightlyRepository = service.createCustomRepositoryPluginUpdateSourceId(secondNightlyRepositoryUrl)
      val firstCustomRepository = service.createCustomRepositoryPluginUpdateSourceId("https://custom.example.com")
      val sameFirstCustomRepository = service.createCustomRepositoryPluginUpdateSourceId("https://custom.example.com")
      val secondCustomRepository = service.createCustomRepositoryPluginUpdateSourceId("https://custom2.example.com")
      val nightlyRepo = createNightlyPluginUpdateSourceId()

      assertCanInstallUpdatesFromSymmetricallyOnlyWithinLists(
        listOf(firstMarketplace, secondMarketplace),
        listOf(firstNightlyRepository, secondNightlyRepository, nightlyRepo),
        listOf(firstCustomRepository, sameFirstCustomRepository),
        listOf(secondCustomRepository),
      )

      val nightlyAndMarketplaceSource = createNightlyAndMarketplacePluginUpdateSourceId()
      for (source in listOf(firstNightlyRepository, secondNightlyRepository, nightlyRepo, firstMarketplace, secondMarketplace)) {
        assertCanInstallUpdatesFrom(nightlyAndMarketplaceSource, source, true)
      }

      for (source in listOf(firstCustomRepository, sameFirstCustomRepository, secondCustomRepository)) {
        assertCanInstallUpdatesFrom(nightlyAndMarketplaceSource, source, false)
      }
    }
  }

  private fun assertCanInstallUpdatesFrom(first: PluginUpdateSource, second: PluginUpdateSource, canInstallUpdates: Boolean) {
    val message = "Should ${if (canInstallUpdates) "" else "not "}be able to install updates: $first from $second"
    assertEquals(canInstallUpdates, first.canInstallUpdatesFrom(second), message)
  }

  private fun assertCanInstallUpdatesFromSymmetricallyOnlyWithinLists(vararg lists: List<PluginUpdateSource>) {
    for (list in lists) {
      for (firstIndex in list.indices) {
        for (secondIndex in firstIndex + 1 until list.size) {
          val first = list[firstIndex]
          val second = list[secondIndex]
          assertCanInstallUpdatesFrom(first, second, true)
          assertCanInstallUpdatesFrom(second, first, true)
        }
      }
    }

    for (firstListIndex in lists.indices) {
      for (secondListIndex in firstListIndex + 1 until lists.size) {
        for (first in lists[firstListIndex]) {
          for (second in lists[secondListIndex]) {
            assertCanInstallUpdatesFrom(first, second, false)
            assertCanInstallUpdatesFrom(second, first, false)
          }
        }
      }
    }
  }
}
