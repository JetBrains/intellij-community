// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.service.fus.FUSWhitelist
import com.intellij.openapi.util.io.FileUtil
import org.junit.Test
import kotlin.test.assertEquals

class FeatureEventLogWhitelistFilterTest {

  @Test
  fun `test empty whitelist`() {
    val all = ArrayList<LogEvent>()
    all.add(newEvent("recorder-id", "first"))
    all.add(newEvent("recorder-id-1", "second"))
    all.add(newEvent("recorder-id-2", "third"))

    testWhitelistFilter(FUSWhitelist.empty(), all, ArrayList())
  }

  @Test
  fun `test whitelist without versions`() {
    val first = newEvent("recorder-id", "first")
    val second = newEvent("recorder-id-1", "second")
    val third = newEvent("recorder-id", "third")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(third)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id")
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist with multi groups`() {
    val first = newEvent("recorder-id", "first")
    val second = newEvent("recorder-id-1", "second")
    val third = newEvent("recorder-id-2", "third")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(third)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id")
    whitelist.add("recorder-id-2")
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist all groups`() {
    val first = newEvent("recorder-id", "first")
    val second = newEvent("recorder-id-1", "second")
    val third = newEvent("recorder-id-2", "third")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)
    filtered.add(third)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id")
    whitelist.add("recorder-id-1")
    whitelist.add("recorder-id-2")
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist with versions from`() {
    val first = newEvent("recorder-id", "first", groupVersion = "1")
    val second = newEvent("recorder-id", "third", groupVersion = "3")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(second)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id", FUSWhitelist.VersionRange.create("2", null))
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist with versions exact from`() {
    val first = newEvent("recorder-id", "first", groupVersion = "1")
    val second = newEvent("recorder-id", "third", groupVersion = "2")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(second)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id", FUSWhitelist.VersionRange.create("2", null))
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist with versions to`() {
    val first = newEvent("recorder-id", "first", groupVersion = "1")
    val second = newEvent("recorder-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id", FUSWhitelist.VersionRange.create(null, "3"))
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist with versions exact to`() {
    val first = newEvent("recorder-id", "first", groupVersion = "1")
    val second = newEvent("recorder-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id", FUSWhitelist.VersionRange.create(null, "4"))
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist with accept all versions`() {
    val first = newEvent("recorder-id", "first", groupVersion = "1")
    val second = newEvent("recorder-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id", FUSWhitelist.VersionRange.create(null, null))
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist with empty versions list`() {
    val first = newEvent("recorder-id", "first", groupVersion = "1")
    val second = newEvent("recorder-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id")
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist with versions from and to`() {
    val first = newEvent("recorder-id", "first", groupVersion = "2")
    val second = newEvent("recorder-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id", FUSWhitelist.VersionRange.create("1", "5"))
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist with versions exact from and to`() {
    val first = newEvent("recorder-id", "first", groupVersion = "1")
    val second = newEvent("recorder-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id", FUSWhitelist.VersionRange.create("1", "5"))
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist with versions from and exact to`() {
    val first = newEvent("recorder-id", "first", groupVersion = "2")
    val second = newEvent("recorder-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id", FUSWhitelist.VersionRange.create("1", "4"))
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist with complimentary multi versions`() {
    val first = newEvent("recorder-id", "first", groupVersion = "2")
    val second = newEvent("recorder-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id", FUSWhitelist.VersionRange.create("1", "4"), FUSWhitelist.VersionRange.create("4", "5"))
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist with intersected multi versions`() {
    val first = newEvent("recorder-id", "first", groupVersion = "2")
    val second = newEvent("recorder-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id", FUSWhitelist.VersionRange.create("1", "4"), FUSWhitelist.VersionRange.create("3", "5"))
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist with incomplete multi versions`() {
    val first = newEvent("recorder-id", "first", groupVersion = "2")
    val second = newEvent("recorder-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id", FUSWhitelist.VersionRange.create(null, "3"), FUSWhitelist.VersionRange.create("5", null))
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist with range and all range version`() {
    val first = newEvent("recorder-id", "first", groupVersion = "2")
    val second = newEvent("recorder-id", "third", groupVersion = "4")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id", FUSWhitelist.VersionRange.create(null, "2"), FUSWhitelist.VersionRange.create(null, null))
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter snapshot builds`() {
    val first = newEvent("recorder-id", "first", build = "999.9999")
    val second = newEvent("recorder-id-1", "second", build = "999.0")
    val third = newEvent("recorder-id", "third", build = "999.9999")

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
    val first = newEvent("recorder-id", "first", build = "999.9999")
    val second = newEvent("recorder-id-1", "second", build = "999.01")
    val third = newEvent("recorder-id", "third", build = "999.9999")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    testSnapshotBuilderFilter(all, all)
  }

  @Test
  fun `test filter all snapshot builds`() {
    val first = newEvent("recorder-id", "first", build = "999.00")
    val second = newEvent("recorder-id-1", "second", build = "999.0")
    val third = newEvent("recorder-id", "third", build = "999.0")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    testSnapshotBuilderFilter(all, ArrayList())
  }

  @Test
  fun `test filter group id and snapshot builds`() {
    val first = newEvent("recorder-id", "first", build = "999.9999")
    val second = newEvent("recorder-id-1", "second", build = "999.9999")
    val third = newEvent("recorder-id", "third", build = "999.0")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id")
    testWhitelistAndSnapshotBuildFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter group id, version from and snapshot builds`() {
    val first = newEvent("recorder-id", "first", build = "999.9999", groupVersion = "3")
    val second = newEvent("recorder-id", "second", build = "999.9999", groupVersion = "2")
    val third = newEvent("recorder-id", "third", build = "999.0", groupVersion = "4")
    val forth = newEvent("recorder-id-1", "forth", build = "999.9999", groupVersion = "5")
    val fifth = newEvent("recorder-id-2", "fifth", build = "999.9999", groupVersion = "1")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    all.add(forth)
    all.add(fifth)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id", FUSWhitelist.VersionRange.create("3", null))
    testWhitelistAndSnapshotBuildFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter group id, version to and snapshot builds`() {
    val first = newEvent("recorder-id", "first", build = "999.9999", groupVersion = "3")
    val second = newEvent("recorder-id", "second", build = "999.9999", groupVersion = "2")
    val third = newEvent("recorder-id", "third", build = "999.0", groupVersion = "4")
    val forth = newEvent("recorder-id-1", "forth", build = "999.9999", groupVersion = "5")
    val fifth = newEvent("recorder-id-2", "fifth", build = "999.9999", groupVersion = "1")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    all.add(forth)
    all.add(fifth)
    val filtered = ArrayList<LogEvent>()
    filtered.add(second)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id", FUSWhitelist.VersionRange.create(null, "3"))
    testWhitelistAndSnapshotBuildFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter group id, version from and to and snapshot builds`() {
    val first = newEvent("recorder-id", "first", build = "999.9999", groupVersion = "3")
    val second = newEvent("recorder-id", "second", build = "999.9999", groupVersion = "2")
    val third = newEvent("recorder-id", "third", build = "999.0", groupVersion = "4")
    val forth = newEvent("recorder-id-1", "forth", build = "999.9999", groupVersion = "5")
    val fifth = newEvent("recorder-id-2", "fifth", build = "999.9999", groupVersion = "1")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    all.add(forth)
    all.add(fifth)
    val filtered = ArrayList<LogEvent>()
    filtered.add(second)

    val whitelist = WhitelistBuilder()
    whitelist.add("recorder-id", FUSWhitelist.VersionRange.create("1", "3"))
    testWhitelistAndSnapshotBuildFilter(whitelist.build(), all, filtered)
  }

  private fun testWhitelistFilter(whitelist: FUSWhitelist, all: List<LogEvent>, filtered: List<LogEvent>) {
    testEventLogFilter(all, filtered, LogEventWhitelistFilter(whitelist))
  }

  private fun testWhitelistAndSnapshotBuildFilter(whitelist: FUSWhitelist, all: List<LogEvent>, filtered: List<LogEvent>) {
    testEventLogFilter(all, filtered, LogEventCompositeFilter(LogEventWhitelistFilter(whitelist), LogEventSnapshotBuildFilter))
  }

  private fun testSnapshotBuilderFilter(all: List<LogEvent>, filtered: List<LogEvent>) {
    testEventLogFilter(all, filtered, LogEventSnapshotBuildFilter)
  }

  private fun testEventLogFilter(all: List<LogEvent>, filtered: List<LogEvent>, filter: LogEventFilter) {
    val records = ArrayList<LogEventRecord>()
    if (!filtered.isEmpty()) {
      records.add(LogEventRecord(filtered))
    }
    val expected = LogEventRecordRequest("IU", "user-id", records, false)

    val log = FileUtil.createTempFile("feature-event-log", ".log")
    try {
      val out = StringBuilder()
      for (event in all) {
        out.append(LogEventSerializer.toString(event)).append("\n")
      }
      FileUtil.writeToFile(log, out.toString())
      val actual = LogEventRecordRequest.create(log, "IU", "user-id", 600, filter, false)
      assertEquals(expected, actual)
    }
    finally {
      FileUtil.delete(log)
    }
  }
}