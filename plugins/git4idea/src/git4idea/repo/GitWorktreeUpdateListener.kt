// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class GitWorktreeUpdateListener @JvmOverloads constructor(
  private val currentProject: Project,
  private val repositoryUpdater: (GitRepository) -> Unit = { repo -> repo.workingTreeHolder.scheduleReload() },
) : GitRepositoryUpdateListener {
  /**
   * Reacts on repository updates in other projects when the current project has a git repository with the worktree matching [root].
   * In case of the match, updates the repository.
   * This mechanism provides faster feedback than reacting to VFS events.
   */
  override fun repositoryUpdated(updatedInProject: Project, root: VirtualFile) {
    if (currentProject == updatedInProject) return

    val repositoryManager = GitRepositoryManager.getInstance(currentProject)
    for (repository in repositoryManager.repositories) {
      val workingTrees = repository.workingTreeHolder.getWorkingTrees()
      // Repository having no other (than self) working trees can be skipped
      if (workingTrees.size <= 1) continue

      val hasMatchingWorktree = workingTrees.any { it.path.virtualFile == root }
      if (hasMatchingWorktree) {
        repositoryUpdater(repository)
      }
    }
  }
}
