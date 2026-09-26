// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics

import com.intellij.ide.plugins.MarketplaceTabSearchSortByOptions
import com.intellij.ide.plugins.marketplace.statistics.collectors.PluginManagerFUSCollector
import com.intellij.ide.plugins.marketplace.statistics.collectors.PluginManagerMPCollector
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchFilterKind
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchQueryShape
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchSection
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchSourceKind
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class UnifiedPluginSearchStatisticsTest {
  @Test
  fun `MP collector inherits the unified schema`() {
    val group = PluginManagerMPCollector().group

    assertTrue(group.events.any { it.eventId == "unified.search" })
    val sessionFields = group.events.last { it.eventId == "session.started" }.getFields()
    assertTrue(sessionFields.any { it.name == "isUnifiedPage" })
  }

  @Test
  fun `unified search reports bounded controls sources and section counts`(@TestDisposable disposable: Disposable) {
    val collector = PluginManagerFUSCollector()
    val statistics = UnifiedPluginSearchStatistics(
      queryShape = UnifiedPluginSearchQueryShape.TEXT_AND_CONTROLS,
      filterKinds = setOf(UnifiedPluginSearchFilterKind.VENDOR, UnifiedPluginSearchFilterKind.TAG),
      sourceKinds = setOf(UnifiedPluginSearchSourceKind.LOCAL, UnifiedPluginSearchSourceKind.MARKETPLACE),
      sort = MarketplaceTabSearchSortByOptions.NAME,
      resultCounts = mapOf(
        UnifiedPluginSearchSection.INSTALLED to 4,
        UnifiedPluginSearchSection.MARKETPLACE to 2,
      ),
    )

    val events = FUCollectorTestCase.collectLogEvents(disposable) {
      collector.performUnifiedSearch(null, statistics, searchIndex = 3, sessionId = 1, searchSessionId = 2)
    }

    val event = events.single { it.event.id == "unified.search" }
    assertEquals(UnifiedPluginSearchQueryShape.TEXT_AND_CONTROLS.toString(), event.event.data["query_shape"])
    assertEquals(listOf("VENDOR", "TAG"), event.event.data["filter_kinds"])
    assertEquals(listOf("LOCAL", "MARKETPLACE"), event.event.data["source_kinds"])
    assertEquals(MarketplaceTabSearchSortByOptions.NAME.toString(), event.event.data["sort"])
    assertEquals(1, event.event.data["sessionId"])
    assertEquals(2, event.event.data["searchSessionId"])
    assertEquals(3, event.event.data["searchIndex"])
    @Suppress("UNCHECKED_CAST")
    val results = event.event.data["results"] as List<Map<String, Any>>
    assertEquals(4, results.single { it["section"] == "INSTALLED" }["result_count"])
    assertEquals(2, results.single { it["section"] == "MARKETPLACE" }["result_count"])
    assertEquals(0, results.single { it["section"] == "CUSTOM_REPOSITORY" }["result_count"])
  }
}
