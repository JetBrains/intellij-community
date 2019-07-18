// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.ChangesUtil.getAffectedVcses
import com.intellij.openapi.vcs.changes.ChangesUtil.getAffectedVcsesForFiles
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

    ui.addStateListener(this, this)
    ui.addExecutorListener(this, this)
    ui.addDataProvider(createDataProvider())
    ui.addChangeListListener(this, this)
  }

  override fun vcsesChanged() = Unit

  fun activate(): Boolean {
    initCommitHandlers()

    ui.addInclusionListener(this, this)
    ui.defaultCommitActionName = getDefaultCommitActionName(workflow.vcses)
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
    if (isDefaultCommit && result == CheckinHandler.ReturnResult.COMMIT) ui.deactivate()
  }

  override fun customCommitSucceeded() = ui.deactivate()

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
    val unversionedFiles = ChangeListManagerImpl.getInstanceImpl(project).unversionedFiles
    val vcses = getAffectedVcses(getChangeList().changes, project) + getAffectedVcsesForFiles(unversionedFiles, project)

    ui.commitOptionsUi.setVisible(vcses)
  }
}