// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.metadata.filter

import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.eventLog.filters.LogEventCompositeFilter
import com.intellij.internal.statistic.eventLog.filters.LogEventFilter
import com.intellij.internal.statistic.eventLog.filters.LogEventSnapshotBuildFilter
import com.intellij.internal.statistic.eventLog.filters.LogEventMetadataFilter
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupsFilterRules
import com.intellij.internal.statistics.StatisticsTestEventFactory.newEvent
import com.intellij.internal.statistics.logger.TestDataCollectorDebugLogger
import com.intellij.openapi.util.io.FileUtil
import org.junit.Test
import kotlin.test.assertEquals

class FeatureEventLogMetadataFilterTest {

  @Test
  fun `test empty metadata`() {
    val all = ArrayList<LogEvent>()
    all.add(newEvent("group-id", "first"))
    all.add(newEvent("group-id-1", "second"))
    all.add(newEvent("group-id-2", "third"))

    testGroupFilteRules(EventGroupsFilterRules.empty(), all, ArrayList())
  }

  @Test
  fun `test metadata with build and group version`() {
    val event = newEvent("group-id", "first", groupVersion = "4", build = "173.23")

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "173.20.132", null)
    rulesBuilder.addVersion("group-id", "2", "10")
    testGroupFilteRules(rulesBuilder.build(), listOf(event), listOf(event))
  }

  @Test
  fun `test metadata only with build`() {
    val event = newEvent("group-id", "first", groupVersion = "4", build = "173.23")

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "173.20.132", null)
    testGroupFilteRules(rulesBuilder.build(), listOf(event), listOf(event))
  }

  @Test
  fun `test metadata only with group version`() {
    val event = newEvent("group-id", "first", groupVersion = "4", build = "173.23")

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addVersion("group-id", "2", "10")
    testGroupFilteRules(rulesBuilder.build(), listOf(event), listOf(event))
  }

  @Test
  fun `test metadata without build and group version`() {
    val event = newEvent("group-id", "first", groupVersion = "4", build = "173.23")

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addGroup("group-id")
    testGroupFilteRules(rulesBuilder.build(), listOf(event), emptyList())
  }

  @Test
  fun `test metadata without versions`() {
    val first = newEvent("group-id", "first", build = "173.23")
    val second = newEvent("group-id-1", "second", build = "173.23")
    val third = newEvent("group-id", "third", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(third)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "173.20.132", null)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test metadata with multi groups`() {
    val first = newEvent("group-id", "first", build = "173.23")
    val second = newEvent("group-id-1", "second", build = "173.23")
    val third = newEvent("group-id-2", "third", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(third)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "173.20.132", null)
    rulesBuilder.addBuild("group-id-2", "173.20.132", "173.24.132")
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test metadata all groups`() {
    val first = newEvent("group-id", "first", build = "173.23")
    val second = newEvent("group-id-1", "second", build = "173.23")
    val third = newEvent("group-id-2", "third", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)
    filtered.add(third)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", null, "182.0")
    rulesBuilder.addBuild("group-id-1", null, "182.0")
    rulesBuilder.addBuild("group-id-2", null, "182.0")
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test metadata with versions from`() {
    val first = newEvent("group-id", "first", groupVersion = "1")
    val second = newEvent("group-id", "third", groupVersion = "3")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(second)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addVersion("group-id", "2", null)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test metadata with versions exact from`() {
    val first = newEvent("group-id", "first", groupVersion = "1")
    val second = newEvent("group-id", "third", groupVersion = "2")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(second)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addVersion("group-id", "2", null)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test metadata with versions to`() {
    val first = newEvent("group-id", "first", groupVersion = "1")
    val second = newEvent("group-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addVersion("group-id", null, "3")
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test metadata with versions exact to`() {
    val first = newEvent("group-id", "first", groupVersion = "1")
    val second = newEvent("group-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addVersion("group-id", null, "4")
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test metadata with accept all versions`() {
    val first = newEvent("group-id", "first", groupVersion = "1")
    val second = newEvent("group-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addVersion("group-id", null, null)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test metadata with empty versions list`() {
    val first = newEvent("group-id", "first", groupVersion = "1", build = "181.0")
    val second = newEvent("group-id", "third", groupVersion = "4", build = "181.0")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", null, "182.0")
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test metadata with versions from and to`() {
    val first = newEvent("group-id", "first", groupVersion = "2")
    val second = newEvent("group-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addVersion("group-id", "1", "5")
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test metadata with versions exact from and to`() {
    val first = newEvent("group-id", "first", groupVersion = "1")
    val second = newEvent("group-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addVersion("group-id", "1", "5")
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test metadata with versions from and exact to`() {
    val first = newEvent("group-id", "first", groupVersion = "2")
    val second = newEvent("group-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addVersion("group-id", "1", "4")
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test metadata with complimentary multi versions`() {
    val first = newEvent("group-id", "first", groupVersion = "2")
    val second = newEvent("group-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addVersion("group-id", "1", "4").addVersion("group-id", "4", "5")
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test metadata with intersected multi versions`() {
    val first = newEvent("group-id", "first", groupVersion = "2")
    val second = newEvent("group-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addVersion("group-id", "1", "4").addVersion("group-id", "3", "5")
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test metadata with incomplete multi versions`() {
    val first = newEvent("group-id", "first", groupVersion = "2")
    val second = newEvent("group-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addVersion("group-id", null, "3").addVersion("group-id", "5", null)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test metadata with range and all range version`() {
    val first = newEvent("group-id", "first", groupVersion = "2")
    val second = newEvent("group-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addVersion("group-id", null, "2").addVersion("group-id", null, null)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter snapshot builds`() {
    val first = newEvent("group-id", "first", build = "999.9999")
    val second = newEvent("group-id-1", "second", build = "999.0")
    val third = newEvent("group-id", "third", build = "999.9999")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(third)

    testSnapshotBuilderFilter(all, filtered)
  }

  @Test
  fun `test filter none snapshot builds`() {
    val first = newEvent("group-id", "first", build = "999.9999")
    val second = newEvent("group-id-1", "second", build = "999.01")
    val third = newEvent("group-id", "third", build = "999.9999")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    testSnapshotBuilderFilter(all, all)
  }

  @Test
  fun `test filter all snapshot builds`() {
    val first = newEvent("group-id", "first", build = "999.00")
    val second = newEvent("group-id-1", "second", build = "999.0")
    val third = newEvent("group-id", "third", build = "999.0")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    testSnapshotBuilderFilter(all, ArrayList())
  }

  @Test
  fun `test filter group id and snapshot builds`() {
    val first = newEvent("group-id", "first", build = "999.9999")
    val second = newEvent("group-id-1", "second", build = "999.9999")
    val third = newEvent("group-id", "third", build = "999.0")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "999.99", null)
    testMetadataAndSnapshotBuildFilter(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter group id, version from and snapshot builds`() {
    val first = newEvent("group-id", "first", build = "999.9999", groupVersion = "3")
    val second = newEvent("group-id", "second", build = "999.9999", groupVersion = "2")
    val third = newEvent("group-id", "third", build = "999.0", groupVersion = "4")
    val forth = newEvent("group-id-1", "forth", build = "999.9999", groupVersion = "5")
    val fifth = newEvent("group-id-2", "fifth", build = "999.9999", groupVersion = "1")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    all.add(forth)
    all.add(fifth)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addVersion("group-id", "3", null)
    testMetadataAndSnapshotBuildFilter(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter group id, version to and snapshot builds`() {
    val first = newEvent("group-id", "first", build = "999.9999", groupVersion = "3")
    val second = newEvent("group-id", "second", build = "999.9999", groupVersion = "2")
    val third = newEvent("group-id", "third", build = "999.0", groupVersion = "4")
    val forth = newEvent("group-id-1", "forth", build = "999.9999", groupVersion = "5")
    val fifth = newEvent("group-id-2", "fifth", build = "999.9999", groupVersion = "1")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    all.add(forth)
    all.add(fifth)
    val filtered = ArrayList<LogEvent>()
    filtered.add(second)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addVersion("group-id", null, "3")
    testMetadataAndSnapshotBuildFilter(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter group id, version from and to and snapshot builds`() {
    val first = newEvent("group-id", "first", build = "999.9999", groupVersion = "3")
    val second = newEvent("group-id", "second", build = "999.9999", groupVersion = "2")
    val third = newEvent("group-id", "third", build = "999.0", groupVersion = "4")
    val forth = newEvent("group-id-1", "forth", build = "999.9999", groupVersion = "5")
    val fifth = newEvent("group-id-2", "fifth", build = "999.9999", groupVersion = "1")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    all.add(forth)
    all.add(fifth)
    val filtered = ArrayList<LogEvent>()
    filtered.add(second)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addVersion("group-id", "1", "3")
    testMetadataAndSnapshotBuildFilter(rulesBuilder.build(), all, filtered)
  }

  // test build ranges
  @Test
  fun `test filter build from with build short the same`() {
    val first = newEvent("group-id", "first", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "173.23", null)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter build from with build long the same`() {
    val first = newEvent("group-id", "first", build = "173.23.435")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "173.23.435", null)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter build from with short build and bugfix build after`() {
    val first = newEvent("group-id", "first", build = "173.232.1")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "173.232", null)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter build from with no bugfix build`() {
    val first = newEvent("group-id", "first", build = "173.232")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "173.232.1", null)
    testGroupFilteRules(rulesBuilder.build(), all, ArrayList())
  }

  @Test
  fun `test filter build from with major build after`() {
    val first = newEvent("group-id", "first", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "172.20.132", null)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter build from with minor build after`() {
    val first = newEvent("group-id", "first", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "173.20.132", null)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter build from with bugfix build after`() {
    val first = newEvent("group-id", "first", build = "173.23.15")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "173.23.13", null)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter build from with major build before`() {
    val first = newEvent("group-id", "first", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "181.20.132", null)
    testGroupFilteRules(rulesBuilder.build(), all, ArrayList())
  }

  @Test
  fun `test filter build from with minor build before`() {
    val first = newEvent("group-id", "first", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "173.203.132", null)
    testGroupFilteRules(rulesBuilder.build(), all, ArrayList())
  }

  @Test
  fun `test filter build from with bugfix build before`() {
    val first = newEvent("group-id", "first", build = "173.23.15")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "173.23.35", null)
    testGroupFilteRules(rulesBuilder.build(), all, ArrayList())
  }

  @Test
  fun `test filter build from with snapshot build after`() {
    val first = newEvent("group-id", "first", build = "172.340")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "181.0", null)
    testGroupFilteRules(rulesBuilder.build(), all, ArrayList())
  }

  @Test
  fun `test filter build from with snapshot build before`() {
    val first = newEvent("group-id", "first", build = "181.34")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "181.0", null)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter build to with build short the same`() {
    val first = newEvent("group-id", "first", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", null, "173.23")
    testGroupFilteRules(rulesBuilder.build(), all, ArrayList())
  }

  @Test
  fun `test filter build to with build long the same`() {
    val first = newEvent("group-id", "first", build = "173.23.234")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", null, "173.23.234")
    testGroupFilteRules(rulesBuilder.build(), all, ArrayList())
  }

  @Test
  fun `test filter build to with major build before`() {
    val first = newEvent("group-id", "first", build = "172.22")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", null, "173.23.234")
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter build to with minor build before`() {
    val first = newEvent("group-id", "first", build = "173.22")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", null, "173.23.234")
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter build to with bugfix build before`() {
    val first = newEvent("group-id", "first", build = "173.23.201")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", null, "173.23.234")
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter build to with major build after`() {
    val first = newEvent("group-id", "first", build = "183.23.201")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", null, "173.23.234")
    testGroupFilteRules(rulesBuilder.build(), all, ArrayList())
  }

  @Test
  fun `test filter build to with minor build after`() {
    val first = newEvent("group-id", "first", build = "183.345.201")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", null, "183.23.234")
    testGroupFilteRules(rulesBuilder.build(), all, ArrayList())
  }

  @Test
  fun `test filter build to with bugfix build after`() {
    val first = newEvent("group-id", "first", build = "183.345.201")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", null, "183.345.12")
    testGroupFilteRules(rulesBuilder.build(), all, ArrayList())
  }

  @Test
  fun `test filter build from and to with major build between`() {
    val first = newEvent("group-id", "first", build = "182.345.201")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "181.345.12", "183.345.12")
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter build from and to with minor build between`() {
    val first = newEvent("group-id", "first", build = "183.45.201")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "183.35.12", "183.345.12")
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter build from and to with bugfix build between`() {
    val first = newEvent("group-id", "first", build = "183.35.21")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "183.35.12", "183.35.120")
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter group and build from with build after`() {
    val first = newEvent("group-id", "first", build = "183.35.21")
    val second = newEvent("group-id-1", "first", build = "183.35.21")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "183.35.12", null)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter group and build from with build before and after`() {
    val first = newEvent("group-id", "first", build = "183.35.21")
    val second = newEvent("group-id-1", "first", build = "183.35.21")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "183.35.12", null)
    rulesBuilder.addBuild("group-id-1", "183.35.32", null)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter group and build from with both build before`() {
    val first = newEvent("group-id", "first", build = "183.35.21")
    val second = newEvent("group-id-1", "first", build = "183.35.21")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "183.35.12", null)
    rulesBuilder.addBuild("group-id-1", "181.35.32", null)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  @Test
  fun `test filter group and build from with both build after`() {
    val first = newEvent("group-id", "first", build = "181.35.21")
    val second = newEvent("group-id-1", "first", build = "181.13")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "183.35.12", null)
    rulesBuilder.addBuild("group-id-1", "181.35.32", null)
    testGroupFilteRules(rulesBuilder.build(), all, ArrayList())
  }

  @Test
  fun `test filter version and build`() {
    val first = newEvent("group-id", "first", groupVersion = "3", build = "182.312")
    val second = newEvent("group-id", "first", groupVersion = "11", build = "181.3")
    val third = newEvent("group-id", "first", groupVersion = "5", build = "181.123")
    val forth = newEvent("group-id", "first", groupVersion = "4", build = "181.32")
    val fifth = newEvent("group-id", "first", groupVersion = "3", build = "183.113.341")
    val sixth = newEvent("group-id", "first", groupVersion = "3", build = "191.13.341")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    all.add(forth)
    all.add(fifth)
    all.add(sixth)

    val filtered = ArrayList<LogEvent>()
    filtered.add(forth)
    filtered.add(fifth)
    filtered.add(sixth)

    val rulesBuilder = TestGroupFilterRulesBuilder()
    rulesBuilder.addBuild("group-id", "181.12", "182.312")
    rulesBuilder.addBuild("group-id", "183.35.12", null)
    rulesBuilder.addVersion("group-id", 3, 5)
    rulesBuilder.addVersion("group-id", 11, Int.MAX_VALUE)
    testGroupFilteRules(rulesBuilder.build(), all, filtered)
  }

  private fun testGroupFilteRules(rules: EventGroupsFilterRules<EventLogBuild>, all: List<LogEvent>, filtered: List<LogEvent>) {
    testMetadataFilter(all, filtered, LogEventMetadataFilter(rules))
  }

  private fun testMetadataAndSnapshotBuildFilter(rules: EventGroupsFilterRules<EventLogBuild>, all: List<LogEvent>, filtered: List<LogEvent>) {
    testMetadataFilter(all, filtered, LogEventCompositeFilter(LogEventMetadataFilter(rules), LogEventSnapshotBuildFilter))
  }

  private fun testSnapshotBuilderFilter(all: List<LogEvent>, filtered: List<LogEvent>) {
    testMetadataFilter(all, filtered, LogEventSnapshotBuildFilter)
  }

  private fun testMetadataFilter(all: List<LogEvent>, filtered: List<LogEvent>, filter: LogEventFilter) {
    val records = ArrayList<LogEventRecord>()
    if (filtered.isNotEmpty()) {
      records.add(LogEventRecord(filtered))
    }
    val expected = LogEventRecordRequest("recorder-id", "IU", "user-id", records, false)

    val log = FileUtil.createTempFile("feature-event-log", ".log")
    try {
      val out = StringBuilder()
      for (event in all) {
        out.append(LogEventSerializer.toString(event)).append("\n")
      }
      FileUtil.writeToFile(log, out.toString())
      val actual = LogEventRecordRequest.create(
        log, "recorder-id", "IU", "user-id", 600, filter, false, TestDataCollectorDebugLogger
      )
      assertEquals(expected, actual)
    }
    finally {
      FileUtil.delete(log)
    }
  }
}