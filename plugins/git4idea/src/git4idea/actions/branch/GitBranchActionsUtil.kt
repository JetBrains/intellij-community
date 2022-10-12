// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.containers.tail
import git4idea.GitBranch
import git4idea.branch.GitBranchUtil
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository

object GitBranchActionsUtil {
  /**
   * See [getAffectedRepositories] for retrieving actual affected repositories
   */
  @JvmField
  val REPOSITORIES_KEY = DataKey.create<List<GitRepository>>("Git.Repositories")

  @JvmField
  val BRANCHES_KEY = DataKey.create<List<GitBranch>>("Git.Branches")

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
   * If [com.intellij.dvcs.repo.RepositoryManager.isSyncEnabled] return all repositories for the particular project,
   * otherwise return user's mostly used repository [GitBranchUtil.guessRepositoryForOperation]
   */
  @JvmStatic
  fun getAffectedRepositories(e: AnActionEvent): List<GitRepository> {
    val project = e.project ?: return emptyList()

    if (userWantsSyncControl(project)) return e.getData(REPOSITORIES_KEY).orEmpty()

    return GitBranchUtil.guessRepositoryForOperation(project, e.dataContext)?.let(::listOf).orEmpty()
  }

  @JvmStatic
  fun userWantsSyncControl(project: Project) = GitVcsSettings.getInstance(project).syncSetting != DvcsSyncSettings.Value.DONT_SYNC
}
