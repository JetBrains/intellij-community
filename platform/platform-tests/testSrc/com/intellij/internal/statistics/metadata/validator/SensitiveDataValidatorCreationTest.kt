// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.metadata.validator

import com.intellij.internal.statistic.eventLog.EventLogGroup
import junit.framework.TestCase

class SensitiveDataValidatorCreationTest : BaseSensitiveDataValidatorTest() {
  private fun doTest(content: String, build: String, vararg expected: String) {
    val validator = newValidator(content, build)
    for (exp in expected) {
      val actual = validator.getEventRules(EventLogGroup(exp, 3))
      TestCase.assertTrue(actual.isNotEmpty())
    }
  }

  private fun doTestNotContains(content: String, build: String, vararg expected: String) {
    val validator = newValidator(content, build)
    for (exp in expected) {
      val actual = validator.getEventRules(EventLogGroup(exp, 3))
      TestCase.assertTrue(actual.isEmpty())
    }
  }

  fun `test creating simple validator`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1234"
    }],
    "versions" : [ {
      "from" : "2",
      "to" : "5"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTest(content, "183.1234.31", "test.group.id")
  }

  fun `test creating validator with build`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1234"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTest(content, "183.1234.31", "test.group.id")
  }

  fun `test creating validator with version`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "versions" : [ {
      "from" : "2",
      "to" : "5"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTest(content, "183.1234.31", "test.group.id")
  }

  /**
   * Sensitive data validator doesn't take into account group version
   */
  fun `test creating validator with version out of range`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "versions" : [ {
      "from" : "12",
      "to" : "15"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTest(content, "183.1234.31", "test.group.id")
  }

  fun `test creating validator without build and version`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTestNotContains(content, "183.1234.31", "test.group.id")
  }

  fun `test creating validator with build equals to from`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1234"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTest(content, "183.1234", "test.group.id")
  }

  fun `test creating validator with build older than from`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1234"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTest(content, "183.2234", "test.group.id")
  }

  fun `test creating validator with major build older than from`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1234"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTest(content, "191.12", "test.group.id")
  }

  fun `test creating validator with build before from`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.1234"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTestNotContains(content, "183.12", "test.group.id")
  }

  fun `test creating validator with major build before from`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "191.1234"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTestNotContains(content, "183.12", "test.group.id")
  }

  fun `test creating validator with build equals to to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "to" : "192.1234"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTestNotContains(content, "192.1234", "test.group.id")
  }

  fun `test creating validator with build before to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "to" : "192.1234"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTest(content, "192.12", "test.group.id")
  }

  fun `test creating validator with major build before to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "to" : "192.1234"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTest(content, "191.12", "test.group.id")
  }

  fun `test creating validator with build after to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "to" : "192.1234"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTestNotContains(content, "192.2421", "test.group.id")
  }

  fun `test creating validator with major build after to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "to" : "192.1234"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTestNotContains(content, "202.124", "test.group.id")
  }

  fun `test creating validator with build between from and to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.351",
      "to" : "191.235.124"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTest(content, "191.12", "test.group.id")
  }

  fun `test creating validator with major build between from and to`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.351",
      "to" : "202.235.124"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTest(content, "201.42", "test.group.id")
  }

  fun `test creating validator with multiple ranges`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.351",
      "to" : "191.235.124"
    }, {
      "from" : "192.21",
      "to" : "202.235.124"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTest(content, "201.42", "test.group.id")
  }

  fun `test creating validator with build before range`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.351",
      "to" : "202.235.124"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTestNotContains(content, "183.42", "test.group.id")
  }

  fun `test creating validator with major build before range`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.351",
      "to" : "202.235.124"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTestNotContains(content, "181.42", "test.group.id")
  }

  fun `test creating validator with build after range`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.351",
      "to" : "202.235.124"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTestNotContains(content, "202.421", "test.group.id")
  }

  fun `test creating validator with build major after range`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
      "from" : "183.351",
      "to" : "202.235.124"
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTestNotContains(content, "203.41", "test.group.id")
  }

  fun `test creating validator when from and to are not set`() {
    val content = """
{
  "groups" : [{
    "id" : "test.group.id",
    "title" : "Test Group",
    "description" : "Test group description",
    "type" : "counter",
    "builds" : [ {
    }],
    "rules" : {
      "event_id" : [ "{enum:foo|bar|baz}" ]
    }
  }]
}
    """.trimIndent()

    doTestNotContains(content, "203.41", "test.group.id")
  }
}