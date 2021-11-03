// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.dvcs.repo.AbstractRepositoryManager
import com.intellij.dvcs.ui.VcsLogAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.impl.RepositoryBrowser
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

class GitBrowseRepoAtRevisionAction : VcsLogAction<GitRepository>() {
  override fun actionPerformed(project: Project, grouped: MultiMap<GitRepository, VcsFullCommitDetails>) {
    val repo = grouped.keySet().single()
    val commit = grouped.values().single()
    val root = GitDirectoryVirtualFile(repo, null, "", commit)
    RepositoryBrowser.showRepositoryBrowser(project, root, repo.root,
      GitBundle.message("tab.title.repo.root.name.at.revision", repo.root.name, commit.id.toShortString()))
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