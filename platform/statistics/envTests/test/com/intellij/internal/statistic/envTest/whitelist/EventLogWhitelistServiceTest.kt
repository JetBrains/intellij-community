// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.envTest.whitelist

import com.intellij.internal.statistic.envTest.StatisticsServiceBaseTest
import com.intellij.internal.statistic.service.fus.*
import com.intellij.internal.statistic.service.fus.StatisticsWhitelistGroupConditions.BuildRange
import com.intellij.internal.statistic.service.fus.StatisticsWhitelistGroupConditions.VersionRange
import junit.framework.TestCase

private const val USER_AGENT = "Test IntelliJ"

internal class EventLogWhitelistServiceTest : StatisticsServiceBaseTest() {

  fun `test load whitelist succeed`() {
    val whitelistUrl = getWhitelistUrl("IC.json")
    val content = StatisticsWhitelistLoader.loadWhiteListFromServer(whitelistUrl, USER_AGENT)
    TestCase.assertNotNull(content)
    TestCase.assertTrue(content.isNotEmpty())
  }

  fun `test load whitelist fail because product code does not exists`() {
    var exception: Exception? = null
    try {
      val whitelistUrl = getWhitelistUrl("AB.json")
      StatisticsWhitelistLoader.loadWhiteListFromServer(whitelistUrl, USER_AGENT)
    }
    catch (e: Exception) {
      exception = e
    }
    TestCase.assertNotNull(exception)
    TestCase.assertTrue(exception is EventLogMetadataLoadException)
  }

  fun `test get last modified whitelist`() {
    val whitelistUrl = getWhitelistUrl()
    val lastModified = StatisticsWhitelistLoader.lastModifiedWhitelist(whitelistUrl, USER_AGENT)
    TestCase.assertEquals(1589968216000, lastModified)
  }

  fun `test load and parse whitelist`() {
    val expected = hashMapOf(
      "test.group" to StatisticsWhitelistGroupConditions(emptyList(), listOf(VersionRange.create("3", null))),
      "second.test.group" to StatisticsWhitelistGroupConditions(listOf(BuildRange.create("191.12345", null)), emptyList())
    )

    val whitelistUrl = getWhitelistUrl()
    val actual = StatisticsWhitelistLoader.getApprovedGroups(whitelistUrl, USER_AGENT)
    TestCase.assertEquals(StatisticsWhitelistConditions.create(expected), actual)
  }

  fun `test failed loading and parsing whitelist because url is unreachable`() {
    val whitelistUrl = getWhitelistUrl("AB.json")
    val actual = StatisticsWhitelistLoader.getApprovedGroups(whitelistUrl, USER_AGENT)
    TestCase.assertNotNull(actual)
    TestCase.assertTrue(actual.isEmpty)
  }

  fun `test failed loading and parsing whitelist because its invalid`() {
    val whitelistUrl = getWhitelistUrl("invalid-whitelist.json")
    val actual = StatisticsWhitelistLoader.getApprovedGroups(whitelistUrl, USER_AGENT)
    TestCase.assertNotNull(actual)
    TestCase.assertTrue(actual.isEmpty)
  }

  private fun getWhitelistUrl(path: String? = null): String {
    return container.getBaseUrl("whitelist/${path ?: "with-last-modified.php"}").toString()
  }
}