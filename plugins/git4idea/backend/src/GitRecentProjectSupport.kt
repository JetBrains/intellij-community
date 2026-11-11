// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.InitialVfsRefreshService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import git4idea.repo.GitCommitTemplateTracker
import git4idea.repo.GitFsMonitorDaemonStopper
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

/** Returns the Git root at or above [projectPath], or null when none exists. */
internal fun findGitRoot(projectPath: Path): Path? =
  generateSequence(projectPath) { it.parent }.firstOrNull { GitUtil.findGitDir(it) != null }

/**
 * Prepares the frameless recent [project] at [projectPath] for a Git action.
 *
 * `RecentProjectsService` loads and holds the project, so version control may still be initializing. This waits for the initialization and
 * registers the Git root mapping when the project has none stored yet. The mapping stays in memory: the project is closed without saving,
 * so nothing is written to the project settings.
 *
 * The Git commands of the action can start an fsmonitor daemon, which outlives the IDE unless something stops it. The project is not open,
 * so its roots are recorded for the stop at exit.
 */
internal suspend fun prepareRecentGitProject(project: Project, projectPath: Path) {
  GitRecentProjectWatcher.getInstance(project).watch(projectPath)
  val vcsManager = ProjectLevelVcsManager.getInstance(project)
  vcsManager.awaitInitialization()
  registerGitRoot(project, projectPath, vcsManager)
  val roots = GitRepositoryManager.getInstance(project).repositories.map { it.root }
  GitFsMonitorDaemonStopper.getInstance().registerFramelessProjectRoots(roots)
  // A project activity loads the commit template for an open project. These projects run no activity. Until the tracker has run, the
  // commit message field stays disabled and waits for the template. The tracker reads the repositories, so it runs after the Git root.
  withContext(Dispatchers.IO) { GitCommitTemplateTracker.getInstance(project).start() }
  // The untracked files of a repository load after the initial VFS refresh. An open project starts that refresh when it indexes its
  // files. These projects index nothing, so the refresh starts here.
  InitialVfsRefreshService.getInstance(project).scheduleInitialVfsRefresh()
}

/** Registers the Git root of [projectPath] when the project has no repository yet. */
private suspend fun registerGitRoot(project: Project, projectPath: Path, vcsManager: ProjectLevelVcsManager) {
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
