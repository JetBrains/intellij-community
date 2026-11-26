// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/** The VCS [roots] of [project] that can hold the commit of a hash in a text, see [findCommitRoots]. */
@ApiStatus.Internal
class VcsCommitRoots(val project: Project, val roots: Set<VirtualFile>)

/** The registry key that adds the roots of the other open projects to [findCommitRoots]. */
private const val CROSS_PROJECT_KEY = "vcs.log.commit.hash.cross.project"

/**
 * Finds the VCS roots that can hold the commit of a hash in a text of [project]. [context] is the file or the directory
 * of the text.
 *
 * The nearest VCS root of [context] comes first, see [ProjectLevelVcsManager.getVcsRootFor]. The other VCS roots of
 * [project] come next. With the registry key [CROSS_PROJECT_KEY], the nearest VCS root of [context] in the other open
 * projects comes last.
 */
@ApiStatus.Internal
fun findCommitRoots(project: Project, context: FilePath?): List<VcsCommitRoots> {
  val vcsManager = ProjectLevelVcsManager.getInstance(project)
  val nearest = context?.let { vcsManager.getVcsRootFor(it) }
  val otherRoots = vcsManager.getAllVcsRoots().mapTo(LinkedHashSet()) { it.path }
  otherRoots.remove(nearest)
  val ownRoots = listOfNotNull(
    nearest?.let { VcsCommitRoots(project, setOf(it)) },
    otherRoots.takeIf { it.isNotEmpty() }?.let { VcsCommitRoots(project, it) },
  )
  if (context == null || !RegistryManager.getInstance().`is`(CROSS_PROJECT_KEY)) return ownRoots
  return ownRoots + findNearestRootsInOtherProjects(project, context)
}

/** Finds the nearest VCS root of [context] in each open project other than [project], and keeps the nearest ones. */
private fun findNearestRootsInOtherProjects(project: Project, context: FilePath): List<VcsCommitRoots> {
  val found = ProjectManager.getInstance().openProjects.filter { it != project }.mapNotNull { candidate ->
    ProjectLevelVcsManager.getInstance(candidate).getVcsRootFor(context)?.let { candidate to it }
  }
  return keepNearestRoots(found) { (_, root) -> root.path }.map { (candidate, root) -> VcsCommitRoots(candidate, setOf(root)) }
}

/**
 * Returns the items of [roots] with the longest [pathOf], in the order of [roots]. Each path is the root of the same
 * directory in a different project, so the longest path is the nearest root. Several items can have the same path.
 */
internal fun <R> keepNearestRoots(roots: List<R>, pathOf: (R) -> String): List<R> {
  val length = roots.maxOfOrNull { pathOf(it).length } ?: return emptyList()
  return roots.filter { pathOf(it).length == length }
}
