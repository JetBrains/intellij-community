// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.CommonBundle
import com.intellij.dvcs.repo.repositoryId
import com.intellij.ide.GeneralSettings
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.eel.fs.EelFileUtils
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.application
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.git.repo.GitRepositoryModel
import com.intellij.vcs.git.workingTrees.GitWorkingTreesUtil
import git4idea.GitNotificationIdsHolder
import git4idea.GitRemoteBranch
import git4idea.GitWorkingTree
import git4idea.actions.workingTree.GitWorkingTreeDialogData
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.awt.Window
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.minutes

@Service(Service.Level.PROJECT)
internal class GitWorkingTreesService(private val project: Project, val coroutineScope: CoroutineScope) {
  init {
    if (!ApplicationManager.getApplication().isUnitTestMode && !ApplicationManager.getApplication().isHeadlessEnvironment) {
      scheduleBackgroundRefresh()

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

  private fun scheduleBackgroundRefresh() {
    val ideActiveFlow = callbackFlow {
      application.messageBus.connect(this).subscribe(ApplicationActivationListener.TOPIC, object : ApplicationActivationListener {
        override fun applicationActivated(ideFrame: IdeFrame) {
          trySend(true)
        }
        override fun delayedApplicationDeactivated(window: Window) {
          trySend(false)
        }
      })
      send(true)
      awaitClose()
    }.distinctUntilChanged()

    val twVisibleFlow = callbackFlow {
      fun update(toolWindowManager: ToolWindowManager) {
        val isVisible = toolWindowManager.getToolWindow(ToolWindowId.VCS)?.isVisible
        trySend(isVisible == true)
      }

      application.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
        override fun stateChanged(toolWindowManager: ToolWindowManager) {
          update(toolWindowManager)
        }
      })
      update(ToolWindowManager.getInstance(project))
      awaitClose()
    }.flowOn(Dispatchers.UI).distinctUntilChanged()

    coroutineScope.launch {
      combine(ideActiveFlow, twVisibleFlow) { ideActive, toolWindowVisible ->
        ideActive && toolWindowVisible
      }.collectLatest { shouldRefresh ->
        if (shouldRefresh) {
          while (true) {
            val status = getWorktreeSupportStatus(project)
            when (status) {
              is GitWorktreeSupportStatus.SingleRepository -> status.repository.workingTreeHolder.scheduleReload()
              is GitWorktreeSupportStatus.MultipleRepository -> status.repositories.forEach { it.workingTreeHolder.scheduleReload() }
              else -> {}
            }
            delay(WORKING_TREES_REFRESH_INTERVAL)
          }
        }
      }
    }
  }

  companion object {
    private const val WORKING_TREE_TAB_STATUS_PROPERTY: String = "Git.Working.Tree.Tab.closed.by.user"
    private const val WORKING_TREE_TAB_STATUS_OPENED_BY_USER: String = "opened"
    private const val WORKING_TREE_TAB_STATUS_CLOSED_BY_USER: String = "closed"
    internal val WORKING_TREES_REFRESH_INTERVAL = 5.minutes

    fun getInstance(project: Project): GitWorkingTreesService = project.getService(GitWorkingTreesService::class.java)

    /**
     * Working trees UI currently supports only the `single repository` case.
     * The returned value distinguishes unsupported, single-repository, and multi-repository project states.
     */
    fun getWorktreeSupportStatus(project: Project?): GitWorktreeSupportStatus {
      if (project == null || !GitWorkingTreesUtil.isWorkingTreesFeatureEnabled()) return GitWorktreeSupportStatus.Unsupported
      val repositories = GitRepositoryManager.getInstance(project).repositories
      return when (repositories.size) {
        0 -> GitWorktreeSupportStatus.Unsupported
        1 -> GitWorktreeSupportStatus.SingleRepository(repositories.single())
        else -> GitWorktreeSupportStatus.MultipleRepository(repositories)
      }
    }
  }

