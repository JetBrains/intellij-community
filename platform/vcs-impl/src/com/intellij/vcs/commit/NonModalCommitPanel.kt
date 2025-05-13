// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButtonUtil
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.actions.ShowCommitOptionsAction
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.EventDispatcher
import com.intellij.util.IJSwingUtilities.updateComponentTreeUI
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders.emptyLeft
import com.intellij.util.ui.JBUI.Borders.emptyRight
import com.intellij.util.ui.JBUI.scale
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcsUtil.VcsUIUtil
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.SwingConstants
import javax.swing.border.Border
import javax.swing.border.EmptyBorder

abstract class NonModalCommitPanel(
  val project: Project,
  val commitActionsPanel: CommitActionsPanel = CommitActionsPanel(),
  val commitAuthorComponent: CommitAuthorComponent = CommitAuthorComponent(project),
) : NonModalCommitWorkflowUi,
    CommitActionsUi by commitActionsPanel,
    CommitAuthorTracker by commitAuthorComponent,
    ComponentContainer,
    EditorColorsListener {

  private val inclusionEventDispatcher = EventDispatcher.create(InclusionListener::class.java)
  private val dataProviders = mutableListOf<DataProvider>()
  private var needUpdateCommitOptionsUi = false

  private val mainPanel: BorderLayoutPanel = object : BorderLayoutPanel(), UiDataProvider {
    override fun uiDataSnapshot(sink: DataSink) {
      DataSink.uiDataSnapshot(sink, commitMessage)
      uiDataSnapshotFromProviders(sink)
    }
  }

  protected val centerPanel = JBUI.Panels.simplePanel()

  private val bottomPanel: JBPanel<JBPanel<*>> = JBPanel<JBPanel<*>>(VerticalLayout(0))

  private val actions = ActionManager.getInstance().getAction("ChangesView.CommitToolbar") as ActionGroup
  val toolbar = ActionManager.getInstance().createActionToolbar(COMMIT_TOOLBAR_PLACE, actions, true).apply {
    targetComponent = mainPanel
    component.isOpaque = false
    setOrientation(SwingConstants.HORIZONTAL)
    setReservePlaceAutoPopupIcon(false)
  }

  private val commitMessagePanel: BorderLayoutPanel = JBUI.Panels.simplePanel()
    .andTransparent()
    .withBackground(UISpec.COMMIT_EDITOR_COLOR)

  val commitMessage = CommitMessage(project, false, false, true, message("commit.message.placeholder")).apply {
    editorField.addSettingsProvider { it.setBorder(emptyLeft(6)) }
  }

  protected val statusComponent: CommitStatusPanel

  init {
    commitActionsPanel.apply {
      border = getButtonPanelBorder()

      setTargetComponent(mainPanel)
    }

    statusComponent = CommitStatusPanel(this).apply {
      border = emptyRight(6)

      addToLeft(toolbar.component)
    }


    commitMessagePanel.addToCenter(commitMessage)

    bottomPanel.add(commitAuthorComponent)
    bottomPanel.add(commitActionsPanel)

    centerPanel
      .addToTop(statusComponent)
      .addToCenter(commitMessagePanel)
      .addToBottom(bottomPanel)

    mainPanel.addToCenter(centerPanel)
    mainPanel.withPreferredHeight(85)
    commitMessage.editorField.setDisposedWith(this)
    installBorders()

    InternalDecoratorImpl.preventRecursiveBackgroundUpdateOnToolwindow(mainPanel)
  }

  protected fun setProgressComponent(progressPanel: CommitProgressPanel) {
    bottomPanel.removeAll()

    bottomPanel.add(progressPanel.component)
    bottomPanel.add(commitAuthorComponent)
    bottomPanel.add(commitActionsPanel)
  }

  private fun installBorders() {
    val editor = commitMessage.editorField.getEditor(true)

    if (editor != null) {
      commitMessagePanel.border = JBUI.Borders.compound(
        commitFieldEmptyBorder(),
        CommitInputBorder(editor, mainPanel)
      )
    }
  }

  override val commitMessageUi: CommitMessageUi get() = commitMessage

  override fun getComponent(): JComponent = mainPanel
  override fun getPreferredFocusableComponent(): JComponent = commitMessage.editorField

  override fun uiDataSnapshot(sink: DataSink) {
    DataSink.uiDataSnapshot(sink, commitMessage)
    uiDataSnapshotFromProviders(sink)
  }

  @Deprecated("Use UiDataRule instead")
  fun uiDataSnapshotFromProviders(sink: DataSink) {
    dataProviders.forEach {
      DataSink.uiDataSnapshot(sink, it)
    }
  }

  override fun addDataProvider(provider: DataProvider) {
    dataProviders += provider
  }

  override fun addInclusionListener(listener: InclusionListener, parent: Disposable) =
    inclusionEventDispatcher.addListener(listener, parent)

  protected fun fireInclusionChanged() = inclusionEventDispatcher.multicaster.inclusionChanged()

  override fun startBeforeCommitChecks() = Unit
  override fun endBeforeCommitChecks(result: CommitChecksResult) = Unit

  override fun dispose() = Unit

  override fun globalSchemeChange(scheme: EditorColorsScheme?) {
    needUpdateCommitOptionsUi = true
    commitActionsPanel.border = getButtonPanelBorder()
  }

  private fun getButtonPanelBorder(): Border {
    @Suppress("UseDPIAwareBorders")
    return EmptyBorder(0, scale(3), (scale(6) - commitActionsPanel.getBottomInset()).coerceAtLeast(0), 0)
  }

  override fun showCommitOptions(options: CommitOptions, actionName: @Nls String, isFromToolbar: Boolean, dataContext: DataContext) {
    val commitOptionsPanel = CommitOptionsPanel(project, actionNameSupplier = { actionName }, nonFocusable = false, nonModalCommit = true,
                                                contentBorder = JBUI.Borders.empty(10, 14))
    commitOptionsPanel.setOptions(options)

    val commitOptionsComponent = commitOptionsPanel.component.apply {
      focusTraversalPolicy = LayoutFocusTraversalPolicy()
      isFocusCycleRoot = true
    }

    // to reflect LaF changes as commit options components are created once per commit
    if (needUpdateCommitOptionsUi) {
      needUpdateCommitOptionsUi = false
      updateComponentTreeUI(commitOptionsComponent)
    }

    val focusComponent = IdeFocusManager.getInstance(project).getFocusTargetFor(commitOptionsComponent)
    val commitOptionsPopup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(commitOptionsComponent, focusComponent)
      .setRequestFocus(true)
      .addListener(object : JBPopupListener {
        override fun beforeShown(event: LightweightWindowEvent) {
          options.restoreState()
        }

        override fun onClosed(event: LightweightWindowEvent) {
          options.saveState()
        }
      })
      .createPopup()

    showCommitOptions(commitOptionsPopup, isFromToolbar, dataContext)
  }

  protected open fun showCommitOptions(popup: JBPopup, isFromToolbar: Boolean, dataContext: DataContext) {
    if (isFromToolbar) {
      VcsUIUtil.showPopupAbove(popup, commitActionsPanel.getShowCommitOptionsButton() ?: commitActionsPanel,
                               scale(COMMIT_OPTIONS_POPUP_MINIMUM_SIZE))
    }
    else {
      popup.showInBestPositionFor(dataContext)
    }
  }

  companion object {
    internal const val COMMIT_TOOLBAR_PLACE: String = "ChangesView.CommitToolbar"
    internal const val COMMIT_EDITOR_PLACE: String = "ChangesView.Editor"
    internal val COMMIT_OPTIONS_POPUP_MINIMUM_SIZE = 300

    @Deprecated("Extracted to a separate file",
                replaceWith = ReplaceWith("showAbove(component)", "com.intellij.vcsUtil.showAbove"))
    fun JBPopup.showAbove(component: JComponent) = VcsUIUtil.showPopupAbove(this, component)
  }
}

private fun CommitActionsPanel.getShowCommitOptionsButton(): JComponent? = ActionButtonUtil.findActionButton(this) {
  it.action is ShowCommitOptionsAction
}

private object UISpec {
  const val COMMIT_LEFT_RIGHT_GAP = 12 - CommitInputBorder.COMMIT_BORDER_INSET
  const val COMMIT_BUTTON_TOP_BOTTOM_GAP = 6 - CommitInputBorder.COMMIT_BORDER_INSET

  val COMMIT_EDITOR_COLOR: JBColor
    get() = JBColor.lazy { EditorColorsManager.getInstance().globalScheme.defaultBackground }
}

private fun commitFieldEmptyBorder(): JBEmptyBorder = JBUI.Borders.empty(UISpec.COMMIT_BUTTON_TOP_BOTTOM_GAP, UISpec.COMMIT_LEFT_RIGHT_GAP)

