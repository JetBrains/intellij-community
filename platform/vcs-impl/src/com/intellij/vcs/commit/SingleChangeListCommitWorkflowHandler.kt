// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesUtil.getAffectedVcses
import com.intellij.openapi.vcs.changes.ChangesUtil.getAffectedVcsesForFilePaths
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager

class SingleChangeListCommitWorkflowHandler(
  override val workflow: SingleChangeListCommitWorkflow,
  override val ui: SingleChangeListCommitWorkflowUi
) : AbstractCommitWorkflowHandler<SingleChangeListCommitWorkflow, SingleChangeListCommitWorkflowUi>(),
    CommitWorkflowUiStateListener,
    SingleChangeListCommitWorkflowUi.ChangeListListener {

  override val commitPanel: CheckinProjectPanel = object : CommitProjectPanelAdapter(this) {
    override fun setCommitMessage(currentDescription: String?) {
      commitMessagePolicy.defaultNameChangeListMessage = currentDescription

      super.setCommitMessage(currentDescription)
    }
  }

  override val amendCommitHandler: AmendCommitHandlerImpl = AmendCommitHandlerImpl(this)

  private fun getChangeList() = ui.getChangeList()

  private fun getCommitState() = ChangeListCommitState(getChangeList(), getIncludedChanges(), getCommitMessage())

  private val commitMessagePolicy get() = workflow.commitMessagePolicy

  init {
    Disposer.register(this, Disposable { workflow.disposeCommitOptions() })
    Disposer.register(ui, this)

    workflow.addListener(this, this)
    workflow.addCommitCustomListener(CommitCustomListener(), this)

    ui.addStateListener(this, this)
    ui.addExecutorListener(this, this)
    ui.addDataProvider(createDataProvider())
    ui.addChangeListListener(this, this)
  }

  override fun vcsesChanged() = Unit

  fun activate(): Boolean {
    initCommitHandlers()

    ui.addInclusionListener(this, this)
    updateDefaultCommitActionName()
    initCommitMessage()
    initCommitOptions()

    amendCommitHandler.initialMessage = getCommitMessage()

    return ui.activate()
  }

  override fun cancelled() {
    commitOptions.saveChangeListSpecificOptions()
    saveCommitMessage(false)

    LineStatusTrackerManager.getInstanceImpl(project).resetExcludedFromCommitMarkers()
  }

  override fun changeListChanged() {
    updateCommitMessage()
    updateCommitOptions()
  }

  override fun beforeCommitChecksEnded(isDefaultCommit: Boolean, result: CheckinHandler.ReturnResult) {
    super.beforeCommitChecksEnded(isDefaultCommit, result)
    if (result == CheckinHandler.ReturnResult.COMMIT) {
      // commit message could be changed during before-commit checks - ensure updated commit message is used for commit
      workflow.commitState = workflow.commitState.copy(getCommitMessage())

      if (isDefaultCommit) ui.deactivate()
    }
  }

  override fun isExecutorEnabled(executor: CommitExecutor): Boolean =
    super.isExecutorEnabled(executor) && (!isCommitEmpty() || !executor.areChangesRequired())

  override fun checkCommit(executor: CommitExecutor?): Boolean =
    getCommitMessage().isNotEmpty() ||
    !VcsConfiguration.getInstance(project).FORCE_NON_EMPTY_COMMENT ||
    ui.confirmCommitWithEmptyMessage()

  override fun updateWorkflow() {
    workflow.commitState = getCommitState()
  }

  override fun addUnversionedFiles() = addUnversionedFiles(getChangeList())

  private fun initCommitMessage() {
    commitMessagePolicy.init(getChangeList(), getIncludedChanges())
    setCommitMessage(commitMessagePolicy.commitMessage)
  }

  private fun updateCommitMessage() {
    commitMessagePolicy.update(getChangeList(), getCommitMessage())
    setCommitMessage(commitMessagePolicy.commitMessage)
  }

  override fun saveCommitMessage(success: Boolean) = commitMessagePolicy.save(getCommitState(), success)

  private fun initCommitOptions() {
    workflow.initCommitOptions(createCommitOptions())
    ui.commitOptionsUi.setOptions(commitOptions)

    commitOptions.restoreState()
    updateCommitOptions()
  }

  private fun updateCommitOptions() {
    commitOptions.changeListChanged(getChangeList())
    updateCommitOptionsVisibility()
  }

  private fun updateCommitOptionsVisibility() {
    val unversionedFiles = ChangeListManager.getInstance(project).unversionedFilesPaths
    val vcses = getAffectedVcses(getChangeList().changes, project) + getAffectedVcsesForFilePaths(unversionedFiles, project)

    ui.commitOptionsUi.setVisible(vcses)
  }

  private inner class CommitCustomListener : CommitResultHandler {
    override fun onSuccess(commitMessage: String) = ui.deactivate()
  }
}