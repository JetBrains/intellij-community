// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts.CTRL_ENTER
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog.DIALOG_TITLE
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.included
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.IdeBorderFactory.createBorder
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.util.FunctionUtil
import com.intellij.util.ui.JBUI.Borders.emptyLeft
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil.addBorder
import com.intellij.util.ui.UIUtil.getTreeBackground
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JButton
import javax.swing.JComponent

class ChangesViewCommitPanel(val project: Project, private val changesView: ChangesListView) : BorderLayoutPanel(), DataProvider {
  val actions = ActionManager.getInstance().getAction("ChangesView.CommitToolbar") as ActionGroup
  val toolbar = ActionManager.getInstance().createActionToolbar("ChangesView.CommitToolbar", actions, false).apply {
    setTargetComponent(this@ChangesViewCommitPanel)
    addBorder(component, createBorder(JBColor.border(), SideBorder.RIGHT))
  }
  val commitMessage = CommitMessage(project, false, false, true).apply {
    editorField.addSettingsProvider { it.setBorder(emptyLeft(3)) }
    editorField.setPlaceholder("Commit Message")
  }

  init {
    val commitButton = object : JButton("Commit") {
      override fun isDefaultButton() = true
    }
    val buttonPanel = simplePanel()
      .addToLeft(commitButton)
      .withBackground(getTreeBackground())
    val centerPanel = simplePanel(commitMessage).addToBottom(buttonPanel)

    addToCenter(centerPanel).addToLeft(toolbar.component).withBorder(createBorder(JBColor.border(), SideBorder.TOP))
    withPreferredHeight(85)

    commitButton.addActionListener { doCommit() }
  }

  override fun getData(dataId: String) = commitMessage.getData(dataId)

  fun setupShortcuts(component: JComponent) {
    CommitAction().registerCustomShortcutSet(CTRL_ENTER, component)
  }

  fun applyParameters(included: Collection<*>) {
    changesView.setIncludedChanges(included)
    commitMessage.requestFocusInMessage()
  }

  private fun getIncludedChanges() = included(changesView).userObjects(Change::class.java)

  private fun doCommit() {
    //    TODO handlers
    //    TODO commit options
    val committer = SimpleCommitter(project, getIncludedChanges(), commitMessage.comment, emptyList(), FunctionUtil.nullConstant())

    committer.addResultHandler(DefaultCommitResultHandler(committer))
    committer.runCommit(DIALOG_TITLE, false)
  }

  private inner class CommitAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) = doCommit()
  }
}