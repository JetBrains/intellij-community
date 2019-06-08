// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.whitelist

import com.intellij.internal.statistic.service.fus.FUSWhitelist
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService
import com.intellij.internal.statistics.WhitelistBuilder
import com.intellij.openapi.util.BuildNumber
import org.junit.Test
import kotlin.test.assertEquals

class StatisticsParseWhitelistWithVersionTest {

  private fun doTest(content: String, expected: FUSWhitelist) {
    val actual = FUStatisticsWhiteListGroupsService.parseApprovedGroups(content)
    assertEquals(expected.size, actual.size)
    assertEquals(expected, actual)
  }

  private fun newBuild(vararg args: Int): BuildNumber {
    return BuildNumber("", *args)
  }

  @Test
  fun `with one group version`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118"
    }],
    "versions" : [ {
      "from" : "3",
      "to" : "5"
    }],
    "context" : {
    }
  }]
}
    """
    val whitelist = WhitelistBuilder().
      addVersion("test.group.id", 3, 5).
      addBuild("test.group.id", newBuild(173, 4284, 118), null)
    doTest(content, whitelist.build())
  }

  @Test
  fun `with from group version`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118"
    }],
    "versions" : [ {
      "from" : "3"
    }],
    "context" : {
    }
  }]
}
    """
    val whitelist = WhitelistBuilder().
      addVersion("test.group.id", 3, Int.MAX_VALUE).
      addBuild("test.group.id", newBuild(173, 4284, 118), null)
    doTest(content, whitelist.build())
  }

  @Test
  fun `with to group version`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118"
    }],
    "versions" : [ {
      "to" : "13"
    }],
    "context" : {
    }
  }]
}
    """
    val whitelist = WhitelistBuilder().
      addVersion("test.group.id", 0, 13).
      addBuild("test.group.id", newBuild(173, 4284, 118), null)
    doTest(content, whitelist.build())
  }

  @Test
  fun `with empty group version`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118"
    }],
    "versions" : [ {
    }],
    "context" : {
    }
  }]
}
    """
    val whitelist = WhitelistBuilder().
      addVersion("test.group.id", 0, Int.MAX_VALUE).
      addBuild("test.group.id", newBuild(173, 4284, 118), null)
    doTest(content, whitelist.build())
  }

  @Test
  fun `with empty list of group version`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118"
    }],
    "versions" : [],
    "context" : {
    }
  }]
}
    """
    val whitelist = WhitelistBuilder().
      addBuild("test.group.id", newBuild(173, 4284, 118), null)
    doTest(content, whitelist.build())
  }

  @Test
  fun `with two full group version`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118"
    }],
    "versions" : [ {
      "from": "1",
      "to": "5"
    },{
      "from": "6",
      "to": "7"
    }],
    "context" : {
    }
  }]
}
    """
    val whitelist = WhitelistBuilder().
      addVersion("test.group.id", 1, 5).
      addVersion("test.group.id", 6, 7).
      addBuild("test.group.id", newBuild(173, 4284, 118), null)
    doTest(content, whitelist.build())
  }

  @Test
  fun `with two intersected group version`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118"
    }],
    "versions" : [ {
      "from": "1",
      "to": "8"
    },{
      "from": "6",
      "to": "7"
    }],
    "context" : {
    }
  }]
}
    """
    val whitelist = WhitelistBuilder().
      addVersion("test.group.id", 1, 8).
      addVersion("test.group.id", 6, 7).
      addBuild("test.group.id", newBuild(173, 4284, 118), null)
    doTest(content, whitelist.build())
  }

  @Test
  fun `with two complimentary group version`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118"
    }],
    "versions" : [ {
      "to": "8"
    },{
      "from": "8"
    }],
    "context" : {
    }
  }]
}
    """
    val whitelist = WhitelistBuilder().
      addVersion("test.group.id", 0, 8).
      addVersion("test.group.id", 8, Int.MAX_VALUE).
      addBuild("test.group.id", newBuild(173, 4284, 118), null)
    doTest(content, whitelist.build())
  }

  @Test
  fun `with from bigger than to group version`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118"
    }],
    "versions" : [ {
      "from": "13",
      "to": "8"
    }],
    "context" : {
    }
  }]
}
    """
    val whitelist = WhitelistBuilder().
      addVersion("test.group.id", 13, 8).
      addBuild("test.group.id", newBuild(173, 4284, 118), null)
    doTest(content, whitelist.build())
  }

  @Test
  fun `with empty build range list`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [],
    "versions" : [ {
      "from": "3",
      "to": "8"
    }],
    "context" : {
    }
  }]
}
    """
    val whitelist = WhitelistBuilder().
      addVersion("test.group.id", 3, 8)
    doTest(content, whitelist.build())
  }

  @Test
  fun `with empty build range list and empty group list`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [],
    "versions" : [],
    "context" : {
    }
  }]
}
    """
    doTest(content, FUSWhitelist.empty())
  }

  @Test
  fun `without build range list`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "versions" : [ {
      "from": "3",
      "to": "8"
    }],
    "context" : {
    }
  }]
}
    """
    val whitelist = WhitelistBuilder().
      addVersion("test.group.id", 3, 8)
    doTest(content, whitelist.build())
  }

  @Test
  fun `without both build range list and group version`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "context" : {
    }
  }]
}
    """
    doTest(content, FUSWhitelist.empty())
  }

  @Test
  fun `with invalid from version with point`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118"
    }],
    "versions" : [ {
      "from" : "3.2",
      "to" : "5"
    }],
    "context" : {
    }
  }]
}
    """
    val whitelist = WhitelistBuilder().
      addVersion("test.group.id", Int.MAX_VALUE, 5).
      addBuild("test.group.id", newBuild(173, 4284, 118), null)
    doTest(content, whitelist.build())
  }

  @Test
  fun `with invalid to version with point`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118"
    }],
    "versions" : [ {
      "from" : "3",
      "to" : "5.1.3"
    }],
    "context" : {
    }
  }]
}
    """
    val whitelist = WhitelistBuilder().
      addVersion("test.group.id", 3, 0).
      addBuild("test.group.id", newBuild(173, 4284, 118), null)
    doTest(content, whitelist.build())
  }

  @Test
  fun `with invalid from version with symbols`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118"
    }],
    "versions" : [ {
      "from" : "some-version",
      "to" : "5"
    }],
    "context" : {
    }
  }]
}
    """
    val whitelist = WhitelistBuilder().
      addVersion("test.group.id", Int.MAX_VALUE, 5).
      addBuild("test.group.id", newBuild(173, 4284, 118), null)
    doTest(content, whitelist.build())
  }

  @Test
  fun `with invalid to version with symbols`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118"
    }],
    "versions" : [ {
      "from" : "3",
      "to" : "5-version"
    }],
    "context" : {
    }
  }]
}
    """
    val whitelist = WhitelistBuilder().
      addVersion("test.group.id", 3, 0).
      addBuild("test.group.id", newBuild(173, 4284, 118), null)
    doTest(content, whitelist.build())
  }
}