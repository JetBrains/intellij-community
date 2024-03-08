// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.vcs.VcsShowToolWindowTabAction
import git4idea.i18n.GitBundle

class GitShowStashToolWindowTabAction : VcsShowToolWindowTabAction() {
  override val tabName: String get() = GitStashContentProvider.TAB_NAME

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    if (project == null || !isStashTabVisible(project)) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (isStashesAndShelvesTabEnabled(project)) {
      e.presentation.text = GitBundle.message("action.Git.Show.Stash.With.Shelf.text")
      e.presentation.description = GitBundle.message("action.Git.Show.Stash.With.Shelf.description")
    } else {
      e.presentation.text = GitBundle.message("action.Git.Show.Stash.text")
      e.presentation.description = GitBundle.message("action.Git.Show.Stash.description")
    }
  }
}