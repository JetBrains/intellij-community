// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.dvcs.push.VcsPushAction
import com.intellij.icons.ExpUiIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.update.CommonUpdateProjectAction
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle

@Suppress("ComponentNotRegistered")
class GitToolbarPushAction : VcsPushAction(), TooltipDescriptionProvider {
  init {
    ActionManager.getInstance().getAction("Vcs.Push")?.let(::copyFrom)
  }

  override fun update(e: AnActionEvent) {
    if (!GitToolbarActions.isEnabledAndVisible()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    super.update(e)

    updatePresentation(e)
  }

  private fun updatePresentation(e: AnActionEvent) {
    val project = e.project ?: return
    val repository = GitBranchUtil.guessWidgetRepository(project, e.dataContext) ?: return
    val currentBranch = repository.currentBranch ?: return

    val hasOutgoingForCurrentBranch = GitBranchIncomingOutgoingManager.getInstance(project).hasOutgoingFor(repository, currentBranch.name)

    with(e.presentation) {
      if (hasOutgoingForCurrentBranch) {
        icon = ExpUiIcons.Vcs.OutgoingPush
        description = GitBundle.message("branches.there.are.outgoing.commits")
      }
      else {
        icon = ExpUiIcons.Vcs.Push
        description = ""
      }
    }
  }
}

@Suppress("ComponentNotRegistered")
class GitToolbarUpdateProjectAction : CommonUpdateProjectAction(), TooltipDescriptionProvider {
  init {
    ActionManager.getInstance().getAction("Vcs.UpdateProject")?.let(::copyFrom)
  }

  override fun update(e: AnActionEvent) {
    if (!GitToolbarActions.isEnabledAndVisible()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    super.update(e)

    updatePresentation(e)
  }

  private fun updatePresentation(e: AnActionEvent) {
    val project = e.project ?: return
    val repository = GitBranchUtil.guessWidgetRepository(project, e.dataContext) ?: return
    val currentBranch = repository.currentBranch ?: return

    val hasIncomingForCurrentBranch = GitBranchIncomingOutgoingManager.getInstance(project).hasIncomingFor(repository, currentBranch.name)

    with(e.presentation) {
      if (hasIncomingForCurrentBranch) {
        icon = ExpUiIcons.Vcs.IncomingUpdate
        description = GitBundle.message("branches.there.are.incoming.commits")
      }
      else {
        icon = ExpUiIcons.Vcs.Update
        description = ""
      }
    }
  }
}

object GitToolbarActions {
  internal fun isEnabledAndVisible(): Boolean {
    return Registry.`is`("vcs.new.ui.main.toolbar.actions")
  }
}
