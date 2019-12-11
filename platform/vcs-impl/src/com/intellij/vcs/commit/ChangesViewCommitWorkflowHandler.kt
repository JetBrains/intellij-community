// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.application.subscribe
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
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
) : AbstractCommitWorkflowHandler<ChangesViewCommitWorkflow, ChangesViewCommitWorkflowUi>(),
    ProjectManagerListener {

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
    if (oldValue?.id != newValue?.id) {
      changeListChanged(oldValue, newValue)
      changeListDataChanged()
    }
    else if (oldValue?.data != newValue?.data) {
      changeListDataChanged()
    }
  }

  init {
    Disposer.register(this, inclusionModel)
    Disposer.register(ui, this)

    workflow.addListener(this, this)
    workflow.addCommitListener(CommitListener(), this)

    ui.addExecutorListener(this, this)
    ui.addDataProvider(createDataProvider())
    ui.addInclusionListener(this, this)
    ui.inclusionModel = inclusionModel
    Disposer.register(inclusionModel, Disposable { ui.inclusionModel = null })

    ProjectManager.TOPIC.subscribe(this, this)

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
    currentChangeList = currentChangeList?.run { changeLists.find { it.id == id } }
  }

  fun setCommitState(changeList: LocalChangeList, items: Collection<Any>, force: Boolean) {
    setInclusion(items, force)
    setSelection(changeList)

    currentChangeList = workflow.getAffectedChangeList(inclusionModel.getInclusion().filterIsInstance<Change>())
  }

  private fun setInclusion(items: Collection<Any>, force: Boolean) {
    val activeChanges = changeListManager.defaultChangeList.changes

    if (!isActive || force) {
      inclusionModel.clearInclusion()
      ui.includeIntoCommit(items)

      knownActiveChanges = if (!isActive) activeChanges else emptyList()
    }
    else {
      // skip if we have inclusion from not active change lists
      if ((inclusionModel.getInclusion() - activeChanges.toPartialAwareSet()).filterIsInstance<Change>().isNotEmpty()) return

      // we have inclusion in active change list and/or unversioned files => include new active changes if any
      val newChanges = activeChanges - knownActiveChanges
      ui.includeIntoCommit(newChanges)

      // include all active changes if nothing is included
      if (inclusionModel.isInclusionEmpty()) ui.includeIntoCommit(activeChanges)
    }
  }

  private fun setSelection(changeList: LocalChangeList) {
    val inclusion = inclusionModel.getInclusion()
    val isChangeListFullyIncluded = changeList.changes.run { isNotEmpty() && all { it in inclusion } }

    if (isChangeListFullyIncluded) {
      ui.select(changeList)
      ui.expand(changeList)
    }
    else {
      ui.selectFirst(inclusion)
    }
  }

  val isActive: Boolean get() = ui.isActive
  fun activate(): Boolean = fireActivityStateChanged { ui.activate() }
  fun deactivate(isRestoreState: Boolean) = fireActivityStateChanged { ui.deactivate(isRestoreState) }

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

  private fun changeListDataChanged() {
    ui.commitAuthor = currentChangeList?.author
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
    if (isToggleCommitUi.asBoolean() && result == CheckinHandler.ReturnResult.COMMIT) deactivate(true)
  }

  override fun updateWorkflow() {
    workflow.commitState = getCommitState()
  }

  override fun addUnversionedFiles(): Boolean = addUnversionedFiles(workflow.getAffectedChangeList(getIncludedChanges()))

  override fun saveCommitOptions(): Boolean = saveCommitOptions(true)

  private fun saveCommitOptions(isEnsureOptionsCreated: Boolean): Boolean {
    if (isEnsureOptionsCreated) ensureCommitOptions()
    return super.saveCommitOptions()
  }

  override fun saveCommitMessage(success: Boolean) = commitMessagePolicy.save(currentChangeList, getCommitMessage(), success)

  // save state on project close
  // using this method ensures change list comment and commit options are updated before project state persisting
  override fun projectClosingBeforeSave(project: Project) = dispose()

  // save state on other events - like "settings changed to use commit dialog"
  override fun dispose() {
    saveStateBeforeDispose()
    disposeCommitOptions()
  }

  private fun saveStateBeforeDispose() {
    saveCommitOptions(false)
    saveCommitMessage(false)
    currentChangeList = null
  }

  private fun disposeCommitOptions() {
    workflow.disposeCommitOptions()
    areCommitOptionsCreated = false
  }

  interface ActivityListener : EventListener {
    fun activityStateChanged()
  }

  private inner class CommitListener : CommitResultHandler {
    override fun onSuccess(commitMessage: String) = resetState()
    override fun onCancel() = Unit
    override fun onFailure(errors: List<VcsException>) = resetState()

    private fun resetState() {
      disposeCommitOptions()

      workflow.clearCommitContext()
      initCommitHandlers()
    }
  }
}