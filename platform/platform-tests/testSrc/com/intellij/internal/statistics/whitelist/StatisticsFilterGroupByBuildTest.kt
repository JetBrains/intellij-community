// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.whitelist

import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService
import org.junit.Assert
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatisticsFilterGroupByBuildTest {

  private fun doTestAccepted(content: String, build: String, vararg expected: String) {
    val actual = FUStatisticsWhiteListGroupsService.parseApprovedGroups(content)
    for (e in expected) {
      Assert.assertTrue(actual.accepts(e, "4", build))
    }
  }

  private fun doTestRejected(content: String, build: String, vararg expected: String) {
    val actual = FUStatisticsWhiteListGroupsService.parseApprovedGroups(content)
    for (e in expected) {
      Assert.assertFalse(actual.accepts(e, "4", build))
    }
  }

  @Test
  fun `with build and group version is accepted`() {
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
    doTestAccepted(content, "IU-173.4284.118", "test.group.id")
  }

  @Test
  fun `with build and from group version is accepted`() {
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
    doTestAccepted(content, "IU-173.4284.118", "test.group.id")
  }

  @Test
  fun `with equal build than from is accepted`() {
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
    doTestAccepted(content, "IU-173.4284.118", "test.group.id")
  }

  @Test
  fun `with greater minor minor number build than from is accepted`() {
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
    doTestAccepted(content, "IU-173.4284.128", "test.group.id")
  }

  @Test
  fun `with greater minor number build than from is accepted`() {
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
    doTestAccepted(content, "IU-173.4285.118", "test.group.id")
  }

  @Test
  fun `with greater but shorter minor number build than from is accepted`() {
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
    doTestAccepted(content, "IU-173.4285", "test.group.id")
  }

  @Test
  fun `with greater major number build than from is accepted`() {
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
    doTestAccepted(content, "IU-182.4280.118", "test.group.id")
  }

  @Test
  fun `with greater but shorter major number build than from is accepted`() {
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
    doTestAccepted(content, "IU-182.4280", "test.group.id")
  }

  @Test
  fun `with greater but even shorter major number build than from is accepted`() {
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
    doTestAccepted(content, "IU-182", "test.group.id")
  }

  @Test
  fun `with earlier build than from is not accepted`() {
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
    doTestRejected(content, "IU-173.4284.10", "test.group.id")
  }

  @Test
  fun `with earlier minor number build than from is not accepted`() {
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
    doTestRejected(content, "IU-173.428.118", "test.group.id")
  }

  @Test
  fun `with earlier and shorter minor number build than from is not accepted`() {
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
    doTestRejected(content, "IU-173.428", "test.group.id")
  }

  @Test
  fun `with earlier major number build than from is not accepted`() {
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
    doTestRejected(content, "IU-172.4284.118", "test.group.id")
  }

  @Test
  fun `with earlier and shorter major number build than from is not accepted`() {
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
    doTestRejected(content, "IU-172.4284", "test.group.id")
  }

  @Test
  fun `with earlier and even shorter major number build than from is not accepted`() {
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
    doTestRejected(content, "IU-172", "test.group.id")
  }

  @Test
  fun `with equal build than to is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "173.4495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "IU-173.4495.123", "test.group.id")
  }

  @Test
  fun `with greater minor minor number build than to is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "173.4495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "IU-173.4495.128", "test.group.id")
  }

  @Test
  fun `with greater minor number build than to is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "173.4495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "IU-173.4595.123", "test.group.id")
  }

  @Test
  fun `with greater but shorter minor number build than to is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "173.4495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "IU-173.4595", "test.group.id")
  }

  @Test
  fun `with greater major number build than to is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "173.4495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "IU-182.4495.123", "test.group.id")
  }

  @Test
  fun `with greater but shorter major number build than to is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "173.4495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "IU-182.4495", "test.group.id")
  }

  @Test
  fun `with greater but even shorter major number build than to is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "173.4495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "IU-182", "test.group.id")
  }

  @Test
  fun `with earlier build than to is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "173.4495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "IU-173.4495.11", "test.group.id")
  }

  @Test
  fun `with earlier minor number build than to is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "173.4495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "IU-173.4384.123", "test.group.id")
  }

  @Test
  fun `with earlier and shorter minor number build than to is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "173.4495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "IU-173.4384", "test.group.id")
  }

  @Test
  fun `with earlier major number build than to is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "183.4495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "IU-181.4284.118", "test.group.id")
  }

  @Test
  fun `with earlier and shorter major number build than to is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "183.4495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "IU-181.4284", "test.group.id")
  }

  @Test
  fun `with earlier and even shorter major number build than to is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "183.4495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "IU-181", "test.group.id")
  }

  @Test
  fun `with lexicographically smaller but numerical greater build than from is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.428.118",
      "to" : "183.4495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "IU-173.1428.118", "test.group.id")
  }

  @Test
  fun `with lexicographically smaller but numerical greater build than from without to is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.428.118"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "IU-173.1428.118", "test.group.id")
  }

  @Test
  fun `with lexicographically greater but numerical smaller build than to is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "173.4284.118",
      "to" : "183.1495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "IU-183.495.123", "test.group.id")
  }

  @Test
  fun `with lexicographically greater but numerical smaller build than to without from is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "to" : "183.1495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "IU-183.495.123", "test.group.id")
  }

  @Test
  fun `with ps product code is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "PS-183.2495", "test.group.id")
  }

  @Test
  fun `without product code is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "183.2495", "test.group.id")
  }

  @Test
  fun `with from snapshot and build later is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.0"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "183.1495", "test.group.id")
  }

  @Test
  fun `with from snapshot and build later with bugfix update is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.0"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "183.1495.245", "test.group.id")
  }

  @Test
  fun `with from snapshot and build earlier is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "191.0"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "183.1495", "test.group.id")
  }

  @Test
  fun `with from equals to build is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1495"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "183.1495", "test.group.id")
  }

  @Test
  fun `with from middle number equals to build is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1495"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "183.1495.0", "test.group.id")
  }

  @Test
  fun `with from middle number equals to build and last is bigger is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1495"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "183.1495.12", "test.group.id")
  }

  @Test
  fun `with build middle number equals to from and last is bigger is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1495.12"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "183.1495", "test.group.id")
  }

  @Test
  fun `with build middle number equals to to is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1495.12",
      "to": "183.4885"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "183.4885", "test.group.id")
  }

  @Test
  fun `with build middle number equals to to and last is bigger is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1495.12",
      "to": "183.4885"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "183.4885.35", "test.group.id")
  }

  @Test
  fun `with build middle number smaller then to is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1495.12",
      "to": "183.4885"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "183.4884.35", "test.group.id")
  }

  @Test
  fun `with build middle number smaller then to and have two numbers is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1495.12",
      "to": "183.4885"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "183.4884", "test.group.id")
  }

  @Test
  fun `snapshot builds major greater than from is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "IC-191.SNAPSHOT", "test.group.id")
  }

  @Test
  fun `snapshot builds major equals to from is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "IC-183.0", "test.group.id")
  }

  @Test
  fun `snapshot builds major smaller than from is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "IC-181.0", "test.group.id")
  }

  @Test
  fun `snapshot builds with another product code is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1495.123"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "PY-191.0", "test.group.id")
  }

  @Test
  fun `snapshot builds major equals to from with to is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1495.123",
      "to" : "191.0"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "PY-191.0", "test.group.id")
  }

  @Test
  fun `snapshot builds major greater than from and smaller than to is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1495.123",
      "to" : "192.0"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "PY-191.0", "test.group.id")
  }

  @Test
  fun `snapshot builds major greater than to is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "182.1495.123",
      "to" : "183.0"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "PY-191.0", "test.group.id")
  }

  @Test
  fun `snapshot builds major equals to to is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "182.1495.123",
      "to" : "183.0"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "PY-183.0", "test.group.id")
  }

  @Test
  fun `with multiple builds first contains is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "182.1495.123",
      "to" : "183.0"
    },{
      "from" : "183.162",
      "to" : "183.234"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "PY-182.2435", "test.group.id")
  }

  @Test
  fun `with multiple builds middle contains is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "182.1495.123",
      "to" : "183.0"
    },{
      "from" : "183.162",
      "to" : "183.234"
    },{
      "from" : "183.1623",
      "to" : "183.2334"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "183.200", "test.group.id")
  }

  @Test
  fun `with multiple builds last contains is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "182.1495.123",
      "to" : "183.0"
    },{
      "from" : "183.162",
      "to" : "183.234"
    },{
      "from" : "183.1623",
      "to" : "183.2334"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "183.2200", "test.group.id")
  }

  @Test
  fun `with multiple builds does not contains is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "182.1495.123",
      "to" : "183.0"
    },{
      "from" : "183.162",
      "to" : "183.234"
    },{
      "from" : "183.1623",
      "to" : "183.2334"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "183.3421", "test.group.id")
  }

  @Test
  fun `with multiple builds before first is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "182.1495.123",
      "to" : "183.0"
    },{
      "from" : "183.162",
      "to" : "183.234"
    },{
      "from" : "183.1623",
      "to" : "183.2334"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "182.421.123", "test.group.id")
  }

  @Test
  fun `with multiple builds between ranges is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "182.1495.123",
      "to" : "183.0"
    },{
      "from" : "183.162",
      "to" : "183.234"
    },{
      "from" : "183.1623",
      "to" : "183.2334"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "183.45.12", "test.group.id")
  }

  @Test
  fun `with multiple builds last without to is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "182.1495.123",
      "to" : "183.0"
    },{
      "from" : "183.162",
      "to" : "183.234"
    },{
      "from" : "183.1623"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "183.2145", "test.group.id")
  }

  @Test
  fun `with multiple builds first without to is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "182.1495.123"
    },{
      "from" : "183.162",
      "to" : "183.234"
    },{
      "from" : "183.1623"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "182.2145", "test.group.id")
  }

  @Test
  fun `with from greater than to is not accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.2162",
      "to" : "183.234"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "183.1145", "test.group.id")
  }

  @Test
  fun `with multiple groups first is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.162",
      "to" : "183.1534"
    }],
    "context" : {
    }
  },{
    "id" : "second.test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "182.162",
      "to" : "183.1234"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "183.1456.23", "test.group.id")
    doTestRejected(content, "183.1456.23", "second.test.group.id")
  }

  @Test
  fun `with multiple groups second is accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.162",
      "to" : "183.1534"
    }],
    "context" : {
    }
  },{
    "id" : "second.test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "182.162",
      "to" : "183.1234"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "182.1056", "second.test.group.id")
    doTestRejected(content, "182.1056", "test.group.id")
  }

  @Test
  fun `with multiple groups both are accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.162",
      "to" : "183.1534"
    }],
    "context" : {
    }
  },{
    "id" : "second.test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "182.162",
      "to" : "183.1234"
    }],
    "context" : {
    }
  }]
}
    """
    doTestAccepted(content, "183.1056", "test.group.id", "second.test.group.id")
  }

  @Test
  fun `with multiple groups none are accepted`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.162",
      "to" : "183.1534"
    }],
    "context" : {
    }
  },{
    "id" : "second.test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "182.162",
      "to" : "183.1234"
    }],
    "context" : {
    }
  }]
}
    """
    doTestRejected(content, "182.56", "test.group.id", "second.test.group.id")
  }
}