// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.DataKey
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository
import org.jetbrains.annotations.ApiStatus

/**
 * @see [com.intellij.vcs.git.actions.GitSingleRefActions]
 */
@ApiStatus.Internal
object GitBranchActionsDataKeys {
  /**
   * See [GitBranchActionsUtil.getAffectedRepositories] for retrieving actual affected repositories
   */
  @JvmField
  val AFFECTED_REPOSITORIES = DataKey.create<List<GitRepository>>("Git.Repositories")

  /**
   * See [GitBranchActionsUtil.getRepositoriesForTopLevelActions] for retrieving selected
   * ([GitBranchUtil.guessRepositoryForOperation] or [GitBranchUtil.guessWidgetRepository]) repository or all affected repositories
   */
  @JvmField
  val SELECTED_REPOSITORY = DataKey.create<GitRepository>("Git.Selected.Repository")

  @JvmField
  val USE_CURRENT_BRANCH = DataKey.create<Boolean>("Git.Branches.UseCurrent")
}