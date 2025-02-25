// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.DisposableWrapperList
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.vcs.commit.CommitProgressPanel
import com.intellij.vcs.commit.EditedCommitPresentation
import com.intellij.vcs.commit.NonModalCommitPanel
import git4idea.i18n.GitBundle
import git4idea.index.ContentVersion
import git4idea.index.GitFileStatus
import git4idea.index.GitStageTracker
import git4idea.index.createChange
import kotlin.properties.Delegates.observable

private fun GitStageTracker.State.getStaged(): Set<GitFileStatus> =
  rootStates.values.flatMapTo(mutableSetOf()) { it.getStaged() }

private fun GitStageTracker.RootState.getStaged(): Set<GitFileStatus> =
  statuses.values.filterTo(mutableSetOf()) { it.getStagedStatus() != null }

private fun GitStageTracker.State.getChanged(): Set<GitFileStatus> =
  rootStates.values.flatMapTo(mutableSetOf()) { it.getChanged() }

private fun GitStageTracker.RootState.getChanged(): Set<GitFileStatus> =
  statuses.values.filterTo(mutableSetOf()) { it.isTracked() }

private fun GitStageTracker.RootState.getStagedChanges(project: Project): List<Change> =
  getStaged().mapNotNull { createChange(project, root, it, ContentVersion.HEAD, ContentVersion.STAGED) }

private fun GitStageTracker.RootState.getTrackedChanges(project: Project): List<Change> =
  getChanged().mapNotNull { createChange(project, root, it, ContentVersion.HEAD, ContentVersion.LOCAL) }

class GitStageCommitPanel(project: Project, private val settings: GitStageUiSettings) : NonModalCommitPanel(project) {
  private val progressPanel = GitStageCommitProgressPanel(project)
  override val commitProgressUi: GitStageCommitProgressPanel get() = progressPanel

  @Volatile
  private var state: InclusionState = InclusionState(emptySet(), GitStageTracker.State.EMPTY, settings.isCommitAllEnabled)

  val rootsToCommit get() = state.rootsToCommit
  val includedRoots get() = state.includedRoots
  val conflictedRoots get() = state.conflictedRoots

  val isCommitAll get() = state.isCommitAll

  private val editedCommitListeners = DisposableWrapperList<() -> Unit>()
  override var editedCommit: EditedCommitPresentation? by observable(null) { _, _, _ ->
    editedCommitListeners.forEach { it() }
  }

  init {
    Disposer.register(this, commitMessage)

    commitMessage.setChangesSupplier { state.changesToCommit }
    progressPanel.setup(this, commitMessage.editorField, empty())

    setProgressComponent(progressPanel)

    settings.addListener(object : GitStageUiSettingsListener {
      override fun settingsChanged() {
        setState(state.includedRoots, state.trackerState, settings.isCommitAllEnabled)
      }
    }, this)
  }

  fun setIncludedRoots(includedRoots: Collection<VirtualFile>) {
    setState(includedRoots.toSet(), state.trackerState, state.isCommitAllEnabled)
  }

  fun setTrackerState(trackerState: GitStageTracker.State) {
    setState(state.includedRoots, trackerState, state.isCommitAllEnabled)
  }

  private fun setState(includedRoots: Set<VirtualFile>, trackerState: GitStageTracker.State, isCommitAllEnabled: Boolean) {
    val newState = InclusionState(includedRoots, trackerState, isCommitAllEnabled)
    val inclusionChanged = newState.isInclusionChangedFrom(state)
    state = newState
    if (inclusionChanged) {
      fireInclusionChanged()
    }
  }

  fun addEditedCommitListener(listener: () -> Unit, parent: Disposable) {
    editedCommitListeners.add(listener, parent)
  }

  override fun activate(): Boolean = true

  override fun getDisplayedChanges(): List<Change> = emptyList()
  override fun getIncludedChanges(): List<Change> = state.stagedChanges
  override fun getDisplayedUnversionedFiles(): List<FilePath> = emptyList()
  override fun getIncludedUnversionedFiles(): List<FilePath> = emptyList()

  private inner class InclusionState(val includedRoots: Set<VirtualFile>,
                                     val trackerState: GitStageTracker.State,
                                     val isCommitAllEnabled: Boolean) {
    private val stagedStatuses: Set<GitFileStatus> = trackerState.getStaged()

    private val changedRoots: Set<VirtualFile> = trackerState.changedRoots
    val conflictedRoots: Set<VirtualFile> = trackerState.conflictedRoots

    private val includedRootStates
      get() = trackerState.rootStates.filterKeys { it in includedRoots }.values

    val stagedChanges by lazy {
      includedRootStates.flatMap { it.getStagedChanges(project) }
    }
    val trackedChanges: List<Change> by lazy {
      includedRootStates.flatMap { it.getTrackedChanges(project) }
    }
    val isCommitAll = isCommitAllEnabled && trackerState.stagedRoots.isEmpty() && trackerState.changedRoots.isNotEmpty()
    val changesToCommit: List<Change> get() = if (isCommitAll) trackedChanges else stagedChanges

    val rootsToCommit: Set<VirtualFile>
      get() {
        if (isCommitAll) {
          return trackerState.changedRoots.intersect(includedRoots)
        }
        return trackerState.stagedRoots.intersect(includedRoots)
      }

    fun isInclusionChangedFrom(other: InclusionState): Boolean {
      if (includedRoots != other.includedRoots || conflictedRoots != other.conflictedRoots) return true
      if (isCommitAll != other.isCommitAll) return true
      if (isCommitAll && changedRoots != other.changedRoots) return true
      return stagedStatuses != other.stagedStatuses
    }
  }
}

class GitStageCommitProgressPanel(project: Project) : CommitProgressPanel(project) {
  var isEmptyRoots by stateFlag()
  var isUnmerged by stateFlag()
  var isCommitAll by stateFlag()

  override fun clearError() {
    super.clearError()
    isEmptyRoots = false
    isUnmerged = false
  }

  override fun buildErrorText(): String? =
    when {
      isEmptyRoots -> GitBundle.message("error.no.selected.roots.to.commit")
      isUnmerged -> GitBundle.message("error.unresolved.conflicts")
      isEmptyChanges && isCommitAll && isEmptyMessage -> GitBundle.message("error.no.changed.files.no.commit.message")
      isEmptyChanges && isEmptyMessage -> GitBundle.message("error.no.staged.changes.no.commit.message")
      isEmptyChanges && isCommitAll -> GitBundle.message("error.no.changed.files.to.commit")
      isEmptyChanges -> GitBundle.message("error.no.staged.changes.to.commit")
      isEmptyMessage -> VcsBundle.message("error.no.commit.message")
      else -> null
    }
}
