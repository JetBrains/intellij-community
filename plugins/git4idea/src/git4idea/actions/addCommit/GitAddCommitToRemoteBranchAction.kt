// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.addCommit

import com.intellij.dvcs.repo.AbstractRepositoryManager
import com.intellij.dvcs.ui.VcsLogAction
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.addCommit.showRemoteBranchSelectionPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class GitAddCommitToRemoteBranchAction : VcsLogAction<GitRepository>() {

  override fun isEnabled(grouped: MultiMap<GitRepository, Hash>): Boolean {
    // Single repository check - detailed validations in update()
    if (grouped.keySet().size != 1) return false
    val repository = grouped.keySet().single()
    return repository.remotes.isNotEmpty()
  }

  override fun actionPerformed(project: Project, grouped: MultiMap<GitRepository, VcsFullCommitDetails>) {
    val repository = grouped.keySet().single()
    val commits = grouped[repository].toList()

    if (repository.remotes.isEmpty()) return
    if (commits.any { it.parents.size > 1 }) return

    showRemoteBranchSelectionPopup(project, repository, commits) { remoteBranch ->
      @Suppress("DEPRECATION")
      (project as ComponentManagerEx).getCoroutineScope().launch(Dispatchers.Default) {
        GitAddCommitToRemoteBranchOperation(project, repository, commits, remoteBranch, cs = this).execute()
      }
    }
  }

  override fun getRepositoryManager(project: Project): AbstractRepositoryManager<GitRepository> {
    return GitRepositoryManager.getInstance(project)
  }

  override fun getRepositoryForRoot(project: Project, root: VirtualFile): GitRepository? {
    return getRepositoryManager(project).getRepositoryForRootQuick(root)
  }
}