// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.workingTree

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import git4idea.GitReference
import git4idea.actions.ref.GitSingleRefAction
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitWorkingTreesService
import git4idea.workingTrees.GitWorkingTreesUtil

class GitOpenExistingWorkingTreeForLocalBranchAction :
  GitSingleRefAction<GitReference>({ GitBundle.message("action.open.worktree.for.a.branch.text") }) {

  override fun isEnabledForRef(ref: GitReference, repositories: List<GitRepository>): Boolean {
    val repository = repositories.singleOrNull() ?: return false
    return GitWorkingTreesUtil.getWorkingTreeWithRef(ref, repository, true) != null
  }

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: GitReference) {
    val repository = repositories.singleOrNull() ?: return
    val workingTree = GitWorkingTreesUtil.getWorkingTreeWithRef(reference, repository, true) ?: return
    GitWorkingTreesService.getInstance(repository.project).openWorkingTreeProject(workingTree, e.coroutineScope)
  }
}