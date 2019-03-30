// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.UNVERSIONED_FILES_TAG
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.*
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.IdeBorderFactory.createBorder
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.JBUI.Borders.emptyLeft
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil.addBorder
import com.intellij.util.ui.UIUtil.getTreeBackground
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JButton
import javax.swing.JComponent

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
  private val commitButton = object : JButton(getDefaultCommitActionName()) {
    init {
      isEnabled = false
    }

    override fun isDefaultButton() = true
  }

  init {
    buildLayout()

    changesView.setInclusionListener { inclusionEventDispatcher.multicaster.inclusionChanged() }
    commitButton.addActionListener { executorEventDispatcher.multicaster.executorCalled(null) }
  }

  private fun buildLayout() {
    val buttonPanel = simplePanel()
      .addToLeft(commitButton)
      .withBackground(getTreeBackground())
    val centerPanel = simplePanel(commitMessage).addToBottom(buttonPanel)

    addToCenter(centerPanel).addToLeft(toolbar.component).withBorder(createBorder(JBColor.border(), SideBorder.TOP))
    withPreferredHeight(85)
  }

  override val commitMessageUi: CommitMessageUi get() = commitMessage

  override var defaultCommitActionName: String
    get() = commitButton.text
    set(value) {
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
}