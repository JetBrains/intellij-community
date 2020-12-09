// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction

class GitShowDiffFromStashAction : AnActionExtensionProvider {
  override fun isActive(e: AnActionEvent): Boolean {
    return e.getData(GitStashTree.GIT_STASH_TREE_FLAG) == true
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null && e.getData(VcsDataKeys.CHANGES_SELECTION)?.isEmpty == false
    e.presentation.isVisible = e.isFromActionToolbar || e.presentation.isEnabled
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val changesSelection = e.getRequiredData(VcsDataKeys.CHANGES_SELECTION)

    ShowDiffAction.showDiffForChange(project, changesSelection)
  }
}