  fun repositoryToModel(repository: GitRepository): GitRepositoryModel? {
    return GitRepositoriesHolder.getInstance(project).get(repository.repositoryId())
  }

  fun shouldWorkingTreesTabBeShown(): Boolean {
    val status = getWorktreeSupportStatus(project)
    if (status == GitWorktreeSupportStatus.Unsupported) return false

    val value = PropertiesComponent.getInstance(project).getValue(WORKING_TREE_TAB_STATUS_PROPERTY)
    when (value) {
      WORKING_TREE_TAB_STATUS_CLOSED_BY_USER -> return false
      WORKING_TREE_TAB_STATUS_OPENED_BY_USER -> return true
    }

    return status is GitWorktreeSupportStatus.SingleRepository && status.repository.workingTreeHolder.getWorkingTrees().size > 1
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
        data.sourceRef is GitRemoteBranch -> data.sourceRef.nameForRemoteOperations
        else -> null
      }
      val commandResult = Git.getInstance().createWorkingTree(repository, data.workingTreePath, data.sourceRef, newBranchName)
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
      if (!Path(tree.path.path).exists()) {
        VcsNotifier.getInstance(project).notifyMinorWarning(
          GitNotificationIdsHolder.WORKING_TREE_DIRECTORY_NOT_FOUND, "",
          GitBundle.message("Git.WorkingTrees.open.directory.not.found", tree.path.presentableUrl)
        )

        val status = getWorktreeSupportStatus(project)
        when (status) {
          is GitWorktreeSupportStatus.SingleRepository -> status.repository.workingTreeHolder.scheduleReload()
          is GitWorktreeSupportStatus.MultipleRepository -> status.repositories.forEach { it.workingTreeHolder.scheduleReload() }
          else -> {}
        }

        return@launch
      }
      openProjectInNewWindow(Path(tree.path.path))
    }
  }

  fun deleteWorkingTree(project: Project, tree: GitWorkingTree, repository: GitRepository) {
    coroutineScope.launch {
      val existingProject = ProjectUtil.findProject(Path(tree.path.path))
      if (existingProject != null) {
        if (shouldStopDeletion(project, tree, existingProject)) {
          closeProject(existingProject)
        }
        else {
          return@launch
        }
      }

      val commandResult = withBackgroundProgress(project, GitBundle.message("progress.title.deleting.worktree"), cancellable = true) {
        service<Git>().deleteWorkingTree(repository, tree)
      }

      if (commandResult.success()) {
        notifyWorkingTreeDeletedSuccess(project, repository, tree)
        return@launch
      }

      if (project.getEelDescriptor().osFamily.isWindows && isPermissionDenied(commandResult)) {
        handleFailedDeletionOnWindows(project, repository, tree)
      } else {
        notifyWorkingTreeDeletedError(project, commandResult.errorOutputAsHtmlString)
      }
    }
  }

  private suspend fun shouldStopDeletion(project: Project, tree: GitWorkingTree, existingProject: Project): Boolean {
    return withContext(Dispatchers.UiWithModelAccess) {
      MessageDialogBuilder.yesNo(
        GitBundle.message("Git.WorkingTrees.dialog.delete.worktree.title"),
        GitBundle.message("Git.WorkingTrees.delete.worktrees.worktree.opened.close.or.cancel",
                          tree.path.presentableUrl, existingProject.name)
      )
        .yesText(GitBundle.message("Git.WorkingTrees.delete.worktrees.button.close.delete"))
        .noText(GitBundle.message("Git.WorkingTrees.delete.worktrees.button.do.not.delete"))
        .ask(project)
    }
  }

  //see com.intellij.ide.actions.CloseProjectsActionBase.actionPerformed
  private suspend fun closeProject(project: Project) {
    withContext(Dispatchers.UiWithModelAccess) {
      writeIntentReadAction {
        ProjectManager.getInstance().closeAndDispose(project)
      }
    }
    RecentProjectsManager.getInstance().updateLastProjectPath()
  }


  private fun isPermissionDenied(result: GitCommandResult): Boolean {
    return result.errorOutput.any { it.contains("permission denied", ignoreCase = true) }
  }

  private fun notifyWorkingTreeDeletedSuccess(project: Project, repository: GitRepository, tree: GitWorkingTree) {
    repository.workingTreeHolder.scheduleReload()
    VcsNotifier.getInstance(project).notifySuccess(GitNotificationIdsHolder.WORKING_TREE_DELETED,
                                                   "",
                                                   GitBundle.message("Git.WorkingTrees.delete.worktree.success.message",
                                                                     tree.path.presentableUrl))
  }

  private fun notifyWorkingTreeDeletedError(project: Project, @NlsSafe errorOutput: String) {
    VcsNotifier.getInstance(project).notifyError(GitNotificationIdsHolder.WORKING_TREE_COULD_NOT_DELETE,
                                                 GitBundle.message("Git.WorkingTrees.delete.worktrees.failure.notification.title"),
                                                 errorOutput,
                                                 true)
  }

  private suspend fun handleFailedDeletionOnWindows(project: Project, repository: GitRepository, tree: GitWorkingTree) {
    val shouldRetry = withContext(Dispatchers.UiWithModelAccess) {
      MessageDialogBuilder.yesNo(
        GitBundle.message("Git.WorkingTrees.dialog.inuse.title"),
        GitBundle.message("Git.WorkingTrees.dialog.inuse.message", tree.path.presentableUrl)
      )
        .yesText(GitBundle.message("Git.WorkingTrees.dialog.inuse.button.try.again"))
        .noText(CommonBundle.getCancelButtonText())
        .ask(project)
    }

    if (!shouldRetry) {
      return
    }

    try  {
      EelFileUtils.deleteRecursively(Path(tree.path.path))
    } catch (e: Exception) {
      notifyWorkingTreeDeletedError(project, e.message ?: "Unknown error while deleting working tree")
      return
    }

    notifyWorkingTreeDeletedSuccess(project, repository, tree)
  }


  fun pruneWorkingTrees(project: Project, repository: GitRepository) {
    coroutineScope.launch {
      val result = withBackgroundProgress(project, GitBundle.message("progress.title.pruning.worktrees"), cancellable = true) {
        service<Git>().pruneWorktrees(repository)
      }

      if (!result.success()) {
        VcsNotifier.getInstance(project).notifyError(
          GitNotificationIdsHolder.WORKING_TREES_PRUNING_FAILED,
          GitBundle.message("Git.WorkingTrees.prune.worktrees.failure.notification.title"),
          result.errorOutputAsHtmlString,
          true
        )
        return@launch
      }

      repository.workingTreeHolder.scheduleReload()
      VcsNotifier.getInstance(project).notifySuccess(
        GitNotificationIdsHolder.WORKING_TREES_PRUNED,
        "",
        GitBundle.message("Git.WorkingTrees.prune.worktree.success.message")
      )
    }
  }

  suspend fun openProjectInNewWindow(path: Path): Project? {
    val generalSettings = GeneralSettings.getInstance()
    val savedConfirmOpen = generalSettings.confirmOpenNewProject
    try {
      generalSettings.confirmOpenNewProject = GeneralSettings.OPEN_PROJECT_ASK
      return ProjectUtil.openOrImportAsync(path)
    }
    finally {
      generalSettings.confirmOpenNewProject = savedConfirmOpen
    }
  }
}

internal sealed class GitWorktreeSupportStatus {
  data object Unsupported : GitWorktreeSupportStatus()
  data class SingleRepository(val repository: GitRepository) : GitWorktreeSupportStatus()
  data class MultipleRepository(val repositories: List<GitRepository>) : GitWorktreeSupportStatus()
}
