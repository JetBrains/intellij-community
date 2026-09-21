// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
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
 */
internal suspend fun prepareRecentGitProject(project: Project, projectPath: Path) {
  project.serviceAsync<GitRecentProjectWatcher>().watch(projectPath)
  val vcsManager = ProjectLevelVcsManager.getInstance(project)
  vcsManager.awaitInitialization()
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
