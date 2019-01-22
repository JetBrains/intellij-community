// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts.CTRL_ENTER
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.UNVERSIONED_FILES_TAG
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog.DIALOG_TITLE
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.*
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.IdeBorderFactory.createBorder
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.util.FunctionUtil
import com.intellij.util.ui.JBUI.Borders.emptyLeft
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil.addBorder
import com.intellij.util.ui.UIUtil.getTreeBackground
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vcsUtil.VcsUtil
import java.io.File
import javax.swing.JButton
import javax.swing.JComponent

class ChangesViewCommitPanel(
  private val project: Project,
  private val changesView: ChangesListView
) : BorderLayoutPanel(), CheckinProjectPanel, DataProvider {

  val actions = ActionManager.getInstance().getAction("ChangesView.CommitToolbar") as ActionGroup
  val toolbar = ActionManager.getInstance().createActionToolbar("ChangesView.CommitToolbar", actions, false).apply {
    setTargetComponent(this@ChangesViewCommitPanel)
    addBorder(component, createBorder(JBColor.border(), SideBorder.RIGHT))
  }
  val commitMessage = CommitMessage(project, false, false, true).apply {
    editorField.addSettingsProvider { it.setBorder(emptyLeft(3)) }
    editorField.setPlaceholder("Commit Message")
  }
  private val legendCalculator = ChangeInfoCalculator()
  private val legend = CommitLegendPanel(legendCalculator)

  private val commitButton = object : JButton("Commit") {
    init {
      background = getTreeBackground()
      isEnabled = false
    }

    override fun isDefaultButton() = true
  }

  private val vcsManager = ProjectLevelVcsManager.getInstance(project)
  val workflow = ChangesViewCommitWorkflow(project)
  //  TODO handlers
  //  TODO vcses
  private var areOptionsSet = false
  val commitOptionsPanel = CommitOptionsPanel(this, workflow.additionalDataConsumer)

  init {
    val buttonPanel = simplePanel()
      .addToLeft(commitButton)
      .addToRight(legend.component)
      .withBackground(getTreeBackground())
    val centerPanel = simplePanel(commitMessage).addToBottom(buttonPanel)

    addToCenter(centerPanel).addToLeft(toolbar.component).withBorder(createBorder(JBColor.border(), SideBorder.TOP))
    withPreferredHeight(85)

    commitButton.addActionListener { doCommit() }
    changesView.setInclusionListener { inclusionChanged() }

    project.messageBus.connect().subscribe(VCS_CONFIGURATION_CHANGED, VcsListener {
      runInEdt {
        if (!areOptionsSet) {
          areOptionsSet = true

          commitOptionsPanel.vcses = vcsManager.allActiveVcss.toList()
          commitOptionsPanel.handlers = CommitChangeListDialog.createCheckinHandlers(project, this, workflow.commitContext)
          restoreState()
        }
      }
    })
  }

  override fun getData(dataId: String) = commitMessage.getData(dataId)

  fun setupShortcuts(component: JComponent) {
    CommitAction().registerCustomShortcutSet(CTRL_ENTER, component)
  }

  fun applyParameters(included: Collection<*>) {
    ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)?.activate {
      changesView.setIncludedChanges(included)
      inclusionChanged()

//    TODO Looks like we need to ensure corresponding node/nodes is expanded, or even selected??

      val contentManager = ChangesViewContentManager.getInstance(project) as ChangesViewContentManager
      contentManager.selectContent(ChangesViewContentManager.LOCAL_CHANGES, true).doWhenDone {
        commitMessage.requestFocusInMessage()
      }
    }
  }

  override fun getProject(): Project = project

  override fun getComponent(): JComponent = this
  override fun getPreferredFocusedComponent(): JComponent = commitMessage.editorField

  override fun hasDiffs(): Boolean = getIncludedChanges().isNotEmpty() || getIncludedUnversioned().isNotEmpty()
  override fun getVirtualFiles(): Collection<VirtualFile> = getIncludedPaths().mapNotNull { it.virtualFile }
  override fun getSelectedChanges(): Collection<Change> = getIncludedChanges()
  override fun getFiles(): Collection<File> = getIncludedPaths().map { it.ioFile }
  override fun getRoots(): Collection<VirtualFile> = getDisplayedPaths().mapNotNullTo(hashSetOf()) { vcsManager.getVcsRootFor(it) }

  override fun vcsIsAffected(name: String): Boolean = workflow.affectedVcses.any { it.name == name }
  override fun getCommitActionName(): String = VcsImplUtil.getCommitActionName(workflow.affectedVcses)

  override fun setCommitMessage(currentDescription: String?) = commitMessage.setText(currentDescription)
  override fun getCommitMessage(): String = commitMessage.comment

  override fun refresh() = commitOptionsPanel.refresh()
  override fun saveState() = commitOptionsPanel.saveState()
  override fun restoreState() = commitOptionsPanel.restoreState()

  private fun inclusionChanged() {
    //    TODO "all" numbers are not used in legend. Remove them from method, or add comment here
    legendCalculator.update(emptyList(), getIncludedChanges(), 0, getIncludedUnversioned().size)
    legend.update()

    commitButton.isEnabled = hasDiffs()
  }

  private fun getDisplayedPaths() =
    getDisplayedChanges().map { ChangesUtil.getFilePath(it) } + getDisplayedUnversioned().map { VcsUtil.getFilePath(it) }

  private fun getIncludedPaths() =
    getIncludedChanges().map { ChangesUtil.getFilePath(it) } + getIncludedUnversioned().map { VcsUtil.getFilePath(it) }

  private fun getDisplayedChanges() = all(changesView).userObjects(Change::class.java)
  private fun getIncludedChanges() = included(changesView).userObjects(Change::class.java)
  private fun getDisplayedUnversioned() = allUnderTag(changesView, UNVERSIONED_FILES_TAG).userObjects(VirtualFile::class.java)
  private fun getIncludedUnversioned() = includedUnderTag(changesView, UNVERSIONED_FILES_TAG).userObjects(VirtualFile::class.java)

  private fun doCommit() {
    //    TODO handlers
    //    TODO commit options
    val committer = SimpleCommitter(project, getIncludedChanges(), commitMessage.comment, emptyList(), FunctionUtil.nullConstant())

    committer.addResultHandler(DefaultCommitResultHandler(committer))
    committer.addResultHandler(UiCommitHandler(committer))
    committer.runCommit(DIALOG_TITLE, false)
  }

  private inner class UiCommitHandler(private val committer: AbstractCommitter) : CommitResultHandler {
    override fun onSuccess(message: String) {
      runInEdt {
        changesView.excludeChanges(committer.changes)
        commitMessage.setCommitMessage(null)

        changesView.requestFocus()
      }
    }

    //    TODO probably we need to also exclude some changes here???
    override fun onFailure() = Unit
  }

  private inner class CommitAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) = doCommit()
  }
}