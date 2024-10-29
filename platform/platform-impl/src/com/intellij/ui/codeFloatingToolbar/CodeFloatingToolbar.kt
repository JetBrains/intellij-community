// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.ide.HelpTooltip
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.FloatingToolbar
import com.intellij.openapi.actionSystem.impl.MoreActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.ui.LightweightHint
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.AnchoredPoint
import com.intellij.ui.popup.util.PopupImplUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import java.awt.Dimension
import java.awt.IllegalComponentStateException
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JComponent

/**
 * Represents floating toolbar which is shown for selected text inside editor.
 * Toolbar is visible only if mouse was used for the selection.
 */
class CodeFloatingToolbar(
  editor: Editor,
  coroutineScope: CoroutineScope
): FloatingToolbar(editor, coroutineScope) {

  companion object {
    private val FLOATING_TOOLBAR = Key<CodeFloatingToolbar>("floating.codeToolbar")

    private var TEMPORARILY_DISABLED = false

    @JvmStatic
    fun getToolbar(editor: Editor?): CodeFloatingToolbar? {
      return editor?.getUserData(FLOATING_TOOLBAR)
    }

    /**
     * Temporarily enables or disables a specific floating toolbar. Can be used to hide the toolbar when it conflicts with another popup.
     * @see hideOnPopupConflict
     */
    @JvmStatic
    fun temporarilyDisable(disable: Boolean = true) {
      TEMPORARILY_DISABLED = disable
    }

    @JvmStatic
    fun isTemporarilyDisabled(): Boolean {
      return TEMPORARILY_DISABLED
    }
  }

  init {
    editor.putUserData(FLOATING_TOOLBAR, this)
    Disposer.register(this) { editor.putUserData(FLOATING_TOOLBAR, null) }
  }

  private var activeMenuPopup: JBPopup? = null

  override fun hasIgnoredParent(element: PsiElement): Boolean {
    return !element.isWritable || TemplateManager.getInstance(element.project).getActiveTemplate(editor) != null
  }

  override fun isEnabled(): Boolean {
    return editor.selectionModel.hasSelection() && !AdvancedSettings.getBoolean("floating.codeToolbar.hide")
           && editor.document.isWritable && !TEMPORARILY_DISABLED
  }

  override fun disableForDoubleClickSelection(): Boolean = true

  override fun shouldSurviveDocumentChange(): Boolean = false

  override fun hideByOtherHints(): Boolean = false

  val hintComponent: JComponent?
    get() = hint?.component

  override fun getHintPosition(hint: LightweightHint): Point {
    val range = editor.calculateVisibleRange()
    val selectionEnd = editor.selectionModel.selectionEnd
    val selectionStart = editor.selectionModel.selectionStart
    val isOneLineSelection = isOneLineSelection(editor)
    val isBelow = shouldBeUnderSelection(selectionEnd)
    val areEdgesOutsideOfVisibleArea = editor.selectionModel.selectionStart !in range && editor.selectionModel.selectionEnd !in range
    val offsetForHint = when {
      isOneLineSelection -> selectionStart
      areEdgesOutsideOfVisibleArea -> getOffsetForLine(editor, getLineByVisualStart(editor, editor.caretModel.offset, true))
      isBelow -> getOffsetForLine(editor, getLineByVisualStart(editor, selectionEnd, true))
      else -> getOffsetForLine(editor, getLineByVisualStart(editor, selectionStart, false))
    }
    val visualPosition = editor.offsetToVisualPosition(offsetForHint)
    val hintPoint = HintManagerImpl.getHintPosition(hint, editor, visualPosition, HintManager.DEFAULT)
    val verticalGap = Registry.get("floating.codeToolbar.verticalOffset").asInteger()
    val dy = if (isBelow) {
      editor.lineHeight + verticalGap
    } else {
      -(hint.component.preferredSize.height + verticalGap)
    }
    hintPoint.translate(0, dy)
    return hintPoint
  }

  private fun shouldBeUnderSelection(selectionEnd: Int): Boolean {
    val showUnderSelection = selectionEnd == editor.caretModel.offset || Registry.get("floating.codeToolbar.showBelow").asBoolean()
    val preferredOffset = if (!showUnderSelection) editor.selectionModel.selectionStart else editor.selectionModel.selectionEnd
    if (editor.offsetToXY(preferredOffset) in editor.scrollingModel.visibleArea) {
      return showUnderSelection
    } else {
      return !showUnderSelection
    }
  }

  private fun isOneLineSelection(editor: Editor): Boolean {
    val document = editor.document
    val selectionModel = editor.selectionModel
    val startLine = document.getLineNumber(selectionModel.selectionStart)
    val endLine = document.getLineNumber(selectionModel.selectionEnd)
    return startLine == endLine
  }

  private fun getOffsetForLine(editor: Editor, line: Int): Int {
    val document = editor.document
    val lineStart = document.getLineStartOffset(line)
    val lineEnd = document.getLineEndOffset(line)
    val lineText = document.getText(TextRange.create(lineStart, lineEnd))
    val textIndex = lineText.indexOfFirst { char -> !char.isWhitespace() }
    if (textIndex < 0) return lineStart
    return lineStart + textIndex
  }

  private fun getLineByVisualStart(editor: Editor, offset: Int, skipLineStartOffset: Boolean): Int {
    val visualPosition = editor.offsetToVisualPosition(offset)
    val skipCurrentLine = skipLineStartOffset && visualPosition.column == 0
    val line = if (skipCurrentLine) maxOf(visualPosition.line - 1, 0) else visualPosition.line
    val lineStartPosition = VisualPosition(line, 0)
    return editor.visualToLogicalPosition(lineStartPosition).line
  }

  override fun createActionGroup(): ActionGroup? {
    val contextAwareActionGroupId = getContextAwareGroupId(editor) ?: return null
    val mainActionGroup = CustomActionsSchema.getInstance().getCorrectedAction(contextAwareActionGroupId) ?: error("Can't find groupId action")
    val configurationGroup = createConfigureGroup(contextAwareActionGroupId)
    val showIntentionAction = CustomActionsSchema.getInstance().getCorrectedAction("ShowIntentionActions")
                              ?: error("Can't find ShowIntentionActions action")
    return DefaultActionGroup(showIntentionAction, mainActionGroup, configurationGroup)
  }

  override suspend fun createHint(): LightweightHint {
    val hint = super.createHint()
    val buttons = UIUtil.findComponentsOfType(hint.component, ActionButton::class.java)
    buttons.forEach { button ->
      button.presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)
      showMenuPopupOnMouseHover(button)
    }
    return hint
  }

  private fun getContextAwareGroupId(editor: Editor): String? {
    val project = editor.project ?: return null
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    val elementAtOffset = psiFile?.findElementAt(editor.caretModel.primaryCaret.offset)
    val targetLanguage = elementAtOffset?.language ?: return null
    return findActionGroupFor(targetLanguage)
  }

  private fun createConfigureGroup(customizableGroupId: String): ActionGroup {
    val customizeAction = CustomizeCodeFloatingToolbarAction(customizableGroupId)
    val disableAction = CustomActionsSchema.getInstance().getCorrectedAction("Floating.CodeToolbar.Disable") ?: error("Can't find Floating.CodeToolbar.Disable action")
    return MoreActionGroup().apply {
      addAction(customizeAction)
      addAction(disableAction)
    }
  }

  fun attachPopupToButton(button: ActionButton, popup: JBPopup) {
    editor.scrollingModel.addVisibleAreaListener( {
      alignButtonPopup(popup)
    }, popup)
    popup.addListener(object : JBPopupListener {

      override fun beforeShown(event: LightweightWindowEvent) {
        activeMenuPopup = popup
        Toggleable.setSelected(button, true)
        alignButtonPopup(popup)
        HelpTooltip.setMasterPopupOpenCondition(button) { true }
      }

      override fun onClosed(event: LightweightWindowEvent) {
        activeMenuPopup = null
        Toggleable.setSelected(button, null)
      }
    })
  }

  override fun onHintShown() {
    val model = editor.selectionModel
    val start = model.leadSelectionOffset
    val end = if (model.selectionStart == start) model.selectionEnd else model.selectionStart
    val linesCount = model.editor.document.run { getLineNumber(model.selectionEnd) - getLineNumber(model.selectionStart) + 1 }
    CodeFloatingToolbarCollector.toolbarShown(start, end, linesCount)
  }

  private fun alignButtonPopup(popup: JBPopup) {
    val component = hintComponent ?: return
    val rootPane = UIUtil.getRootPane(component) ?: return
    popup.setMinimumSize(Dimension(rootPane.width, 0))
    val verticalGap = 1
    val point = AnchoredPoint(AnchoredPoint.Anchor.BOTTOM_LEFT, rootPane, Point(0, verticalGap)).screenPoint
    val screenRectangle = ScreenUtil.getScreenRectangle(point)
    val popupSize = PopupImplUtil.getPopupSize(popup)
    if (point.x + popupSize.width > screenRectangle.x + screenRectangle.width) { //horizontal overflow
      point.x += rootPane.size.width - popupSize.width
    }
    if (point.y + popupSize.height > screenRectangle.y + screenRectangle.height) { //vertical overflow
      val pointTopLeft = AnchoredPoint(AnchoredPoint.Anchor.TOP_LEFT, rootPane, Point(0, -verticalGap)).screenPoint
      point.y = pointTopLeft.y - popupSize.height
    }
    point.translate(1, 1)
    popup.setLocation(point)
  }

  /**
   * Hides the toolbar if it intersects another popup. Should be called when popup is visible already.
   */
  fun hideOnPopupConflict(popup: JBPopup){
    val toolbarBounds = getScreenBounds(hintComponent) ?: return
    val popupBounds = getScreenBounds(popup.content) ?: return
    if (!toolbarBounds.intersects(popupBounds)) return
    val wasVisible = isShown()
    val wasDisabled = TEMPORARILY_DISABLED
    val disposable = getPopupDisposable(popup)
    temporarilyDisable(true)
    scheduleHide()
    Disposer.register(disposable) {
      temporarilyDisable(wasDisabled)
      if (wasVisible) {
        allowInstantShowing()
      }
    }
  }

  private fun getScreenBounds(component: JComponent?): Rectangle? {
    try {
      val location = component?.locationOnScreen ?: return null
      val size = component.size ?: return null
      return Rectangle(location.x, location.y, size.width, size.height)
    } catch (e: IllegalComponentStateException) { //thrown if component is not visible
      return null
    }
  }

  private fun getPopupDisposable(popup: JBPopup): Disposable {
    val disposable = Disposer.newDisposable()
    Disposer.register(popup, disposable)
    popup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        Disposer.dispose(disposable)
      }
    })
    return disposable
  }

  private fun showMenuPopupOnMouseHover(button: ActionButton) {
    var mouseWasOutsideOfComponent: Boolean
    val isPopupButton = button.presentation.isPopupGroup
    button.addMouseListener(object : java.awt.event.MouseListener {
      override fun mouseEntered(e: MouseEvent?) {
        val component = hintComponent ?: return
        val modality = ModalityState.stateForComponent(component)
        coroutineScope.launch {
          mouseWasOutsideOfComponent = false
          val delayMs = if (isPopupButton && activeMenuPopup != null) 40L else 300L
          delay(delayMs)
          if (mouseWasOutsideOfComponent) {
            cancel()
          }
          withContext(Dispatchers.EDT + modality.asContextElement()) {
            if (isPopupButton) button.click() else activeMenuPopup?.cancel()
          }
        }
      }
      override fun mouseClicked(e: MouseEvent?) { }
      override fun mousePressed(e: MouseEvent?) { }
      override fun mouseReleased(e: MouseEvent?) { }
      override fun mouseExited(e: MouseEvent?) {
        mouseWasOutsideOfComponent = true
      }
    })
  }
}