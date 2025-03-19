// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.DataKey
import git4idea.GitBranch
import git4idea.GitTag
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository

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
  val BRANCHES = DataKey.create<List<GitBranch>>("Git.Branches")

  @JvmField
  val USE_CURRENT_BRANCH = DataKey.create<Boolean>("Git.Branches.UseCurrent")

  @JvmField
  val REMOTE = DataKey.create<GitRemote>("Git.Remote")

  @JvmField
  val TAGS = DataKey.create<List<GitTag>>("Git.Tags")
}