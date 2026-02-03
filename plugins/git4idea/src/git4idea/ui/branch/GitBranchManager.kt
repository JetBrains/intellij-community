// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.dvcs.branch.BranchType
import com.intellij.dvcs.branch.DvcsBranchManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import git4idea.branch.GitBranchType
import git4idea.config.GitVcsSettings
import git4idea.log.GitRefManager
import git4idea.remoteApi.GitRepositoryFrontendSynchronizer
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class GitBranchManager(
  project: Project,
  private val cs: CoroutineScope,
) : DvcsBranchManager<GitRepository?>(project,
                                      GitVcsSettings.getInstance(project).branchSettings,
                                      GitBranchType.entries.toTypedArray(),
                                      GitRepositoryManager.getInstance(project)) {
  override fun notifyFavoriteSettingsChanged(repository: GitRepository?) {
    cs.launch {
      myProject.messageBus.syncPublisher(GitRepositoryFrontendSynchronizer.TOPIC).favoriteRefsUpdated(repository)
      myProject.messageBus.syncPublisher(DVCS_BRANCH_SETTINGS_CHANGED).branchFavoriteSettingsChanged()
    }
  }

  override fun getDefaultBranchNames(type: BranchType): Collection<String> = buildList {
    if (type === GitBranchType.LOCAL) {
      add(GitRefManager.MASTER)
      add(GitRefManager.MAIN)
    }
    else if (type === GitBranchType.REMOTE) {
      add(GitRefManager.ORIGIN_MASTER)
      add(GitRefManager.ORIGIN_MAIN)
    }
  }
}
