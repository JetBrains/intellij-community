// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch.popup

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.TreePopup
import com.intellij.vcs.git.repo.GitRepositoryModel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.Promise

@ApiStatus.Internal
@ApiStatus.NonExtendable
interface GitBranchesPopup: TreePopup {
  val userResized: Boolean

  var groupByDirectory: Boolean

  fun restoreDefaultSize()

  @TestOnly
  fun promiseExpandTree(): Promise<*>

  @TestOnly
  fun getExpandedPathsSize(): Int

  companion object {
    /**
     * @param excludedTopLevelActions ids of the top level actions to leave out, for a caller that offers only some of them.
     */
    fun createDefaultPopup(
      project: Project,
      preferredSelection: GitRepositoryModel?,
      repositories: List<GitRepositoryModel>,
      excludedTopLevelActions: Set<String> = emptySet(),
    ): GitBranchesPopup =
      GitDefaultBranchesPopup.create(project, preferredSelection, repositories, excludedTopLevelActions)
  }
}