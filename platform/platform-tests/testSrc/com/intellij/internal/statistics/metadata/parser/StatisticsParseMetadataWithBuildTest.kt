// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.metadata.parser

import com.intellij.internal.statistic.eventLog.EventLogBuild
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupsFilterRules
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUtils
import com.intellij.internal.statistics.metadata.TestGroupFilterRulesBuilder
import org.junit.Test
import kotlin.test.assertEquals

class StatisticsParseMetadataWithBuildTest {

  private fun doTest(content: String, expected: EventGroupsFilterRules) {
    val actual = EventLogMetadataUtils.parseGroupFilterRules(content)
    assertEquals(expected.size, actual.size)
    assertEquals(expected, actual)
  }

  private fun newBuild(vararg args: Int): EventLogBuild {
    return EventLogBuild(*args)
  }

  @Test
  fun `with one build with from`() {
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
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(173, 4284, 118), null)
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with one build with to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "to" : "173.4284.118"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", null, newBuild(173, 4284, 118))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with one build from and to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "181.231"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(173, 4284, 118), newBuild(181, 231))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with one build from snapshot and to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.0",
      "to" : "181.231"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(173, 0), newBuild(181, 231))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with one build from and to snapshot`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "181.0"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(173, 4284, 118), newBuild(181, 0))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with one build from snapshot and to snapshot`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.0",
      "to" : "181.0"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(173, 0), newBuild(181, 0))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with one number in from`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(173, 0), null)
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with both from and to and one number in from`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173",
      "to" : "182.31.3"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(173, 0), newBuild(182, 31, 3))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with one number in to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "to" : "183"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", null, newBuild(183, 0))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with both from and to and one number in to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.332",
      "to" : "182.0"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(173, 332), newBuild(182, 0))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with one number in from and to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "12",
      "to" : "183"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(12, 0), newBuild(183, 0))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with both from and to and negative from`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "-12",
      "to" : "183.23"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(-12, 0), newBuild(183, 23))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with both from and to and negative to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "12.2351.123",
      "to" : "-183.23"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(12, 2351, 123), newBuild(-183, 23))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with two build ranges with first from`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118"
    },{
      "from" : "182.421",
      "to" : "183.5.1"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(173, 4284, 118), null).
      addBuild("test.group.id", newBuild(182, 421), newBuild(183, 5, 1))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with two build ranges with second from`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "181.231"
    },{
      "from" : "182.421"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(173, 4284, 118), newBuild(181, 231)).
      addBuild("test.group.id", newBuild(182, 421), null)
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with two build ranges with first to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "to" : "181.231"
    },{
      "from" : "182.421",
      "to" : "183.5.1"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", null, newBuild(181, 231)).
      addBuild("test.group.id", newBuild(182, 421), newBuild(183, 5, 1))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with two build ranges with second to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "181.231"
    },{
      "to" : "183.5.1"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(173, 4284, 118), newBuild(181, 231)).
      addBuild("test.group.id", null, newBuild(183, 5, 1))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with two build ranges with from and to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "181.231"
    },{
      "from" : "182.421",
      "to" : "183.5.1"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(173, 4284, 118), newBuild(181, 231)).
      addBuild("test.group.id", newBuild(182, 421), newBuild(183, 5, 1))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with build and version ranges`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "181.231"
    }],
    "versions" : [{
      "from" : "10",
      "to" : "15"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addVersion("test.group.id", 10, 15).
      addBuild("test.group.id", newBuild(173, 4284, 118), newBuild(181, 231))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with multiple build and version ranges`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "181.231"
    },{
      "from" : "182.421",
      "to" : "183.5.1"
    }],
    "versions" : [ {
      "from" : "2",
      "to" : "5"
    },{
      "from" : "10",
      "to" : "15"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addVersion("test.group.id", 2, 5).
      addVersion("test.group.id", 10, 15).
      addBuild("test.group.id", newBuild(173, 4284, 118), newBuild(181, 231)).
      addBuild("test.group.id", newBuild(182, 421), newBuild(183, 5, 1))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with build and empty version ranges`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "181.231"
    }],
    "versions" : [],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(173, 4284, 118), newBuild(181, 231))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with multiple build and empty version ranges`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "181.231"
    },{
      "from" : "182.421",
      "to" : "183.5.1"
    }],
    "versions" : [],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addBuild("test.group.id", newBuild(173, 4284, 118), newBuild(181, 231)).
      addBuild("test.group.id", newBuild(182, 421), newBuild(183, 5, 1))
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with multiple version and empty build ranges`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [],
    "versions" : [ {
      "from" : "2",
      "to" : "5"
    },{
      "from" : "10",
      "to" : "15"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addVersion("test.group.id", 2, 5).
      addVersion("test.group.id", 10, 15)
    doTest(content, rulesBuilder.build())
  }

  @Test
  fun `with version and empty build ranges`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [],
    "versions" : [ {
      "from" : "2",
      "to" : "5"
    }],
    "context" : {
    }
  }]
}
    """
    val rulesBuilder = TestGroupFilterRulesBuilder().
      addVersion("test.group.id", 2, 5)
    doTest(content, rulesBuilder.build())
  }
}