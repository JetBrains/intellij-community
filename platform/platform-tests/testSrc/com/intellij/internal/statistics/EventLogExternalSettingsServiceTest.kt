// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.EventLogExternalSettingsService
import com.intellij.internal.statistic.persistence.ApprovedGroupsCacheConfigurable
import com.intellij.openapi.util.BuildNumber
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
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
      addSuppressedException(e);
    }
    finally {
      myFixture = null
    }
  }

  @Test
  fun testCachedGroupsForActualCache() {
    assertEquals(mutableSetOf("cachedGroup1", "cachedGroup2"), WorkingEventLogExternalSettingsService().getApprovedGroups(ActualCache()))
  }

  @Test
  fun testReturnActualGroupsForNullableCache() {
    assertEquals(mutableSetOf("actualGroup1", "actualGroup2"), WorkingEventLogExternalSettingsService().getApprovedGroups(StaleCache()))
  }

  @Test
  fun testCachedGroupsForActualCacheAndBrokenService() {
    assertEquals(mutableSetOf("cachedGroup1", "cachedGroup2"), BrokenEventLogExternalSettingsService().getApprovedGroups(ActualCache()))
  }

  @Test
  fun testEmptySetInCaseOfNothing() {
    assertEmpty(BrokenEventLogExternalSettingsService().getApprovedGroups(StaleCache()))
  }

  private class BrokenEventLogExternalSettingsService : EventLogExternalSettingsService() {
    override fun getWhitelistedGroups(): MutableSet<String>? {
      return null
    }
  }

  private class WorkingEventLogExternalSettingsService : EventLogExternalSettingsService() {
    override fun getWhitelistedGroups(): MutableSet<String>? {
      return mutableSetOf("actualGroup1", "actualGroup2")
    }
  }

  private class ActualCache : ApprovedGroupsCacheConfigurable() {
    override fun getCachedGroups(date: Date, cacheActualDuration: Long, currentBuild: BuildNumber?): MutableSet<String> {
      return mutableSetOf("cachedGroup1", "cachedGroup2")
    }
  }

  private class StaleCache : ApprovedGroupsCacheConfigurable() {
    override fun getCachedGroups(date: Date, cacheActualDuration: Long, currentBuild: BuildNumber?): MutableSet<String>? {
      return null
    }
  }
}