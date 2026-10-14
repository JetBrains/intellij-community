// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.dialog

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.project.Project
import git4idea.GitReference
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitWorktreeAdditionalConfigCopier

/** Input/context for the New Worktree dialog. The dialog produces a [GitWorktreeCreationRequest]. */
internal data class GitWorktreeDialogContext(
  val project: Project,
  val initialRepository: GitRepository,
  val ideActivity: StructuredIdeActivity,
  val initialExistingRef: GitReference?,
  val initialParentPath: String?,
  val candidateRepositories: List<GitRepository> = listOf(initialRepository),
  val openAfterCreation: Boolean = true,
  val initialState: GitWorkingTreeDialogState? = null,
)

/**
 * A snapshot of the New Worktree dialog's fields. Lets the dialog reopen with the same choices after
 * the user closes it to create or edit a .worktreeinclude file.
 */
internal data class GitWorkingTreeDialogState(
  val repository: GitRepository,
  val existingRef: GitReference?,
  val createNewBranch: Boolean,
  val newBranchName: String,
  val additionalCopiers: Map<GitWorktreeAdditionalConfigCopier, Boolean>,
  val copyWorktreeIncludeMatches: Boolean,
  val runSetupScript: Boolean,
  val setupScriptPath: String,
  val projectName: String,
  val parentPath: String,
  val configCategoriesExpanded: Boolean,
)
