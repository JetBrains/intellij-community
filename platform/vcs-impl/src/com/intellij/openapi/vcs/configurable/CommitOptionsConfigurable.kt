// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBPanel
import com.intellij.util.containers.mapNotNullLoggingErrors
import com.intellij.vcs.commit.AbstractCommitWorkflow.Companion.getCommitHandlers
import com.intellij.vcs.commit.CommitOptionsPanel.Companion.verticalPanel
import com.intellij.vcs.commit.CommitWorkflowHandler
import com.intellij.vcs.commit.NullCommitWorkflowHandler
import com.intellij.vcs.commit.getDefaultCommitActionName
import java.awt.GridLayout
import java.io.File

private val LOG = logger<CommitOptionsConfigurable>()

class CommitOptionsConfigurable(val project: Project) : JBPanel<CommitOptionsConfigurable>(GridLayout()), UnnamedConfigurable, Disposable {
  private val checkinPanel = CheckinPanel(project)
  private val commitContext = CommitContext()
  private val checkinHandlers = getCommitHandlers(
    ProjectLevelVcsManager.getInstance(project).allActiveVcss.toList(), checkinPanel, commitContext)
  private val beforeSettings = checkinHandlers.mapNotNullLoggingErrors(LOG) { it.beforeCheckinSettings }

  init {
    add(verticalPanel().apply {
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

    override fun getCommitWorkflowHandler(): CommitWorkflowHandler = NullCommitWorkflowHandler
    override fun getProject() = project

    override fun vcsIsAffected(name: String) = false
    override fun hasDiffs() = false
    override fun getRoots() = emptyList<VirtualFile>()
    override fun getVirtualFiles() = emptyList<VirtualFile>()
    override fun getSelectedChanges() = emptyList<Change>()
    override fun getFiles() = emptyList<File>()

    override fun getCommitActionName(): String = getDefaultCommitActionName()
    override fun setCommitMessage(currentDescription: String) {}
    override fun getCommitMessage() = ""

    override fun saveState() {}
    override fun restoreState() {}
  }
}