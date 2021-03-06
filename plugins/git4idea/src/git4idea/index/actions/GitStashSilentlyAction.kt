// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.actions.GitRepositoryAction
import git4idea.actions.GitStash
import git4idea.i18n.GitBundle
import git4idea.index.GitStageTracker
import git4idea.stash.createStashHandler

class GitStashSilentlyAction : GitRepositoryAction() {
  override fun perform(project: Project, gitRoots: MutableList<VirtualFile>, defaultRoot: VirtualFile) {
    val roots = GitStageTracker.getInstance(project).state.changedRoots
    GitStash.runStashInBackground(project, roots) { createStashHandler(project, it) }
  }

  override fun isEnabled(e: AnActionEvent): Boolean {
    val gitStageTracker = e.project?.serviceIfCreated<GitStageTracker>() ?: return false
    return super.isEnabled(e) && gitStageTracker.state.hasChangedRoots()
  }

  override fun getActionName() = GitBundle.message("stash.action.name")
}