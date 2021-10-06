// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.util.VcsUserUtil.isSamePerson
import org.jetbrains.concurrency.CancellablePromise
import kotlin.properties.Delegates.observable

private val AMEND_AUTHOR_DATA_KEY = Key.create<AmendAuthorData>("Vcs.Commit.AmendAuthorData")
private var CommitContext.amendAuthorData: AmendAuthorData? by commitProperty(AMEND_AUTHOR_DATA_KEY, null)

private class AmendAuthorData(val beforeAmendAuthor: VcsUser?, val amendAuthor: VcsUser)

class NonModalAmendCommitHandler(private val workflowHandler: NonModalCommitWorkflowHandler<*, *>) :
  AmendCommitHandlerImpl(workflowHandler) {

  private var amendRoot by observable<VcsRoot?>(null) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable
    updateAmendCommitState()
  }
  private var amendDetailsGetter: CancellablePromise<EditedCommitDetails>? = null
  private var _isLoading: Boolean by observable(false) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable
    workflowHandler.updateDefaultCommitActionEnabled()
  }

  init {
    workflowHandler.workflow.addCommitListener(EditedCommitCleaner(), workflowHandler)

    amendRoot = getSingleRoot()
    project.messageBus.connect(workflowHandler).subscribe(VCS_CONFIGURATION_CHANGED, VcsListener {
      runInEdt { amendRoot = getSingleRoot() }
    })
  }

  val isLoading: Boolean get() = _isLoading

  internal fun isAmendWithoutChangesAllowed(): Boolean = isAmendCommitMode && amendRoot != null

  override fun amendCommitModeToggled() {
    val root = amendRoot?.path ?: return super.amendCommitModeToggled()
    val amendAware = amendRoot?.vcs?.checkinEnvironment as? AmendCommitAware ?: return super.amendCommitModeToggled()

    fireAmendCommitModeToggled()
    workflowHandler.updateDefaultCommitActionName()
    updateAmendCommitState()
    if (isAmendCommitMode) loadAmendDetails(amendAware, root) else restoreAmendDetails()
  }

  private fun updateAmendCommitState() {
    commitContext.commitWithoutChangesRoots = if (isAmendCommitMode) listOfNotNull(amendRoot) else emptyList()
  }

  private fun loadAmendDetails(amendAware: AmendCommitAware, root: VirtualFile) {
    _isLoading = true
    amendDetailsGetter = amendAware.getAmendCommitDetails(root)
    amendDetailsGetter?.run {
      onSuccess { setAmendDetails(it) }
      onProcessed {
        _isLoading = false
        amendDetailsGetter = null
      }
    }
  }

  private fun restoreAmendDetails() {
    amendDetailsGetter?.cancel()
    workflowHandler.updateDefaultCommitActionEnabled()

    restoreAmendAuthor()
    restoreBeforeAmendMessage()
    setEditedCommit(null)
  }

  private fun setAmendDetails(amendDetails: EditedCommitDetails) {
    setAmendAuthor(amendDetails.currentUser, amendDetails.commit.author)
    setAmendMessage(workflowHandler.getCommitMessage(), amendDetails.commit.fullMessage)
    setEditedCommit(amendDetails)
  }

  private fun setEditedCommit(amendDetails: EditedCommitDetails?) {
    workflowHandler.ui.editedCommit = amendDetails
  }

  private fun setAmendAuthor(currentUser: VcsUser?, amendAuthor: VcsUser) {
    val beforeAmendAuthor = workflowHandler.ui.commitAuthor
    if (beforeAmendAuthor != null && isSamePerson(beforeAmendAuthor, amendAuthor)) return
    if (beforeAmendAuthor == null && currentUser != null && isSamePerson(currentUser, amendAuthor)) return

    workflowHandler.ui.commitAuthor = amendAuthor
    commitContext.amendAuthorData = AmendAuthorData(beforeAmendAuthor, amendAuthor)
  }

  private fun restoreAmendAuthor() {
    val amendAuthorData = commitContext.amendAuthorData ?: return
    commitContext.amendAuthorData = null

    val author = workflowHandler.ui.commitAuthor
    if (author == null || !isSamePerson(author, amendAuthorData.amendAuthor)) return

    workflowHandler.ui.commitAuthor = amendAuthorData.beforeAmendAuthor
  }

  private inner class EditedCommitCleaner : CommitResultHandler {
    override fun onSuccess(commitMessage: String) = setEditedCommit(null)
    override fun onCancel() = Unit
    override fun onFailure(errors: MutableList<VcsException>) = setEditedCommit(null)
  }
}