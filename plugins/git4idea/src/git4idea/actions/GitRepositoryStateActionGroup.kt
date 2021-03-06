// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import git4idea.GitUtil

abstract class GitRepositoryStateActionGroup(val repositoryState: Repository.State) : DefaultActionGroup(), DumbAware {

  class Merge : GitRepositoryStateActionGroup(Repository.State.MERGING)
  class Rebase : GitRepositoryStateActionGroup(Repository.State.REBASING)

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    presentation.isEnabledAndVisible = false
    val project = e.project ?: return
    if (GitUtil.getRepositoriesInState(project, repositoryState).isNotEmpty()) {
      presentation.isEnabledAndVisible = true
    }
  }

}