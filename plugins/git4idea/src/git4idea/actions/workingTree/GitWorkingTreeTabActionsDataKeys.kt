// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.workingTree

import com.intellij.openapi.actionSystem.DataKey
import git4idea.GitWorkingTree
import git4idea.repo.GitRepository
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
object GitWorkingTreeTabActionsDataKeys {
  @JvmField
  val SELECTED_WORKING_TREES: DataKey<List<GitWorkingTree>> = DataKey.create("SELECTED_GIT_WORKING_TREE_IN_WORKING_TREE_TAB")
  @JvmField
  val CURRENT_REPOSITORY: DataKey<GitRepository> = DataKey.create("CURRENT_REPOSITORY_IN_WORKING_TREE_TAB")
}
