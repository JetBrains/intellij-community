// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.EventLogExternalSettingsService
import com.intellij.internal.statistic.persistence.ApprovedGroupsCacheConfigurable
import com.intellij.internal.statistic.service.fus.FUSWhitelist
import com.intellij.openapi.util.BuildNumber
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.containers.ContainerUtil
import org.junit.Assert
import org.junit.Test
import java.util.*

class EventLogExternalSettingsServiceTest : UsefulTestCase() {
  private var myFixture: CodeInsightTestFixture? = null

  override fun setUp() {
    super.setUp()

    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val fixtureBuilder = factory.createFixtureBuilder("ApprovedGroupsCacheTest")
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixtureBuilder.fixture)
    myFixture?.setUp()
  }

  override fun tearDown() {
    super.tearDown()
    try {
      myFixture?.tearDown()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      myFixture = null
    }
  }

  @Test
  fun testCachedGroupsForActualCache() {
    assertEquals(
      WhitelistBuilder().add("cachedGroup1").add("cachedGroup2").build(),
      WorkingEventLogExternalSettingsService().getApprovedGroups(ActualCache())
    )
  }

  @Test
  fun testCachedGroupsForActualCacheWithVersionFrom() {
    val whitelist = WhitelistBuilder().add(
      "cachedGroup1",
      FUSWhitelist.VersionRange.create("2", "5"),
      FUSWhitelist.VersionRange.create(null, "5"),
      FUSWhitelist.VersionRange.create(null, null)
    ).add(
      "cachedGroup2",
      FUSWhitelist.VersionRange.create("4", "5"),
      FUSWhitelist.VersionRange.create("4", null)
    ).build()
    assertEquals(whitelist, WorkingEventLogExternalSettingsServiceWithVersion().getApprovedGroups(ActualCacheWithVersions()))
  }

  @Test
  fun testReturnActualGroupsForNullableCache() {
    assertEquals(
      WhitelistBuilder().add("actualGroup1").add("actualGroup2").build(),
      WorkingEventLogExternalSettingsService().getApprovedGroups(StaleCache())
    )
  }

  @Test
  fun testReturnActualGroupsForNullableCacheWithVersions() {
    val whitelist = WhitelistBuilder().add(
      "actualGroup1",
      FUSWhitelist.VersionRange.create(null, "5"),
      FUSWhitelist.VersionRange.create(null, null)
    ).add(
      "actualGroup2",
      FUSWhitelist.VersionRange.create("6", null)
    ).build()
    val approvedGroups = WorkingEventLogExternalSettingsServiceWithVersion().getApprovedGroups(StaleCache())
    assertEquals(whitelist, approvedGroups)
  }

  @Test
  fun testCachedGroupsForActualCacheAndBrokenService() {
    assertEquals(
      WhitelistBuilder().add("cachedGroup1").add("cachedGroup2").build(),
      BrokenEventLogExternalSettingsService().getApprovedGroups(ActualCache())
    )
  }

  @Test
  fun testCachedGroupsForActualCacheAndBrokenServiceWithVersion() {
    val whitelist = WhitelistBuilder().add(
      "cachedGroup1",
      FUSWhitelist.VersionRange.create("2", "5"),
      FUSWhitelist.VersionRange.create(null, "5"),
      FUSWhitelist.VersionRange.create(null, null)
    ).add(
      "cachedGroup2",
      FUSWhitelist.VersionRange.create("4", "5"),
      FUSWhitelist.VersionRange.create("4", null)
    ).build()
    assertEquals(whitelist, BrokenEventLogExternalSettingsService().getApprovedGroups(ActualCacheWithVersions()))
  }

  @Test
  fun testEmptySetInCaseOfNothing() {
    Assert.assertTrue(BrokenEventLogExternalSettingsService().getApprovedGroups(StaleCache()).isEmpty())
  }

  private class BrokenEventLogExternalSettingsService : EventLogExternalSettingsService() {
    override fun getWhitelistedGroups(): FUSWhitelist? {
      return null
    }
  }

  private class WorkingEventLogExternalSettingsService : EventLogExternalSettingsService() {
    override fun getWhitelistedGroups(): FUSWhitelist? {
      return FUSWhitelist.create(mutableMapOf(
        Pair("actualGroup1", ContainerUtil.emptyList()),
        Pair("actualGroup2", ContainerUtil.emptyList())
      ))
    }
  }

  private class WorkingEventLogExternalSettingsServiceWithVersion : EventLogExternalSettingsService() {
    override fun getWhitelistedGroups(): FUSWhitelist? {
      return WhitelistBuilder().add(
        "actualGroup1",
        FUSWhitelist.VersionRange.create(null, "5"),
        FUSWhitelist.VersionRange.create(null, null)
      ).add(
        "actualGroup2",
        FUSWhitelist.VersionRange.create("6", null)
      ).build()
    }
  }

  private class ActualCache : ApprovedGroupsCacheConfigurable() {
    override fun getCachedGroups(date: Date,
                                 cacheActualDuration: Long,
                                 currentBuild: BuildNumber?): FUSWhitelist {
      return FUSWhitelist.create(mutableMapOf(
        Pair("cachedGroup1", ContainerUtil.emptyList()),
        Pair("cachedGroup2", ContainerUtil.emptyList())
      ))
    }
  }

  private class ActualCacheWithVersions : ApprovedGroupsCacheConfigurable() {
    override fun getCachedGroups(date: Date,
                                 cacheActualDuration: Long,
                                 currentBuild: BuildNumber?): FUSWhitelist {
      return WhitelistBuilder().add(
        "cachedGroup1",
        FUSWhitelist.VersionRange.create("2", "5"),
        FUSWhitelist.VersionRange.create(null, "5"),
        FUSWhitelist.VersionRange.create(null, null)
      ).add(
        "cachedGroup2",
        FUSWhitelist.VersionRange.create("4", "5"),
        FUSWhitelist.VersionRange.create("4", null)
      ).build()
    }
  }

  private class StaleCache : ApprovedGroupsCacheConfigurable() {
    override fun getCachedGroups(date: Date,
                                 cacheActualDuration: Long,
                                 currentBuild: BuildNumber?): FUSWhitelist? {
      return null
    }
  }
}