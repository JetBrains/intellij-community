// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.workingTree

import com.intellij.dvcs.ui.CloneDvcsValidationUtils
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.StructuredIdeActivity
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
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ContainerUtil
import git4idea.GitNotificationIdsHolder
import git4idea.GitReference
import git4idea.GitOperationsCollector
import git4idea.GitWorkingTree
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitWorkingTreesService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.io.path.Path

@Service(Service.Level.APP)
internal class GitCreateWorkingTreeService(private val coroutineScope: CoroutineScope) {

  companion object {
    @JvmStatic
    fun getInstance(): GitCreateWorkingTreeService = service()

    private const val LAST_PARENT_PATH_KEY = "Git.CreateWorkingTree.LastParentPath"
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
  ) {
    val project = repository.project
    val ideActivity = GitOperationsCollector.logCreateWorktreeActionInvoked(project, place, refFromContext)
    coroutineScope.launch(Dispatchers.Default) {
      val preDialogData = readAction {
        val lastParentPath = loadLastParentPath(project)
        val initialParentPath = computeInitialParentPath(project, repository)?.path
        GitWorkingTreePreDialogData(project, repository, ideActivity, refFromContext,
                                    lastParentPath ?: initialParentPath)
      }

      withContext(Dispatchers.UiWithModelAccess) {
        val dialog = GitWorkingTreeDialog(preDialogData)
        if (dialog.showAndGet()) {
          val workingTreeData = dialog.getWorkTreeData()
          workingTreeData.workingTreePath.parentPath?.path?.let { saveLastParentPath(project, it) }
          withContext(Dispatchers.Default) {
            doCreateWorkingTree(preDialogData.project, preDialogData.repository, preDialogData.ideActivity, workingTreeData)
          }
        }
      }
    }
  }

  /**
   * Searches for a directory that doesn't lie under any of roots of the [project].
   */
  @RequiresReadLock
  private fun computeInitialParentPath(project: Project, repository: GitRepository): VirtualFile? {
    val fromProject = project.guessProjectDir()?.parent
    var root: VirtualFile? = fromProject ?: repository.root.parent
    val index = ProjectFileIndex.getInstance(project)
    while (root != null && index.isInProjectOrExcluded(root)) {
      root = root.parent
    }
    return root
  }

  private suspend fun doCreateWorkingTree(
    project: Project,
    repository: GitRepository,
    ideActivity: StructuredIdeActivity,
    workingTreeData: GitWorkingTreeDialogData,
  ) {
    GitOperationsCollector.logWorktreeCreationDialogExitedWithOk(ideActivity, workingTreeData)

    val path = workingTreeData.workingTreePath.path
    val destinationValidation = CloneDvcsValidationUtils.createDestination(path)
    if (destinationValidation != null) {
      VcsNotifier.getInstance(project).notifyError(GitNotificationIdsHolder.WORKTREE_COULD_NOT_CREATE_TARGET_DIR,
                                                   GitBundle.message("notification.title.worktree.creation.failed"),
                                                   destinationValidation.message,
                                                   true)
      return
    }

    rootsUnderCreation.add(workingTreeData.workingTreePath)
    val gitWTService = GitWorkingTreesService.getInstance(project)
    val result = try {
      gitWTService.createWorkingTree(repository, workingTreeData)
    }
    finally {
      rootsUnderCreation.remove(workingTreeData.workingTreePath)
    }

    if (!result.success) {
      VcsNotifier.getInstance(project).notifyError(GitNotificationIdsHolder.WORKTREE_ADD_FAILED,
                                                   GitBundle.message("notification.title.worktree.creation.failed"),
                                                   result.errorOutputAsHtmlString,
                                                   true)
      return
    }

    TrustedProjects.setProjectTrusted(Path(workingTreeData.workingTreePath.path), true)

    val worktreeProject = gitWTService.openProjectInNewWindow(Path(workingTreeData.workingTreePath.path))

    if (worktreeProject != null) {
      GitOperationsCollector.logWorktreeProjectOpenedAfterCreation(ideActivity)
    } else {
      repository.workingTreeHolder.scheduleReload()
    }
  }
}
