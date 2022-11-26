// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsDataKeys.COMMIT_WORKFLOW_HANDLER
import com.intellij.openapi.vcs.changes.*
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.CollectionFactory
import java.util.*
import kotlin.properties.Delegates.observable

private fun Collection<Change>.toPartialAwareSet() =
  CollectionFactory.createCustomHashingStrategySet(ChangeListChange.HASHING_STRATEGY)
    .also { it.addAll(this) }

internal class ChangesViewCommitWorkflowHandler(
  override val workflow: ChangesViewCommitWorkflow,
  override val ui: ChangesViewCommitWorkflowUi
) : NonModalCommitWorkflowHandler<ChangesViewCommitWorkflow, ChangesViewCommitWorkflowUi>(),
    CommitAuthorListener,
    ProjectCloseListener {

  override val commitPanel: CheckinProjectPanel = CommitProjectPanelAdapter(this)
  override val amendCommitHandler: NonModalAmendCommitHandler = NonModalAmendCommitHandler(this)
  override val commitAuthorTracker: CommitAuthorTracker get() = ui

  private fun getCommitState(): ChangeListCommitState {
    val changes = getIncludedChanges()
    val changeList = workflow.getAffectedChangeList(changes)
    return ChangeListCommitState(changeList, changes, getCommitMessage())
  }

  private val activityEventDispatcher = EventDispatcher.create(ActivityListener::class.java)

  private val changeListManager = ChangeListManagerEx.getInstanceEx(project)
  private var knownActiveChanges: Collection<Change> = emptyList()

  private val inclusionModel = PartialCommitInclusionModel(project)

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
    Disposer.register(this, ui)

    workflow.addListener(this, this)
    workflow.addVcsCommitListener(GitCommitStateCleaner(), this)
    workflow.addVcsCommitListener(PostCommitChecksRunner(), this)

    ui.addCommitAuthorListener(this, this)
    ui.addExecutorListener(this, this)
    ui.addDataProvider(createDataProvider())
    ui.addInclusionListener(this, this)
    ui.inclusionModel = inclusionModel
    Disposer.register(inclusionModel, Disposable { ui.inclusionModel = null })
    ui.setCompletionContext(changeListManager.changeLists)

    setupDumbModeTracking()
    setupCommitHandlersTracking()
    setupCommitChecksResultTracking()

    vcsesChanged() // as currently vcses are set before handler subscribes to corresponding event
    currentChangeList = workflow.getAffectedChangeList(emptySet())

    if (isToggleMode()) deactivate(false)

    val busConnection = project.messageBus.connect(this)
    busConnection.subscribe(ProjectCloseListener.TOPIC, this)
    CommitModeManager.subscribeOnCommitModeChange(busConnection, object : CommitModeManager.CommitModeListener {
      override fun commitModeChanged() {
        if (isToggleMode()) {
          deactivate(false)
        }
        else {
          activate()
        }
      }
    })

    DelayedCommitMessageProvider.init(project, ui, getCommitMessageFromPolicy(currentChangeList))
  }

  override fun createDataProvider(): DataProvider = object : DataProvider {
    private val superProvider = super@ChangesViewCommitWorkflowHandler.createDataProvider()

    override fun getData(dataId: String): Any? =
      if (COMMIT_WORKFLOW_HANDLER.`is`(dataId)) this@ChangesViewCommitWorkflowHandler.takeIf { it.isActive }
      else superProvider.getData(dataId)
  }

  override fun commitOptionsCreated() {
    currentChangeList?.let { commitOptions.changeListChanged(it) }
  }

  override fun executionEnded() {
    super.executionEnded()
    ui.endExecution()
  }

  fun synchronizeInclusion(changeLists: List<LocalChangeList>, unversionedFiles: List<FilePath>) {
    if (!inclusionModel.isInclusionEmpty()) {
      val possibleInclusion = CollectionFactory.createCustomHashingStrategySet(ChangeListChange.HASHING_STRATEGY)
      possibleInclusion.addAll(changeLists.asSequence().flatMap { it.changes })
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
    setSelection(changeList)
  }

  private fun setInclusion(items: Collection<Any>, force: Boolean) {
    val activeChanges = changeListManager.defaultChangeList.changes

    if (!isActive || force) {
      inclusionModel.clearInclusion()
      inclusionModel.addInclusion(items)

      knownActiveChanges = if (!isActive) activeChanges else emptyList()
    }
    else {
      // skip if we have inclusion from not active change lists
      if ((inclusionModel.getInclusion() - activeChanges.toPartialAwareSet()).filterIsInstance<Change>().isNotEmpty()) return

      // we have inclusion in active change list and/or unversioned files => include new active changes if any
      val newChanges = activeChanges - knownActiveChanges
      inclusionModel.addInclusion(newChanges)

      // include all active changes if nothing is included
      if (inclusionModel.isInclusionEmpty()) inclusionModel.addInclusion(activeChanges)
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
  fun deactivate(isRestoreState: Boolean) {
    fireActivityStateChanged { ui.deactivate(isRestoreState) }
    if (isToggleMode()) {
      resetCommitChecksResult()
      ui.commitProgressUi.clearCommitCheckFailures()
    }
  }

  fun addActivityListener(listener: ActivityListener) = activityEventDispatcher.addListener(listener)

  private fun <T> fireActivityStateChanged(block: () -> T): T {
    val oldValue = isActive
    return block().also { if (oldValue != isActive) activityEventDispatcher.multicaster.activityStateChanged() }
  }

  private fun changeListChanged(oldChangeList: LocalChangeList?, newChangeList: LocalChangeList?) {
    oldChangeList?.let { commitMessagePolicy.save(it, getCommitMessage(), false) }

    val newCommitMessage = getCommitMessageFromPolicy(newChangeList)
    setCommitMessage(newCommitMessage)

    newChangeList?.let { commitOptions.changeListChanged(it) }
  }

  private fun getCommitMessageFromPolicy(changeList: LocalChangeList?): String? {
    if (changeList == null) return null

    return commitMessagePolicy.getCommitMessage(changeList) { getIncludedChanges() }
  }

  private fun changeListDataChanged() {
    ui.commitAuthor = currentChangeList?.author
    ui.commitAuthorDate = currentChangeList?.authorDate
  }

  override fun commitAuthorChanged() {
    val changeList = changeListManager.getChangeList(currentChangeList?.id) ?: return
    if (ui.commitAuthor == changeList.author) return

    changeListManager.editChangeListData(changeList.name, ChangeListData.of(ui.commitAuthor, ui.commitAuthorDate))
  }

  override fun commitAuthorDateChanged() {
    val changeList = changeListManager.getChangeList(currentChangeList?.id) ?: return
    if (ui.commitAuthorDate == changeList.authorDate) return

    changeListManager.editChangeListData(changeList.name, ChangeListData.of(ui.commitAuthor, ui.commitAuthorDate))
  }

  override fun inclusionChanged() {
    val inclusion = inclusionModel.getInclusion()
    val activeChanges = changeListManager.defaultChangeList.changes
    val includedActiveChanges = activeChanges.filter { it in inclusion }

    // ensure all included active changes are known => if user explicitly checks and unchecks some change, we know it is unchecked
    knownActiveChanges = knownActiveChanges.union(includedActiveChanges)

    currentChangeList = workflow.getAffectedChangeList(inclusion.filterIsInstance<Change>())
    super.inclusionChanged()
  }

  override fun beforeCommitChecksEnded(sessionInfo: CommitSessionInfo, result: CommitChecksResult) {
    super.beforeCommitChecksEnded(sessionInfo, result)
    if (result.shouldCommit) {
      // commit message could be changed during before-commit checks - ensure updated commit message is used for commit
      workflow.commitState = workflow.commitState.copy(getCommitMessage())

      if (isToggleMode()) deactivate(true)
    }
  }

  private fun isToggleMode(): Boolean {
    val commitMode = CommitModeManager.getInstance(project).getCurrentCommitMode()
    return commitMode is CommitMode.NonModalCommitMode && commitMode.isToggleMode
  }

  override fun updateWorkflow(sessionInfo: CommitSessionInfo): Boolean {
    workflow.commitState = getCommitState()
    return configureCommitSession(project, sessionInfo,
                                  workflow.commitState.changes,
                                  workflow.commitState.commitMessage)
  }

  override fun prepareForCommitExecution(sessionInfo: CommitSessionInfo): Boolean {
    if (sessionInfo.isVcsCommit) {
      val changeList = workflow.getAffectedChangeList(getIncludedChanges())
      if (!addUnversionedFiles(project, getIncludedUnversionedFiles(), changeList, inclusionModel)) return false
    }
    return super.prepareForCommitExecution(sessionInfo)
  }

  override fun saveCommitMessage(success: Boolean) = commitMessagePolicy.save(currentChangeList, getCommitMessage(), success)

  // save state on project close
  // using this method ensures change list comment and commit options are updated before project state persisting
  override fun projectClosingBeforeSave(project: Project) {
    saveStateBeforeDispose()
    disposeCommitOptions()
    currentChangeList = null
  }

  // save state on other events - like "settings changed to use commit dialog"
  override fun dispose() {
    saveStateBeforeDispose()
    disposeCommitOptions()

    super.dispose()
  }

  private fun saveStateBeforeDispose() {
    commitOptions.saveState()
    saveCommitMessage(false)
  }

  interface ActivityListener : EventListener {
    fun activityStateChanged()
  }

  private inner class GitCommitStateCleaner : CommitStateCleaner() {

    override fun onSuccess() {
      setCommitMessage(getCommitMessageFromPolicy(currentChangeList))

      super.onSuccess()
    }
  }
}
