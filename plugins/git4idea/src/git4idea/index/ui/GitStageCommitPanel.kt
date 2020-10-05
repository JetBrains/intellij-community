// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.vcs.commit.NonModalCommitPanel

class GitStageCommitPanel(project: Project) : NonModalCommitPanel(project) {
  init {
    Disposer.register(this, commitMessage)

    bottomPanel = { add(commitActionsPanel) }
    buildLayout()
  }

  override fun activate(): Boolean = true
  override fun refreshData() = Unit

  override fun getDisplayedChanges(): List<Change> = emptyList()
  override fun getIncludedChanges(): List<Change> = emptyList()
  override fun getDisplayedUnversionedFiles(): List<FilePath> = emptyList()
  override fun getIncludedUnversionedFiles(): List<FilePath> = emptyList()

  override fun includeIntoCommit(items: Collection<*>) = Unit
  override fun addInclusionListener(listener: InclusionListener, parent: Disposable) = Unit

  override fun showCommitOptions(popup: JBPopup, isFromToolbar: Boolean, dataContext: DataContext) =
    if (isFromToolbar) popup.showAbove(toolbar.component) else popup.showInBestPositionFor(dataContext)
}
