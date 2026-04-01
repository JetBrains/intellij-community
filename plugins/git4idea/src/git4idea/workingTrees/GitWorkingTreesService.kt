// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.dvcs.repo.repositoryId
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.git.repo.GitRepositoryModel
import com.intellij.vcs.git.workingTrees.GitWorkingTreesUtil
import git4idea.GitRemoteBranch
import git4idea.GitWorkingTree
import git4idea.actions.workingTree.GitWorkingTreeDialogData
import git4idea.commands.Git
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.io.path.Path

@Service(Service.Level.PROJECT)
internal class GitWorkingTreesService(private val project: Project, val coroutineScope: CoroutineScope) {

  init {
    if (!ApplicationManager.getApplication().isUnitTestMode && !ApplicationManager.getApplication().isHeadlessEnvironment) {
      coroutineScope.launch {
        GitRepositoriesHolder.getInstance(project).updates.collect { updateType ->
          if (updateType == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED ||
              updateType == GitRepositoriesHolder.UpdateType.RELOAD_STATE) {
            ApplicationManager.getApplication().invokeLater {
              project.messageBus.syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged()
            }
          }
        }
      }
    }
  }

  companion object {
    private const val WORKING_TREE_TAB_STATUS_PROPERTY: String = "Git.Working.Tree.Tab.closed.by.user"
    private const val WORKING_TREE_TAB_STATUS_OPENED_BY_USER: String = "opened"
    private const val WORKING_TREE_TAB_STATUS_CLOSED_BY_USER: String = "closed"

    fun getInstance(project: Project): GitWorkingTreesService = project.getService(GitWorkingTreesService::class.java)

    /**
     * Returns Git repositories that should be displayed in the Worktrees tool window.
     */
    fun getRepositoriesForWorkingTreesSupport(project: Project?): List<GitRepository> {
      if (project == null) return emptyList()
      if (!GitWorkingTreesUtil.isWorkingTreesFeatureEnabled()) return emptyList()
      val repositoryManager = GitRepositoryManager.getInstance(project)
      return repositoryManager.sortByDependency(repositoryManager.repositories)
    }

    /**
     * Returns a Git repository that supports working trees for the current project.
     * Supports single-repository projects and multi-repository projects that have a unique top-level repository
     * (for example mono-repos or projects containing submodules).
     */
    fun getRepoForWorkingTreesSupport(project: Project?): GitRepository? {
      if (project == null) return null
      val repositories = getRepositoriesForWorkingTreesSupport(project)
      if (repositories.isEmpty()) return null

      if (repositories.size == 1) return repositories.first()

      val topLevelRepositories = repositories.filter { candidate ->
        repositories.none { other -> other != candidate && VfsUtilCore.isAncestor(other.root, candidate.root, true) }
      }
      return topLevelRepositories.singleOrNull()
    }
  }

  fun repositoryToModel(repository: GitRepository): GitRepositoryModel? {
    return GitRepositoriesHolder.getInstance(project).get(repository.repositoryId())
  }

  fun shouldWorkingTreesTabBeShown(): Boolean {
    val repositories = getRepositoriesForWorkingTreesSupport(project)
    if (repositories.isEmpty()) return false
    val value = PropertiesComponent.getInstance(project).getValue(WORKING_TREE_TAB_STATUS_PROPERTY)
    return when (value) {
      WORKING_TREE_TAB_STATUS_CLOSED_BY_USER -> false
      WORKING_TREE_TAB_STATUS_OPENED_BY_USER -> true
      else -> repositories.any { it.workingTreeHolder.getWorkingTrees().size > 1 }
    }
  }

  fun workingTreesTabOpenedByUser() {
    PropertiesComponent.getInstance(project).setValue(WORKING_TREE_TAB_STATUS_PROPERTY,
                                                      WORKING_TREE_TAB_STATUS_OPENED_BY_USER)
  }

  fun workingTreesTabClosedByUser() {
    PropertiesComponent.getInstance(project).setValue(WORKING_TREE_TAB_STATUS_PROPERTY,
                                                      WORKING_TREE_TAB_STATUS_CLOSED_BY_USER)
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

  fun openWorkingTreeProject(tree: GitWorkingTree) {
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.Default) {
      ProjectUtil.openOrImportAsync(Path(tree.path.path))
    }
  }
}
