// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.actions.DefaultCommitExecutorAction
import com.intellij.vcs.commit.AbstractCommitWorkflow.Companion.getCommitExecutors

class ChangesViewCommitWorkflowHandler(
  override val workflow: ChangesViewCommitWorkflow,
  override val ui: ChangesViewCommitWorkflowUi
) : AbstractCommitWorkflowHandler<ChangesViewCommitWorkflow, ChangesViewCommitWorkflowUi>() {

  private val changeListManager = ChangeListManager.getInstance(project)

  override val commitPanel: CheckinProjectPanel = CommitProjectPanelAdapter(this)
  override val amendCommitHandler: AmendCommitHandler = AmendCommitHandlerImpl(this)

  private fun getCommitState() = CommitState(getIncludedChanges(), getCommitMessage())

  init {
    Disposer.register(ui, this)

    workflow.addListener(this, this)

    ui.addExecutorListener(this, this)
    ui.addDataProvider(createDataProvider())
    ui.addInclusionListener(this, this)

    updateDefaultCommitAction()
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

  fun setCommitState(items: Collection<*>, forceIfNotEmpty: Boolean) {
    if (forceIfNotEmpty || ui.isInclusionEmpty()) {
      ui.clearInclusion()
      ui.includeIntoCommit(items)
    }
  }

  fun activate(): Boolean = ui.activate()

  fun showCommitOptions(isFromToolbar: Boolean, dataContext: DataContext) =
    ui.showCommitOptions(ensureCommitOptions(), isFromToolbar, dataContext)

  override fun inclusionChanged() {
    ui.isDefaultCommitActionEnabled = isDefaultCommitEnabled()
    super.inclusionChanged()
  }

  override fun updateWorkflow() {
    workflow.commitState = getCommitState()
  }

  override fun addUnversionedFiles(): Boolean = addUnversionedFiles(workflow.getChangeListFor(getIncludedChanges().firstOrNull()))

  override fun saveCommitOptions(): Boolean {
    ensureCommitOptions()
    return super.saveCommitOptions()
  }

  override fun saveCommitMessage(success: Boolean) = VcsConfiguration.getInstance(project).saveCommitMessage(getCommitMessage())

  override fun customCommitSucceeded() = Unit
}