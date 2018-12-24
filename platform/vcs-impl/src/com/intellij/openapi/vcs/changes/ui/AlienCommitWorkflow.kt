// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vfs.VirtualFile

class AlienCommitWorkflow(val vcs: AbstractVcs<*>, changeListName: String, changes: List<Change>, commitMessage: String?) :
  DialogCommitWorkflow(vcs.project, changes, vcsToCommit = vcs, initialCommitMessage = commitMessage) {
  val changeList = AlienLocalChangeList(changes, changeListName)

  override val isAlien: Boolean get() = true

  override fun prepareCommit(unversionedFiles: List<VirtualFile>, browser: CommitDialogChangesBrowser) = true

  override fun doRunBeforeCommitChecks(changeList: LocalChangeList, checks: Runnable) = checks.run()

  override fun canExecute(executor: CommitExecutor, changes: Collection<Change>) = true

  override fun createBrowser() = AlienChangeListBrowser(project, changeList)

  override fun initDialog(dialog: CommitChangeListDialog) {
    val browser = dialog.browser

    browser.viewer.setIncludedChanges(initiallyIncluded)
    browser.viewer.rebuildTree()
  }
}