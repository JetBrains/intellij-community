// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions

import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.EditorModelFixture
import com.intellij.openapi.editor.impl.SettingsImpl
import com.intellij.openapi.editor.impl.editorModelFixture
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * "Scroll to Center" must center the caret also when the editor refrains from scrolling. That setting keeps a visible
 * caret in place, so the action turns it off for one scroll in this editor only.
 */
@TestApplication
internal class ScrollToCenterActionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private const val CARET_LINE = 46
    private const val TOP_LINE = 45
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  @Test
  fun `the action centers a visible caret without a write to the global setting`(): Unit = fixture.runTest {
    initText((0 until 100).joinToString("\n") { "line $it" })
    setEditorVisibleSize(80, 10)
    val global = EditorSettingsExternalizable.getInstance()
    val original = global.isRefrainFromScrolling
    val events = mutableListOf<String>()
    val disposable = Disposer.newDisposable()
    try {
      global.isRefrainFromScrolling = false
      val centeredTop = centeredTop()
      global.isRefrainFromScrolling = true
      placeTheCaretBelowTheTop()
      global.addPropertyChangeListener({ events.add(it.propertyName) }, disposable)

      executeAction("EditorScrollToCenter")

      assertEquals(centeredTop, editor.scrollingModel.visibleArea.y, "The action must center the caret")
      assertEquals(emptyList<String>(), events, "The action must not write the global setting")
      assertTrue(editor.settings.isRefrainFromScrolling)
      val state = (editor.settings as SettingsImpl).getState()
      assertFalse(state.isOverridden(state::myIsRefrainFromScrolling), "The editor must follow the global setting again")
    } finally {
      Disposer.dispose(disposable)
      global.isRefrainFromScrolling = original
    }
  }

  @Test
  fun `the action keeps the own value of the editor`(): Unit = fixture.runTest {
    initText((0 until 100).joinToString("\n") { "line $it" })
    setEditorVisibleSize(80, 10)
    val global = EditorSettingsExternalizable.getInstance()
    val original = global.isRefrainFromScrolling
    try {
      global.isRefrainFromScrolling = false
      val centeredTop = centeredTop()
      editor.settings.isRefrainFromScrolling = true
      placeTheCaretBelowTheTop()

      executeAction("EditorScrollToCenter")

      assertEquals(centeredTop, editor.scrollingModel.visibleArea.y, "The action must center the caret")
      assertTrue(editor.settings.isRefrainFromScrolling)
      val state = (editor.settings as SettingsImpl).getState()
      assertTrue(state.isOverridden(state::myIsRefrainFromScrolling), "The editor must keep its own value")
    } finally {
      global.isRefrainFromScrolling = original
    }
  }

  /**
   * Puts the caret on a visible line just below the top of the visible area, where the setting stops a center scroll.
   */
  private fun EditorModelFixture.placeTheCaretBelowTheTop() {
    editor.caretModel.moveToOffset(editor.document.getLineStartOffset(CARET_LINE))
    editor.scrollingModel.disableAnimation()
    editor.scrollingModel.scrollVertically(TOP_LINE * editor.lineHeight)
  }

  /**
   * Returns the top of the visible area after a center scroll that the setting does not stop. Call it while the editor
   * does not refrain from scrolling.
   */
  private fun EditorModelFixture.centeredTop(): Int {
    placeTheCaretBelowTheTop()
    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    val top = editor.scrollingModel.visibleArea.y
    assertTrue(top != TOP_LINE * editor.lineHeight, "A center scroll must move the visible area")
    return top
  }
}
