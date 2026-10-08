// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics

import com.intellij.ide.plugins.MarketplaceTabSearchSortByOptions
import com.intellij.ide.plugins.marketplace.statistics.collectors.PluginManagerFUSCollector
import com.intellij.ide.plugins.marketplace.statistics.collectors.PluginManagerMPCollector
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchFilterKind
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchQueryShape
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchSection
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchSourceKind
import com.intellij.ide.plugins.unified.PluginListModelData
import com.intellij.ide.plugins.unified.PluginsQueryState
import com.intellij.ide.plugins.unified.UnifiedPluginQueryAttribute
import com.intellij.ide.plugins.unified.UnifiedPluginQueryCommand
import com.intellij.ide.plugins.unified.UnifiedPluginsPageSourceState
import com.intellij.ide.plugins.unified.unifiedPluginSearchStatistics
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.eventLog.events.StringListEventField
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
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
  fun `unified schemas validate precise filter kinds`() {
    for (recorder in listOf("FUS", "MP")) {
      val group = collector(recorder).group
      val field = group.events.last { it.eventId == "unified.search" }.getFields()
        .filterIsInstance<StringListEventField>().single { it.name == "filter_kinds" }

      assertEquals(if (recorder == "FUS") 14 else 18, group.version)
      assertEquals(
        listOf("{enum:VENDOR|CATEGORY|TAG|REPOSITORY|UPDATE_SOURCE|OUTDATED|ENABLED|DISABLED|INVALID|UPDATED_BUNDLED|" +
               "BUNDLED|USER_INSTALLED|DOWNLOADED}"),
        field.validationRule,
      )
    }
  }

  @Test
  fun `unified search reports bounded controls sources and section counts`(@TestDisposable disposable: Disposable) {
    for (recorder in listOf("FUS", "MP")) {
      val recorderDisposable = Disposer.newDisposable(disposable, recorder)
      try {
        assertUnifiedSearch(recorder, recorderDisposable)
      }
      finally {
        Disposer.dispose(recorderDisposable)
      }
    }
  }

  private fun assertUnifiedSearch(recorder: String, disposable: Disposable) {
    val collector = collector(recorder)
    val statistics = UnifiedPluginSearchStatistics(
      queryShape = UnifiedPluginSearchQueryShape.TEXT_AND_CONTROLS,
      filterKinds = setOf(
        UnifiedPluginSearchFilterKind.VENDOR,
        UnifiedPluginSearchFilterKind.TAG,
        UnifiedPluginSearchFilterKind.DISABLED,
        UnifiedPluginSearchFilterKind.BUNDLED,
        UnifiedPluginSearchFilterKind.UPDATE_SOURCE,
      ),
      sourceKinds = setOf(UnifiedPluginSearchSourceKind.LOCAL, UnifiedPluginSearchSourceKind.MARKETPLACE),
      sort = MarketplaceTabSearchSortByOptions.NAME,
      resultCounts = mapOf(
        UnifiedPluginSearchSection.INSTALLED to 4,
        UnifiedPluginSearchSection.MARKETPLACE to 2,
      ),
    )

    val events = FUCollectorTestCase.collectLogEvents(recorder, disposable, escapeChars = true) {
      collector.performUnifiedSearch(null, statistics, searchIndex = 3, sessionId = 1, searchSessionId = 2)
    }

    val event = events.single { it.event.id == "unified.search" }
    assertEquals(UnifiedPluginSearchQueryShape.TEXT_AND_CONTROLS.toString(), event.event.data["query_shape"])
    assertEquals(listOf("VENDOR", "TAG", "DISABLED", "BUNDLED", "UPDATE_SOURCE"), event.event.data["filter_kinds"])
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

  @Test
  fun `filter kinds distinguish commands with and without text`() {
    val filters = mapOf(
      "/outdated" to UnifiedPluginSearchFilterKind.OUTDATED,
      "/enabled" to UnifiedPluginSearchFilterKind.ENABLED,
      "/disabled" to UnifiedPluginSearchFilterKind.DISABLED,
      "/invalid" to UnifiedPluginSearchFilterKind.INVALID,
      "/updatedBundled" to UnifiedPluginSearchFilterKind.UPDATED_BUNDLED,
      "/bundled" to UnifiedPluginSearchFilterKind.BUNDLED,
      "/userInstalled" to UnifiedPluginSearchFilterKind.USER_INSTALLED,
      "/downloaded" to UnifiedPluginSearchFilterKind.DOWNLOADED,
    )
    for ((command, kind) in filters) {
      for (text in listOf("", "Private query ")) {
        val query = text + command
        val statistics = statisticsFor(query)
        assertEquals(setOf(kind), statistics.filterKinds, query)
        val shape = if (text.isEmpty()) UnifiedPluginSearchQueryShape.CONTROLS_ONLY else UnifiedPluginSearchQueryShape.TEXT_AND_CONTROLS
        assertEquals(shape, statistics.queryShape, query)
      }
    }
  }

  @Test
  fun `filter kinds include attribute families without values`() {
    val filters = mapOf(
      UnifiedPluginQueryAttribute.Vendor to UnifiedPluginSearchFilterKind.VENDOR,
      UnifiedPluginQueryAttribute.Category to UnifiedPluginSearchFilterKind.CATEGORY,
      UnifiedPluginQueryAttribute.Tag to UnifiedPluginSearchFilterKind.TAG,
      UnifiedPluginQueryAttribute.Repository to UnifiedPluginSearchFilterKind.REPOSITORY,
      UnifiedPluginQueryAttribute.UpdateSource to UnifiedPluginSearchFilterKind.UPDATE_SOURCE,
    )
    val controls = filters.keys.joinToString(" ") { it.prefix + "\"Private value\"" }
    for (text in listOf("", "Private query ")) {
      val query = text + controls
      val statistics = statisticsFor(query)
      assertEquals(filters.values.toSet(), statistics.filterKinds, query)
      val shape = if (text.isEmpty()) UnifiedPluginSearchQueryShape.CONTROLS_ONLY else UnifiedPluginSearchQueryShape.TEXT_AND_CONTROLS
      assertEquals(shape, statistics.queryShape, query)
    }
  }

  @Test
  fun `filter kinds keep only effective installed state and deduplicate commands`() {
    val statistics = statisticsFor("/enabled /disabled /enabled /invalid /bundled /downloaded /bundled")

    assertEquals(
      setOf(UnifiedPluginSearchFilterKind.INVALID, UnifiedPluginSearchFilterKind.BUNDLED, UnifiedPluginSearchFilterKind.DOWNLOADED),
      statistics.filterKinds,
    )
  }

  @Test
  fun `source modes and sort do not add filter kinds`() {
    val commands = listOf(UnifiedPluginQueryCommand.Suggested, UnifiedPluginQueryCommand.StaffPicks, UnifiedPluginQueryCommand.Internal)
      .joinToString(" ") { it.command }
    val statistics = statisticsFor("$commands ${UnifiedPluginQueryAttribute.Sort.prefix}name")

    assertTrue(statistics.filterKinds.isEmpty())
    assertEquals(MarketplaceTabSearchSortByOptions.NAME, statistics.sort)
  }

  private fun collector(recorder: String): PluginManagerFUSCollector = when (recorder) {
    "FUS" -> PluginManagerFUSCollector()
    "MP" -> PluginManagerMPCollector()
    else -> error("Unexpected recorder: $recorder")
  }

  private fun statisticsFor(query: String): UnifiedPluginSearchStatistics = unifiedPluginSearchStatistics(
    UnifiedPluginsPageSourceState(
      query = PluginsQueryState(query, query),
      sections = emptyList(),
      listModelData = PluginListModelData.EMPTY,
      repositoryPlugins = emptyList(),
      mayEstablishSelection = true,
    )
  )
}
