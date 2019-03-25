// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesUtil.getFilePath
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil.getFilePath
import java.io.File
import javax.swing.JComponent

internal open class CommitProjectPanelAdapter(private val handler: SingleChangeListCommitWorkflowHandler) : CheckinProjectPanel {
  private val workflow get() = handler.workflow
  private val ui get() = handler.ui
  private val vcsManager get() = ProjectLevelVcsManager.getInstance(workflow.project)

  private fun getDisplayedChanges() = ui.getDisplayedChanges()
  private fun getIncludedChanges() = ui.getIncludedChanges()
  private fun getDisplayedUnversionedFiles() = ui.getDisplayedUnversionedFiles()
  private fun getIncludedUnversionedFiles() = ui.getIncludedUnversionedFiles()

  private fun getDisplayedPaths() = getDisplayedChanges().map { getFilePath(it) } + getDisplayedUnversionedFiles().map { getFilePath(it) }
  private fun getIncludedPaths() = getIncludedChanges().map { getFilePath(it) } + getIncludedUnversionedFiles().map { getFilePath(it) }

  override fun getProject(): Project = workflow.project

  // NOTE: Seems it is better to remove getComponent()/getPreferredFocusedComponent() usages. And provide more specific methods instead.
  // So corresponding methods are not added to workflow ui interface explicitly.

  override fun getComponent(): JComponent? = (ui as? ComponentContainer)?.component
  override fun getPreferredFocusedComponent(): JComponent? = (ui as? ComponentContainer)?.preferredFocusableComponent

  override fun hasDiffs(): Boolean = !handler.isCommitEmpty()
  override fun getVirtualFiles(): Collection<VirtualFile> = getIncludedPaths().mapNotNull { it.virtualFile }
  override fun getSelectedChanges(): Collection<Change> = getIncludedChanges()
  override fun getFiles(): Collection<File> = getIncludedPaths().map { it.ioFile }
  override fun getRoots(): Collection<VirtualFile> = getDisplayedPaths().mapNotNullTo(hashSetOf()) { vcsManager.getVcsRootFor(it) }

  override fun vcsIsAffected(name: String): Boolean = vcsManager.checkVcsIsActive(name) && workflow.affectedVcses.any { it.name == name }
  override fun getCommitActionName(): String = ui.defaultCommitActionName

  override fun getCommitMessage(): String = ui.commitMessageUi.text
  override fun setCommitMessage(currentDescription: String?) {
    ui.commitMessageUi.setText(currentDescription)
    ui.commitMessageUi.focus()
  }

  override fun refresh() =
    ChangeListManager.getInstance(workflow.project).invokeAfterUpdate(
      {
        ui.refreshData()
        workflow.commitOptions.refresh()
      },
      InvokeAfterUpdateMode.SILENT, null, ModalityState.current()
    )

  override fun saveState() = workflow.commitOptions.saveState()
  override fun restoreState() = workflow.commitOptions.restoreState()
}