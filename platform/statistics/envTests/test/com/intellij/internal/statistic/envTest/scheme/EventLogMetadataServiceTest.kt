// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.envTest.scheme

import com.intellij.internal.statistic.envTest.StatisticsServiceBaseTest
import com.intellij.internal.statistic.service.fus.*
import com.intellij.internal.statistic.service.fus.StatisticsWhitelistGroupConditions.BuildRange
import com.intellij.internal.statistic.service.fus.StatisticsWhitelistGroupConditions.VersionRange
import junit.framework.TestCase

private const val USER_AGENT = "Test IntelliJ"

internal class EventLogMetadataServiceTest : StatisticsServiceBaseTest() {

  fun `test load metadata succeed`() {
    val metadataUrl = getMetadataUrl("IC.json")
    val content = StatisticsWhitelistLoader.loadWhiteListFromServer(metadataUrl, USER_AGENT)
    TestCase.assertNotNull(content)
    TestCase.assertTrue(content.isNotEmpty())
  }

  fun `test load metadata fail because product code does not exists`() {
    var exception: Exception? = null
    try {
      val metadataUrl = getMetadataUrl("AB.json")
      StatisticsWhitelistLoader.loadWhiteListFromServer(metadataUrl, USER_AGENT)
    }
    catch (e: Exception) {
      exception = e
    }
    TestCase.assertNotNull(exception)
    TestCase.assertTrue(exception is EventLogWhitelistLoadException)
  }

  fun `test get last modified metadata`() {
    val metadataUrl = getMetadataUrl()
    val lastModified = StatisticsWhitelistLoader.lastModifiedWhitelist(metadataUrl, USER_AGENT)
    TestCase.assertEquals(1589968216000, lastModified)
  }

  fun `test load and parse metadata`() {
    val expected = hashMapOf(
      "test.group" to StatisticsWhitelistGroupConditions(emptyList(), listOf(VersionRange.create("3", null))),
      "second.test.group" to StatisticsWhitelistGroupConditions(listOf(BuildRange.create("191.12345", null)), emptyList())
    )

    val metadataUrl = getMetadataUrl()
    val actual = StatisticsWhitelistLoader.getApprovedGroups(metadataUrl, USER_AGENT)
    TestCase.assertEquals(StatisticsWhitelistConditions.create(expected), actual)
  }

  fun `test failed loading and parsing metadata because url is unreachable`() {
    val metadataUrl = getMetadataUrl("AB.json")
    val actual = StatisticsWhitelistLoader.getApprovedGroups(metadataUrl, USER_AGENT)
    TestCase.assertNotNull(actual)
    TestCase.assertTrue(actual.isEmpty)
  }

  fun `test failed loading and parsing metadata because its invalid`() {
    val metadataUrl = getMetadataUrl("invalid-metadata.json")
    val actual = StatisticsWhitelistLoader.getApprovedGroups(metadataUrl, USER_AGENT)
    TestCase.assertNotNull(actual)
    TestCase.assertTrue(actual.isEmpty)
  }

  private fun getMetadataUrl(path: String? = null): String {
    return container.getBaseUrl("metadata/${path ?: "with-last-modified.php"}").toString()
  }
}