// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.GitWorkingTree
import git4idea.commands.*
import git4idea.repo.GitRepository
import kotlin.io.path.Path

@Service
internal class GitWorkingTreesCommandService {

  companion object {
    fun getInstance(): GitWorkingTreesCommandService = service<GitWorkingTreesCommandService>()
  }

  fun deleteWorkingTree(project: Project, tree: GitWorkingTree): GitCommandResult {
    val handler = GitLineHandler(project, Path(tree.path.path), GitCommand.WORKTREE)
    handler.addParameters(listOf("remove", tree.path.path, "--force"))
    return Git.getInstance().runCommand(handler)
  }

  fun listWorktrees(repository: GitRepository, vararg listeners: GitLineHandlerListener): GitCommandResult {
    val handler = GitLineHandler(repository.project, repository.root, GitCommand.WORKTREE)
    handler.addParameters("list")
    handler.addParameters("--porcelain")

    for (listener in listeners) {
      handler.addLineListener(listener)
    }
    return Git.getInstance().runCommand(handler)
  }
}