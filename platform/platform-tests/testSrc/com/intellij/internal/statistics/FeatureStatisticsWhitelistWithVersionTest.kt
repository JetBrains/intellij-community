// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.service.fus.FUSWhitelist
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService
import com.intellij.openapi.util.BuildNumber
import org.junit.Test
import kotlin.test.assertEquals

class FeatureStatisticsWhitelistWithVersionTest {

  private fun doTest(content: String, expected: FUSWhitelist) {
    val actual = FUStatisticsWhiteListGroupsService.parseApprovedGroups(content, BuildNumber.fromString("191.0"))
    assertEquals(expected.size, actual.size)
    assertEquals(expected, actual)
  }

  private fun newVersion(from: Int, to: Int): FUSWhitelist.VersionRange {
    val range = FUSWhitelist.VersionRange()
    range.from = from
    range.to = to
    return range
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
    doTest(content, WhitelistBuilder().add("test.group.id", newVersion(3, 5)).build())
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
    doTest(content, WhitelistBuilder().add("test.group.id", newVersion(3, Int.MAX_VALUE)).build())
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
    doTest(content, WhitelistBuilder().add("test.group.id", newVersion(0, 13)).build())
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
    doTest(content, WhitelistBuilder().add("test.group.id", newVersion(0, Int.MAX_VALUE)).build())
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
    doTest(content, WhitelistBuilder().add("test.group.id").build())
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
    doTest(content, WhitelistBuilder().add("test.group.id", newVersion(1, 5), newVersion(6, 7)).build())
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
    doTest(content, WhitelistBuilder().add("test.group.id", newVersion(1, 8), newVersion(6, 7)).build())
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
    doTest(content, WhitelistBuilder().add("test.group.id", newVersion(0, 8), newVersion(8, Int.MAX_VALUE)).build())
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
    doTest(content, WhitelistBuilder().add("test.group.id", newVersion(13, 8)).build())
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
    doTest(content, WhitelistBuilder().add("test.group.id", newVersion(3, 8)).build())
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
    doTest(content, WhitelistBuilder().add("test.group.id", newVersion(3, 8)).build())
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
    doTest(content, WhitelistBuilder().add("test.group.id", newVersion(Int.MAX_VALUE, 5)).build())
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
    doTest(content, WhitelistBuilder().add("test.group.id", newVersion(3, 0)).build())
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
    doTest(content, WhitelistBuilder().add("test.group.id", newVersion(Int.MAX_VALUE, 5)).build())
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
    doTest(content, WhitelistBuilder().add("test.group.id", newVersion(3, 0)).build())
  }
}