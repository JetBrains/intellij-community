// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.update.UpdatedFiles
import git4idea.commands.Git
import git4idea.config.UpdateMethod
import git4idea.fetch.GitFetchResult
import git4idea.fetch.GitFetchSupport.fetchSupport
import git4idea.repo.GitRepository

internal class GitFetchUpdater private constructor(
  project: Project,
  git: Git,
  repository: GitRepository,
  progressIndicator: ProgressIndicator,
  updatedFiles: UpdatedFiles,
  private val fetchMethod: () -> GitFetchResult
) : GitUpdater(
  project,
  git,
  repository,
  progressIndicator,
  updatedFiles
) {

  override fun isSaveNeeded(): Boolean = false

  override fun doUpdate(): GitUpdateResult {
    try {
      val fetchResult = fetchMethod()
      return if (fetchResult.showNotificationIfFailed()) { // showNotificationIfFailed() returns true if notification was NOT shown
        GitUpdateResult.SUCCESS
      } else {
        GitUpdateResult.ERROR
      }
    } catch (e: ProcessCanceledException) {
      return GitUpdateResult.CANCEL
    }
  }

  companion object {
    @JvmStatic
    fun newInstance(
      updateMethod: UpdateMethod,
      project: Project,
      git: Git,
      repository: GitRepository,
      progressIndicator: ProgressIndicator,
      updatedFiles: UpdatedFiles
    ): GitUpdater = when (updateMethod) {
      UpdateMethod.FETCH_DEFAULT -> GitFetchUpdater(project, git, repository, progressIndicator, updatedFiles) {
        fetchSupport(project).fetchDefaultRemote(listOf(repository))
      }
      UpdateMethod.FETCH_ALL -> GitFetchUpdater(project, git, repository, progressIndicator, updatedFiles) {
        fetchSupport(project).fetchAllRemotes(listOf(repository))
      }
      else -> throw IllegalArgumentException("Update method $updateMethod is not supported by GitFetchUpdater")
    }
  }

}
