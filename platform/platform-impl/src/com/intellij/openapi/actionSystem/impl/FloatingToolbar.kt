// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.LightweightHint
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.MouseEvent
import javax.swing.JComponent
import kotlin.coroutines.resume
import kotlin.properties.Delegates
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
abstract class FloatingToolbar(
  val editor: Editor,
  /**
   * This scope will be canceled on dispose.
   */
  private val coroutineScope: CoroutineScope
): Disposable {
  protected var hint: LightweightHint? = null
  private var buttonSize: Int by Delegates.notNull()
  private var lastSelection: String? = null
  private var hintWasShownForSelection = false

  private enum class HintRequest {
    Show,
    Hide
  }

  private val hintRequests = MutableSharedFlow<HintRequest>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch {
      hintRequests.debounce(50.milliseconds).collectLatest { request ->
        withContext(Dispatchers.EDT) {
          when (request) {
            HintRequest.Show -> showIfHidden()
            HintRequest.Hide -> hide()
          }
        }
      }
    }
    //@Suppress("LeakingThis")
    editor.apply {
      addEditorMouseListener(MouseListener(), this@FloatingToolbar)
      addEditorMouseMotionListener(MouseMotionListener(), this@FloatingToolbar)
      contentComponent.addKeyListener(KeyboardListener(), this@FloatingToolbar)
      selectionModel.addSelectionListener(EditorSelectionListener(), this@FloatingToolbar)
      document.addDocumentListener(DocumentChangeListener(), this@FloatingToolbar)
    }
  }

  protected abstract fun createActionGroup(): ActionGroup?

  open fun hideByOtherHints(): Boolean = false

  @RequiresEdt
  fun isShown(): Boolean {
    return hint?.isVisible == true
  }

  @RequiresEdt
  private fun hide() {
    hint?.hide()
    hint = null
  }

  @RequiresEdt
  private suspend fun showIfHidden() {
    hintWasShownForSelection = true
    if (isShown() || !isEnabled()) {
      return
    }
    val canBeShownAtCurrentSelection = readAction { canBeShownAtCurrentSelection() }
    if (!canBeShownAtCurrentSelection) {
      return
    }
    val hint = createHint()
    showHint(hint)
    hint.addHintListener {
      this.hint = null
    }
    this.hint = hint
  }

  fun show(callback: Runnable){
    coroutineScope.launch {
      withContext(Dispatchers.EDT) {
        showIfHidden()
        callback.run()
      }
    }
  }

  private suspend fun createHint(): LightweightHint {
    val toolbar = createUpdatedActionToolbar(editor.contentComponent)
    val component = BorderLayoutPanel().apply {
      addToCenter(toolbar.component)
    }
    val hint = LightweightHint(component).apply {
      setForceShowAsPopup(true)
    }
    return hint
  }

  fun scheduleShow() {
    if (isEnabled() && !hintWasShownForSelection) {
      check(hintRequests.tryEmit(HintRequest.Show))
    }
  }

  fun scheduleHide() {
    check(hintRequests.tryEmit(HintRequest.Hide))
  }

  @RequiresEdt
  private fun updateLocationIfShown() {
    hint?.let(::showHint)
  }

  override fun dispose() {
    coroutineScope.cancel()
    hide()
  }

  private suspend fun createUpdatedActionToolbar(targetComponent: JComponent): ActionToolbar {
    return suspendCancellableCoroutine { continuation ->
      createActionToolbar(targetComponent) {
        if (!continuation.isCompleted) {
          continuation.resume(it)
        }
      }
    }
  }

  private fun createActionToolbar(targetComponent: JComponent, onUpdated: (ActionToolbar) -> Unit) {
    val group = createActionGroup() ?: return
    val place = ActionPlaces.EDITOR_FLOATING_TOOLBAR
    val toolbar = ToolbarUtils.createImmediatelyUpdatedToolbar(
      group,
      place,
      targetComponent,
      horizontal = true,
      onUpdated
    )
    buttonSize = toolbar.maxButtonHeight
  }

  private fun showHint(hint: LightweightHint) {
    val hideByOtherHintsMask = when {
      hideByOtherHints() -> HintManager.HIDE_BY_OTHER_HINT
      else -> 0
    }
    HintManagerImpl.getInstanceImpl().showEditorHint(
      hint,
      editor,
      getHintPosition(hint),
      HintManager.HIDE_BY_ESCAPE or HintManager.UPDATE_BY_SCROLLING or hideByOtherHintsMask,
      0,
      true
    )
  }

  @RequiresReadLock
  fun canBeShownAtCurrentSelection(): Boolean {
    val selectionModel = editor.selectionModel
    if (!selectionModel.hasSelection()) return false
    val file = PsiEditorUtil.getPsiFile(editor)
    val document = editor.document
    if (!PsiDocumentManager.getInstance(file.project).isCommitted(document)) {
      return false
    }
    val elementAtStart = PsiUtilCore.getElementAtOffset(file, selectionModel.selectionStart)
    val elementAtEnd = PsiUtilCore.getElementAtOffset(file, selectionModel.selectionEnd)
    return !(hasIgnoredParent(elementAtStart) || hasIgnoredParent(elementAtEnd))
  }

  @RequiresReadLock
  protected open fun hasIgnoredParent(element: PsiElement): Boolean {
    return false
  }

  protected open fun disableForDoubleClickSelection(): Boolean {
    return false
  }

  protected open fun shouldSurviveDocumentChange(): Boolean = true

  protected open fun isEnabled(): Boolean {
    return true
  }

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
      null -> scheduleHide()
      lastSelection -> Unit
      else -> onSelectionChanged(newSelection)
    }
    lastSelection = newSelection
  }

  private inner class MouseListener : EditorMouseListener {
    override fun mouseReleased(event: EditorMouseEvent) {
      updateOnProbablyChangedSelection {
        if (isShown()) {
          updateLocationIfShown()
        } else {
          scheduleShow()
        }
      }
    }
  }

  private inner class KeyboardListener : KeyAdapter() {
    override fun keyReleased(event: KeyEvent) {
      super.keyReleased(event)
      if (event.source != editor.contentComponent) {
        return
      }
      updateOnProbablyChangedSelection {
        scheduleHide()
      }
    }
  }

  private inner class MouseMotionListener : EditorMouseMotionListener {
    override fun mouseMoved(event: EditorMouseEvent) {
      val visualPosition = event.visualPosition
      val hoverSelected = editor.caretModel.allCarets.any { visualPosition.isInsideSelection(it) }
      if (hoverSelected) {
        scheduleShow()
      } else if (!isShown()){
        hintWasShownForSelection = false
      }
    }

    private fun VisualPosition.isInsideSelection(caret: Caret): Boolean {
      val beforeSelectionEnd = caret.selectionEndPosition.after(this)
      val afterSelectionStart = this.after(caret.selectionStartPosition)
      return beforeSelectionEnd && afterSelectionStart
    }
  }

  private inner class EditorSelectionListener : SelectionListener {
    override fun selectionChanged(event: SelectionEvent) {
      hintWasShownForSelection = false
      if (isIgnoredEvent(IdeEventQueue.getInstance().trueCurrentEvent)) {
        hintWasShownForSelection = true
      }
    }

    private fun isIgnoredEvent(event: AWTEvent): Boolean {
      return disableForDoubleClickSelection() && (event as? MouseEvent)?.clickCount == 2
    }
  }

  private inner class DocumentChangeListener : BulkAwareDocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      hintWasShownForSelection = false
      if (!shouldSurviveDocumentChange()) {
        scheduleHide()
      }
    }
  }

  companion object {
    private fun JComponent.addKeyListener(listener: KeyListener, parentDisposable: Disposable) {
      addKeyListener(listener)
      Disposer.register(parentDisposable) {
        removeKeyListener(listener)
      }
    }
  }
}
