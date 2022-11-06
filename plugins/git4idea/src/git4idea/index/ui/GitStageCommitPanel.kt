// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.vcs.commit.EditedCommitDetails
import com.intellij.vcs.commit.NonModalCommitPanel
import git4idea.i18n.GitBundle
import git4idea.index.ContentVersion
import git4idea.index.GitFileStatus
import git4idea.index.GitStageTracker
import git4idea.index.createChange
import org.jetbrains.concurrency.resolvedPromise
import kotlin.properties.Delegates.observable

private fun GitStageTracker.State.getStaged(): Set<GitFileStatus> =
  rootStates.values.flatMapTo(mutableSetOf()) { it.getStaged() }

private fun GitStageTracker.RootState.getStaged(): Set<GitFileStatus> =
  statuses.values.filterTo(mutableSetOf()) { it.getStagedStatus() != null }

private fun GitStageTracker.RootState.getStagedChanges(project: Project): List<Change> =
  getStaged().mapNotNull { createChange(project, root, it, ContentVersion.HEAD, ContentVersion.STAGED) }

class GitStageCommitPanel(project: Project) : NonModalCommitPanel(project) {
  private val progressPanel = GitStageCommitProgressPanel()
  override val commitProgressUi: GitStageCommitProgressPanel get() = progressPanel

  @Volatile
  private var state: InclusionState = InclusionState(emptySet(), GitStageTracker.State.EMPTY)

  val rootsToCommit get() = state.rootsToCommit
  val includedRoots get() = state.includedRoots
  val conflictedRoots get() = state.conflictedRoots

  private val editedCommitListeners = DisposableWrapperList<() -> Unit>()
  override var editedCommit: EditedCommitDetails? by observable(null) { _, _, _ ->
    editedCommitListeners.forEach { it() }
  }

  init {
    Disposer.register(this, commitMessage)

    commitMessage.setChangesSupplier { state.stagedChanges }
    progressPanel.setup(this, commitMessage.editorField, empty(6))

    bottomPanel.add(progressPanel.component)
    bottomPanel.add(commitAuthorComponent.apply { border = empty(0, 5, 4, 0) })
    bottomPanel.add(commitActionsPanel)
  }

  fun setIncludedRoots(includedRoots: Collection<VirtualFile>) {
    setState(includedRoots, state.trackerState)
  }

  fun setTrackerState(trackerState: GitStageTracker.State) {
    setState(state.includedRoots, trackerState)
  }

  private fun setState(includedRoots: Collection<VirtualFile>, trackerState: GitStageTracker.State) {
    val newState = InclusionState(includedRoots, trackerState)
    if (state != newState) {
      state = newState
      fireInclusionChanged()
    }
  }

  fun addEditedCommitListener(listener: () -> Unit, parent: Disposable) {
    editedCommitListeners.add(listener, parent)
  }

  override fun activate(): Boolean = true
  override fun refreshData() = resolvedPromise<Unit>()

  override fun getDisplayedChanges(): List<Change> = emptyList()
  override fun getIncludedChanges(): List<Change> = state.stagedChanges
  override fun getDisplayedUnversionedFiles(): List<FilePath> = emptyList()
  override fun getIncludedUnversionedFiles(): List<FilePath> = emptyList()

  private inner class InclusionState(val includedRoots: Collection<VirtualFile>, val trackerState: GitStageTracker.State) {
    private val stagedStatuses: Set<GitFileStatus> = trackerState.getStaged()
    val conflictedRoots: Set<VirtualFile> = trackerState.rootStates.filter { it.value.hasConflictedFiles() }.keys
    val stagedChanges by lazy {
      trackerState.rootStates.filterKeys {
        includedRoots.contains(it)
      }.values.flatMap { it.getStagedChanges(project) }
    }
    val rootsToCommit get() = trackerState.stagedRoots.intersect(includedRoots)

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as InclusionState

      if (includedRoots != other.includedRoots) return false
      if (stagedStatuses != other.stagedStatuses) return false
      if (conflictedRoots != other.conflictedRoots) return false

      return true
    }

    override fun hashCode(): Int {
      var result = includedRoots.hashCode()
      result = 31 * result + stagedStatuses.hashCode()
      result = 31 * result + conflictedRoots.hashCode()
      return result
    }
  }
}

class GitStageCommitProgressPanel : CommitProgressPanel() {
  var isEmptyRoots by stateFlag()
  var isUnmerged by stateFlag()

  override fun clearError() {
    super.clearError()
    isEmptyRoots = false
    isUnmerged = false
  }

  override fun buildErrorText(): String? =
    when {
      isEmptyRoots -> GitBundle.message("error.no.selected.roots.to.commit")
      isUnmerged -> GitBundle.message("error.unresolved.conflicts")
      isEmptyChanges && isEmptyMessage -> GitBundle.message("error.no.staged.changes.no.commit.message")
      isEmptyChanges -> GitBundle.message("error.no.staged.changes.to.commit")
      isEmptyMessage -> VcsBundle.message("error.no.commit.message")
      else -> null
    }
}
