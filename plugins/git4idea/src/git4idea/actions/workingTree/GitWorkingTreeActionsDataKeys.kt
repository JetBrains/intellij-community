// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.workingTree

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import git4idea.GitWorkingTree
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
object GitWorkingTreeActionsDataKeys {
  @JvmField
  val SELECTED_WORKING_TREES: DataKey<List<GitWorkingTree>> = DataKey.create("SELECTED_GIT_WORKING_TREE")
  @JvmField
  val GIT_REPOSITORY_MODEL_ID: DataKey<RepositoryId> = DataKey.create("GIT_REPOSITORY_MODEL_ID")
}
