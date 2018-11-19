// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangesUtil.findValidParentAccurately
import com.intellij.vcs.branch.BranchData
import com.intellij.vcs.branch.BranchStateProvider
import com.intellij.vcs.branch.LinkedBranchDataImpl
import git4idea.GitVcs
import git4idea.repo.GitRepository

class GitBranchStateProvider(val project: Project,
                             val vcsManager: ProjectLevelVcsManager,
                             val repositoryManager: VcsRepositoryManager) : BranchStateProvider {
  override fun getCurrentBranch(path: FilePath): BranchData? {
    if (!vcsManager.checkVcsIsActive(GitVcs.NAME)) return null

    val repository = findValidParentAccurately(path)?.let { repositoryManager.getRepositoryForFile(it, true) } as? GitRepository
    return repository?.let {
      LinkedBranchDataImpl(it.root.presentableName, it.currentBranch?.name, it.currentBranch?.findTrackedBranch(it)?.name)
    }
  }
}