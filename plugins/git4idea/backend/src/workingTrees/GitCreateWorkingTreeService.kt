// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.dvcs.ui.CloneDvcsValidationUtils
import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.utils.EelSystemFolderUtils
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.withProgressText
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitBranch
import git4idea.GitOperationsCollector
import git4idea.GitReference
import git4idea.GitWorkingTree
import git4idea.actions.ref.GitSingleRefAction
import git4idea.branch.GitBranchUiHandler
import git4idea.branch.GitCheckoutInOtherWorktreeDialogs
import git4idea.commands.GitBranchAlreadyCheckedOutInOtherWorktreeDetector
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.util.EelUtils.getEel
import git4idea.workingTrees.dialog.GitWorkingTreeDialog
import git4idea.workingTrees.dialog.GitWorktreeCreationRequest
import git4idea.workingTrees.dialog.GitWorktreeDialogContext
import git4idea.workingTrees.dialog.WorktreeBranchSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.resume
import kotlin.io.path.Path
import kotlin.io.path.exists

@Service(Service.Level.APP)
internal class GitCreateWorkingTreeService(private val coroutineScope: CoroutineScope) {

  companion object {
    @JvmStatic
    fun getInstance(): GitCreateWorkingTreeService = service()

    private val LOG = logger<GitCreateWorkingTreeService>()

    private const val LAST_PARENT_PATH_KEY = "Git.CreateWorkingTree.LastParentPath"
    private const val MAX_WORKTREE_DIR_NAME_LENGTH = 100

    //The system temp directory, resolved in the [eel]'s own environment (WSL/Docker/local)
    @RequiresBackgroundThread(generateAssertion = false)
    @VisibleForTesting
    internal fun getSystemTempDir(eel: EelApi): Path = EelSystemFolderUtils.getSystemFolder(eel).resolve("tmp")

    //The default new-project directory for a local [eel], or that environment's home directory otherwise. A
    //remote environment has no notion of the IDE host's "default project" setting.
    @VisibleForTesting
    internal fun getDefaultParentDir(eel: EelApi): Path {
      if (eel is LocalEelApi) return Path(ProjectUtil.getBaseDir())
      return eel.userInfo.home.asNioPath()
    }
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
    val branchSpec =
      if (newBranchName != null) WorktreeBranchSpec.CreateNewBranch(branch, newBranchName) else WorktreeBranchSpec.CheckoutExisting(branch)
    val request = GitWorktreeCreationRequest(repository, VcsUtil.getFilePath(worktreeDir, true), branchSpec)
    val ideActivity = GitOperationsCollector.logCreateWorktreeActionInvoked(repository.project, place, branch)
    doCreateWorkingTree(ideActivity, request, onProjectOpened, force, reportOwnProgress = false)
  }

