// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButtonUtil
import com.intellij.openapi.application.ex.ApplicationManagerEx
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
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders.emptyLeft
import com.intellij.util.ui.JBUI.scale
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcsUtil.VcsUIUtil
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.border.Border

abstract class NonModalCommitPanel(
  val project: Project,
  val commitActionsPanel: CommitActionsPanel = CommitActionsPanel(),
  private val commitAuthorComponent: CommitAuthorComponent = CommitAuthorComponent(project),
) : NonModalCommitWorkflowUi,
    CommitActionsUi by commitActionsPanel,
    CommitAuthorTracker by commitAuthorComponent,
    ComponentContainer,
    EditorColorsListener {

  private val inclusionEventDispatcher = EventDispatcher.create(InclusionListener::class.java)
  private val dataProviders = mutableListOf<DataProvider>()
  private var needUpdateCommitOptionsUi = false

  private var commitInputBorder: CommitInputBorder? = null

  private val mainPanel: BorderLayoutPanel = object : BorderLayoutPanel(), UiDataProvider {
    override fun uiDataSnapshot(sink: DataSink) {
      DataSink.uiDataSnapshot(sink, commitMessage)
      uiDataSnapshotFromProviders(sink)
    }
  }

  protected val centerPanel: BorderLayoutPanel = JBUI.Panels.simplePanel()

  private val bottomPanel: JBPanel<JBPanel<*>> = JBPanel<JBPanel<*>>(VerticalLayout(0))

  private val actions = ActionManager.getInstance().getAction("ChangesView.CommitToolbar") as ActionGroup
  val toolbar: ActionToolbar = ActionManager.getInstance().createActionToolbar(COMMIT_TOOLBAR_PLACE, actions, true).apply {
    targetComponent = mainPanel
    component.isOpaque = false
    setOrientation(SwingConstants.HORIZONTAL)
    setReservePlaceAutoPopupIcon(false)
  }

  private val commitMessagePanel: BorderLayoutPanel = JBUI.Panels.simplePanel()
    .andTransparent()
    .withBackground(UISpec.COMMIT_EDITOR_COLOR)

  val commitMessage: CommitMessage = CommitMessage(project, false, false, true, message("commit.message.placeholder")).apply {
    editorField.addSettingsProvider { it.setBorder(emptyLeft(6)) }
  }

  protected val statusComponent: CommitStatusPanel

  init {
    commitActionsPanel.apply {
      setTargetComponent(mainPanel)
    }

    statusComponent = CommitStatusPanel(this).apply {
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

    subscribeOnLafChange()
    updateBorders()

    InternalDecoratorImpl.preventRecursiveBackgroundUpdateOnToolwindow(mainPanel)
  }

  protected fun setProgressComponent(progressPanel: CommitProgressPanel) {
    bottomPanel.removeAll()

    bottomPanel.add(progressPanel.component)
    bottomPanel.add(commitAuthorComponent)
    bottomPanel.add(commitActionsPanel)
    updateBorders()
  }

  private fun subscribeOnLafChange() {
    ApplicationManagerEx.getApplicationEx().messageBus.connect(this)
      .subscribe(LafManagerListener.TOPIC, LafManagerListener {
        updateBorders()
      })
  }

  private fun updateBorders() {
    val editor = commitMessage.editorField.getEditor(true)

    if (editor != null) {
      commitInputBorder = commitInputBorder ?: CommitInputBorder(editor, mainPanel)
      commitMessagePanel.border = JBUI.Borders.compound(
        UISpec.commitFieldBorder(),
        commitInputBorder!!
      )
    }
    statusComponent.border = UISpec.statusPanelBorder()
    bottomPanel.components.forEach {
      (it as JComponent).border = UISpec.bottomComponentBorder()
    }
    commitActionsPanel.border = UISpec.actionPanelBorder()
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
  private val BASE_LEFT_RIGHT_INSET: Int
    get() = if (isCompact) 6 else 12

  private val COMMIT_MESSAGE_LEFT_RIGHT_GAP: Int
    get() = BASE_LEFT_RIGHT_INSET + 2 - CommitInputBorder.COMMIT_BORDER_INSET

  private val COMMIT_MESSAGE_TOP_BOTTOM_GAP: Int
    get() = (if (isCompact) 4 else 6) - CommitInputBorder.COMMIT_BORDER_INSET

  private val isCompact: Boolean
    get() = UISettings.getInstance().compactMode

  val COMMIT_EDITOR_COLOR: JBColor
    get() = JBColor.lazy { EditorColorsManager.getInstance().globalScheme.defaultBackground }

  fun commitFieldBorder(): Border {
    return JBUI.Borders.empty(COMMIT_MESSAGE_TOP_BOTTOM_GAP, COMMIT_MESSAGE_LEFT_RIGHT_GAP)
  }

  fun statusPanelBorder(): Border {
    val toolbarInset = JBUI.CurrentTheme.Toolbar.horizontalToolbarInsets()?.left ?: 0
    val checkBoxInsets = UIManager.getInsets("CheckBox.borderInsets")?.left ?: 0 // not the proper constant
    val left = (BASE_LEFT_RIGHT_INSET - toolbarInset - checkBoxInsets).coerceAtLeast(0)

    return JBUI.Borders.empty(0, left, 0, BASE_LEFT_RIGHT_INSET)
  }

  fun bottomComponentBorder(): Border {
    return JBUI.Borders.empty(4, BASE_LEFT_RIGHT_INSET)
  }

  fun actionPanelBorder(): Border {
    val buttonInset = 3 // darcula, newUI
    val left = BASE_LEFT_RIGHT_INSET - buttonInset

    return JBUI.Borders.empty(3, left, 3, BASE_LEFT_RIGHT_INSET)
  }
}