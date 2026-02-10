// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates

import org.junit.Test

class ComposeResourcesItemTemplateTest {

  @Test
  fun `test item template variations`() {
    assertItemTemplate(
      expectedTemplate = """
        <item>${variable(0)}</item>
        $TEMPLATE_END""",
      expectedVariableCounter = 1,
      text = "",
      repetitions = 1,
      hasQuantity = false
    )

    assertItemTemplate(
      expectedTemplate = """
        <item quantity="${variable(0)}">${variable(1)}</item>
        $TEMPLATE_END""",
      expectedVariableCounter = 2,
      text = "",
      repetitions = 1,
      hasQuantity = true
    )

    assertItemTemplate(
      expectedTemplate = """
        <item>Hello</item>
        $TEMPLATE_END""",
      expectedVariableCounter = 0,
      text = "Hello",
      repetitions = 1,
      hasQuantity = false
    )

    assertItemTemplate(
      expectedTemplate = """
        <item quantity="${variable(0)}">Hello</item>
        $TEMPLATE_END""",
      expectedVariableCounter = 1,
      text = "Hello",
      repetitions = 1,
      hasQuantity = true
    )

    assertItemTemplate(
      expectedTemplate = """
        <item quantity="${variable(0)}">${variable(1)}</item>
        $TAB<item quantity="${variable(2)}">${variable(3)}</item>
        $TEMPLATE_END""",
      expectedVariableCounter = 4,
      text = "",
      repetitions = 2,
      hasQuantity = true
    )
  }

  @Test
  fun `test TemplateType matches item triggers`() {
    ItemType.assertMatchesKeys("i", "item")
  }
}