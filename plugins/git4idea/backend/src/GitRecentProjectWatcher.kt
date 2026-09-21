// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.update.UpdatedFilesListener
import git4idea.changes.GitChangeUtils
import git4idea.i18n.GitBundle
import git4idea.repo.GitRecentProjectsBranchesService
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reports what a recent project the welcome screen holds does, since such a project has no frame of its own to report it.
 *
 * It keeps two things in step:
 * - The branch the recent project row shows. [GitRecentProjectsBranchesService] caches it from `.git/HEAD` and reloads it only on a timer
 *   or when the IDE is activated, but a branch action on a held project moves HEAD right away, so the cached branch is reloaded on every
 *   repository change and the row shows the new branch.
 * - The end of an update that stopped with conflicts, see [notifyWhenConflictsResolved].
 *
 * Every Git action on a recent project installs the watcher through [prepareRecentGitProject]; it is installed once and ends with the
 * project.
 */
@Service(Service.Level.PROJECT)
internal class GitRecentProjectWatcher(private val project: Project) {
  private val installed = AtomicBoolean()
  private val awaitingConflictResolution = AtomicBoolean()

  fun watch(projectPath: Path) {
    if (!installed.compareAndSet(false, true)) return
    val connection = project.messageBus.connect(GitDisposable.getInstance(project))
    connection.subscribe(
      GitRepository.GIT_REPO_CHANGE,
      GitRepositoryChangeListener { service<GitRecentProjectsBranchesService>().refresh(projectPath) },
    )
    connection.subscribe(UpdatedFilesListener.UPDATED_FILES, UpdatedFilesListener {
      GitDisposable.getInstance(project).coroutineScope.launch { notifyWhenConflictsResolved() }
    })
  }

  /**
   * Reports the finished update once the user has resolved the update conflicts.
   *
   * An update that hits conflicts stops and reports them at once. The user resolves them later through the "Resolve conflicts"
   * notification, which finishes the merge or rebase and refreshes the repository, and nothing reports the update as done after that. So
   * this waits for the last repository to leave the merging or rebasing state and reports it. A clean update reports itself and arms
   * nothing here.
   *
   * Suspends because it asks Git for the unmerged files, and it is reached from a topic published on the update's own thread.
   */
  private suspend fun notifyWhenConflictsResolved() {
    val repositoryManager = GitRepositoryManager.getInstance(project)
    val hasConflicts = withContext(Dispatchers.IO) {
      repositoryManager.repositories.any {
        runCatching { GitChangeUtils.getUnmergedFiles(it).isNotEmpty() }.getOrDefault(false)
      }
    }
    // One watcher at a time: a second update started before the first one's conflicts are resolved must not report it twice.
    if (!hasConflicts || !awaitingConflictResolution.compareAndSet(false, true)) return

    val connection = project.messageBus.connect(GitDisposable.getInstance(project))
    connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
      val resolved = repositoryManager.repositories.none {
        it.state == Repository.State.MERGING || it.state == Repository.State.REBASING
      }
      if (resolved) {
        connection.disconnect()
        awaitingConflictResolution.set(false)
        VcsNotifier.getInstance(project)
          .notifySuccess(GitNotificationIdsHolder.PROJECT_UPDATED, GitBundle.message("recent.project.update.success", project.name), "")
      }
    })
  }
}
