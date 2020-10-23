// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.containers.DisposableWrapperList
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.vcs.commit.CommitProgressPanel
import com.intellij.vcs.commit.CommitProgressUi
import com.intellij.vcs.commit.EditedCommitDetails
import com.intellij.vcs.commit.NonModalCommitPanel
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

  private val progressPanel = CommitProgressPanel()

  private var staged: Set<GitFileStatus> = emptySet()
  private val stagedChanges = ClearableLazyValue.create { state.rootStates.values.flatMap { it.getStagedChanges(project) } }

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

  override val commitProgressUi: CommitProgressUi get() = progressPanel

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

  override fun showCommitOptions(popup: JBPopup, isFromToolbar: Boolean, dataContext: DataContext) =
    if (isFromToolbar) popup.showAbove(toolbar.component) else popup.showInBestPositionFor(dataContext)
}
