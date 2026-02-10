// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates

import org.junit.Test

class ComposeResourcesPluralsTemplateTest {

  @Test
  fun `test plurals template variations`() {
    assertPluralsTemplate(
      expectedTemplate = """
        <plurals name="apples">
        <item quantity="one">${variable(0)}</item>
        <item quantity="other">${variable(1)}</item>
        </plurals>
        $TEMPLATE_END""",
      expectedVariableCounter = 2,
      name = "apples",
      text = "",
      repetitions = 1,
      categoryMode = "c",
      qualifier = "en"
    )

    assertPluralsTemplate(
      expectedTemplate = """
        <plurals name="jabloki">
        <item quantity="one">${variable(0)}</item>
        <item quantity="few">${variable(1)}</item>
        <item quantity="many">${variable(2)}</item>
        <item quantity="other">${variable(3)}</item>
        </plurals>
        $TEMPLATE_END""",
      expectedVariableCounter = 4,
      name = "jabloki",
      text = "",
      repetitions = 1,
      categoryMode = "c",
      qualifier = "ru"
    )

    assertPluralsTemplate(
      expectedTemplate = """
        <plurals name="test">
        <item quantity="zero">${variable(0)}</item>
        <item quantity="one">${variable(1)}</item>
        <item quantity="two">${variable(2)}</item>
        <item quantity="few">${variable(3)}</item>
        <item quantity="many">${variable(4)}</item>
        <item quantity="other">${variable(5)}</item>
        </plurals>
        $TEMPLATE_END""",
      expectedVariableCounter = 6,
      name = "test",
      text = "",
      repetitions = 1,
      categoryMode = "c",
      qualifier = "ar"
    )

    assertPluralsTemplate(
      expectedTemplate = """
        <plurals name="test">
        <item quantity="one">${variable(0)}</item>
        <item quantity="many">${variable(1)}</item>
        <item quantity="other">${variable(2)}</item>
        </plurals>
        $TEMPLATE_END""",
      expectedVariableCounter = 3,
      name = "test",
      text = "",
      repetitions = 1,
      categoryMode = "c",
      qualifier = "fr"
    )

    assertPluralsTemplate(
      expectedTemplate = """
        <plurals name="test">
        <item quantity="zero">val${variable(0)}</item>
        <item quantity="one">val${variable(1)}</item>
        <item quantity="two">val${variable(2)}</item>
        <item quantity="few">val${variable(3)}</item>
        <item quantity="many">val${variable(4)}</item>
        <item quantity="other">val${variable(5)}</item>
        </plurals>
        $TEMPLATE_END""",
      expectedVariableCounter = 6,
      name = "test",
      text = "val",
      repetitions = 1,
      categoryMode = "a",
      qualifier = "en"
    )

    assertPluralsTemplate(
      expectedTemplate = """
        <plurals name="test">
        <item quantity="zero">${variable(0)}</item>
        <item quantity="one">${variable(1)}</item>
        <item quantity="two">${variable(2)}</item>
        <item quantity="few">${variable(3)}</item>
        <item quantity="many">${variable(4)}</item>
        <item quantity="other">${variable(5)}</item>
        </plurals>
        $TEMPLATE_END""",
      expectedVariableCounter = 6,
      name = "test",
      text = "",
      repetitions = 1,
      categoryMode = "c",
      qualifier = ""
    )
  }

  @Test
  fun `test TemplateType matches plurals triggers`() {
    PluralsType.assertMatchesKeys("p", "plurals", ":", "p.test", "plurals.test", "test:a")
  }

}