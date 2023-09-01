// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.codeInsight.daemon.impl.IntentionsUIImpl
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.PriorityQuestionAction
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.ToolbarUtils.createImmediatelyUpdatedToolbar
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.HintHint
import com.intellij.ui.LightweightHint
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import java.awt.Point
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

internal class InlayRunToCursorEditorListener(private val project: Project) : EditorMouseMotionListener, EditorMouseListener {
  private var currentHint = WeakReference<RunToCursorHint?>(null)
  private var currentEditor = WeakReference<Editor?>(null)
  private var currentLineNumber = -1

  override fun mouseMoved(e: EditorMouseEvent) {
    if (!Registry.`is`("debugger.inlayRunToCursor")) {
      IntentionsUIImpl.DISABLE_INTENTION_BULB[project] = false
      return
    }
    val wasShown = showInlayRunToCursor(e)
    if (!wasShown) {
      hideHint()
    }
  }

  private fun reset(hint: RunToCursorHint) {
    if (hint != currentHint.get()) {
      return
    }
    currentHint.clear()
    currentEditor = WeakReference(null)
    currentLineNumber = -1
  }

  private fun hideHint() {
    val hint = currentHint.get()
    hint?.hide()
  }

  private fun showInlayRunToCursor(e: EditorMouseEvent): Boolean {
    val editor = e.editor
    if (editor.getEditorKind() != EditorKind.MAIN_EDITOR) {
      return false
    }
    if (editor.getScrollingModel().getHorizontalScrollOffset() != 0) {
      return false
    }
    val session = XDebuggerManager.getInstance(project).getCurrentSession() as XDebugSessionImpl?
    if (session == null || !session.isPaused || session.isReadOnly) {
      IntentionsUIImpl.DISABLE_INTENTION_BULB[project] = false
      return false
    }
    IntentionsUIImpl.DISABLE_INTENTION_BULB[project] = true
    val lineNumber = XDebuggerManagerImpl.getLineNumber(e)
    if (lineNumber < 0) {
      return false
    }
    if (currentEditor.get() === editor && currentLineNumber == lineNumber) {
      return true
    }
    currentEditor = WeakReference(editor)
    currentLineNumber = lineNumber
    var firstNonSpaceSymbol = editor.getDocument().getLineStartOffset(lineNumber)
    val charsSequence = editor.getDocument().charsSequence
    while (true) {
      if (firstNonSpaceSymbol >= charsSequence.length) {
        return false //end of file
      }
      val c = charsSequence[firstNonSpaceSymbol]
      if (c == '\n') {
        return false // empty line
      }
      if (!Character.isWhitespace(c)) {
        break
      }
      firstNonSpaceSymbol++
    }
    val firstNonSpacePos = editor.offsetToXY(firstNonSpaceSymbol)
    if (firstNonSpacePos.x < JBUI.scale(16)) {
      return false
    }
    val lineY = editor.logicalPositionToXY(LogicalPosition(lineNumber, 0)).y
    val position = SwingUtilities.convertPoint(editor.getContentComponent(), Point(-JBUI.scale(6), lineY),
                                               editor.getComponent().rootPane.layeredPane)
    val group = DefaultActionGroup()
    val pausePosition = session.currentPosition
    if (pausePosition != null && pausePosition.getFile() == editor.virtualFile && pausePosition.getLine() == lineNumber) {
      group.add(ActionManager.getInstance().getAction(XDebuggerActions.RESUME))
    }
    else {
      group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_RUN_TO_CURSOR))
    }
    val caretLine = editor.getCaretModel().logicalPosition.line
    if (editor.getSettings().isShowIntentionBulb() && caretLine == lineNumber && firstNonSpacePos.x >= JBUI.scale(4 + 22 * 2)) {
      group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS))
    }
    val toolbarImpl = createImmediatelyUpdatedToolbar(group, ActionPlaces.EDITOR_HINT, editor.getComponent(), true) {} as ActionToolbarImpl
    toolbarImpl.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY)
    val sideButtonOffset = 2
    toolbarImpl.setActionButtonBorder(JBUI.Borders.empty(0, sideButtonOffset))
    toolbarImpl.setBorder(null)
    toolbarImpl.setOpaque(false)
    toolbarImpl.setAdditionalDataProvider { dataId: String? ->
      if (XDebuggerUtilImpl.LINE_NUMBER.`is`(dataId)) {
        return@setAdditionalDataProvider lineNumber
      }
      null
    }
    val justPanel: JPanel = NonOpaquePanel()
    justPanel.preferredSize = JBDimension((2 * sideButtonOffset + 22) * group.childrenCount, 22)
    justPanel.add(toolbarImpl.component)
    val hint = RunToCursorHint(justPanel, this)
    currentHint = WeakReference(hint)
    val questionAction: QuestionAction = object : PriorityQuestionAction {
      override fun execute(): Boolean {
        return true
      }

      override fun getPriority(): Int {
        return PriorityQuestionAction.INTENTION_BULB_PRIORITY
      }
    }
    val offset = editor.getCaretModel().offset
    HintManagerImpl.getInstanceImpl().showQuestionHint(editor, position, offset, offset, hint, questionAction, HintManager.RIGHT)
    return true
  }

  private class RunToCursorHint(component: JComponent, private val listener: InlayRunToCursorEditorListener) : LightweightHint(component) {
    override fun show(parentComponent: JComponent, x: Int, y: Int, focusBackComponent: JComponent, hintHint: HintHint) {
      super.show(parentComponent, x, y, focusBackComponent, HintHint(parentComponent, Point(x, y)))
    }

    override fun hide(ok: Boolean) {
      listener.reset(this)
      super.hide(ok)
    }
  }
}
