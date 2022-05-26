// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.DataKey
import git4idea.GitBranch
import git4idea.repo.GitRepository

object GitBranchActionsUtil {
  @JvmField
  val REPOSITORIES_KEY = DataKey.create<List<GitRepository>>("Git.Repositories")

  @JvmField
  val BRANCHES_KEY = DataKey.create<List<GitBranch>>("Git.Branches")
}