  @VisibleForTesting
  internal suspend fun confirmCreateNewWorktreeInsteadOfOpening(project: Project, branch: GitBranch, worktreePath: String?): Boolean {
    val decision = withContext(Dispatchers.UiWithModelAccess) {
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

  private val _pendingCreations = MutableStateFlow<Map<FilePath, GitWorktreePendingCreation>>(emptyMap())

  /** Worktrees whose `git worktree add` is still running, keyed by target path; feeds the tab's synthetic "creating" rows. */
  val pendingCreations: StateFlow<Map<FilePath, GitWorktreePendingCreation>> = _pendingCreations.asStateFlow()

  internal fun isWorkingTreeCreationInProgress(workingTree: GitWorkingTree): Boolean {
    return _pendingCreations.value.containsKey(workingTree.path)
  }

  internal fun collectDataAndCreateWorkingTree(
    repository: GitRepository,
    refFromContext: GitReference?,
    place: String,
    candidateRepositories: List<GitRepository> = listOf(repository),
    openAfterCreation: Boolean = true,
    onProjectOpened: ((Project) -> Unit)? = null,
  ) {
    val project = repository.project
    val ideActivity = GitOperationsCollector.logCreateWorktreeActionInvoked(project, place, refFromContext)
    coroutineScope.launch(Dispatchers.Default) {
      val (eel, systemTempDir) = withContext(Dispatchers.IO) {
        val eel = getEel(project)
        if (eel == null) {
          LOG.warn("Not opening the New Worktree dialog")
          return@withContext null
        }
        eel to getSystemTempDir(eel)
      } ?: return@launch
      val dialogContext = readAction {
        val lastParentPath = loadLastParentPath(project)
        val initialParentPath = computeInitialParentPath(project, repository, systemTempDir) { getDefaultParentDir(eel) }
        GitWorktreeDialogContext(project, repository, ideActivity, refFromContext,
                                 lastParentPath ?: initialParentPath, candidateRepositories, openAfterCreation)
      }

      withContext(Dispatchers.UiWithModelAccess) {
        var currentDialogContext = dialogContext
        while (true) {
          val dialog = GitWorkingTreeDialog(currentDialogContext)
          dialog.show()
          when (dialog.exitCode) {
            DialogWrapper.OK_EXIT_CODE -> {
              val request = dialog.getWorkTreeData()
              request.workingTreePath.parentPath?.path?.let { saveLastParentPath(project, it) }
              withContext(Dispatchers.Default) {
                doCreateWorkingTree(dialogContext.ideActivity, request, onProjectOpened,
                                    openAfterCreation = dialogContext.openAfterCreation)
              }
              return@withContext
            }
            GitWorkingTreeDialog.CREATE_WORKTREE_INCLUDE_EXIT_CODE -> {
              // The dialog already wrote the file and opened it for editing. Keep its field values, so the
              // dialog can reopen with the same choices once the user asks to return to it.
              currentDialogContext = currentDialogContext.copy(initialState = dialog.captureState())
              val targetFile = dialog.worktreeIncludeFile
              val result = if (targetFile != null) {
                waitForReturnToDialogAction(project, targetFile)
              }
              else {
                WorktreeIncludeEditResult.ABORTED
              }
              // waitForReturnToDialogAction also resumes when the project disposes first (see its doc
              // comment) or when the user closes the editor tab without clicking the banner action.
              // In those cases, terminate the wizard loop instead of reopening the dialog.
              if (project.isDisposed || result == WorktreeIncludeEditResult.ABORTED) return@withContext
            }
            else -> return@withContext
          }
        }
      }
    }
  }

  /**
   * Suspends until the user invokes the "Return to the dialog" action on [file]'s editor notification
   * banner (see [GitWorktreeIncludeEditorNotificationProvider]) or closes the editor tab. Teardown runs
   * through a [Disposer]-registered [Disposable] so it also fires if [project] disposes first, instead of leaking
   * the coroutine.
   */
  @VisibleForTesting
  internal suspend fun waitForReturnToDialogAction(project: Project, file: VirtualFile): WorktreeIncludeEditResult {
    val session = GitWorktreeIncludeEditingSessionService.getInstance(project)
    // Registered under project so a project disposal still resumes the coroutine, but disposed in the `finally`
    // below so it doesn't linger on the project's disposable tree once this call is done, e.g. after just closing
    // the editor tab.
    val disposable = Disposer.newDisposable()
    try {
      return suspendCancellableCoroutine { continuation ->
        val connection = project.messageBus.connect()
        var resumed = false

        fun finish(result: WorktreeIncludeEditResult) {
          if (resumed) return
          resumed = true
          session.clearPendingReturn(file)
          connection.disconnect()
          if (continuation.isActive) {
            continuation.resume(result)
          }
        }

        session.setPendingReturn(file) {
          finish(WorktreeIncludeEditResult.RETURN_TO_DIALOG)
        }

        @Suppress("SplitModeApiUsage")
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
          override fun fileClosed(source: FileEditorManager, closedFile: VirtualFile) {
            if (closedFile == file) {
              finish(WorktreeIncludeEditResult.ABORTED)
            }
          }
        })

        Disposer.register(disposable, Disposable { finish(WorktreeIncludeEditResult.ABORTED) })
        @Suppress("IncorrectParentDisposable")
        Disposer.register(project, disposable)
        continuation.invokeOnCancellation { finish(WorktreeIncludeEditResult.ABORTED) }
      }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  /**
   * Searches for a directory that doesn't lie under any of roots of the [project]. Falls back to [defaultParentDir]
   * when that search fails to leave [systemTempDir], e.g. when [project] itself is a scratch worktree opened
   * from a temp directory (IJPL-252877). [defaultParentDir] runs only on that fallback path.
   */
  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  internal fun computeInitialParentPath(project: Project, repository: GitRepository, systemTempDir: Path, defaultParentDir: () -> Path): String {
    val fromProject = project.guessProjectDir()?.parent
    var root: VirtualFile? = fromProject ?: repository.root.parent
    val index = ProjectFileIndex.getInstance(project)
    while (root != null && index.isInProjectOrExcluded(root)) {
      root = root.parent
    }
    if (root == null || Path(root.path).startsWith(systemTempDir)) {
      return defaultParentDir().toString()
    }
    return root.path
  }

  @VisibleForTesting
  internal suspend fun doCreateWorkingTree(
    ideActivity: StructuredIdeActivity,
    request: GitWorktreeCreationRequest,
    onProjectOpened: ((Project) -> Unit)? = null,
    force: Boolean = false,
    reportOwnProgress: Boolean = true,
    openAfterCreation: Boolean = true,
  ) {
    GitOperationsCollector.logWorktreeCreationDialogExitedWithOk(ideActivity, request)

    val project = request.repository.project
    val path = request.workingTreePath.path
    val destinationValidation = CloneDvcsValidationUtils.createDestination(path)
    if (destinationValidation != null) {
      GitWorktreeNotifications.notifyCouldNotCreateTargetDir(project, destinationValidation.message)
      return
    }

    val gitWTService = GitWorkingTreesService.getInstance(project)
    val targetWorktreeDir = Path(request.workingTreePath.path)

    val workingTreeCreated = if (reportOwnProgress) {
      withBackgroundProgress(project, GitBundle.message("progress.title.creating.worktree"), cancellable = true) {
        createAndConfigureWorkingTree(project, request, gitWTService, targetWorktreeDir, force)
      }
    }
    else {
      createAndConfigureWorkingTree(project, request, gitWTService, targetWorktreeDir, force)
    }
    if (!workingTreeCreated) return

    if (!openAfterCreation) {
      registerAsRecentProject(project, targetWorktreeDir)
      request.repository.workingTreeHolder.scheduleReload()
      GitWorktreeNotifications.notifyWorktreeCreated(project, request.branch.presentableBranchName, targetWorktreeDir)
      return
    }

    openWorktreeProject(project, request, gitWTService, targetWorktreeDir, ideActivity, onProjectOpened)
  }

  /**
   * Runs the git command and, on success, copies the local project configuration into it, all as one progress: the
   * caller passes reportOwnProgress = false when it already runs its own background progress around this call.
   * Returns `true` only when the worktree itself was created. A post-creation step failing does not stop the
   * project from opening, since each such step already shows its own error notification.
   */
  private suspend fun createAndConfigureWorkingTree(
    project: Project,
    request: GitWorktreeCreationRequest,
    gitWTService: GitWorkingTreesService,
    targetWorktreeDir: Path,
    force: Boolean,
  ): Boolean {
    if (!createGitWorkingTree(project, request, gitWTService, force)) return false

    withProgressText(GitBundle.message("progress.text.worktree.copying.settings")) {
      copyProjectConfigStep(project, request, targetWorktreeDir)

      val configCopyContext = GitWorktreeConfigCopyContext(project, request.repository.root, targetWorktreeDir)
      runAdditionalConfigCopiers(project, request, configCopyContext, targetWorktreeDir)
      applyAdditionalSettingsHandles(project, request, configCopyContext, targetWorktreeDir)
      markWorktreeTrusted(project, targetWorktreeDir)
    }

    runSetupScriptStep(project, request, targetWorktreeDir)
    refreshCopiedConfigDirectories(targetWorktreeDir)
    return true
  }

  /**
   * Backstops [runAdditionalConfigCopiers] and [applyAdditionalSettingsHandles]: refreshes `.idea`/`.run` in
   * case either wrote there through a raw NIO call, same as [GitWorktreeProjectConfigService] already does
   * for its own copied files. Without a refresh, the native file watcher has to work through the whole
   * freshly checked-out worktree first, and the new project can open and show stale or empty settings for
   * minutes.
   */
  private fun refreshCopiedConfigDirectories(targetWorktreeDir: Path) {
    val localFileSystem = LocalFileSystem.getInstance()
    for (relativeDir in listOf(".idea", ".run")) {
      val virtualFile = localFileSystem.refreshAndFindFileByNioFile(targetWorktreeDir.resolve(relativeDir)) ?: continue
      VfsUtil.markDirtyAndRefresh(false, true, true, virtualFile)
    }
  }

  /** Creates the worktree at the git level, retrying once if it's already checked out elsewhere and the user
   *  confirms doing it anyway. Notifies and returns false on failure. */
  private suspend fun createGitWorkingTree(
    project: Project,
    request: GitWorktreeCreationRequest,
    gitWTService: GitWorkingTreesService,
    force: Boolean,
  ): Boolean {
    var result = createWorkingTreeTracked(gitWTService, request, force, reportOwnProgress = false)
    if (result.success) return true

    val otherWorktreeMatch = GitBranchAlreadyCheckedOutInOtherWorktreeDetector.matchInOutput(result.errorOutput)
    if (otherWorktreeMatch != null &&
        confirmCreateWorktreeIgnoringOtherWorktree(project, otherWorktreeMatch.branchName, otherWorktreeMatch.worktreePath)) {
      result = createWorkingTreeTracked(gitWTService, request, force = true, reportOwnProgress = false)
    }
    if (result.success) return true

    GitWorktreeNotifications.notifyWorktreeAddFailed(project, result.errorOutputAsHtmlString)
    return false
  }

  /** Copies every .worktreeinclude match into the new worktree. Never throws; notifies on any failure. */
  private suspend fun copyProjectConfigStep(project: Project, request: GitWorktreeCreationRequest, targetWorktreeDir: Path) {
    if (!request.copyWorktreeIncludeMatches) return

    try {
      val failedFiles = GitWorktreeProjectConfigService.getInstance(project)
        .copyAndCleanUpWorktreeIncludeFiles(request.repository.root, targetWorktreeDir)

      if (failedFiles.isNotEmpty()) {
        GitWorktreeNotifications.notifyConfigCopyFailed(project, failedFiles)
      }
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.error("Failed to copy the local project configuration into the new worktree", e)
      GitWorktreeNotifications.notifyConfigCopyFailedUnexpectedly(project, targetWorktreeDir)
    }
  }

  /** Runs every enabled [GitWorktreeAdditionalConfigCopier] extension; notifies once if any of them failed. */
  private suspend fun runAdditionalConfigCopiers(
    project: Project,
    request: GitWorktreeCreationRequest,
    configCopyContext: GitWorktreeConfigCopyContext,
    targetWorktreeDir: Path,
  ) {
    if (!GitWorktreeAdditionalConfigCopier.copyAll(configCopyContext, request.enabledAdditionalConfigCopiers)) {
      GitWorktreeNotifications.notifyAdditionalConfigCopyFailed(project, targetWorktreeDir)
    }
  }

  /** Runs every additional settings panel's post-creation action. Notifies (and logs) per handle that throws,
   *  and keeps running the rest. */
  private suspend fun applyAdditionalSettingsHandles(
    project: Project,
    request: GitWorktreeCreationRequest,
    configCopyContext: GitWorktreeConfigCopyContext,
    targetWorktreeDir: Path,
  ) {
    for (handle in request.additionalSettingsHandles) {
      try {
        handle.apply(configCopyContext)
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        LOG.error("Failed to apply additional worktree settings", e)
        GitWorktreeNotifications.notifyAdditionalSettingsFailed(project, targetWorktreeDir)
      }
    }
  }

  /** Marks the new worktree as trusted. Notifies (and logs) if that fails, without stopping the flow. */
  private fun markWorktreeTrusted(project: Project, targetWorktreeDir: Path) {
    try {
      TrustedProjects.setProjectTrusted(targetWorktreeDir, true)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.error("Failed to mark the new worktree as trusted", e)
      GitWorktreeNotifications.notifyTrustFailed(project, targetWorktreeDir)
    }
  }

  /** Runs the request's setup script, if any. Notifies on failure. */
  private suspend fun runSetupScriptStep(project: Project, request: GitWorktreeCreationRequest, targetWorktreeDir: Path) {
    val scriptPath = request.setupScriptPath ?: return
    val succeeded = withProgressText(GitBundle.message("progress.text.worktree.running.setup.script")) {
      GitWorktreeSetupScriptRunner.runSetupScript(project, scriptPath, targetWorktreeDir)
    }
    if (!succeeded) {
      GitWorktreeNotifications.notifySetupScriptFailed(project, scriptPath)
    }
  }

  /**
   * Opens the new worktree's project on a scope detached from [project]: opening in the same window closes and
   * disposes the current project, so this must survive that disposal instead of being cancelled by it. A
   * failure only logs once [project] is already disposed, since a notification on it would not show.
   */
  private fun openWorktreeProject(
    project: Project,
    request: GitWorktreeCreationRequest,
    gitWTService: GitWorkingTreesService,
    targetWorktreeDir: Path,
    ideActivity: StructuredIdeActivity,
    onProjectOpened: ((Project) -> Unit)?,
  ) {
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.Default) {
      try {
        val worktreeProject = withProgressText(GitBundle.message("progress.text.worktree.opening.project")) {
          gitWTService.openProjectInNewWindow(targetWorktreeDir)
        }

        if (worktreeProject != null) {
          GitOperationsCollector.logWorktreeProjectOpenedAfterCreation(ideActivity)
          onProjectOpened?.invoke(worktreeProject)
        } else {
          request.repository.workingTreeHolder.scheduleReload()
        }
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        LOG.error("Failed to open the new worktree project", e)
        if (!project.isDisposed) {
          GitWorktreeNotifications.notifyOpenProjectFailed(project, targetWorktreeDir)
          request.repository.workingTreeHolder.scheduleReload()
        }
      }
    }
  }

  private suspend fun registerAsRecentProject(project: Project, worktreePath: Path) {
    val pathToOpen = resolveWorktreeOpenPath(project, worktreePath)
    val recentProjectsManager = serviceAsync<RecentProjectsManager>() as? RecentProjectsManagerBase ?: return
    recentProjectsManager.addRecentPath(pathToOpen, RecentProjectMetaInfo())
    withContext(Dispatchers.EDT) {
      ApplicationManager.getApplication().messageBus.syncPublisher(RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC).change()
    }
  }

  private suspend fun createWorkingTreeTracked(
    gitWTService: GitWorkingTreesService,
    request: GitWorktreeCreationRequest,
    force: Boolean,
    reportOwnProgress: Boolean,
  ): GitWorkingTreesService.Result {
    val pending = GitWorktreePendingCreation.from(request)
    _pendingCreations.update { it + (request.workingTreePath to pending) }
    try {
      return gitWTService.createWorkingTree(request, force, reportOwnProgress)
    }
    finally {
      _pendingCreations.update { it - request.workingTreePath }
    }
  }

  @VisibleForTesting
  internal suspend fun confirmCreateWorktreeIgnoringOtherWorktree(
    project: Project,
    branchName: String,
    worktreePath: String?,
  ): Boolean {
    val decision = withContext(Dispatchers.UiWithModelAccess) {
      GitCheckoutInOtherWorktreeDialogs.buildAndShow(
        project, branchName, worktreePath,
        GitBundle.message("working.tree.dialog.branch.already.checked.out.confirm.create.anyway"),
        GitCheckoutInOtherWorktreeDialogs.ButtonSet.PROCEED_OR_CANCEL)
    }
    return decision == GitBranchUiHandler.CheckoutInOtherWorktreeDecision.CHECKOUT_ANYWAY
  }
}

internal enum class WorktreeIncludeEditResult {
  RETURN_TO_DIALOG,
  ABORTED,
}
