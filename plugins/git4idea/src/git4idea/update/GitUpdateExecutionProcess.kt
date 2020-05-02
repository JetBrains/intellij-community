// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.update.*
import com.intellij.util.containers.toArray
import com.intellij.vcsUtil.VcsUtil
import git4idea.branch.GitBranchPair
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.UpdateMethod
import git4idea.repo.GitRepository
import git4idea.update.GitUpdateEnvironment.performUpdate

internal class GitUpdateExecutionProcess(private val project: Project,
                                         private val repositories: Collection<GitRepository>,
                                         private val updateConfig: Map<GitRepository, GitBranchPair>,
                                         private val updateMethod: UpdateMethod,
                                         private val shouldSetAsUpstream: Boolean = false) {

  fun execute() {
    val vcsToRoots = getVcsRoots(repositories)
    val roots = vcsToRoots.values.flatten().toArray(emptyArray())

    ProgressManager.getInstance()
      .run(UpdateExecution(project, vcsToRoots, roots, updateConfig, updateMethod, shouldSetAsUpstream))
  }

  private fun getVcsRoots(repositories: Collection<GitRepository>): Map<AbstractVcs, Collection<FilePath>> {
    return repositories.associate { repo ->
      repo.vcs to listOf(VcsUtil.getFilePath(repo.root))
    }
  }

  internal class UpdateExecution(project: Project,
                                 vcsToRoots: Map<AbstractVcs, Collection<FilePath>>,
                                 private val roots: Array<FilePath>,
                                 private val updateConfig: Map<GitRepository, GitBranchPair>,
                                 private val updateMethod: UpdateMethod,
                                 private val shouldSetAsUpstream: Boolean = false)
    : AbstractCommonUpdateAction.Updater(project, roots, vcsToRoots, ActionInfo.UPDATE, "Update") {

    override fun performUpdate(progressIndicator: ProgressIndicator, updateEnvironment: UpdateEnvironment?,
                               files: MutableCollection<FilePath>?, refContext: Ref<SequentialUpdatesContext>?): UpdateSession {
      if (shouldSetAsUpstream) {
        updateConfig.forEach { (repository, branchPair) -> setBranchUpstream(repository, branchPair) }
      }

      return performUpdate(project, roots, myUpdatedFiles, progressIndicator, updateMethod, updateConfig)
    }

    private fun setBranchUpstream(repository: GitRepository, branchConfig: GitBranchPair) {
      val handler = GitLineHandler(project, repository.root, GitCommand.BRANCH)
      val (local, remote) = branchConfig
      handler.addParameters("--set-upstream-to", remote.name, local.name)

      val result = Git.getInstance().runCommand(handler)
      if (!result.success()) {
        LOG.error("Failed to set '${remote.name}' as upstream for '${local.name}' in '${repository.root.path}'")
      }
    }

    companion object {
      private val LOG = Logger.getInstance(UpdateExecution::class.java)
    }
  }
}