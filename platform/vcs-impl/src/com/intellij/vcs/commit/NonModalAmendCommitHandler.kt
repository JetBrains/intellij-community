// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.ProjectLevelVcsManager.Companion.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitDetails
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitPresentation
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.util.VcsUserUtil.isSamePerson
import org.jetbrains.concurrency.CancellablePromise
import kotlin.properties.Delegates.observable

private val AMEND_AUTHOR_DATA_KEY = Key.create<AmendAuthorData>("Vcs.Commit.AmendAuthorData")
private var CommitContext.amendAuthorData: AmendAuthorData? by commitProperty(AMEND_AUTHOR_DATA_KEY, null)

private class AmendAuthorData(
  val beforeAmendAuthor: VcsUser?, // Author before entering amend mode
  val amendAuthor: VcsUser?, // Last author that was set by selecting amend mode
)

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
    workflowHandler.workflow.addVcsCommitListener(EditedCommitCleaner(), workflowHandler)

    amendRoot = getSingleRoot()
    project.messageBus.connect(workflowHandler).subscribe(VCS_CONFIGURATION_CHANGED, VcsListener {
      runInEdt { amendRoot = getSingleRoot() }
    })
  }

  val isLoading: Boolean get() = _isLoading

  internal fun isAmendWithoutChangesAllowed(): Boolean = commitToAmend !is CommitToAmend.None && amendRoot != null

  override fun amendCommitModeToggled() {
    val root = amendRoot?.path ?: return super.amendCommitModeToggled()
    val amendAware = amendRoot?.vcs?.checkinEnvironment as? AmendCommitAware ?: return super.amendCommitModeToggled()

    fireAmendCommitModeToggled()
    workflowHandler.updateDefaultCommitActionName()
    workflowHandler.hideCommitChecksFailureNotification()
    updateAmendCommitState()
    if (commitToAmend is CommitToAmend.None) restoreAmendDetails() else loadAmendDetails(amendAware, root, commitToAmend)
  }

  private fun updateAmendCommitState() {
    commitContext.commitWithoutChangesRoots = if (commitToAmend !is CommitToAmend.None) listOfNotNull(amendRoot) else emptyList()
  }

  private fun loadAmendDetails(amendAware: AmendCommitAware, root: VirtualFile, commitToAmend: CommitToAmend) {
    _isLoading = true
    setEditedCommit(EditedCommitPresentation.Loading)
    amendDetailsGetter = amendAware.getAmendCommitDetails(root, commitToAmend)
    amendDetailsGetter?.run {
      onSuccess { setAmendDetails(it) }
      onError { setEditedCommit(null) }
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
    setAmendAuthor(amendDetails.currentUser, amendDetails.author)
    setAmendMessage(workflowHandler.getCommitMessage(), amendDetails.fullMessage)
    setEditedCommit(amendDetails)
  }

  private fun setEditedCommit(amendDetails: EditedCommitPresentation?) {
    ChangesViewWorkflowManager.getInstance(project).setEditedCommit(amendDetails)
  }

  private fun setAmendAuthor(currentUser: VcsUser?, amendAuthor: VcsUser) {
    val currentAuthor = workflowHandler.ui.commitAuthor
    val newAuthor = if (currentUser != null && isSamePerson(amendAuthor, currentUser)) null else amendAuthor

    workflowHandler.ui.commitAuthor = newAuthor

    val previousAuthorData = commitContext.amendAuthorData

    // Preserve the original author from before first entering amend mode
    commitContext.amendAuthorData = AmendAuthorData(
      beforeAmendAuthor = if (previousAuthorData != null) previousAuthorData.beforeAmendAuthor else currentAuthor,
      amendAuthor = newAuthor
    )
  }

  private fun restoreAmendAuthor() {
    val amendAuthorData = commitContext.amendAuthorData ?: return
    commitContext.amendAuthorData = null

    val currentAuthor = workflowHandler.ui.commitAuthor

    // don't restore if user changed author manually
    if (!isSameAuthor(currentAuthor, amendAuthorData.amendAuthor)) return

    workflowHandler.ui.commitAuthor = amendAuthorData.beforeAmendAuthor
  }

  private fun isSameAuthor(author1: VcsUser?, author2: VcsUser?): Boolean = when {
    author1 == null && author2 == null -> true
    author1 != null && author2 != null -> isSamePerson(author1, author2)
    else -> false
  }

  private inner class EditedCommitCleaner : CommitterResultHandler {
    override fun onSuccess() {
      setEditedCommit(null)
    }
    override fun onCancel() = Unit
    override fun onFailure() {
      setEditedCommit(null)
    }
  }
}