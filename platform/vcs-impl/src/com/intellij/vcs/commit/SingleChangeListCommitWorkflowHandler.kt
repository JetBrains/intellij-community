// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.EdtNoGetDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.withModalProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerEx
import com.intellij.openapi.vcs.changes.ChangesUtil.getAffectedVcses
import com.intellij.openapi.vcs.changes.ChangesUtil.getAffectedVcsesForFilePaths
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.await

class SingleChangeListCommitWorkflowHandler(
  override val workflow: CommitChangeListDialogWorkflow,
  override val ui: SingleChangeListCommitWorkflowUi
) : AbstractCommitWorkflowHandler<CommitChangeListDialogWorkflow, SingleChangeListCommitWorkflowUi>(),
    CommitWorkflowUiStateListener,
    SingleChangeListCommitWorkflowUi.ChangeListListener {

  override val commitPanel: CheckinProjectPanel = object : CommitProjectPanelAdapter(this) {
    override fun setCommitMessage(currentDescription: String?) {
      commitMessagePolicy.onCommitMessageReset(currentDescription)
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
    ui.addDataProvider(EdtNoGetDataProvider { sink -> uiDataSnapshot(sink) })
    ui.addChangeListListener(this, this)
  }

  fun activate(): Boolean {
    initCommitHandlers()

    if (workflow.isDefaultCommitEnabled) {
      LineStatusTrackerManager.getInstanceImpl(project).resetExcludedFromCommitMarkers()
    }
    ui.getInclusionModel().setInclusion(workflow.initiallyIncluded)

    ui.addInclusionListener(this, this)
    updateDefaultCommitActionName()
    setCommitMessage(commitMessagePolicy.init(getChangeList(), getIncludedChanges()))
    initCommitOptions()

    amendCommitHandler.initialMessage = getCommitMessage()

    return ui.activate()
  }

  override fun cancelled() {
    commitOptions.saveChangeListSpecificOptions()
    commitMessagePolicy.onDialogClosed(getCommitState(), false)

    if (workflow.isDefaultCommitEnabled) {
      LineStatusTrackerManager.getInstanceImpl(project).resetExcludedFromCommitMarkers()
    }
  }

  override fun changeListChanged(oldChangeList: LocalChangeList, newChangeList: LocalChangeList) {
    commitMessagePolicy.onChangelistChanged(oldChangeList, newChangeList, ui.commitMessageUi)
    updateCommitOptions()
  }

  override fun updateDefaultCommitActionName() {
    ui.defaultCommitActionName = getDefaultCommitActionName(workflow.vcses)
  }

  override fun beforeCommitChecksEnded(sessionInfo: CommitSessionInfo, result: CommitChecksResult) {
    super.beforeCommitChecksEnded(sessionInfo, result)
    if (result.shouldCommit) {
      // commit message could be changed during before-commit checks - ensure updated commit message is used for commit
      workflow.commitState = workflow.commitState.copy(getCommitMessage())

      if (sessionInfo.isVcsCommit) ui.deactivate()
    }
  }

  override fun isExecutorEnabled(executor: CommitExecutor): Boolean =
    super.isExecutorEnabled(executor) && (!executor.areChangesRequired() || !isCommitEmpty())

  override fun checkCommit(sessionInfo: CommitSessionInfo): Boolean =
    super.checkCommit(sessionInfo) &&
    (
      getCommitMessage().isNotEmpty() ||
      ui.confirmCommitWithEmptyMessage()
    )

  override suspend fun updateWorkflow(sessionInfo: CommitSessionInfo): Boolean {
    if (!addUnversionedFiles(sessionInfo)) return false

    refreshLocalChanges()

    workflow.commitState = getCommitState()
    return configureCommitSession(project, sessionInfo,
                                  workflow.commitState.changes,
                                  workflow.commitState.commitMessage)
  }

  private suspend fun addUnversionedFiles(sessionInfo: CommitSessionInfo): Boolean {
    if (sessionInfo.isVcsCommit) {
      return withModalProgressIndicator(project, VcsBundle.message("progress.title.adding.files.to.vcs")) {
        withContext(Dispatchers.EDT) {
          addUnversionedFiles(project, getIncludedUnversionedFiles(), getChangeList(), ui.getInclusionModel())
        }
      }
    }
    return true
  }

  private suspend fun refreshLocalChanges() {
    withModalProgressIndicator(project, VcsBundle.message("commit.progress.title")) {
      ChangeListManagerEx.getInstanceEx(project).promiseWaitForUpdate().await()
      withContext(Dispatchers.EDT) {
        ui.refreshDataBeforeCommit()
      }
    }
  }

  override fun saveCommitMessageBeforeCommit() {
    commitMessagePolicy.onDialogClosed(getCommitState(), true)
  }

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

  private inner class CommitCustomListener : CommitterResultHandler {
    override fun onSuccess() = ui.deactivate()
  }
}