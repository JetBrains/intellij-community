// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
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
import kotlin.properties.Delegates.observable

private fun GitStageTracker.State.getStaged(): Set<GitFileStatus> =
  rootStates.values.flatMapTo(mutableSetOf()) { it.getStaged() }

private fun GitStageTracker.RootState.getStaged(): Set<GitFileStatus> =
  statuses.values.filterTo(mutableSetOf()) { it.getStagedStatus() != null }

private fun GitStageTracker.RootState.getStagedChanges(project: Project): List<Change> =
  getStaged().mapNotNull { createChange(project, root, it, ContentVersion.HEAD, ContentVersion.STAGED) }

class GitStageCommitPanel(project: Project) : NonModalCommitPanel(project) {
  private val editedCommitListeners = DisposableWrapperList<() -> Unit>()

  private val progressPanel = GitStageCommitProgressPanel()

  var includedRoots: Collection<VirtualFile> by observable(emptySet()) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      stagedChanges.drop()
      fireInclusionChanged()
    }
  }
  val rootsToCommit get() = state.stagedRoots.intersect(includedRoots)

  private var staged: Set<GitFileStatus> = emptySet()
  private val stagedChanges = ClearableLazyValue.create {
    state.rootStates.filterKeys {
      includedRoots.contains(it)
    }.values.flatMap { it.getStagedChanges(project) }
  }

  var state: GitStageTracker.State by observable(GitStageTracker.State.EMPTY) { _, _, newValue ->
    val newStaged = newValue.getStaged()
    if (staged == newStaged) return@observable

    staged = newStaged
    stagedChanges.drop()
    fireInclusionChanged()
  }

  init {
    Disposer.register(this, commitMessage)

    progressPanel.setup(this, commitMessage.editorField)
    bottomPanel = {
      add(progressPanel.apply { border = empty(6) })
      add(commitAuthorComponent.apply { border = empty(0, 5, 4, 0) })
      add(commitActionsPanel)
    }
    buildLayout()
  }

  override val commitProgressUi: GitStageCommitProgressPanel get() = progressPanel

  override var editedCommit: EditedCommitDetails? by observable(null) { _, _, _ ->
    editedCommitListeners.forEach { it() }
  }

  fun addEditedCommitListener(listener: () -> Unit, parent: Disposable) {
    editedCommitListeners.add(listener, parent)
  }

  override fun activate(): Boolean = true
  override fun refreshData() = Unit

  override fun getDisplayedChanges(): List<Change> = emptyList()
  override fun getIncludedChanges(): List<Change> = stagedChanges.value
  override fun getDisplayedUnversionedFiles(): List<FilePath> = emptyList()
  override fun getIncludedUnversionedFiles(): List<FilePath> = emptyList()

  override fun includeIntoCommit(items: Collection<*>) = Unit
}

class GitStageCommitProgressPanel : CommitProgressPanel() {
  var isEmptyRoots: Boolean by observable(false) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable
    update()
  }

  override fun clearError() {
    super.clearError()
    isEmptyRoots = false
  }

  override fun buildErrorText(): String? =
    when {
      isEmptyRoots -> GitBundle.message("error.no.selected.roots.to.commit")
      isEmptyChanges && isEmptyMessage -> GitBundle.message("error.no.staged.changes.no.commit.message")
      isEmptyChanges -> GitBundle.message("error.no.staged.changes.to.commit")
      isEmptyMessage -> VcsBundle.message("error.no.commit.message")
      else -> null
    }
}
