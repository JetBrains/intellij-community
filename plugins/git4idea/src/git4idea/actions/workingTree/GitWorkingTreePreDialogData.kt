// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.workingTree

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitStandardLocalBranch
import git4idea.repo.GitRepository
import java.nio.file.Path

internal data class GitWorkingTreePreDialogData(
  val project: Project,
  val repository: GitRepository,
  val ideActivity: StructuredIdeActivity,
  val initialExistingBranch: GitStandardLocalBranch?,
  val initialParentPath: VirtualFile?,
  //should be the main repo root in case of working in a worktree
  val projectNameBase: Path = repository.repositoryFiles.configFile.toPath().parent.parent,
)