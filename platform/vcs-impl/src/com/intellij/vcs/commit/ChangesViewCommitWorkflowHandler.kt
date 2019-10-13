// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsDataKeys.COMMIT_WORKFLOW_HANDLER
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.actions.DefaultCommitExecutorAction
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.util.EventDispatcher
import com.intellij.vcs.commit.AbstractCommitWorkflow.Companion.getCommitExecutors
import gnu.trove.THashSet
import java.util.*
import kotlin.properties.Delegates.observable

private fun Collection<Change>.toPartialAwareSet() = THashSet(this, ChangeListChange.HASHING_STRATEGY)

class ChangesViewCommitWorkflowHandler(
  override val workflow: ChangesViewCommitWorkflow,
  override val ui: ChangesViewCommitWorkflowUi
) : AbstractCommitWorkflowHandler<ChangesViewCommitWorkflow, ChangesViewCommitWorkflowUi>() {

  override val commitPanel: CheckinProjectPanel = CommitProjectPanelAdapter(this)
  override val amendCommitHandler: AmendCommitHandler = AmendCommitHandlerImpl(this)

  private fun getCommitState() = CommitState(getIncludedChanges(), getCommitMessage())

  private val activityEventDispatcher = EventDispatcher.create(ActivityListener::class.java)

  private val changeListManager = ChangeListManager.getInstance(project)
  private var knownActiveChanges: Collection<Change> = emptyList()

  private val inclusionModel = PartialCommitInclusionModel(project)

  private var areCommitOptionsCreated = false
  private val commitMessagePolicy = ChangesViewCommitMessagePolicy(project)
  private var currentChangeList by observable<LocalChangeList?>(null) { _, oldValue, newValue ->
    if (oldValue != newValue) changeListChanged(oldValue, newValue)
  }

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

  override fun createDataProvider(): DataProvider = object : DataProvider {
    private val superProvider = super@ChangesViewCommitWorkflowHandler.createDataProvider()

    override fun getData(dataId: String): Any? =
      if (COMMIT_WORKFLOW_HANDLER.`is`(dataId)) this@ChangesViewCommitWorkflowHandler.takeIf { it.isActive }
      else superProvider.getData(dataId)
  }

  private fun ensureCommitOptions(): CommitOptions {
    if (!areCommitOptionsCreated) {
      areCommitOptionsCreated = true

      workflow.initCommitOptions(createCommitOptions())
      commitOptions.restoreState()

      currentChangeList?.let { commitOptions.changeListChanged(it) }
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

    currentChangeList = workflow.getAffectedChangeList(inclusion.filterIsInstance<Change>())
  }

  private fun setInclusion(items: Collection<Any>, force: Boolean) {
    if (force) {
      inclusionModel.clearInclusion()
      ui.includeIntoCommit(items)

      knownActiveChanges = emptyList()
    }
    else {
      val activeChanges = changeListManager.defaultChangeList.changes

      // clear inclusion from not active change lists
      val inclusionFromNotActiveLists = (inclusionModel.getInclusion() - activeChanges.toPartialAwareSet()).filterIsInstance<Change>()
      inclusionModel.removeInclusion(inclusionFromNotActiveLists)

      // we have inclusion in active change list and/or unversioned files => include new active changes if any
      val newChanges = activeChanges - knownActiveChanges
      ui.includeIntoCommit(newChanges)

      // include all active changes if nothing is included
      if (inclusionModel.isInclusionEmpty()) ui.includeIntoCommit(activeChanges)
    }
  }

  val isActive: Boolean get() = ui.isActive
  fun activate(): Boolean = fireActivityStateChanged { ui.activate() }
  fun deactivate() = fireActivityStateChanged { ui.deactivate() }

  fun addActivityListener(listener: ActivityListener, parent: Disposable) = activityEventDispatcher.addListener(listener, parent)

  private fun <T> fireActivityStateChanged(block: () -> T): T {
    val oldValue = isActive
    return block().also { if (oldValue != isActive) activityEventDispatcher.multicaster.activityStateChanged() }
  }

  fun showCommitOptions(isFromToolbar: Boolean, dataContext: DataContext) =
    ui.showCommitOptions(ensureCommitOptions(), getCommitActionName(), isFromToolbar, dataContext)

  private fun changeListChanged(oldChangeList: LocalChangeList?, newChangeList: LocalChangeList?) {
    oldChangeList?.let { commitMessagePolicy.save(it, getCommitMessage(), false) }

    val newCommitMessage = newChangeList?.let { commitMessagePolicy.getCommitMessage(it) { getIncludedChanges() } }
    setCommitMessage(newCommitMessage)

    newChangeList?.let { commitOptions.changeListChanged(it) }
  }

  override fun inclusionChanged() {
    val inclusion = inclusionModel.getInclusion()
    val activeChanges = changeListManager.defaultChangeList.changes
    val includedActiveChanges = activeChanges.filter { it in inclusion }

    // ensure all included active changes are known => if user explicitly checks and unchecks some change, we know it is unchecked
    knownActiveChanges = knownActiveChanges.union(includedActiveChanges)

    updateDefaultCommitActionEnabled()
    super.inclusionChanged()
  }

  override fun beforeCommitChecksEnded(isDefaultCommit: Boolean, result: CheckinHandler.ReturnResult) {
    super.beforeCommitChecksEnded(isDefaultCommit, result)
    if (isToggleCommitUi.asBoolean() && result == CheckinHandler.ReturnResult.COMMIT) ui.deactivate()
  }

  override fun updateWorkflow() {
    workflow.commitState = getCommitState()
  }

  override fun addUnversionedFiles(): Boolean = addUnversionedFiles(workflow.getAffectedChangeList(getIncludedChanges()))

  override fun saveCommitOptions(): Boolean {
    ensureCommitOptions()
    return super.saveCommitOptions()
  }

  override fun saveCommitMessage(success: Boolean) = commitMessagePolicy.save(currentChangeList, getCommitMessage(), success)

  interface ActivityListener : EventListener {
    fun activityStateChanged()
  }

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