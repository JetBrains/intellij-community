// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.dvcs.push.VcsPushAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.vcs.update.CommonUpdateProjectAction
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle

class GitToolbarPushAction: VcsPushAction(), TooltipDescriptionProvider {
  init {
    ActionManager.getInstance().getAction("Vcs.Push")?.let(::copyFrom)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    updatePresentation(e)
  }

  private fun updatePresentation(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isExperimentEnabled()
    val project = e.project ?: return
    val repository = GitBranchUtil.guessWidgetRepository(project, e.dataContext) ?: return
    val currentBranch = repository.currentBranch ?: return

    val hasOutgoingForCurrentBranch = GitBranchIncomingOutgoingManager.getInstance(project).hasOutgoingFor(repository, currentBranch.name)

    e.presentation.description = if (hasOutgoingForCurrentBranch) GitBundle.message("branches.there.are.outgoing.commits") else ""
  }
}

class GitToolbarUpdateProjectAction : CommonUpdateProjectAction(), TooltipDescriptionProvider {
  init {
    ActionManager.getInstance().getAction("Vcs.UpdateProject")?.let(::copyFrom)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    updatePresentation(e)
  }

  private fun updatePresentation(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isExperimentEnabled()
    val project = e.project ?: return
    val repository = GitBranchUtil.guessWidgetRepository(project, e.dataContext) ?: return
    val currentBranch = repository.currentBranch ?: return

    val hasIncomingForCurrentBranch = GitBranchIncomingOutgoingManager.getInstance(project).hasIncomingFor(repository, currentBranch.name)

    e.presentation.description = if (hasIncomingForCurrentBranch) GitBundle.message("branches.there.are.incoming.commits") else ""
  }
}

private fun isExperimentEnabled(): Boolean {
  return Experiments.getInstance().isFeatureEnabled("git4idea.new.ui.main.toolbar.actions")
}