// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.containers.tail
import git4idea.GitBranch
import git4idea.actions.branch.GitBranchActionsUtil.getAffectedRepositories
import git4idea.branch.GitBranchUtil
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

object GitBranchActionsUtil {
  @JvmStatic
  fun calculateNewBranchInitialName(branch: GitBranch): @NlsSafe String {
    return calculateNewBranchInitialName(branch.name, branch.isRemote)
  }

  /**
   *  Calculate initial branch name for the "New branch" actions.
   *
   *  Calculation use the following rules:
   *
   *  * For selected remote branch - use the corresponding local name
   *  * For selected local branch - use the same name as the selected branch
   *
   */
  @JvmStatic
  fun calculateNewBranchInitialName(branchName: @NlsSafe String, isRemote: Boolean): @NlsSafe String {
    require(branchName.isNotEmpty()) { "Given branch name cannot be empty" }

    if (!isRemote) {
      return branchName
    }

    val split = branchName.split('/')

    return if (split.size == 1) branchName else split.tail().joinToString("/")
  }

  /**
   * For top level (without explicit repository selection) actions:
   * if [com.intellij.dvcs.repo.RepositoryManager.isSyncEnabled] will delegate to [getAffectedRepositories],
   * otherwise get [GitBranchActionsDataKeys.SELECTED_REPOSITORY]
   */
  @JvmStatic
  fun getRepositoriesForTopLevelActions(e: AnActionEvent): List<GitRepository> {
    val project = e.project ?: return emptyList()

    if (!GitVcsSettings.getInstance(project).shouldExecuteOperationsOnAllRoots()) {
      val selectedRepository = e.getData(GitBranchActionsDataKeys.SELECTED_REPOSITORY)
      if (selectedRepository != null) return listOf(selectedRepository)
    }

    return getAffectedRepositories(e)
  }

  /**
   * If particular repositories already specified in action's data context - return it.
   * If [com.intellij.dvcs.repo.RepositoryManager.isSyncEnabled] return all repositories for the particular project,
   * otherwise return user's mostly used repository [GitBranchUtil.guessRepositoryForOperation]
   */
  @JvmStatic
  fun getAffectedRepositories(e: AnActionEvent): List<GitRepository> {
    val project = e.project ?: return emptyList()

    val repositoriesInContext = e.getData(GitBranchActionsDataKeys.AFFECTED_REPOSITORIES).orEmpty()

    if (repositoriesInContext.isNotEmpty()) {
      return repositoriesInContext
    }

    val allRepositories = GitRepositoryManager.getInstance(project).repositories
    if (allRepositories.size == 1 || GitVcsSettings.getInstance(project).shouldExecuteOperationsOnAllRoots()) {
      return allRepositories
    }

    return GitBranchUtil.guessRepositoryForOperation(project, e.dataContext)?.let(::listOf).orEmpty()
  }
}
