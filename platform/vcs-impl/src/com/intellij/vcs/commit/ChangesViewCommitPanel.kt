// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.MnemonicHelper
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.CommonShortcuts.CTRL_ENTER
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesViewManager
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.UNVERSIONED_FILES_TAG
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.*
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.IdeBorderFactory.createBorder
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.JBUI.Borders.emptyLeft
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.JBUI.scale
import com.intellij.util.ui.UIUtil.addBorder
import com.intellij.util.ui.UIUtil.getTreeBackground
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Point
import javax.swing.JButton
import javax.swing.JComponent

private fun createHorizontalPanel(): JBPanel<*> = JBPanel<JBPanel<*>>(HorizontalLayout(scale(16)))

private fun JBPopup.showAbove(component: JComponent) {
  val northWest = RelativePoint(component, Point())

  addListener(object : JBPopupListener {
    override fun beforeShown(event: LightweightWindowEvent) {
      val popup = event.asPopup()
      val location = northWest.screenPoint.apply { translate(0, -popup.size.height) }

      popup.setLocation(location)
    }
  })
  show(northWest)
}

class ChangesViewCommitPanel(private val changesView: ChangesListView) : BorderLayoutPanel(), ChangesViewCommitWorkflowUi, ComponentContainer, DataProvider {
  private val project get() = changesView.project

  private val dataProviders = mutableListOf<DataProvider>()

  private val executorEventDispatcher = EventDispatcher.create(CommitExecutorListener::class.java)
  private val inclusionEventDispatcher = EventDispatcher.create(InclusionListener::class.java)

  val actions = ActionManager.getInstance().getAction("ChangesView.CommitToolbar") as ActionGroup
  val toolbar = ActionManager.getInstance().createActionToolbar("ChangesView.CommitToolbar", actions, false).apply {
    setTargetComponent(this@ChangesViewCommitPanel)
    addBorder(component, createBorder(JBColor.border(), SideBorder.RIGHT))
  }
  val commitMessage = CommitMessage(project, false, false, true).apply {
    editorField.addSettingsProvider { it.setBorder(emptyLeft(3)) }
    editorField.setPlaceholder("Commit Message")
  }
  private val commitButton = object : JButton() {
    override fun isDefaultButton() = true
  }
  private val commitLegendCalculator = ChangeInfoCalculator()
  private val commitLegend = CommitLegendPanel(commitLegendCalculator)

  init {
    buildLayout()

    changesView.setInclusionListener { inclusionEventDispatcher.multicaster.inclusionChanged() }
    commitButton.addActionListener { fireDefaultExecutorCalled() }

    addInclusionListener(object : InclusionListener {
      override fun inclusionChanged() = this@ChangesViewCommitPanel.inclusionChanged()
    }, this)
  }

  private fun buildLayout() {
    val buttonPanel = createHorizontalPanel().apply {
      add(commitButton)
      add(CurrentBranchComponent(project, changesView, this@ChangesViewCommitPanel))
      add(commitLegend.component)
    }.withBackground(getTreeBackground())
    val centerPanel = simplePanel(commitMessage).addToBottom(buttonPanel)

    addToCenter(centerPanel).addToLeft(toolbar.component).withBorder(createBorder(JBColor.border(), SideBorder.TOP))
    withPreferredHeight(85)
  }

  private fun inclusionChanged() {
    updateLegend()
  }

  private fun updateLegend() {
    // Displayed changes and unversioned files are not actually used in legend - so we don't pass them
    commitLegendCalculator.update(
      includedChanges = getIncludedChanges(), includedUnversionedFilesCount = getIncludedUnversionedFiles().size)
    commitLegend.update()
  }

  private fun fireDefaultExecutorCalled() = executorEventDispatcher.multicaster.executorCalled(null)

  fun setupShortcuts(component: JComponent) = DefaultCommitAction().registerCustomShortcutSet(CTRL_ENTER, component)

  override val commitMessageUi: CommitMessageUi get() = commitMessage

  // store as a separate property - this way getter will return text with mnemonic
  override var defaultCommitActionName: String = ""
    set(value) {
      field = value
      commitButton.text = value
    }

  override var isDefaultCommitActionEnabled: Boolean
    get() = commitButton.isEnabled
    set(value) {
      commitButton.isEnabled = value
    }

  override fun activate(): Boolean {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID) ?: return false
    val contentManager = ChangesViewContentManager.getInstance(project)

    contentManager.selectContent(ChangesViewContentManager.LOCAL_CHANGES)
    toolWindow.activate({ commitMessage.requestFocusInMessage() }, false)
    return true
  }

  override fun showCommitOptions(options: CommitOptions, isFromToolbar: Boolean, dataContext: DataContext) {
    val commitOptionsPanel = CommitOptionsPanel { defaultCommitActionName }.apply {
      setOptions(options)
      border = empty(0, 10)
      MnemonicHelper.init(this)
    }
    val commitOptionsPopup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(commitOptionsPanel, null)
      .setRequestFocus(true)
      .createPopup()

    if (isFromToolbar) commitOptionsPopup.showAbove(this)
    else commitOptionsPopup.showInBestPositionFor(dataContext)
  }

  override fun getComponent(): JComponent = this
  override fun getPreferredFocusableComponent(): JComponent = commitMessage.editorField

  override fun getData(dataId: String) = getDataFromProviders(dataId) ?: commitMessage.getData(dataId)
  private fun getDataFromProviders(dataId: String) = dataProviders.asSequence().mapNotNull { it.getData(dataId) }.firstOrNull()

  override fun addDataProvider(provider: DataProvider) {
    dataProviders += provider
  }

  override fun addExecutorListener(listener: CommitExecutorListener, parent: Disposable) =
    executorEventDispatcher.addListener(listener, parent)

  override fun refreshData() = (ChangesViewManager.getInstance(project) as ChangesViewManager).refreshImmediately()

  override fun getDisplayedChanges(): List<Change> = all(changesView).userObjects(Change::class.java)
  override fun getIncludedChanges(): List<Change> = included(changesView).userObjects(Change::class.java)

  override fun getDisplayedUnversionedFiles(): List<VirtualFile> =
    allUnderTag(changesView, UNVERSIONED_FILES_TAG).userObjects(VirtualFile::class.java)

  override fun getIncludedUnversionedFiles(): List<VirtualFile> =
    includedUnderTag(changesView, UNVERSIONED_FILES_TAG).userObjects(VirtualFile::class.java)

  override fun includeIntoCommit(items: Collection<*>) = changesView.includeChanges(items)

  override fun addInclusionListener(listener: InclusionListener, parent: Disposable) =
    inclusionEventDispatcher.addListener(listener, parent)

  override fun confirmCommitWithEmptyMessage(): Boolean =
    Messages.YES == Messages.showYesNoDialog(
      message("confirmation.text.check.in.with.empty.comment"),
      message("confirmation.title.check.in.with.empty.comment"),
      Messages.getWarningIcon()
    )

  override fun startBeforeCommitChecks() = Unit
  override fun endBeforeCommitChecks(result: CheckinHandler.ReturnResult) = Unit

  override fun dispose() = Unit

  private inner class DefaultCommitAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = commitButton.isEnabled
    }

    override fun actionPerformed(e: AnActionEvent) = fireDefaultExecutorCalled()
  }
}