// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.InputException
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsDataKeys.COMMIT_WORKFLOW_HANDLER
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ChangesUtil.getAffectedVcses
import com.intellij.openapi.vcs.changes.ChangesUtil.getAffectedVcsesForFiles
import com.intellij.openapi.vcs.changes.ui.DialogCommitWorkflow.Companion.getCommitHandlers
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vcs.ui.Refreshable
import com.intellij.util.PairConsumer

private val VCS_COMPARATOR = compareBy<AbstractVcs<*>, String>(String.CASE_INSENSITIVE_ORDER) { it.keyInstanceMethod.name }

private fun getDefaultCommitActionName(vcses: Collection<AbstractVcs<*>>) =
  vcses.mapNotNull { it.checkinEnvironment?.checkinOperationName }.distinct().singleOrNull()
  ?: VcsBundle.getString("commit.dialog.default.commit.operation.name")

class SingleChangeListCommitWorkflowHandler(
  internal val workflow: DialogCommitWorkflow,
  internal val ui: CommitChangeListDialog
) : CommitWorkflowHandler,
    CommitWorkflowListener,
    CommitWorkflowUiStateListener,
    SingleChangeListCommitWorkflowUi.ChangeListListener,
    InclusionListener,
    CommitExecutorListener,
    Disposable {

  private val project get() = workflow.project
  private val vcsConfiguration = VcsConfiguration.getInstance(project)

  private val commitPanel: CheckinProjectPanel = object : CommitProjectPanelAdapter(this) {
    override fun setCommitMessage(currentDescription: String?) {
      commitMessagePolicy.defaultNameChangeListMessage = currentDescription

      super.setCommitMessage(currentDescription)
    }
  }

  private fun getChangeList() = ui.getChangeList()
  private fun getIncludedChanges() = ui.getIncludedChanges()
  private fun getIncludedUnversionedFiles() = ui.getIncludedUnversionedFiles()

  private fun getCommitMessage() = ui.commitMessageUi.text
  private fun setCommitMessage(text: String?) = ui.commitMessageUi.setText(text)

  private val commitHandlers get() = workflow.commitHandlers
  private val commitMessagePolicy get() = workflow.commitMessagePolicy
  private val commitOptions get() = workflow.commitOptions

  init {
    Disposer.register(ui, this)

    workflow.addListener(this, this)

    ui.addStateListener(this, this)
    ui.addExecutorListener(this, this)
    ui.addDataProvider(DataProvider { dataId ->
      when (dataId) {
        COMMIT_WORKFLOW_HANDLER.name -> this
        Refreshable.PANEL_KEY.name -> commitPanel
        else -> null
      }
    })
    ui.addChangeListListener(this, this)
  }

  fun activate(): Boolean {
    workflow.initCommitHandlers(getCommitHandlers(commitPanel, workflow.commitContext))

    ui.addInclusionListener(this, this)
    ui.defaultCommitActionName = getDefaultCommitActionName(workflow.affectedVcses)
    initCommitMessage()
    initCommitOptions()

    return ui.activate()
  }

  override fun cancelled() {
    commitOptions.saveChangeListSpecificOptions()
    saveCommitMessage(false)

    LineStatusTrackerManager.getInstanceImpl(project).resetExcludedFromCommitMarkers()
  }

  override fun inclusionChanged() = commitHandlers.forEach { it.includedChangesChanged() }

  override fun changeListChanged() {
    updateCommitMessage()
    updateCommitOptions()
  }

  override fun executorCalled(executor: CommitExecutor?) = executor?.let { execute(it) } ?: executeDefault(null)

  override fun getExecutor(executorId: String): CommitExecutor? = workflow.executors.find { it.id == executorId }

  override fun isExecutorEnabled(executor: CommitExecutor): Boolean =
    ui.hasDiffs() || (executor is CommitExecutorBase && !executor.areChangesRequired())

  override fun execute(executor: CommitExecutor) {
    val session = executor.createCommitSession()

    if (session === CommitSession.VCS_COMMIT) {
      executeDefault(executor)
    }
    else {
      executeCustom(executor, session)
    }
  }

  override fun beforeCommitChecksStarted() = ui.startBeforeCommitChecks()
  override fun beforeCommitChecksEnded(result: CheckinHandler.ReturnResult) = ui.endBeforeCommitChecks(result)

  private fun executeDefault(executor: CommitExecutor?) {
    if (!addUnversionedFiles()) return
    if (!checkEmptyCommitMessage()) return
    if (!saveCommitOptions()) return
    saveCommitMessage(true)

    refreshChanges { doExecuteDefault(executor) }
  }

  private fun doExecuteDefault(executor: CommitExecutor?) = try {
    workflow.executeDefault(executor, getChangeList(), getIncludedChanges(), getCommitMessage())
  }
  catch (e: InputException) { // TODO Looks like this catch is unnecessary - check
    e.show()
  }

  private fun executeCustom(executor: CommitExecutor, session: CommitSession) {
    if (!workflow.canExecute(executor, getIncludedChanges())) return
    if (!checkEmptyCommitMessage()) return
    if (!saveCommitOptions()) return
    saveCommitMessage(true)

    ui.execute(executor, session)
  }

  private fun addUnversionedFiles(): Boolean =
    workflow.addUnversionedFiles(getChangeList(), getIncludedUnversionedFiles()) { changes -> ui.includeIntoCommit(changes) }

  private fun checkEmptyCommitMessage(): Boolean =
    getCommitMessage().isNotEmpty() || !vcsConfiguration.FORCE_NON_EMPTY_COMMENT || ui.confirmCommitWithEmptyMessage()

  private fun initCommitMessage() {
    commitMessagePolicy.init(getChangeList(), getIncludedChanges())
    setCommitMessage(commitMessagePolicy.commitMessage)
  }

  private fun updateCommitMessage() {
    commitMessagePolicy.update(getChangeList(), getCommitMessage())
    setCommitMessage(commitMessagePolicy.commitMessage)
  }

  private fun saveCommitMessage(success: Boolean) =
    commitMessagePolicy.save(getChangeList(), getIncludedChanges(), getCommitMessage(), success)

  private fun initCommitOptions() {
    workflow.initCommitOptions(CommitOptionsImpl(
      if (workflow.isDefaultCommitEnabled) getVcsOptions(commitPanel, workflow.affectedVcses, workflow.additionalDataConsumer)
      else emptyMap(),
      getBeforeOptions(workflow.commitHandlers),
      getAfterOptions(workflow.commitHandlers, this)
    ))
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

  private fun saveCommitOptions() = try {
    commitOptions.saveState()
    true
  }
  catch (ex: InputException) {
    ex.show()
    false
  }

  private fun getVcsOptions(commitPanel: CheckinProjectPanel, vcses: Collection<AbstractVcs<*>>, additionalData: PairConsumer<Any, Any>) =
    vcses.sortedWith(VCS_COMPARATOR)
      .associateWith { it.checkinEnvironment?.createAdditionalOptionsPanel(commitPanel, additionalData) }
      .filterValues { it != null }
      .mapValues { it.value!! }

  private fun getBeforeOptions(handlers: Collection<CheckinHandler>) = handlers.mapNotNull { it.beforeCheckinConfigurationPanel }

  private fun getAfterOptions(handlers: Collection<CheckinHandler>, parent: Disposable) =
    handlers.mapNotNull { it.getAfterCheckinConfigurationPanel(parent) }

  private fun refreshChanges(callback: () -> Unit) =
    ChangeListManager.getInstance(project).invokeAfterUpdate(
      {
        ui.refreshData()
        callback()
      },
      InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE, "Commit", ModalityState.current()
    )

  override fun dispose() = Unit
}