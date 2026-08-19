// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.dvcs.ui.CloneDvcsValidationUtils
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.provider.utils.EelSystemFolderUtils
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.util.progress.withProgressText
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.VisibleForTesting
import git4idea.GitBranch
import git4idea.GitNotificationIdsHolder
import git4idea.GitReference
import git4idea.GitOperationsCollector
import git4idea.GitWorkingTree
import git4idea.actions.ref.GitSingleRefAction
import git4idea.branch.GitBranchUiHandler
import git4idea.branch.GitCheckoutInOtherWorktreeDialogs
import git4idea.commands.GitBranchAlreadyCheckedOutInOtherWorktreeDetector
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.workingTrees.dialog.GitWorkingTreeDialog
import git4idea.workingTrees.dialog.GitWorktreeCreationRequest
import git4idea.workingTrees.dialog.GitWorktreeDialogContext
import git4idea.workingTrees.dialog.WorktreeBranchSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

@Service(Service.Level.APP)
internal class GitCreateWorkingTreeService(private val coroutineScope: CoroutineScope) {

  companion object {
    @JvmStatic
    fun getInstance(): GitCreateWorkingTreeService = service()

    private const val LAST_PARENT_PATH_KEY = "Git.CreateWorkingTree.LastParentPath"
    private const val MAX_WORKTREE_DIR_NAME_LENGTH = 100

    //The [project]'s system temp directory, resolved in the same Eel environment (WSL/Docker/local) as the project itself
    @RequiresBackgroundThread(generateAssertion = false)
    internal fun getSystemTempDir(project: Project): Path = EelSystemFolderUtils.getSystemFolder(project).resolve("tmp")
  }

  /**
   * Without showing the New Worktree dialog, opens [branch] in a worktree under [parentDir]: creates a new one named
   * [worktreeName] (a unique suffix is appended on collision), or opens the existing worktree if [branch] is already
   * checked out in one (no-op if that is the current worktree). Suspends until done so it stays under the caller's progress.
   */
  internal suspend fun createOrOpenWorktreeForBranch(
    repository: GitRepository,
    branch: GitBranch,
    parentDir: Path,
    worktreeName: String,
    place: String,
    newBranchName: String? = null,
    onProjectOpened: ((Project) -> Unit)? = null,
  ) {
    if (!GitWorkingTreesService.isWorktreeCreationSupported(repository)) return

    val existingWorkingTree = GitSingleRefAction.findCheckedOutWorkingTree(branch, listOf(repository), skipCurrentWorkingTree = false)
    var force = false
    if (existingWorkingTree != null) {
      // If the branch is checked out in the current worktree there's nothing to open; the popup item is hidden in that case.
      if (existingWorkingTree.isCurrent) return
      if (!confirmCreateNewWorktreeInsteadOfOpening(repository.project, branch, existingWorkingTree.path.path)) {
        GitWorkingTreesService.getInstance(repository.project).openWorkingTreeProject(existingWorkingTree, onProjectOpened)
        return
      }
      force = true
    }

    val dirName = sanitizeFileName(worktreeName, extraIllegalChars = { it.isWhitespace() })
      .take(MAX_WORKTREE_DIR_NAME_LENGTH).trimEnd('-', '_', '.')
    val worktreeDir = withContext(Dispatchers.IO) {
      Files.createDirectories(parentDir)
      parentDir.resolve(UniqueNameGenerator.generateUniqueName(dirName) { !parentDir.resolve(it).exists() })
    }
    val branchSpec = if (newBranchName != null) WorktreeBranchSpec.CreateNewBranch(branch, newBranchName) else WorktreeBranchSpec.CheckoutExisting(branch)
    val request = GitWorktreeCreationRequest(repository, VcsUtil.getFilePath(worktreeDir, true), branchSpec)
    val ideActivity = GitOperationsCollector.logCreateWorktreeActionInvoked(repository.project, place, branch)
    doCreateWorkingTree(ideActivity, request, onProjectOpened, force, reportOwnProgress = false)
  }

  @VisibleForTesting
  internal suspend fun confirmCreateNewWorktreeInsteadOfOpening(project: Project, branch: GitBranch, worktreePath: String?): Boolean {
    val decision = withContext(Dispatchers.UI) {
      GitCheckoutInOtherWorktreeDialogs.buildAndShow(
        project, branch.name, worktreePath,
        GitBundle.message("working.tree.dialog.branch.already.checked.out.confirm.create.anyway"),
        GitCheckoutInOtherWorktreeDialogs.ButtonSet.PROCEED_OR_OPEN_EXISTING)
    }
    return decision == GitBranchUiHandler.CheckoutInOtherWorktreeDecision.CHECKOUT_ANYWAY
  }

  private fun loadLastParentPath(project: Project): String? =
    PropertiesComponent.getInstance(project).getValue(LAST_PARENT_PATH_KEY)

  private fun saveLastParentPath(project: Project, path: String) {
    PropertiesComponent.getInstance(project).setValue(LAST_PARENT_PATH_KEY, path)
  }

  private val rootsUnderCreation = ContainerUtil.newConcurrentSet<FilePath>()

  internal fun isWorkingTreeCreationInProgress(workingTree: GitWorkingTree): Boolean {
    return rootsUnderCreation.contains(workingTree.path)
  }

