// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates

import org.junit.Test
import kotlin.test.assertIs

class ComposeResourcesStringTemplateTest {

  @Test
  fun `test string template variations`() {
    assertStringTemplate(
      expectedTemplate = """
        <string name="test">${variable(0)}</string>
        $TEMPLATE_END""",
      expectedVariableCounter = 1,
      name = "test",
      text = "",
      repetitions = 1
    )

    assertStringTemplate(
      expectedTemplate = """
        <string name="greeting">Hello</string>
        $TEMPLATE_END""",
      expectedVariableCounter = 0,
      name = "greeting",
      text = "Hello",
      repetitions = 1
    )

    assertStringTemplate(
      expectedTemplate = """
        <string name="${variable(0)}">${variable(1)}</string>
        $TEMPLATE_END""",
      expectedVariableCounter = 2,
      name = "",
      text = "",
      repetitions = 1
    )

    assertStringTemplate(
      expectedTemplate = """
        <string name="test">val${variable(0)}</string>
        $TAB<string name="test1">val${variable(1)}</string>
        $TAB<string name="test2">val${variable(2)}</string>
        $TEMPLATE_END""",
      expectedVariableCounter = 3,
      name = "test",
      text = "val",
      repetitions = 3
    )

    assertStringTemplate(
      expectedTemplate = """
        <string name="${variable(0)}">v${variable(1)}</string>
        $TAB<string name="${variable(2)}">v${variable(3)}</string>
        $TEMPLATE_END""",
      expectedVariableCounter = 4,
      name = "",
      text = "v",
      repetitions = 2
    )
  }

  @Test
  fun `test Name gets replaced by identifier`() {
    val name1 = "test.underscores".asNameAttributeValue()
    assertStringTemplate(
      expectedTemplate = """
        <string name="test_underscores">${variable(0)}</string>
        $TEMPLATE_END""",
      expectedVariableCounter = 1,
      name = name1,
      text = "",
      repetitions = 1
    )

    val name2 = "1test".asNameAttributeValue()
    assertStringTemplate(
      expectedTemplate = """
        <string name="_1test">${variable(0)}</string>
        $TEMPLATE_END""",
      expectedVariableCounter = 1,
      name = name2,
      text = "",
      repetitions = 1
    )
  }

  @Test
  fun `test various prefixes and variants`() {
    assertIsStringType(TemplateType.fromParsedKey("."))
    assertIsStringType(TemplateType.fromParsedKey("s"))
    assertIsStringType(TemplateType.fromParsedKey("string"))

    assertIsStringType(TemplateType.fromParsedKey("test"))
    assertIsStringType(TemplateType.fromParsedKey(".test"))
    assertIsStringType(TemplateType.fromParsedKey("s.test"))
    assertIsStringType(TemplateType.fromParsedKey("string.test"))
  }

  private fun assertIsStringType(type: TemplateType) {
    assertIs<StringTemplateType>(type)
  }

}
