// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.keymap

import com.intellij.openapi.actionSystem.KeyboardGestureAction
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.ui.KeyStrokeAdapter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.InstanceOfAssertFactories
import org.junit.jupiter.api.Test

internal class KeymapsTestCaseBaseParsingTest {
  private val testCase = ExposedKeymapsTestCase()

  @Test
  fun `parses keyboard modifier gesture shortcut duplicate key`() {
    val shortcut = testCase.parseShortcutForTest("ctrl control dblClick")

    assertThat(shortcut)
      .asInstanceOf(InstanceOfAssertFactories.type(KeyboardModifierGestureShortcut::class.java))
      .returns(KeyboardGestureAction.ModifierType.dblClick) { it.type }
      .returns(KeyStrokeAdapter.getKeyStroke("ctrl control")) { it.stroke }
  }

  @Test
  fun `formats keyboard modifier gesture shortcut duplicate key`() {
    val shortcut = KeyboardModifierGestureShortcut.newInstance(
      KeyboardGestureAction.ModifierType.dblClick,
      KeyStrokeAdapter.getKeyStroke("ctrl control"),
    )

    assertThat(testCase.getTextForTest(shortcut)).isEqualTo("ctrl control dblClick")
  }

  private class ExposedKeymapsTestCase : KeymapsTestCaseBase() {
    fun parseShortcutForTest(shortcut: String): Shortcut = parseShortcut(shortcut)

    fun getTextForTest(shortcut: Shortcut): String = getText(shortcut)

    override fun getKnownDuplicates(): Map<String, Map<String, List<String>>> = emptyMap()

    override fun getUnknownActions(): Set<String> = emptySet()

    override fun getBoundActions(): Set<String> = emptySet()

    override fun getConflictSafeGroups(): Set<String> = emptySet()

    override fun getGroupForUnknownAction(actionId: String): String? = null
  }
}
