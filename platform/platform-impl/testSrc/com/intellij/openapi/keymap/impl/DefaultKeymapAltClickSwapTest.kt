// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl

import com.intellij.openapi.util.JDOMUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DefaultKeymapAltClickSwapTest {
  @Test
  fun `only the exact alt click shortcuts swap`() {
    val keymap = JDOMUtil.load("""
      <keymap>
        <action id="QuickEvaluateExpression">
          <keyboard-shortcut first-keystroke="control alt F8"/>
          <mouse-shortcut keystroke="alt button1"/>
        </action>
        <action id="EditorAddOrRemoveCaret">
          <mouse-shortcut keystroke="alt shift button1"/>
        </action>
        <action id="DoubleClick">
          <mouse-shortcut keystroke="alt button1 doubleClick"/>
        </action>
        <action id="ControlAltClick">
          <mouse-shortcut keystroke="ctrl alt button1"/>
        </action>
      </keymap>
    """.trimIndent())

    swapAltClickWithAltShiftClick(keymap)

    assertThat(keymap.getChildren("action").associate { action ->
      action.getAttributeValue("id") to action.getChildren("mouse-shortcut").map { it.getAttributeValue("keystroke") }
    }).isEqualTo(mapOf(
      "QuickEvaluateExpression" to listOf("alt shift button1"),
      "EditorAddOrRemoveCaret" to listOf("alt button1"),
      "DoubleClick" to listOf("alt button1 doubleClick"),
      "ControlAltClick" to listOf("ctrl alt button1"),
    ))
    assertThat(keymap.getChildren("action").first().getChild("keyboard-shortcut")!!.getAttributeValue("first-keystroke"))
      .isEqualTo("control alt F8")
  }
}
