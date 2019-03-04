// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.commit.handle.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBDimension
import com.jetbrains.changeReminder.predict.PredictedChange
import com.jetbrains.changeReminder.predict.PredictedFile
import java.awt.BorderLayout
import javax.swing.Action
import javax.swing.JPanel

class ChangeReminderDialog(private val project: Project, private val files: List<PredictedFile>) : DialogWrapper(project), DataProvider {
  init {
    init()
    title = "ChangeReminder Plugin"
  }

  private lateinit var tree: ChangesTree

  override fun getData(dataId: String) = tree.getData(dataId)

  override fun getDimensionServiceKey() = "com.jetbrains.changeReminder.commit.handle.ui.ChangeReminderDialog"

  override fun createActions(): Array<Action> {
    val cancel = DialogWrapperExitAction("Cancel Commit", 1)
    cancel.putValue(DEFAULT_ACTION, 0)
    val commitAnyway = DialogWrapperExitAction("Commit Anyway", 0)
    return arrayOf(cancel, commitAnyway)
  }

  private fun createActionsPanel(): JPanel {
    val group = DefaultActionGroup()
    group.add(ActionManager.getInstance().getAction(ChangesTree.GROUP_BY_ACTION_GROUP))
    val toolbar = ActionManager.getInstance()
      .createActionToolbar("ChangeReminder.PredictionDialog", group, true)
    toolbar.setTargetComponent(tree)
    return TreeActionsToolbarPanel(toolbar, tree)
  }

  private fun createTreeScrollPane(): JBScrollPane {
    val scrollPane = JBScrollPane(tree)
    scrollPane.preferredSize = JBDimension(400, 300)
    return scrollPane
  }

  private fun createTreePanel(): JPanel {
    tree = PredictedFilesTreeImpl(project, false, false, files)
    val panel = JPanel(BorderLayout())
    panel.add(createActionsPanel(), BorderLayout.PAGE_START)
    panel.add(createTreeScrollPane(), BorderLayout.CENTER)

    return panel
  }

  private fun createForgottenFilesString() = buildString {
    val modifiedFilesCount = files.filterIsInstance<PredictedChange>().size
    val unmodifiedFilesCount = files.size - modifiedFilesCount
    if (modifiedFilesCount > 0) {
      append("commit ")
    }
    if (modifiedFilesCount > 0 && unmodifiedFilesCount > 0) {
      append("or ")
    }
    if (unmodifiedFilesCount > 0) {
      append("modify ")
    }
    append("${StringUtil.pluralize("this", files.size)} ${StringUtil.pluralize("file", files.size)}")
  }

  override fun createCenterPanel() = panel {
    row {
      JBLabel("You might have forgotten to ${createForgottenFilesString()}:")()
    }
    row {
      createTreePanel()(grow, push)
    }
  }

}