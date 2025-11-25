// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.dvcs.repo.repositoryId
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.git.repo.GitRepositoryModel
import git4idea.GitRemoteBranch
import git4idea.actions.workingTree.GitWorkingTreeDialogData
import git4idea.commands.Git
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class GitWorkingTreesService(private val project: Project, val coroutineScope: CoroutineScope) {

  companion object {
    private const val WORKING_TREE_TAB_CLOSED_BY_USER_PROPERTY: String = "Git.Working.Tree.Tab.closed.by.user"

    fun getInstance(project: Project): GitWorkingTreesService = project.getService(GitWorkingTreesService::class.java)

    /**
     * So far only the `single repository` case is supported for working trees
     */
    fun getRepoForWorkingTreesSupport(project: Project?): GitRepository? {
      if (project == null) return null
      if (!GitWorkingTreesUtil.isWorkingTreesFeatureEnabled()) return null
      val repositories = GitRepositoryManager.getInstance(project).repositories
      return repositories.singleOrNull()
    }
  }

  fun repositoryToModel(repository: GitRepository): GitRepositoryModel? {
    return GitRepositoriesHolder.getInstance(project).get(repository.repositoryId())
  }

  fun shouldWorkingTreesTabBeShown(): Boolean {
    if (getRepoForWorkingTreesSupport(project) == null) {
      return false
    }
    return !PropertiesComponent.getInstance(project).getBoolean(WORKING_TREE_TAB_CLOSED_BY_USER_PROPERTY, false)
  }

  fun workingTreesTabOpenedByUser() {
    PropertiesComponent.getInstance(project).unsetValue(WORKING_TREE_TAB_CLOSED_BY_USER_PROPERTY)
  }

  fun workingTreesTabClosedByUser() {
    PropertiesComponent.getInstance(project).setValue(WORKING_TREE_TAB_CLOSED_BY_USER_PROPERTY, true)
  }

  class Result private constructor(
    val success: Boolean,
    val errorOutputAsHtmlString: @NlsSafe @NlsContexts.NotificationContent String,
  ) {
    companion object {
      val SUCCESS = Result(true, "")

      fun createFailure(@NlsContexts.NotificationContent errorOutputAsHtmlString: @NlsSafe String): Result {
        return Result(false, errorOutputAsHtmlString)
      }
    }
  }

  suspend fun createWorkingTree(repository: GitRepository, data: GitWorkingTreeDialogData): Result {
    return withBackgroundProgress(project, GitBundle.message("progress.title.creating.worktree"), cancellable = true) {
      val newBranchName = when {
        data.newBranchName != null -> data.newBranchName
        data.sourceBranch is GitRemoteBranch -> data.sourceBranch.nameForRemoteOperations
        else -> null
      }
      val commandResult = Git.getInstance().createWorkingTree(repository, data.workingTreePath, data.sourceBranch, newBranchName)
      if (commandResult.success()) {
        Result.SUCCESS
      }
      else {
        Result.createFailure(commandResult.errorOutputAsHtmlString)
      }
    }
  }
}