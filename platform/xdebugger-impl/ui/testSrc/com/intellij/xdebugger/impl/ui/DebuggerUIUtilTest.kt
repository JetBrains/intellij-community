// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.debugger.impl.ui.evaluate.quick.common.ValueLookupManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.impl.settings.DataViewsConfigurableUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Point
import java.awt.event.MouseEvent

@TestApplication
@RegistryKey(key = DataViewsConfigurableUi.DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_KEY, value = "true")
class DebuggerUIUtilTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun `text viewer does not request value hints`(): Unit = checkValueLookupDisabled(isViewer = true)

  @Test
  fun `text editor does not request value hints`(): Unit = checkValueLookupDisabled(isViewer = false)

  private fun checkValueLookupDisabled(isViewer: Boolean): Unit = timeoutRunBlocking {
    val requestCounts = withContext(Dispatchers.EDT) {
      val project = projectFixture.get()
      val disposable = Disposer.newDisposable()
      try {
        val handler = RecordingHandler()
        val manager = ValueLookupManager(project)
        manager.startListening(handler)

        val popupEditor = DebuggerUIUtil.createFormattedTextEditor("X".repeat(2000), FileTypes.PLAIN_TEXT, project, disposable, isViewer)
        moveMouse(manager, popupEditor)
        val popupRequests = handler.requests

        val factory = EditorFactory.getInstance()
        val sourceEditor = factory.createEditor(factory.createDocument("longString"), project)
        Disposer.register(disposable) { factory.releaseEditor(sourceEditor) }
        moveMouse(manager, sourceEditor)
        popupRequests to handler.requests
      }
      finally {
        Disposer.dispose(disposable)
      }
    }
    assertEquals(0, requestCounts.first, "The popup editor must not request a value hint")
    assertEquals(1, requestCounts.second, "The source editor must still request a value hint")
  }

  private fun moveMouse(manager: ValueLookupManager, editor: Editor) {
    val mouseEvent = MouseEvent(editor.contentComponent, MouseEvent.MOUSE_MOVED, 0, 0, 0, 0, 0, false)
    val event = EditorMouseEvent(editor, mouseEvent, EditorMouseEventArea.EDITING_AREA)
    try {
      manager.mouseMoved(event)
    }
    finally {
      manager.mouseExited(event)
    }
  }

  private class RecordingHandler : QuickEvaluateHandler() {
    var requests = 0

    override fun isEnabled(project: Project): Boolean = true

    override fun canShowHint(project: Project): Boolean = true

    override fun getValueLookupDelay(project: Project): Int {
      requests++
      return 0
    }

    override fun createValueHint(project: Project, editor: Editor, point: Point, type: ValueHintType): AbstractValueHint? {
      error("The pending request must be cancelled")
    }
  }
}