  internal fun collectDataAndCreateWorkingTree(
    repository: GitRepository,
    refFromContext: GitReference?,
    place: String,
    candidateRepositories: List<GitRepository> = listOf(repository),
    onProjectOpened: ((Project) -> Unit)? = null,
  ) {
    val project = repository.project
    val ideActivity = GitOperationsCollector.logCreateWorktreeActionInvoked(project, place, refFromContext)
    coroutineScope.launch(Dispatchers.Default) {
      val systemTempDir = withContext(Dispatchers.IO) { getSystemTempDir(project) }
      val dialogContext = readAction {
        val lastParentPath = loadLastParentPath(project)
        val initialParentPath = computeInitialParentPath(project, repository, systemTempDir)
        GitWorktreeDialogContext(project, repository, ideActivity, refFromContext,
                                 lastParentPath ?: initialParentPath, candidateRepositories)
      }

      withContext(Dispatchers.UiWithModelAccess) {
        val dialog = GitWorkingTreeDialog(dialogContext)
        if (dialog.showAndGet()) {
          val request = dialog.getWorkTreeData()
          request.workingTreePath.parentPath?.path?.let { saveLastParentPath(project, it) }
          withContext(Dispatchers.Default) {
            doCreateWorkingTree(dialogContext.ideActivity, request, onProjectOpened)
          }
        }
      }
    }
  }

  /**
   * Searches for a directory that doesn't lie under any of roots of the [project]. Falls back to the platform's
   * default new-project directory ([ProjectUtil.getBaseDir]) when that search fails to leave [systemTempDir],
   * e.g. when [project] itself is a scratch worktree opened from a temp directory (IJPL-252877).
   */
  @RequiresReadLock
  internal fun computeInitialParentPath(project: Project, repository: GitRepository, systemTempDir: Path): String {
    val fromProject = project.guessProjectDir()?.parent
    var root: VirtualFile? = fromProject ?: repository.root.parent
    val index = ProjectFileIndex.getInstance(project)
    while (root != null && index.isInProjectOrExcluded(root)) {
      root = root.parent
    }
    if (root == null || Path(root.path).startsWith(systemTempDir)) {
      return ProjectUtil.getBaseDir()
    }
    return root.path
  }

  private suspend fun doCreateWorkingTree(
    ideActivity: StructuredIdeActivity,
    request: GitWorktreeCreationRequest,
    onProjectOpened: ((Project) -> Unit)? = null,
    force: Boolean = false,
    reportOwnProgress: Boolean = true,
  ) {
    GitOperationsCollector.logWorktreeCreationDialogExitedWithOk(ideActivity, request)

    val project = request.repository.project
    val path = request.workingTreePath.path
    val destinationValidation = CloneDvcsValidationUtils.createDestination(path)
    if (destinationValidation != null) {
      VcsNotifier.getInstance(project).notifyError(GitNotificationIdsHolder.WORKTREE_COULD_NOT_CREATE_TARGET_DIR,
                                                   GitBundle.message("notification.title.worktree.creation.failed"),
                                                   destinationValidation.message,
                                                   true)
      return
    }

    val gitWTService = GitWorkingTreesService.getInstance(project)
    var result = createWorkingTreeTracked(gitWTService, request, force, reportOwnProgress)

    if (!result.success) {
      val otherWorktreeMatch = GitBranchAlreadyCheckedOutInOtherWorktreeDetector.matchInOutput(result.errorOutput)
      if (otherWorktreeMatch != null &&
          confirmCreateWorktreeIgnoringOtherWorktree(project, otherWorktreeMatch.branchName, otherWorktreeMatch.worktreePath)) {
        result = createWorkingTreeTracked(gitWTService, request, force = true, reportOwnProgress)
      }
      if (!result.success) {
        VcsNotifier.getInstance(project).notifyError(GitNotificationIdsHolder.WORKTREE_ADD_FAILED,
                                                     GitBundle.message("notification.title.worktree.creation.failed"),
                                                     result.errorOutputAsHtmlString,
                                                     true)
        return
      }
    }

    TrustedProjects.setProjectTrusted(Path(request.workingTreePath.path), true)

    // Opening the worktree project in the same window closes (and disposes) the current project, so this must not run
    // on a scope tied to it - otherwise the operation is cancelled before the new project finishes opening.
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.Default) {
      val worktreeProject = withProgressText(GitBundle.message("progress.text.worktree.opening.project")) {
        gitWTService.openProjectInNewWindow(Path(request.workingTreePath.path))
      }

      if (worktreeProject != null) {
        GitOperationsCollector.logWorktreeProjectOpenedAfterCreation(ideActivity)
        onProjectOpened?.invoke(worktreeProject)
      } else {
        request.repository.workingTreeHolder.scheduleReload()
      }
    }
  }

  private suspend fun createWorkingTreeTracked(
    gitWTService: GitWorkingTreesService,
    request: GitWorktreeCreationRequest,
    force: Boolean,
    reportOwnProgress: Boolean,
  ): GitWorkingTreesService.Result {
    rootsUnderCreation.add(request.workingTreePath)
    try {
      return gitWTService.createWorkingTree(request, force, reportOwnProgress)
    }
    finally {
      rootsUnderCreation.remove(request.workingTreePath)
    }
  }

  @VisibleForTesting
  internal suspend fun confirmCreateWorktreeIgnoringOtherWorktree(
    project: Project,
    branchName: String,
    worktreePath: String?,
  ): Boolean {
    val decision = withContext(Dispatchers.UI) {
      GitCheckoutInOtherWorktreeDialogs.buildAndShow(
        project, branchName, worktreePath,
        GitBundle.message("working.tree.dialog.branch.already.checked.out.confirm.create.anyway"),
        GitCheckoutInOtherWorktreeDialogs.ButtonSet.PROCEED_OR_CANCEL)
    }
    return decision == GitBranchUiHandler.CheckoutInOtherWorktreeDecision.CHECKOUT_ANYWAY
  }
}
