// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import git4idea.i18n.GitBundle

class GitShowStashTabAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    if (!project.service<GitStashUIHandler>().isStashTabVisible()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (project.service<GitStashUIHandler>().isStashesAndShelvesTabAvailable()) {
      e.presentation.text = GitBundle.message("action.Git.Show.Stash.With.Shelf.text")
      e.presentation.description = GitBundle.message("action.Git.Show.Stash.With.Shelf.description")
    } else {
      e.presentation.text = GitBundle.message("action.Git.Show.Stash.text")
      e.presentation.description = GitBundle.message("action.Git.Show.Stash.description")
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project?: return
    project.service<GitStashUIHandler>().showStashes()
  }
}