// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.update.UpdatedFiles
import git4idea.branch.GitBranchPair
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository

private val LOG = logger<GitSubmoduleUpdater>()

internal class GitSubmoduleUpdater(val project: Project,
                                   val git: Git,
                                   private val parentRepository: GitRepository,
                                   private val repository: GitRepository,
                                   progressIndicator: ProgressIndicator,
                                   updatedFiles: UpdatedFiles) :
  GitUpdater(project, git, repository, progressIndicator, updatedFiles) {

  override fun isSaveNeeded(): Boolean = true

  override fun doUpdate(): GitUpdateResult {
    try {
      val result = git.runCommand {
        val handler = GitLineHandler(project, parentRepository.root, GitCommand.SUBMODULE)
        handler.addParameters("update", "--recursive")
        handler.endOptions()
        handler.addRelativeFiles(listOf(repository.root))
        handler.setSilent(false)
        handler.setStdoutSuppressed(false)
        handler
      }

      if (result.success()) {
        return GitUpdateResult.SUCCESS
      }

      LOG.info("Submodule update failed for submodule [$repository] called from parent root [$parentRepository]: " +
               result.errorOutputAsJoinedString)
      return GitUpdateResult.ERROR
    }
    catch (pce: ProcessCanceledException) {
      return GitUpdateResult.CANCEL
    }
  }

  // general logic doesn't apply to submodules
  override fun isUpdateNeeded(branchPair: GitBranchPair): Boolean = true

  override fun toString(): String = "Submodule updater"
}
