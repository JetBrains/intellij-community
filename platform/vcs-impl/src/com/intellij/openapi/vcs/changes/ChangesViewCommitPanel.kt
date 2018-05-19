// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangeInfoCalculator
import com.intellij.openapi.vcs.changes.ui.CommitLegendPanel
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcsUtil.VcsUtil
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.BoxLayout

class ChangesViewCommitPanel(val project: Project) : BorderLayoutPanel() {
  init {
    val actions = DefaultActionGroup(ActionManager.getInstance().getAction("ChangesView.Refresh"),
                                     ActionManager.getInstance().getAction("ChangesView.NewChangeList"))
    val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actions, false)
    addToLeft(toolbar.component)

    //    val main = JBPanel<JBPanel<*>>().apply {
    //      add(CommitMessage(project, false, false, true))
    //      add(JButton("Commit"))
    //    }
    val button = object : JBOptionButton(SimpleAction("Commit"), arrayOf(SimpleAction("Executor 1"), SimpleAction("Executor 2"))) {
      override fun isDefaultButton() = true
    }
    val legendCalculator = ChangeInfoCalculator()
    legendCalculator.update(createChanges(), emptyList())
    val legend = CommitLegendPanel(legendCalculator)
    legend.update()

    val legendPanel = NonOpaquePanel()
    legendPanel.layout = BoxLayout(legendPanel, BoxLayout.X_AXIS)
    legendPanel.add(button)
    legendPanel.add(legend.component)

    val main = simplePanel(CommitMessage(project, false, false, true)).addToBottom(legendPanel)
    addToCenter(main)
  }

  private fun createChanges() = listOf(
    Change(SimpleContentRevision("1", VcsUtil.getFilePath("/home/file1"), ""), null),
    Change(null, SimpleContentRevision("2", VcsUtil.getFilePath("/home/file2"), "")),
    Change(SimpleContentRevision("3", VcsUtil.getFilePath("/home/file3"), ""),
           SimpleContentRevision("4", VcsUtil.getFilePath("/home/file3"), ""))
  )
}

class SimpleAction(val text: String) : AbstractAction(text) {
  override fun actionPerformed(e: ActionEvent) {
  }
}