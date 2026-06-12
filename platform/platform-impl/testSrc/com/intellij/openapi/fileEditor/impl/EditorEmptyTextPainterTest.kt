// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardGestureAction
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

@TestApplication
@RunInEdt(writeIntent = true)
internal class EditorEmptyTextPainterTest {
  private val doubleShiftShortcut = KeyboardModifierGestureShortcut.newInstance(
    KeyboardGestureAction.ModifierType.dblClick,
    KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, InputEvent.SHIFT_MASK),
  ) as KeyboardModifierGestureShortcut

  private lateinit var originalShortcuts: List<Shortcut>

  @BeforeEach
  fun setUp() {
    originalShortcuts = activeKeymap().getShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE).toList()
  }

  @AfterEach
  fun tearDown() {
    resetShortcuts(originalShortcuts)
  }

  @Test
  fun searchEverywhereHintIsHiddenWithoutShortcut() {
    resetShortcuts(emptyList())

    assertThat(RecordingEditorEmptyTextPainter().appendSearchEverywhereLines()).isEmpty()
  }

  @Test
  fun searchEverywhereHintUsesAssignedShortcut() {
    resetShortcuts(listOf(doubleShiftShortcut))

    val expectedLine = IdeBundle.message("empty.text.search.everywhere") +
                       " <shortcut>" + KeymapUtil.getShortcutText(doubleShiftShortcut) + "</shortcut>"
    assertThat(RecordingEditorEmptyTextPainter().appendSearchEverywhereLines())
      .containsExactly(expectedLine)
  }

  private fun resetShortcuts(shortcuts: List<Shortcut>) {
    val keymap = activeKeymap()
    runWriteAction {
      keymap.getShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE).forEach { shortcut ->
        keymap.removeShortcut(IdeActions.ACTION_SEARCH_EVERYWHERE, shortcut)
      }
      shortcuts.forEach { shortcut ->
        keymap.addShortcut(IdeActions.ACTION_SEARCH_EVERYWHERE, shortcut)
      }
    }
  }

  private fun activeKeymap(): Keymap = checkNotNull(KeymapManager.getInstance()).activeKeymap

  private class RecordingEditorEmptyTextPainter : EditorEmptyTextPainter() {
    private val lines = mutableListOf<String>()

    fun appendSearchEverywhereLines(): List<String> {
      appendSearchEverywhere(createTextPainter())
      return lines
    }

    override fun appendLine(painter: UIUtil.TextPainter, line: String) {
      lines.add(line)
    }
  }
}
