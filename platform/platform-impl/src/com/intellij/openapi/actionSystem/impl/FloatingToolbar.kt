// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.customization.CustomActionsSchema
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
import com.intellij.openapi.util.registry.Registry
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
open class FloatingToolbar(
  val editor: Editor,
  /**
   * This scope will be canceled on dispose.
   */
  private val coroutineScope: CoroutineScope,
  protected val defaultActionGroupId: String
): Disposable {
  private var hint: LightweightHint? = null
  private var buttonSize: Int by Delegates.notNull()
  private var lastSelection: String? = null
  private var showToolbar = true
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

  open fun hideByOtherHints(): Boolean = false

  @RequiresEdt
  fun isShown(): Boolean {
    return hint != null
  }

  @RequiresEdt
  private fun hide() {
    hint?.hide()
  }

  @RequiresEdt
  private suspend fun showIfHidden() {
    if (hint != null || !shouldReviveAfterClose() && !showToolbar) {
      return
    }
    val canBeShownAtCurrentSelection = readAction { canBeShownAtCurrentSelection() }
    if (!canBeShownAtCurrentSelection) {
      return
    }
    val hint = createHint()
    hint.addHintListener {
      this.hint = null
      showToolbar = false
    }
    this.hint = hint
    showHint(hint)
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
    if (isEnabled()) {
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
    hint = null
  }

  protected open fun createActionGroup(): ActionGroup? {
    return CustomActionsSchema.getInstance().getCorrectedAction(defaultActionGroupId) as? ActionGroup
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
  private fun canBeShownAtCurrentSelection(): Boolean {
    val file = PsiEditorUtil.getPsiFile(editor)
    val document = editor.document
    if (!PsiDocumentManager.getInstance(file.project).isCommitted(document)) {
      return false
    }
    val selectionModel = editor.selectionModel
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


  protected open fun shouldReviveAfterClose(): Boolean = true

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
      hintWasShownForSelection = false
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
      hintWasShownForSelection = false
      updateOnProbablyChangedSelection {
        scheduleHide()
      }
    }
  }

  private inner class MouseMotionListener : EditorMouseMotionListener {
    override fun mouseMoved(event: EditorMouseEvent) {
      val visualPosition = event.visualPosition
      val hoverSelected = editor.caretModel.allCarets.any { visualPosition.isInsideSelection(it) }
      if (hoverSelected && (hintWasShownForSelection || hint?.isVisible == true)) {
        hintWasShownForSelection = true
        scheduleShow()
      } else {
        if (!Registry.get("floating.codeToolbar.revive.selectionChangeOnly").asBoolean()) {
          showToolbar = true
        }
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
      val event = IdeEventQueue.getInstance().trueCurrentEvent
      val isDoubleClick = (event as? MouseEvent)?.clickCount == 2
      showToolbar = !(disableForDoubleClickSelection() && isDoubleClick)
      hintWasShownForSelection = false
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
