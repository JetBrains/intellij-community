// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/** The VCS [roots] of [project] that can hold the commit of a hash in a text, see [findCommitRoots]. */
@ApiStatus.Internal
class VcsCommitRoots(val project: Project, val roots: Set<VirtualFile>)

/**
 * Finds the VCS roots of [project] that can hold the commit of a hash in a text. [context] is the file or the directory
 * of the text.
 *
 * The nearest VCS root of [context] comes first, see [ProjectLevelVcsManager.getVcsRootFor]. The other VCS roots of
 * [project] come last. A root of another open project is not in the result.
 */
@ApiStatus.Internal
fun findCommitRoots(project: Project, context: FilePath?): List<VcsCommitRoots> {
  val vcsManager = ProjectLevelVcsManager.getInstance(project)
  val nearest = context?.let { vcsManager.getVcsRootFor(it) }
  val otherRoots = vcsManager.getAllVcsRoots().mapTo(LinkedHashSet()) { it.path }
  otherRoots.remove(nearest)
  return listOfNotNull(
    nearest?.let { VcsCommitRoots(project, setOf(it)) },
    otherRoots.takeIf { it.isNotEmpty() }?.let { VcsCommitRoots(project, it) },
  )
}
