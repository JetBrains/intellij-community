// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.update.*
import com.intellij.util.containers.toArray
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitNotificationIdsHolder.Companion.BRANCH_SET_UPSTREAM_ERROR
import git4idea.GitNotificationIdsHolder.Companion.UPDATE_NOTHING_TO_UPDATE
import git4idea.branch.GitBranchPair
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.UpdateMethod
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.update.GitUpdateEnvironment.performUpdate

internal class GitUpdateExecutionProcess(
  private val project: Project,
  private val repositories: Collection<GitRepository>,
  private val updateConfig: Map<GitRepository, GitBranchPair>,
  private val updateMethod: UpdateMethod,
  private val shouldSetAsUpstream: Boolean = false,
) {
  fun execute() {
    if (updateConfig.isEmpty()) {
      VcsNotifier.getInstance(project).notifyMinorWarning(UPDATE_NOTHING_TO_UPDATE,
                                                          "",
                                                          GitBundle.message("update.process.nothing.to.update"))
      return
    }

    val vcsToRoots = getVcsRoots(repositories)
    val roots = vcsToRoots.values.flatten().toArray(emptyArray())

    ProgressManager.getInstance()
      .run(UpdateExecution(
        project = project,
        vcsToRoots = vcsToRoots,
        roots = roots,
        updateConfig = updateConfig,
        updateMethod = updateMethod,
        shouldSetAsUpstream = shouldSetAsUpstream,
      ))
  }

  private fun getVcsRoots(repositories: Collection<GitRepository>): Map<AbstractVcs, Collection<FilePath>> {
    return repositories.associate { repo ->
      repo.vcs to listOf(VcsUtil.getFilePath(repo.root))
    }
  }
}

private class UpdateExecution(
  project: Project,
  vcsToRoots: Map<AbstractVcs, Collection<FilePath>>,
  private val roots: Array<FilePath>,
  private val updateConfig: Map<GitRepository, GitBranchPair>,
  private val updateMethod: UpdateMethod,
  private val shouldSetAsUpstream: Boolean = false,
)
  : AbstractCommonUpdateAction.Updater(project, roots, vcsToRoots, ActionInfo.UPDATE, GitBundle.message("progress.title.update")) {
  override fun performUpdate(
    progressIndicator: ProgressIndicator?,
    updateEnvironment: UpdateEnvironment,
    files: Collection<FilePath>,
    refContext: Ref<SequentialUpdatesContext>,
  ): UpdateSession {
    if (shouldSetAsUpstream) {
      updateConfig.forEach { (repository, branchPair) -> setBranchUpstream(repository, branchPair) }
    }

    return performUpdate(project, roots, updatedFiles, progressIndicator, updateMethod, updateConfig)
  }

  private fun setBranchUpstream(repository: GitRepository, branchConfig: GitBranchPair) {
    val handler = GitLineHandler(project, repository.root, GitCommand.BRANCH)
    handler.setSilent(false)
    val (local, remote) = branchConfig
    handler.addParameters("--set-upstream-to", remote.name, local.name)

    val result = Git.getInstance().runCommand(handler)
    if (!result.success()) {
      VcsNotifier.getInstance(project).notifyError(BRANCH_SET_UPSTREAM_ERROR,
                                                   GitBundle.message("update.process.error.notification.title"),
                                                   result.errorOutputAsHtmlString,
                                                   true)
    }
  }
}