// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.*
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.LightweightHint
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.properties.Delegates

@ApiStatus.Internal
open class FloatingToolbar(val editor: Editor, private val actionGroupId: String) : Disposable {
  private val mouseListener = MouseListener()
  private val keyboardListener = KeyboardListener()
  private val mouseMotionListener = MouseMotionListener()
  private val selectionListener = EditorSelectionListener()
  private val documentListener = DocumentChangeListener()

  private var hint: LightweightHint? = null
  private var buttonSize: Int by Delegates.notNull()
  private var lastSelection: String? = null
  private var isJustClosed = false

  init {
    registerListeners()
  }

  fun isShown() = hint != null

  fun hideIfShown() {
    hint?.hide()
  }

  fun showIfHidden() {
    if (hint != null || !canBeShownAtCurrentSelection() || (!shouldReviveAfterClose() && isJustClosed)) {
      return
    }
    createActionToolbar(editor.contentComponent) { toolbar ->
      val hint = hint ?: return@createActionToolbar
      hint.component.add(toolbar.component, BorderLayout.CENTER)
      showOrUpdateLocation(hint)
      hint.addHintListener {
        this@FloatingToolbar.hint = null
        isJustClosed = true
      }
    }
    hint = LightweightHint(JPanel(BorderLayout())).apply {
      setForceShowAsPopup(true)
    }
  }

  fun updateLocationIfShown() {
    showOrUpdateLocation(hint ?: return)
  }

  override fun dispose() {
    unregisterListeners()
    hideIfShown()
    hint = null
  }

  private fun createActionToolbar(targetComponent: JComponent, onUpdated: (ActionToolbar) -> Unit) {
    val group = CustomActionsSchema.getInstance().getCorrectedAction(actionGroupId) as? ActionGroup ?: return
    val place = ActionPlaces.EDITOR_FLOATING_TOOLBAR
    val toolbar = ToolbarUtils.createImmediatelyUpdatedToolbar(group, place, targetComponent, horizontal = true, onUpdated)
    buttonSize = toolbar.maxButtonHeight
  }

  private fun showOrUpdateLocation(hint: LightweightHint) {
    HintManagerImpl.getInstanceImpl().showEditorHint(
      hint,
      editor,
      getHintPosition(hint),
      HintManager.HIDE_BY_ESCAPE or HintManager.UPDATE_BY_SCROLLING,
      0,
      true
    )
  }

  private fun registerListeners() {
    editor.addEditorMouseListener(mouseListener)
    editor.addEditorMouseMotionListener(mouseMotionListener)
    editor.contentComponent.addKeyListener(keyboardListener)
    editor.selectionModel.addSelectionListener(selectionListener)
    editor.document.addDocumentListener(documentListener)
  }

  private fun unregisterListeners() {
    editor.removeEditorMouseListener(mouseListener)
    editor.removeEditorMouseMotionListener(mouseMotionListener)
    editor.contentComponent.removeKeyListener(keyboardListener)
    editor.selectionModel.removeSelectionListener(selectionListener)
    editor.document.removeDocumentListener(documentListener)
  }

  private fun canBeShownAtCurrentSelection(): Boolean {
    val file = PsiEditorUtil.getPsiFile(editor)
    PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)
    val selectionModel = editor.selectionModel
    val elementAtStart = PsiUtilCore.getElementAtOffset(file, selectionModel.selectionStart)
    val elementAtEnd = PsiUtilCore.getElementAtOffset(file, selectionModel.selectionEnd)
    return !(hasIgnoredParent(elementAtStart) || hasIgnoredParent(elementAtEnd))
  }

  protected open fun hasIgnoredParent(element: PsiElement): Boolean {
    return false
  }

  protected open fun shouldReviveAfterClose(): Boolean = true

  protected open fun shouldSurviveDocumentChange(): Boolean = true

  protected open fun getHintPosition(hint: LightweightHint): Point {
    val hintPos = HintManagerImpl.getInstanceImpl().getHintPosition(hint, editor, HintManager.DEFAULT)
    // because of `hint.setForceShowAsPopup(true)`, HintManager.ABOVE does not place the hint above
    // the hint remains on the line, so we need to move it up ourselves
    val verticalGap = 2
    val dy = -(hint.component.preferredSize.height + verticalGap)
    val dx = buttonSize * -2
    hintPos.translate(dx, dy)
    return hintPos
  }

  private fun updateOnProbablyChangedSelection(onSelectionChanged: (String) -> Unit) {
    val newSelection = editor.selectionModel.selectedText

    when (newSelection) {
      null -> hideIfShown()
      lastSelection -> Unit
      else -> onSelectionChanged(newSelection)
    }

    lastSelection = newSelection
  }

  private inner class MouseListener : EditorMouseListener {
    override fun mouseReleased(e: EditorMouseEvent) {
      updateOnProbablyChangedSelection {
        if (isShown()) {
          updateLocationIfShown()
        } else {
          showIfHidden()
        }
      }
    }
  }

  private inner class KeyboardListener : KeyAdapter() {
    override fun keyReleased(e: KeyEvent) {
      super.keyReleased(e)
      if (e.source != editor.contentComponent) {
        return
      }
      updateOnProbablyChangedSelection {
        hideIfShown()
      }
    }
  }

  private inner class MouseMotionListener : EditorMouseMotionListener {
    override fun mouseMoved(e: EditorMouseEvent) {
      val visualPosition = e.visualPosition
      val hoverSelected = editor.caretModel.allCarets.any {
        val beforeSelectionEnd = it.selectionEndPosition.after(visualPosition)
        val afterSelectionStart = visualPosition.after(it.selectionStartPosition)
        beforeSelectionEnd && afterSelectionStart
      }
      if (hoverSelected) {
        showIfHidden()
      }
    }
  }

  private inner class EditorSelectionListener : SelectionListener {
    override fun selectionChanged(e: SelectionEvent) {
      isJustClosed = false
    }
  }

  private inner class DocumentChangeListener : BulkAwareDocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      if (!shouldSurviveDocumentChange()) {
        hideIfShown()
      }
    }
  }
}
