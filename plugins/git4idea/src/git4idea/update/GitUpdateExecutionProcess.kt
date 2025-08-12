// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.update.*
import com.intellij.vcsUtil.VcsUtil.getFilePath
import git4idea.GitNotificationIdsHolder.Companion.BRANCH_SET_UPSTREAM_ERROR
import git4idea.GitNotificationIdsHolder.Companion.UPDATE_NOTHING_TO_UPDATE
import git4idea.GitVcs
import git4idea.branch.GitBranchPair
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.UpdateMethod
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository

internal object GitUpdateExecutionProcess {
  @JvmStatic
  fun launchUpdate(
    project: Project,
    repositories: Collection<GitRepository>,
    updateConfig: Map<GitRepository, GitBranchPair>,
    updateMethod: UpdateMethod,
    shouldSetAsUpstream: Boolean = false,
  ) {
    if (updateConfig.isEmpty()) {
      notifyNothingToUpdate(project)
      return
    }

    val roots = repositories.map { getFilePath(it.root) }
    val spec = createSpec(project, roots, updateConfig, updateMethod, shouldSetAsUpstream)

    VcsUpdateProcess.launchUpdate(project,
                                  roots.toTypedArray(),
                                  listOf(spec),
                                  ActionInfo.UPDATE,
                                  GitBundle.message("progress.title.update"))
  }

  suspend fun update(
    project: Project,
    repositories: Collection<GitRepository>,
    updateConfig: Map<GitRepository, GitBranchPair>,
    updateMethod: UpdateMethod,
    shouldSetAsUpstream: Boolean = false,
  ) {
    if (updateConfig.isEmpty()) {
      notifyNothingToUpdate(project)
      return
    }

    val roots = repositories.map { getFilePath(it.root) }
    val spec = createSpec(project, roots, updateConfig, updateMethod, shouldSetAsUpstream)

    VcsUpdateProcess.update(project,
                            roots.toTypedArray(),
                            listOf(spec),
                            ActionInfo.UPDATE,
                            GitBundle.message("progress.title.update"))
  }

  private fun notifyNothingToUpdate(project: Project) {
    VcsNotifier.getInstance(project).notifyMinorWarning(UPDATE_NOTHING_TO_UPDATE, "", GitBundle.message("update.process.nothing.to.update"))
  }

  private fun createSpec(
    project: Project,
    roots: List<FilePath>,
    updateConfig: Map<GitRepository, GitBranchPair>,
    updateMethod: UpdateMethod,
    shouldSetAsUpstream: Boolean,
  ): VcsUpdateSpecification {
    val gitUpdateEnvironment = project.service<GitUpdateEnvironment>()
    val updateEnvironment = object : UpdateEnvironment by gitUpdateEnvironment {
      override fun updateDirectories(contentRoots: Array<out FilePath>, updatedFiles: UpdatedFiles, progressIndicator: ProgressIndicator, context: Ref<SequentialUpdatesContext?>): UpdateSession {
        if (shouldSetAsUpstream) {
          updateConfig.forEach { (repository, branchPair) -> setBranchUpstream(repository, branchPair) }
        }

        return GitUpdateEnvironment.performUpdate(project, contentRoots, updatedFiles, progressIndicator, updateMethod, updateConfig)
      }

      override fun hasCustomNotification(): Boolean = gitUpdateEnvironment.hasCustomNotification()
    }
    val spec = VcsUpdateSpecification(GitVcs.getInstance(project), updateEnvironment, roots)
    return spec
  }

  private fun setBranchUpstream(repository: GitRepository, branchConfig: GitBranchPair) {
    val handler = GitLineHandler(repository.project, repository.root, GitCommand.BRANCH)
    handler.setSilent(false)
    val (local, remote) = branchConfig
    handler.addParameters("--set-upstream-to", remote.name, local.name)

    val result = Git.getInstance().runCommand(handler)
    if (!result.success()) {
      VcsNotifier.getInstance(repository.project).notifyError(BRANCH_SET_UPSTREAM_ERROR,
                                                              GitBundle.message("update.process.error.notification.title"),
                                                              result.errorOutputAsHtmlString,
                                                              true)
    }
  }
}