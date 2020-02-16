// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.concurrency.CancellablePromise
import kotlin.properties.Delegates.observable

class ChangesViewAmendCommitHandler(private val workflowHandler: ChangesViewCommitWorkflowHandler) :
  AmendCommitHandlerImpl(workflowHandler) {

  private var amendRoot by observable<VcsRoot?>(null) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable
    updateAmendCommitState()
  }
  private var amendDetailsGetter: CancellablePromise<EditedCommitDetails>? = null

  init {
    workflowHandler.workflow.addCommitListener(EditedCommitCleaner(), workflowHandler)

    amendRoot = getSingleRoot()
    project.messageBus.connect(workflowHandler).subscribe(VCS_CONFIGURATION_CHANGED, VcsListener {
      runInEdt { amendRoot = getSingleRoot() }
    })
  }

  internal fun isAmendWithoutChangesAllowed(): Boolean = isAmendCommitMode && amendRoot != null

  override fun amendCommitModeToggled() {
    val root = amendRoot?.path ?: return super.amendCommitModeToggled()
    val amendAware = amendRoot?.vcs?.checkinEnvironment as? AmendCommitAware ?: return super.amendCommitModeToggled()

    fireAmendCommitModeToggled()
    setAmendPrefix(isAmendCommitMode)
    workflowHandler.updateDefaultCommitActionEnabled()
    updateAmendCommitState()
    if (isAmendCommitMode) loadAmendDetails(amendAware, root) else restoreAmendDetails()
  }

  private fun updateAmendCommitState() {
    commitContext.commitWithoutChangesRoots = if (isAmendCommitMode) listOfNotNull(amendRoot) else emptyList()
  }

  private fun loadAmendDetails(amendAware: AmendCommitAware, root: VirtualFile) {
    amendDetailsGetter = amendAware.getAmendCommitDetails(root)
    amendDetailsGetter?.run {
      onSuccess { setAmendDetails(it) }
      onProcessed { amendDetailsGetter = null }
    }
  }

  private fun restoreAmendDetails() {
    amendDetailsGetter?.cancel()

    restoreBeforeAmendMessage()
    setEditedCommit(null)
  }

  private fun setAmendDetails(amendDetails: EditedCommitDetails) {
    setAmendMessage(workflowHandler.getCommitMessage(), amendDetails.commit.fullMessage)
    setEditedCommit(amendDetails)
  }

  private fun setEditedCommit(amendDetails: EditedCommitDetails?) {
    workflowHandler.ui.editedCommit = amendDetails
  }

  private inner class EditedCommitCleaner : CommitResultHandler {
    override fun onSuccess(commitMessage: String) = setEditedCommit(null)
    override fun onCancel() = Unit
    override fun onFailure() = setEditedCommit(null)
  }
}