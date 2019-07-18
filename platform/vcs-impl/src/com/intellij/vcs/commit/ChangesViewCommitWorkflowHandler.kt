// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListChange
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.actions.DefaultCommitExecutorAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.commit.AbstractCommitWorkflow.Companion.getCommitExecutors
import gnu.trove.THashSet

class ChangesViewCommitWorkflowHandler(
  override val workflow: ChangesViewCommitWorkflow,
  override val ui: ChangesViewCommitWorkflowUi
) : AbstractCommitWorkflowHandler<ChangesViewCommitWorkflow, ChangesViewCommitWorkflowUi>() {

  override val commitPanel: CheckinProjectPanel = CommitProjectPanelAdapter(this)
  override val amendCommitHandler: AmendCommitHandler = AmendCommitHandlerImpl(this)

  private fun getCommitState() = CommitState(getIncludedChanges(), getCommitMessage())

  private val changeListManager = ChangeListManager.getInstance(project)
  private var knownActiveChanges: Collection<Change> = emptyList()

  init {
    Disposer.register(this, Disposable { workflow.disposeCommitOptions() })
    Disposer.register(ui, this)

    workflow.addListener(this, this)

    ui.addExecutorListener(this, this)
    ui.addDataProvider(createDataProvider())
    ui.addInclusionListener(this, this)

    vcsesChanged() // as currently vcses are set before handler subscribes to corresponding event
  }

  private fun ensureCommitOptions(): CommitOptions {
    if (!workflow.areCommitOptionsCreated) {
      workflow.areCommitOptionsCreated = true

      workflow.initCommitOptions(createCommitOptions())
      commitOptions.restoreState()
    }
    return commitOptions
  }

  private fun isDefaultCommitEnabled() = workflow.vcses.isNotEmpty() && !isCommitEmpty()

  override fun vcsesChanged() {
    updateDefaultCommitAction()

    initCommitHandlers()
    workflow.initCommitExecutors(getCommitExecutors(project, workflow.vcses))

    ui.setCustomCommitActions(createCommitExecutorActions())
  }

  private fun updateDefaultCommitAction() {
    ui.defaultCommitActionName = getDefaultCommitActionName(workflow.vcses)
    ui.isDefaultCommitActionEnabled = isDefaultCommitEnabled()
  }

  private fun createCommitExecutorActions(): List<AnAction> {
    val executors = workflow.commitExecutors.ifEmpty { return emptyList() }
    val group = ActionManager.getInstance().getAction("Vcs.CommitExecutor.Actions") as ActionGroup

    return group.getChildren(null).toList() + executors.filter { it.useDefaultAction() }.map { DefaultCommitExecutorAction(it) }
  }

  fun synchronizeInclusion(changeLists: List<LocalChangeList>, unversionedFiles: List<VirtualFile>) {
    if (!ui.isInclusionEmpty()) {
      val possibleInclusion = changeLists.flatMapTo(THashSet(ChangeListChange.HASHING_STRATEGY)) { it.changes }
      possibleInclusion.addAll(unversionedFiles)

      ui.retainInclusion(possibleInclusion)
    }

    if (knownActiveChanges.isNotEmpty()) {
      val activeChanges = changeListManager.defaultChangeList.changes
      knownActiveChanges = knownActiveChanges.intersect(activeChanges)
    }
  }

  fun setCommitState(items: Collection<Any>, force: Boolean) {
    val activeChanges = changeListManager.defaultChangeList.changes

    if (force || ui.isInclusionEmpty()) {
      ui.clearInclusion()
      ui.includeIntoCommit(items)

      // update known active changes on "Commit File"
      if (force) knownActiveChanges = activeChanges
    }
    else {
      // skip if we have inclusion from other change lists
      if ((ui.getInclusion() - activeChanges).filterIsInstance<Change>().isNotEmpty()) return

      // we have inclusion in active change list and/or unversioned files => include new active changes if any
      val newChanges = activeChanges - knownActiveChanges
      ui.includeIntoCommit(newChanges)
    }
  }

  fun activate(): Boolean = ui.activate()

  fun showCommitOptions(isFromToolbar: Boolean, dataContext: DataContext) =
    ui.showCommitOptions(ensureCommitOptions(), isFromToolbar, dataContext)

  override fun inclusionChanged() {
    val inclusion = ui.getInclusion()
    val activeChanges = changeListManager.defaultChangeList.changes
    val includedActiveChanges = activeChanges.filter { it in inclusion }

    // if something new is included => consider it as "user defined state for all active changes"
    if (!knownActiveChanges.containsAll(includedActiveChanges)) {
      knownActiveChanges = activeChanges
    }

    ui.isDefaultCommitActionEnabled = isDefaultCommitEnabled()
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

  override fun customCommitSucceeded() = Unit
}