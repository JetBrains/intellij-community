// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.dialog

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.project.Project
import git4idea.GitReference
import git4idea.repo.GitRepository

/** Input/context for the New Worktree dialog. The dialog produces a [GitWorktreeCreationRequest]. */
internal data class GitWorktreeDialogContext(
  val project: Project,
  val initialRepository: GitRepository,
  val ideActivity: StructuredIdeActivity,
  val initialExistingRef: GitReference?,
  val initialParentPath: String?,
  val candidateRepositories: List<GitRepository> = listOf(initialRepository),
)
