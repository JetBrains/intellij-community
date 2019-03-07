// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.persistence.ApprovedGroupsCacheConfigurable
import com.intellij.openapi.util.BuildNumber
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import junit.framework.TestCase
import org.junit.Test
import java.util.*

class ApprovedGroupsCacheTest : UsefulTestCase() {

  private var myFixture: CodeInsightTestFixture? = null
  private val build: BuildNumber = BuildNumber.fromString("183")

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
  fun testCacheValues() {
    val cache = ApprovedGroupsCacheConfigurable.getInstance()
    val date = Date()
    cache.cacheGroups(date, setOf("firstGroup", "secondGroup"), build)
    assertEquals(setOf("firstGroup", "secondGroup"), cache.getCachedGroups(date, 100))
  }

  @Test
  fun testCacheUpdatesValues() {
    val cache = ApprovedGroupsCacheConfigurable.getInstance()
    val date = Date()
    cache.cacheGroups(date, setOf("firstGroup", "secondGroup"), build)
    cache.cacheGroups(Date(date.time + 1), setOf("thirdGroup"), build)
    assertEquals(setOf("thirdGroup"), cache.getCachedGroups(date, 100))
  }

  @Test
  fun testDoestReturnStaleValues() {
    val cache = ApprovedGroupsCacheConfigurable.getInstance()
    val date = Date()
    cache.cacheGroups(date, setOf("firstGroup", "secondGroup"), build)
    TestCase.assertNull(cache.getCachedGroups(Date(date.time + 101), 100))
  }

  @Test
  fun testCacheValuesByBuild() {
    val cache = ApprovedGroupsCacheConfigurable.getInstance()
    val date = Date()
    cache.cacheGroups(date, setOf("firstGroup", "secondGroup"), build)
    assertEquals(setOf("firstGroup", "secondGroup"), cache.getCachedGroups(date, 100, build))
  }

  @Test
  fun testDoestReturnValuesOutdatedByBuild() {
    val cache = ApprovedGroupsCacheConfigurable.getInstance()
    val date = Date()
    cache.cacheGroups(date, setOf("firstGroup", "secondGroup"), build)
    TestCase.assertNull(cache.getCachedGroups(date, 100, BuildNumber.fromString("191")))
  }
}