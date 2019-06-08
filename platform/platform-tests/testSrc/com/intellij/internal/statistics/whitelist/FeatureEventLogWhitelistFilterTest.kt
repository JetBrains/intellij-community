// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.whitelist

import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.service.fus.FUSWhitelist
import com.intellij.internal.statistics.WhitelistBuilder
import com.intellij.internal.statistics.newEvent
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
    val first = newEvent("recorder-id", "first", build = "173.23")
    val second = newEvent("recorder-id-1", "second", build = "173.23")
    val third = newEvent("recorder-id", "third", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(third)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "173.20.132", null)
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist with multi groups`() {
    val first = newEvent("recorder-id", "first", build = "173.23")
    val second = newEvent("recorder-id-1", "second", build = "173.23")
    val third = newEvent("recorder-id-2", "third", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(third)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "173.20.132", null)
    whitelist.addBuild("recorder-id-2", "173.20.132", "173.24.132")
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist all groups`() {
    val first = newEvent("recorder-id", "first", build = "173.23")
    val second = newEvent("recorder-id-1", "second", build = "173.23")
    val third = newEvent("recorder-id-2", "third", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)
    filtered.add(third)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", null, "182.0")
    whitelist.addBuild("recorder-id-1", null, "182.0")
    whitelist.addBuild("recorder-id-2", null, "182.0")
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
    whitelist.addVersion("recorder-id", "2", null)
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
    whitelist.addVersion("recorder-id", "2", null)
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
    whitelist.addVersion("recorder-id", null, "3")
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
    whitelist.addVersion("recorder-id", null, "4")
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
    whitelist.addVersion("recorder-id", null, null)
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test whitelist with empty versions list`() {
    val first = newEvent("recorder-id", "first", groupVersion = "1", build = "181.0")
    val second = newEvent("recorder-id", "third", groupVersion = "4", build = "181.0")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", null, "182.0")
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
    whitelist.addVersion("recorder-id", "1", "5")
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
    whitelist.addVersion("recorder-id", "1", "5")
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
    whitelist.addVersion("recorder-id", "1", "4")
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
    whitelist.addVersion("recorder-id", "1", "4").addVersion("recorder-id", "4", "5")
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
    whitelist.addVersion("recorder-id", "1", "4").addVersion("recorder-id", "3", "5")
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
    whitelist.addVersion("recorder-id", null, "3").addVersion("recorder-id", "5", null)
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
    whitelist.addVersion("recorder-id", null, "2").addVersion("recorder-id", null, null)
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
    whitelist.addBuild("recorder-id", "999.99", null)
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
    whitelist.addVersion("recorder-id", "3", null)
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
    whitelist.addVersion("recorder-id", null, "3")
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
    whitelist.addVersion("recorder-id", "1", "3")
    testWhitelistAndSnapshotBuildFilter(whitelist.build(), all, filtered)
  }

  // test build ranges
  @Test
  fun `test filter build from with build short the same`() {
    val first = newEvent("recorder-id", "first", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "173.23", null)
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter build from with build long the same`() {
    val first = newEvent("recorder-id", "first", build = "173.23.435")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "173.23.435", null)
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter build from with short build and bugfix build after`() {
    val first = newEvent("recorder-id", "first", build = "173.232.1")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "173.232", null)
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter build from with no bugfix build`() {
    val first = newEvent("recorder-id", "first", build = "173.232")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "173.232.1", null)
    testWhitelistFilter(whitelist.build(), all, ArrayList())
  }

  @Test
  fun `test filter build from with major build after`() {
    val first = newEvent("recorder-id", "first", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "172.20.132", null)
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter build from with minor build after`() {
    val first = newEvent("recorder-id", "first", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "173.20.132", null)
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter build from with bugfix build after`() {
    val first = newEvent("recorder-id", "first", build = "173.23.15")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "173.23.13", null)
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter build from with major build before`() {
    val first = newEvent("recorder-id", "first", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "181.20.132", null)
    testWhitelistFilter(whitelist.build(), all, ArrayList())
  }

  @Test
  fun `test filter build from with minor build before`() {
    val first = newEvent("recorder-id", "first", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "173.203.132", null)
    testWhitelistFilter(whitelist.build(), all, ArrayList())
  }

  @Test
  fun `test filter build from with bugfix build before`() {
    val first = newEvent("recorder-id", "first", build = "173.23.15")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "173.23.35", null)
    testWhitelistFilter(whitelist.build(), all, ArrayList())
  }

  @Test
  fun `test filter build from with whitelisted snapshot build after`() {
    val first = newEvent("recorder-id", "first", build = "172.340")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "181.0", null)
    testWhitelistFilter(whitelist.build(), all, ArrayList())
  }

  @Test
  fun `test filter build from with whitelisted snapshot build before`() {
    val first = newEvent("recorder-id", "first", build = "181.34")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "181.0", null)
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter build to with build short the same`() {
    val first = newEvent("recorder-id", "first", build = "173.23")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", null, "173.23")
    testWhitelistFilter(whitelist.build(), all, ArrayList())
  }

  @Test
  fun `test filter build to with build long the same`() {
    val first = newEvent("recorder-id", "first", build = "173.23.234")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", null, "173.23.234")
    testWhitelistFilter(whitelist.build(), all, ArrayList())
  }

  @Test
  fun `test filter build to with major build before`() {
    val first = newEvent("recorder-id", "first", build = "172.22")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", null, "173.23.234")
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter build to with minor build before`() {
    val first = newEvent("recorder-id", "first", build = "173.22")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", null, "173.23.234")
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter build to with bugfix build before`() {
    val first = newEvent("recorder-id", "first", build = "173.23.201")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", null, "173.23.234")
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter build to with major build after`() {
    val first = newEvent("recorder-id", "first", build = "183.23.201")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", null, "173.23.234")
    testWhitelistFilter(whitelist.build(), all, ArrayList())
  }

  @Test
  fun `test filter build to with minor build after`() {
    val first = newEvent("recorder-id", "first", build = "183.345.201")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", null, "183.23.234")
    testWhitelistFilter(whitelist.build(), all, ArrayList())
  }

  @Test
  fun `test filter build to with bugfix build after`() {
    val first = newEvent("recorder-id", "first", build = "183.345.201")

    val all = ArrayList<LogEvent>()
    all.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", null, "183.345.12")
    testWhitelistFilter(whitelist.build(), all, ArrayList())
  }

  @Test
  fun `test filter build from and to with major build between`() {
    val first = newEvent("recorder-id", "first", build = "182.345.201")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "181.345.12", "183.345.12")
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter build from and to with minor build between`() {
    val first = newEvent("recorder-id", "first", build = "183.45.201")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "183.35.12", "183.345.12")
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter build from and to with bugfix build between`() {
    val first = newEvent("recorder-id", "first", build = "183.35.21")

    val all = ArrayList<LogEvent>()
    all.add(first)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "183.35.12", "183.35.120")
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter group and build from with build after`() {
    val first = newEvent("recorder-id", "first", build = "183.35.21")
    val second = newEvent("recorder-id-1", "first", build = "183.35.21")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "183.35.12", null)
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter group and build from with build before and after`() {
    val first = newEvent("recorder-id", "first", build = "183.35.21")
    val second = newEvent("recorder-id-1", "first", build = "183.35.21")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "183.35.12", null)
    whitelist.addBuild("recorder-id-1", "183.35.32", null)
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter group and build from with both build before`() {
    val first = newEvent("recorder-id", "first", build = "183.35.21")
    val second = newEvent("recorder-id-1", "first", build = "183.35.21")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "183.35.12", null)
    whitelist.addBuild("recorder-id-1", "181.35.32", null)
    testWhitelistFilter(whitelist.build(), all, filtered)
  }

  @Test
  fun `test filter group and build from with both build after`() {
    val first = newEvent("recorder-id", "first", build = "181.35.21")
    val second = newEvent("recorder-id-1", "first", build = "181.13")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "183.35.12", null)
    whitelist.addBuild("recorder-id-1", "181.35.32", null)
    testWhitelistFilter(whitelist.build(), all, ArrayList())
  }

  @Test
  fun `test filter version and build`() {
    val first = newEvent("recorder-id", "first", groupVersion = "3", build = "182.312")
    val second = newEvent("recorder-id", "first", groupVersion = "11", build = "181.3")
    val third = newEvent("recorder-id", "first", groupVersion = "5", build = "181.123")
    val forth = newEvent("recorder-id", "first", groupVersion = "4", build = "181.32")
    val fifth = newEvent("recorder-id", "first", groupVersion = "3", build = "183.113.341")
    val sixth = newEvent("recorder-id", "first", groupVersion = "3", build = "191.13.341")

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

    val whitelist = WhitelistBuilder()
    whitelist.addBuild("recorder-id", "181.12", "182.312")
    whitelist.addBuild("recorder-id", "183.35.12", null)
    whitelist.addVersion("recorder-id", 3, 5)
    whitelist.addVersion("recorder-id", 11, Int.MAX_VALUE)
    testWhitelistFilter(whitelist.build(), all, filtered)
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
      val actual = LogEventRecordRequest.create(log, "recorder-id", "IU", "user-id", 600, filter, false)
      assertEquals(expected, actual)
    }
    finally {
      FileUtil.delete(log)
    }
  }
}