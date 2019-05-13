// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog.createCheckinHandlers
import com.intellij.openapi.vcs.changes.ui.CommitOptionsPanel.Companion.verticalPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.UIUtil.removeMnemonic
import java.awt.GridLayout
import java.io.File

class CommitOptionsConfigurable(val project: Project) : JBPanel<CommitOptionsConfigurable>(GridLayout()), UnnamedConfigurable, Disposable {
  private val checkinPanel = CheckinPanel(project)
  private val commitContext = CommitContext()
  private val checkinHandlers = createCheckinHandlers(project, checkinPanel, commitContext)
  private val beforeSettings = checkinHandlers.mapNotNull { it.beforeCheckinSettings }

  init {
    val actionName = removeMnemonic(checkinPanel.commitActionName)
    add(verticalPanel(message("border.standard.checkin.options.group", actionName)).apply {
      beforeSettings.mapNotNull { it.createComponent() }.forEach { add(it) }
    })
  }

  override fun reset() = beforeSettings.forEach { it.reset() }
  override fun isModified() = beforeSettings.any { it.isModified }
  override fun apply() = beforeSettings.forEach { it.apply() }
  override fun dispose() = beforeSettings.forEach { it.disposeUIResources() }
  override fun createComponent() = this

  class CheckinPanel(private val project: Project) : CheckinProjectPanel {
    override fun getComponent() = null
    override fun getPreferredFocusedComponent() = null

    override fun getProject() = project

    override fun vcsIsAffected(name: String) = false
    override fun hasDiffs() = false
    override fun getRoots() = emptyList<VirtualFile>()
    override fun getVirtualFiles() = emptyList<VirtualFile>()
    override fun getSelectedChanges() = emptyList<Change>()
    override fun getFiles() = emptyList<File>()

    override fun getCommitActionName(): String = message("commit.dialog.default.commit.operation.name")
    override fun setCommitMessage(currentDescription: String) {}
    override fun getCommitMessage() = ""

    override fun refresh() {}
    override fun saveState() {}
    override fun restoreState() {}
  }
}