// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.update.ActionInfo
import com.intellij.openapi.vcs.update.ScopeInfo
import com.intellij.openapi.vcs.update.VcsUpdateProcess
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectUpdater
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import git4idea.GitDisposable
import git4idea.GitNotificationIdsHolder
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.changes.GitChangeUtils
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Updates a recent Git project from the welcome screen without opening its frame.
 *
 * The project is already loaded and held by `RecentProjectService`, so this only runs the update on it. It delegates to the standard
 * update, whose notifications reach the welcome screen through the holder. Progress is shown in a modal dialog on the welcome frame.
 */
internal class GitRecentProjectUpdater : RecentProjectUpdater {
  override fun canUpdate(projectPath: Path): Boolean = findGitRoot(projectPath) != null

  override suspend fun update(project: Project, projectPath: Path) {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    vcsManager.awaitInitialization()
    ensureGitMapping(project, projectPath, vcsManager)

    val actionInfo = ActionInfo.UPDATE
    val scopeInfo = ScopeInfo.PROJECT
    val dataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project)

    val (roots, updateSpec) = withContext(Dispatchers.EDT) {
      VcsUpdateProcess.prepareUpdate(project, actionInfo, scopeInfo, dataContext, actionInfo.showOptions(project))
    } ?: return
    withModalProgress(ModalTaskOwner.guess(),
                      GitBundle.message("recent.project.update.progress", project.name),
                      TaskCancellation.cancellable()) {
      VcsUpdateProcess.update(project, roots, updateSpec, actionInfo, GitBundle.message("progress.title.update"))
    }
    notifyWhenConflictsResolved(project)
  }

  /**
   * Reports the finished update after the user resolves the update conflicts.
   *
   * The update stops with conflicts and reports them at once. The user resolves them later through the "Resolve conflicts" notification, which
   * finishes the merge or rebase and refreshes the repository. A frameless project shows no result then, so this waits for the last repository to
   * leave the merging or rebasing state and reports the finished update.
   */
  private fun notifyWhenConflictsResolved(project: Project) {
    val repositoryManager = GitRepositoryManager.getInstance(project)
    val hasConflicts = repositoryManager.repositories.any {
      runCatching { GitChangeUtils.getUnmergedFiles(it).isNotEmpty() }.getOrDefault(false)
    }
    if (!hasConflicts) return

    val connection = project.messageBus.connect(GitDisposable.getInstance(project))
    connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
      val resolved = repositoryManager.repositories.none {
        it.state == Repository.State.MERGING || it.state == Repository.State.REBASING
      }
      if (resolved) {
        connection.disconnect()
        notifyUpdated(project)
      }
    })
  }

  private fun notifyUpdated(project: Project) {
    VcsNotifier.getInstance(project)
      .notifySuccess(GitNotificationIdsHolder.PROJECT_UPDATED, GitBundle.message("recent.project.update.success", project.name), "")
  }

  /**
   * Registers the Git root mapping for the loaded project when it is not detected yet. The mapping stays in memory. The project is closed
   * without saving, so nothing is written to the project settings.
   */
  private suspend fun ensureGitMapping(project: Project, projectPath: Path, vcsManager: ProjectLevelVcsManager) {
    if (GitRepositoryManager.getInstance(project).repositories.isNotEmpty()) {
      return
    }
    val gitRoot = findGitRoot(projectPath) ?: return
    val mapping = VcsDirectoryMapping(gitRoot.invariantSeparatorsPathString, GitVcs.getKey().name)
    withContext(Dispatchers.EDT) {
      vcsManager.setDirectoryMappings(vcsManager.getDirectoryMappings() + mapping)
    }
    VcsRepositoryManager.getInstance(project).ensureUpToDate(force = true)
  }
}

private fun findGitRoot(projectPath: Path): Path? =
  generateSequence(projectPath) { it.parent }.firstOrNull { GitUtil.findGitDir(it) != null }
