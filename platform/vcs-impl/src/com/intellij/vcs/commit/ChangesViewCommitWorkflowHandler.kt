// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.actions.DefaultCommitExecutorAction
import com.intellij.vcs.commit.AbstractCommitWorkflow.Companion.getCommitExecutors
import gnu.trove.THashSet

private fun Collection<Change>.toPartialAwareSet() = THashSet(this, ChangeListChange.HASHING_STRATEGY)

class ChangesViewCommitWorkflowHandler(
  override val workflow: ChangesViewCommitWorkflow,
  override val ui: ChangesViewCommitWorkflowUi
) : AbstractCommitWorkflowHandler<ChangesViewCommitWorkflow, ChangesViewCommitWorkflowUi>() {

  override val commitPanel: CheckinProjectPanel = CommitProjectPanelAdapter(this)
  override val amendCommitHandler: AmendCommitHandler = AmendCommitHandlerImpl(this)

  private fun getCommitState() = CommitState(getIncludedChanges(), getCommitMessage())

  private val changeListManager = ChangeListManager.getInstance(project)
  private var knownActiveChanges: Collection<Change> = emptyList()

  private val inclusionModel = PartialCommitInclusionModel(project)

  private var areCommitOptionsCreated = false

  init {
    Disposer.register(this, Disposable { workflow.disposeCommitOptions() })
    Disposer.register(this, inclusionModel)
    Disposer.register(ui, this)

    workflow.addListener(this, this)
    workflow.addCommitListener(CommitListener(), this)

    ui.addExecutorListener(this, this)
    ui.addDataProvider(createDataProvider())
    ui.addInclusionListener(this, this)
    ui.inclusionModel = inclusionModel
    Disposer.register(inclusionModel, Disposable { ui.inclusionModel = null })

    vcsesChanged() // as currently vcses are set before handler subscribes to corresponding event
  }

  private fun ensureCommitOptions(): CommitOptions {
    if (!areCommitOptionsCreated) {
      areCommitOptionsCreated = true

      workflow.initCommitOptions(createCommitOptions())
      commitOptions.restoreState()
    }
    return commitOptions
  }

  private fun isDefaultCommitEnabled() = workflow.vcses.isNotEmpty() && !workflow.isExecuting && !isCommitEmpty()

  override fun vcsesChanged() {
    initCommitHandlers()
    workflow.initCommitExecutors(getCommitExecutors(project, workflow.vcses))

    updateDefaultCommitActionEnabled()
    ui.defaultCommitActionName = getCommitActionName()
    ui.setCustomCommitActions(createCommitExecutorActions())
  }

  override fun executionStarted() = updateDefaultCommitActionEnabled()
  override fun executionEnded() {
    // Local Changes tree is not yet updated here. So calling `updateDefaultCommitActionEnabled()` leads to button blinking.
    // Next `inclusionChanged()` (likely because of `synchronizeInclusion()` after committed changes refresh) will set correct button
    // state without blinking.
  }

  private fun updateDefaultCommitActionEnabled() {
    ui.isDefaultCommitActionEnabled = isDefaultCommitEnabled()
  }

  private fun createCommitExecutorActions(): List<AnAction> {
    val executors = workflow.commitExecutors.ifEmpty { return emptyList() }
    val group = ActionManager.getInstance().getAction("Vcs.CommitExecutor.Actions") as ActionGroup

    return group.getChildren(null).toList() + executors.filter { it.useDefaultAction() }.map { DefaultCommitExecutorAction(it) }
  }

  fun synchronizeInclusion(changeLists: List<LocalChangeList>, unversionedFiles: List<FilePath>) {
    if (!inclusionModel.isInclusionEmpty()) {
      val possibleInclusion = changeLists.flatMapTo(THashSet(ChangeListChange.HASHING_STRATEGY)) { it.changes }
      possibleInclusion.addAll(unversionedFiles)

      inclusionModel.retainInclusion(possibleInclusion)
    }

    if (knownActiveChanges.isNotEmpty()) {
      val activeChanges = changeListManager.defaultChangeList.changes
      knownActiveChanges = knownActiveChanges.intersect(activeChanges)
    }

    inclusionModel.changeLists = changeLists
    ui.setCompletionContext(changeLists)
  }

  fun setCommitState(changeList: LocalChangeList, items: Collection<Any>, force: Boolean) {
    setInclusion(items, force)

    val inclusion = inclusionModel.getInclusion()
    val isChangeListFullyIncluded = changeList.changes.run { isNotEmpty() && all { it in inclusion } }
    if (isChangeListFullyIncluded) ui.select(changeList) else ui.selectFirst(inclusion)
  }

  private fun setInclusion(items: Collection<Any>, force: Boolean) {
    val activeChanges = changeListManager.defaultChangeList.changes

    if (force || inclusionModel.isInclusionEmpty()) {
      inclusionModel.clearInclusion()
      ui.includeIntoCommit(items)

      // update known active changes on "Commit File"
      if (force) knownActiveChanges = activeChanges
    }
    else {
      // skip if we have inclusion from other change lists
      if ((inclusionModel.getInclusion() - activeChanges.toPartialAwareSet()).filterIsInstance<Change>().isNotEmpty()) return

      // we have inclusion in active change list and/or unversioned files => include new active changes if any
      val newChanges = activeChanges - knownActiveChanges
      ui.includeIntoCommit(newChanges)
    }
  }

  fun activate(): Boolean = ui.activate()

  fun showCommitOptions(isFromToolbar: Boolean, dataContext: DataContext) =
    ui.showCommitOptions(ensureCommitOptions(), getCommitActionName(), isFromToolbar, dataContext)

  override fun inclusionChanged() {
    val inclusion = inclusionModel.getInclusion()
    val activeChanges = changeListManager.defaultChangeList.changes
    val includedActiveChanges = activeChanges.filter { it in inclusion }

    // if something new is included => consider it as "user defined state for all active changes"
    if (!knownActiveChanges.containsAll(includedActiveChanges)) {
      knownActiveChanges = activeChanges
    }

    updateDefaultCommitActionEnabled()
    super.inclusionChanged()
  }

  override fun updateWorkflow() {
    workflow.commitState = getCommitState()
  }

  override fun addUnversionedFiles(): Boolean = addUnversionedFiles(workflow.getAffectedChangeList(getIncludedChanges()))

  override fun saveCommitOptions(): Boolean {
    ensureCommitOptions()
    return super.saveCommitOptions()
  }

  override fun saveCommitMessage(success: Boolean) = VcsConfiguration.getInstance(project).saveCommitMessage(getCommitMessage())

  private inner class CommitListener : CommitResultHandler {
    override fun onSuccess(commitMessage: String) = resetState()
    override fun onCancel() = Unit
    override fun onFailure(errors: List<VcsException>) = resetState()

    private fun resetState() {
      workflow.disposeCommitOptions()
      areCommitOptionsCreated = false

      workflow.clearCommitContext()
      initCommitHandlers()

      ui.defaultCommitActionName = getCommitActionName() // to remove "Amend" prefix if any
    }
  }
}