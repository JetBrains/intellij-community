// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import git4idea.GitWorkingTree
import org.jetbrains.annotations.Nls

/** A single row of the Worktrees tab. */
internal data class GitWorktreeRow(
  val gitWorkingTree: GitWorkingTree,
  @param:Nls val presentableBranchName: String,
  @param:Nls val location: String,
)
