// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardGestureAction
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke

@TestApplication
@RunInEdt(writeIntent = true)
internal class EditorEmptyTextPainterTest {
  private val doubleShiftShortcut = KeyboardModifierGestureShortcut.newInstance(
    KeyboardGestureAction.ModifierType.dblClick,
    KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, InputEvent.SHIFT_MASK),
  ) as KeyboardModifierGestureShortcut
  private val doubleCtrlShortcut = KeyboardModifierGestureShortcut.newInstance(
    KeyboardGestureAction.ModifierType.dblClick,
    KeyStroke.getKeyStroke(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK),
  ) as KeyboardModifierGestureShortcut
  private val ctrlBackslashShortcut = KeyboardShortcut(
    KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, InputEvent.CTRL_MASK),
    null,
  )

  private lateinit var originalShortcuts: Map<String, List<Shortcut>>

  @BeforeEach
  fun setUp() {
    originalShortcuts = listOf(IdeActions.ACTION_SEARCH_EVERYWHERE, PROMOTED_ACTION_ID)
      .associateWith { activeKeymap().getShortcuts(it).toList() }
  }

  @AfterEach
  fun tearDown() {
    originalShortcuts.forEach { (actionId, shortcuts) -> resetShortcuts(actionId, shortcuts) }
  }

  @Test
  fun searchEverywhereHintIsHiddenWithoutShortcut() {
    resetShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE, emptyList())

    assertThat(RecordingEditorEmptyTextPainter().appendSearchEverywhereLines()).isEmpty()
  }

  @Test
  fun searchEverywhereHintUsesAssignedShortcut() {
    resetShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE, listOf(doubleShiftShortcut))

    val expectedLine = IdeBundle.message("empty.text.search.everywhere") +
                       " <shortcut>" + KeymapUtil.getShortcutText(doubleShiftShortcut) + "</shortcut>"
    assertThat(RecordingEditorEmptyTextPainter().appendSearchEverywhereLines())
      .containsExactly(expectedLine)
  }

  @Test
  fun promotedActionHintIsRenderedBeforeSearchEverywhere(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROMOTED_ACTION_ID, listOf(doubleCtrlShortcut))
    resetShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE, listOf(doubleShiftShortcut))
    registerPromotedActionProvider(disposable)

    val promotedLine = PROMOTED_ACTION_TEXT + " <shortcut>" + KeymapUtil.getShortcutText(doubleCtrlShortcut) + "</shortcut>"
    val searchEverywhereLine = IdeBundle.message("empty.text.search.everywhere") +
                               " <shortcut>" + KeymapUtil.getShortcutText(doubleShiftShortcut) + "</shortcut>"
    assertThat(RecordingEditorEmptyTextPainter().appendAdvertisedActionLines())
      .startsWith(promotedLine, searchEverywhereLine)
  }

  @Test
  fun promotedActionHintUsesAssignedShortcuts(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROMOTED_ACTION_ID, listOf(ctrlBackslashShortcut, doubleCtrlShortcut))
    registerPromotedActionProvider(disposable)

    val lines = RecordingEditorEmptyTextPainter().appendPromotedActionLines()

    val expectedShortcutText = KeymapUtil.getShortcutText(doubleCtrlShortcut) +
                               " " + IdeBundle.message("empty.text.shortcut.separator") + " " +
                               KeymapUtil.getShortcutText(ctrlBackslashShortcut)
    assertThat(lines).containsExactly("$PROMOTED_ACTION_TEXT <shortcut>$expectedShortcutText</shortcut>")
  }

  @Test
  fun promotedActionHintIsHiddenWithoutShortcut(@TestDisposable disposable: Disposable) {
    resetShortcuts(PROMOTED_ACTION_ID, emptyList())
    registerPromotedActionProvider(disposable)

    assertThat(RecordingEditorEmptyTextPainter().appendPromotedActionLines()).isEmpty()
  }

  private fun registerPromotedActionProvider(disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(EditorEmptyTextPromotedActionProvider.EP_NAME, listOf(object : EditorEmptyTextPromotedActionProvider {
      override fun getPromotedAction(splitters: JComponent): EditorEmptyTextPromotedActionProvider.PromotedAction {
        return EditorEmptyTextPromotedActionProvider.PromotedAction(PROMOTED_ACTION_ID, PROMOTED_ACTION_TEXT)
      }
    }), disposable)
  }

  private fun resetShortcuts(actionId: String, shortcuts: List<Shortcut>) {
    val keymap = activeKeymap()
    runWriteAction {
      keymap.getShortcuts(actionId).forEach { shortcut ->
        keymap.removeShortcut(actionId, shortcut)
      }
      shortcuts.forEach { shortcut ->
        keymap.addShortcut(actionId, shortcut)
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

    fun appendAdvertisedActionLines(): List<String> {
      advertiseActions(JPanel(), createTextPainter())
      return lines
    }

    fun appendPromotedActionLines(): List<String> {
      advertiseActions(JPanel(), createTextPainter())
      return lines.filter { it.startsWith(PROMOTED_ACTION_TEXT) }
    }

    override fun appendLine(painter: UIUtil.TextPainter, line: String) {
      lines.add(line)
    }
  }

  private companion object {
    const val PROMOTED_ACTION_ID: String = "EditorEmptyTextPainterTest.PromotedAction"
    const val PROMOTED_ACTION_TEXT: String = "Promoted Action"
  }
}
