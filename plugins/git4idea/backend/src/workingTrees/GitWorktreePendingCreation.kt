// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.dvcs.repo.repositoryId
import com.intellij.openapi.vcs.FilePath
import com.intellij.platform.vcs.impl.shared.RepositoryId
import git4idea.workingTrees.dialog.GitWorktreeCreationRequest
import org.jetbrains.annotations.Nls

internal data class GitWorktreePendingCreation(
  val repositoryId: RepositoryId,
  val targetPath: FilePath,
  val presentableBranchName: @Nls String,
) {
  companion object {
    fun from(request: GitWorktreeCreationRequest): GitWorktreePendingCreation = GitWorktreePendingCreation(
      repositoryId = request.repository.repositoryId(),
      targetPath = request.workingTreePath,
      presentableBranchName = request.branch.presentableBranchName,
    )
  }
}
