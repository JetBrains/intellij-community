// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.SimpleContentRevision
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcsUtil.VcsUtil
import java.awt.event.ActionEvent
import java.io.File
import javax.swing.AbstractAction
import javax.swing.BoxLayout

class ChangesViewCommitPanel(private val project: Project, private val view: ChangesListView) : BorderLayoutPanel(), CheckinProjectPanel {
  override fun getComponent() = this

  //  duplicates CCLD
  override fun getPreferredFocusedComponent() = commitMessage.getEditorField()


  override fun hasDiffs(): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getVirtualFiles(): MutableCollection<VirtualFile> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getSelectedChanges(): MutableCollection<Change> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getFiles(): MutableCollection<File> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getProject() = project

  override fun vcsIsAffected(name: String?): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getCommitActionName(): String {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun setCommitMessage(currentDescription: String?) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getRoots(): MutableCollection<VirtualFile> {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun getCommitMessage(): String {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun refresh() {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun saveState() {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun restoreState() {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  private val commitMessage = CommitMessage(project, false, false, true)

  init {
    val actions = DefaultActionGroup(ActionManager.getInstance().getAction("Vcs.ShowCommitOptions"))
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

    val main = simplePanel(commitMessage).addToBottom(legendPanel)
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