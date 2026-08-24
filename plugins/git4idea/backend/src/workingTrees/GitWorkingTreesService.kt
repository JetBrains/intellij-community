// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.CommonBundle
import com.intellij.dvcs.repo.repositoryId
import com.intellij.ide.GeneralSettings
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
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
import com.intellij.openapi.util.io.FileUtil
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
import org.jetbrains.annotations.VisibleForTesting
import git4idea.workingTrees.ui.GitWorktreesUiUtil
import git4idea.GitNotificationIdsHolder
import git4idea.GitRemoteBranch
import git4idea.GitWorkingTree
import git4idea.workingTrees.dialog.GitWorktreeCreationRequest
import git4idea.workingTrees.dialog.WorktreeBranchSpec
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CancellationException
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
import org.jetbrains.annotations.ApiStatus
import java.awt.Window
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.minutes

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class GitWorkingTreesService(private val project: Project, val coroutineScope: CoroutineScope) {
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

    fun isWorktreeCreationSupported(repository: GitRepository): Boolean {
      val status = getWorktreeSupportStatus(repository.project)
      return status is GitWorktreeSupportStatus.SingleRepository && status.repository == repository
    }

    // The returned value distinguishes unsupported, single-repository, and multi-repository project states.
    internal fun getWorktreeSupportStatus(project: Project?): GitWorktreeSupportStatus {
      if (project == null || !GitWorkingTreesUtil.isWorkingTreesFeatureEnabled()) return GitWorktreeSupportStatus.Unsupported
      val repositories = findWorktreeCapableRepositories(project)
      return when (repositories.size) {
        0 -> GitWorktreeSupportStatus.Unsupported
        1 -> GitWorktreeSupportStatus.SingleRepository(repositories.single())
        else -> GitWorktreeSupportStatus.MultipleRepository(repositories)
      }
    }

    // All repositories a worktree can be created for. A linked working tree may itself be registered as a VCS
    // root; collapse those into their underlying repository so it is not counted/offered twice.
    fun findWorktreeCapableRepositories(project: Project): List<GitRepository> =
      GitWorkingTreesUtil.mergeLinkedWorktreeRepositories(
        GitRepositoryManager.getInstance(project).repositories,
        rootPath = { it.root.path },
        commonGitDirPath = { it.repositoryFiles.commonGitDir.path },
        workingTrees = { it.workingTreeHolder.getWorkingTrees() },
      )

    /**
     * The closest [candidates] path that is [worktreePath] itself or an ancestor of it (deepest wins), or
     * [worktreePath] when none owns it. Used to reopen the whole owning (possibly multi-root) project.
     */
    @VisibleForTesting
    internal fun resolveOwningProjectPath(worktreePath: Path, candidates: List<Path>): Path =
      candidates
        .filter { FileUtil.isAncestor(it.toString(), worktreePath.toString(), false) }
        .maxByOrNull { it.nameCount }
      ?: worktreePath

    /**
     * The path to open for [tree]: its owning project for the main worktree, or the worktree directory itself
     * for a linked worktree (which must not resolve to a parent project it lives inside).
     */
    @VisibleForTesting
    internal fun resolveProjectPathToOpen(tree: GitWorkingTree, candidates: List<Path>): Path {
      val worktreePath = Path(tree.path.path)
      return if (tree.isMain) resolveOwningProjectPath(worktreePath, candidates) else worktreePath
    }
  }

  fun repositoryToModel(repository: GitRepository): GitRepositoryModel? {
    return GitRepositoriesHolder.getInstance(project).get(repository.repositoryId())
  }

  fun shouldWorkingTreesTabBeShown(): Boolean {
    if (GitWorktreesUiUtil.isEmpty(project)) return false

    val value = PropertiesComponent.getInstance(project).getValue(WORKING_TREE_TAB_STATUS_PROPERTY)
    when (value) {
      WORKING_TREE_TAB_STATUS_CLOSED_BY_USER -> return false
      WORKING_TREE_TAB_STATUS_OPENED_BY_USER -> return true
    }

    return GitWorktreesUiUtil.anyRepositoryHasMultipleWorktrees(project)
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

  /**
   * @param reportOwnProgress whether to open a new top-level background progress for this operation. Pass `false`
   * when the caller already runs its own background progress (e.g. checking out a PR branch into a new worktree),
   * so this step just runs as part of it instead of opening a second progress window.
   */
  internal suspend fun createWorkingTree(request: GitWorktreeCreationRequest, reportOwnProgress: Boolean = true): Result {
    val runCommand: suspend () -> Result = {
      val branch = request.branch
      val newBranchName = when (branch) {
        is WorktreeBranchSpec.CreateNewBranch -> branch.newBranchName
        // A remote branch is checked out into a new local branch tracking it.
        is WorktreeBranchSpec.CheckoutExisting -> (branch.sourceRef as? GitRemoteBranch)?.nameForRemoteOperations
      }
      val commandResult = Git.getInstance().createWorkingTree(request.repository, request.workingTreePath, branch.sourceRef, newBranchName)
      if (commandResult.success()) {
        Result.SUCCESS
      }
      else {
        Result.createFailure(commandResult.errorOutputAsHtmlString)
      }
    }
    if (!reportOwnProgress) {
      return runCommand()
    }
    return withBackgroundProgress(project, GitBundle.message("progress.title.creating.worktree"), cancellable = true) {
      runCommand()
    }
  }

  fun isCurrentProjectLinkedWorktree(): Boolean {
    if (!GitWorkingTreesUtil.isWorkingTreesFeatureEnabled()) return false
    val repository = GitRepositoryManager.getInstance(project).repositories.singleOrNull() ?: return false
    return repository.workingTreeHolder.getWorkingTrees().any { it.isCurrent && !it.isMain }
  }

  fun deleteCurrentProjectWorktree() {
    val currentProject = project
    val worktrees = GitRepositoryManager.getInstance(currentProject).repositories.singleOrNull()
      ?.workingTreeHolder?.getWorkingTrees() ?: return
    val currentWorktree = worktrees.find { it.isCurrent && !it.isMain } ?: return
    val mainWorktreePath = worktrees.find { it.isMain }?.path?.path ?: return

    // Runs on the application scope, not the closing project's scope, and removes the worktree through the still-open
    // main project so the git command and notifications don't target the disposed worktree project.
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      val confirmed = withContext(Dispatchers.UiWithModelAccess) {
        MessageDialogBuilder.yesNo(
          GitBundle.message("Git.WorkingTrees.dialog.delete.worktree.title"),
          GitBundle.message("Git.WorkingTrees.delete.current.worktree.confirm.message", currentWorktree.path.name)
        )
          .yesText(GitBundle.message("Git.WorkingTrees.delete.worktrees.button.close.delete"))
          .noText(GitBundle.message("Git.WorkingTrees.delete.worktrees.button.do.not.delete"))
          .ask(currentProject)
      }
      if (!confirmed) return@launch

      closeProject(currentProject)

      val mainProject = ProjectUtil.findProject(Path(mainWorktreePath)) ?: return@launch
      val mainRepository = GitRepositoryManager.getInstance(mainProject).repositories.singleOrNull() ?: return@launch

      val commandResult = withBackgroundProgress(mainProject, GitBundle.message("progress.title.deleting.worktree"), cancellable = true) {
        service<Git>().deleteWorkingTree(mainRepository, currentWorktree)
      }
      if (commandResult.success()) {
        notifyWorkingTreeDeletedSuccess(mainProject, mainRepository, currentWorktree)
      }
      else {
        notifyWorkingTreeDeletedError(mainProject, commandResult.errorOutputAsHtmlString)
      }
    }
  }

  fun openWorkingTreeProject(tree: GitWorkingTree, onProjectOpened: ((Project) -> Unit)? = null) {
    // Opening a new window is intentionally tied to the application scope so it survives this project closing.
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.IO) {
      if (!Path(tree.path.path).exists()) {
        if (project.isDisposed) return@launch
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
      val worktreeProject = openProjectInNewWindow(resolveProjectPathToOpen(tree))
      if (worktreeProject != null) {
        onProjectOpened?.invoke(worktreeProject)
      }
    }
  }

  /**
   * The path to open for [tree]. A **linked** worktree is a standalone checkout — open its own directory,
   * never a parent project it happens to live inside (e.g. a worktree created under the currently open
   * project, which would otherwise resolve back to that already-open project and do nothing). Only the
   * **main** worktree resolves to its owning project, so that opening it restores a whole multi-root project.
   */
  private fun resolveProjectPathToOpen(tree: GitWorkingTree): Path {
    val candidates = buildList {
      ProjectManager.getInstance().openProjects.forEach { p -> p.basePath?.let { add(Path(it)) } }
      (RecentProjectsManager.getInstance() as? RecentProjectsManagerBase)?.getRecentPaths()?.forEach { add(Path(it)) }
    }
    return resolveProjectPathToOpen(tree, candidates)
  }

  fun deleteWorkingTree(project: Project, tree: GitWorkingTree, repository: GitRepository) {
    coroutineScope.launch {
      doDeleteWorkingTree(project, tree, repository)
    }
  }

  private suspend fun doDeleteWorkingTree(project: Project, tree: GitWorkingTree, repository: GitRepository) {
    val existingProject = ProjectUtil.findProject(Path(tree.path.path))
    if (existingProject != null) {
      if (shouldStopDeletion(project, tree, existingProject)) {
        closeProject(existingProject)
      }
      else {
        return
      }
    }

    val commandResult = withBackgroundProgress(project, GitBundle.message("progress.title.deleting.worktree"), cancellable = true) {
      service<Git>().deleteWorkingTree(repository, tree)
    }

    if (commandResult.success()) {
      notifyWorkingTreeDeletedSuccess(project, repository, tree)
      return
    }

    if (project.getEelDescriptor().osFamily.isWindows && isPermissionDenied(commandResult)) {
      handleFailedDeletionOnWindows(project, repository, tree)
    } else {
      notifyWorkingTreeDeletedError(project, commandResult.errorOutputAsHtmlString)
    }
  }

  private suspend fun shouldStopDeletion(project: Project, tree: GitWorkingTree, existingProject: Project): Boolean {
    return withContext(Dispatchers.UiWithModelAccess) {
      MessageDialogBuilder.yesNo(
        GitBundle.message("Git.WorkingTrees.dialog.delete.worktree.title"),
        GitBundle.message("Git.WorkingTrees.delete.worktrees.worktree.opened.close.or.cancel",
                          tree.path.name, existingProject.name)
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
    RecentProjectsManager.getInstance().removePath(tree.path.path)
    VcsNotifier.getInstance(project).notifySuccess(GitNotificationIdsHolder.WORKING_TREE_DELETED,
                                                   "",
                                                   GitBundle.message("Git.WorkingTrees.delete.worktree.success.message",
                                                                     tree.path.name))
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
    } catch (c: CancellationException) {
      throw c
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
