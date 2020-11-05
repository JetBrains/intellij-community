// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import git4idea.index.GitStageTracker
import git4idea.index.isStagingAreaAvailable
import git4idea.index.ui.NodeKind
import git4idea.index.ui.fileStatusNodes
import git4idea.index.ui.hasMatchingRoots

abstract class GitStageAllActionBase(vararg val kinds: NodeKind) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null || !isStagingAreaAvailable(project)) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true
    e.presentation.isEnabled = GitStageTracker.getInstance(project).state.hasMatchingRoots(*kinds)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    performStageOperation(project, GitStageTracker.getInstance(project).state.fileStatusNodes(*kinds), GitAddOperation)
  }
}

class GitStageAllAction : GitStageAllActionBase(NodeKind.UNTRACKED, NodeKind.UNSTAGED)
class GitStageTrackedAction : GitStageAllActionBase(NodeKind.UNSTAGED)