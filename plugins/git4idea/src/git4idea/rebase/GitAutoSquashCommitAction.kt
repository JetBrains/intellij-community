// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.vcs.log.VcsShortCommitDetails

abstract class GitAutoSquashCommitAction : GitCommitEditingAction() {

  override fun actionPerformedAfterChecks(e: AnActionEvent) {
    val commit = getSelectedCommit(e)
    val project = e.project!!

    val changeList = ChangeListManager.getInstance(project).defaultChangeList
    CommitChangeListDialog.commitChanges(project, changeList.changes, changeList, null, getCommitMessage(commit))
  }

  protected abstract fun getCommitMessage(commit: VcsShortCommitDetails): String
}