// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates

import org.junit.Test

class ComposeResourcesStringArrayTemplateTest {

  @Test
  fun `test string-array template variations`() {
    assertStringArrayTemplate(
      expectedTemplate = """
        <string-array name="colors">
        <item>${variable(0)}</item>
        $TEMPLATE_END
        </string-array>
        $TEMPLATE_END""",
      expectedVariableCounter = 1,
      name = "colors",
      text = "",
      repetitions = 1,
      items = 1
    )

    assertStringArrayTemplate(
      expectedTemplate = """
        <string-array name="fruits">
        <item>${variable(0)}</item>
        $TAB<item>${variable(1)}</item>
        $TAB<item>${variable(2)}</item>
        $TAB</string-array>
        $TEMPLATE_END""",
      expectedVariableCounter = 3,
      name = "fruits",
      text = "",
      repetitions = 1,
      items = 3
    )

    assertStringArrayTemplate(
      expectedTemplate = """
        <string-array name="greetings">
        <item>Hello ${variable(0)}</item>
        $TAB<item>Hello ${variable(1)}</item>
        $TAB</string-array>
        $TEMPLATE_END""",
      expectedVariableCounter = 2,
      name = "greetings",
      text = "Hello ",
      repetitions = 1,
      items = 2
    )

    assertStringArrayTemplate(
      expectedTemplate = """
        <string-array name="${variable(0)}">
        <item>${variable(1)}</item>
        $TEMPLATE_END
        </string-array>
        $TEMPLATE_END""",
      expectedVariableCounter = 2,
      name = "",
      text = "",
      repetitions = 1,
      items = 1
    )

    assertStringArrayTemplate(
      expectedTemplate = """
        <string-array name="arr">
        <item>${variable(0)}</item>
        $TEMPLATE_END
        </string-array>
        $TAB<string-array name="arr1">
        <item>${variable(1)}</item>
        $TEMPLATE_END
        </string-array>
        $TEMPLATE_END""",
      expectedVariableCounter = 2,
      name = "arr",
      text = "",
      repetitions = 2,
      items = 1
    )

  }

  @Test
  fun `test TemplateType matches string-array triggers`() {
    StringArrayType.assertMatchesKeys("sa", "string-array", "sa.test", "string-array.test", "test>2", ">")
  }
}