// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.dvcs.repo.AbstractRepositoryManager
import com.intellij.dvcs.ui.VcsLogAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.impl.showRepositoryBrowser
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

class GitBrowseRepoAtRevisionAction : VcsLogAction<GitRepository>() {
  override fun actionPerformed(project: Project, grouped: MultiMap<GitRepository, VcsFullCommitDetails>) {
    val repo = grouped.keySet().single()
    val commit = grouped.values().single()
    val root = GitDirectoryVirtualFile(repo, null, "", commit)
    showRepositoryBrowser(project, root, repo.root, repo.root.name + " at " + commit.id.toShortString())
  }

  override fun isVisible(project: Project, grouped: MultiMap<GitRepository, Hash>): Boolean {
    return super.isVisible(project, grouped) && ApplicationManager.getApplication().isInternal
  }

  override fun isEnabled(grouped: MultiMap<GitRepository, Hash>): Boolean {
    return grouped.values().size == 1
  }

  override fun getRepositoryManager(project: Project): AbstractRepositoryManager<GitRepository> {
    return GitRepositoryManager.getInstance(project)
  }

  override fun getRepositoryForRoot(project: Project, root: VirtualFile): GitRepository? {
    return getRepositoryManager(project).getRepositoryForRootQuick(root)
  }
}