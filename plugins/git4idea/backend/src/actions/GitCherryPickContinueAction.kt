// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.actionSystem.AnActionEvent
import git4idea.cherrypick.GitCherryPickContinueProcess.launchCherryPick
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import icons.DvcsImplIcons
import javax.swing.Icon

internal class GitCherryPickContinueAction : GitOperationActionBase(Repository.State.GRAFTING) {

  override val operationName: String
    get() = GitBundle.message("action.Git.CherryPick.Continue.text")

  override fun getMainToolbarIcon(): Icon = DvcsImplIcons.ResolveContinue

  override fun performInBackground(repository: GitRepository) {
    launchCherryPick(repository)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project ?: return

    // Hide if there are unresolved conflicts (ResolveConflicts action would be shown instead)
    if (e.presentation.isEnabledAndVisible) {
      e.presentation.isEnabledAndVisible = !GitResolveConflictsAction.isEnabled(project)
    }
  }
}