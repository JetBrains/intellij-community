// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/frontend-file-contexts.spec.md
// @spec ../../../../../../../../spec/settings-contexts.spec.md

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

/**
 * Returns the topmost content roots of this project, sorted by url. It drops every root that lies under another
 * one. See [collapseNestedRoots].
 *
 * `SettingsRootsCache` in the backend module holds the result. `BackendContentRootsApi` in the remote-dev backend
 * split module sends the same result to the frontend, so both sides anchor on the same roots.
 */
@ApiStatus.Internal
@RequiresReadLock
fun Project.topmostContentRoots(): List<VirtualFile> {
  val contentRoots = ProjectRootManager.getInstance(this).contentRoots
  val topmost = collapseNestedRoots(contentRoots.map { it.url }).toSet()
  return contentRoots.filter { it.url in topmost }.sortedBy { it.url }
}

/**
 * Returns the urls of the roots that a file context of [project] anchors on. The first [SettingsContentRootsProvider]
 * answers. With no provider, as in a client light session, the base directory of the project is the only root. The
 * fallback builds the url from the base path, with no VFS access.
 *
 * @param project the project whose roots to return
 * @return the root urls, or an empty list when no provider answers and the project has no base path
 */
@RequiresReadLock
internal fun fileContextRootUrls(project: Project): List<String> {
  val provider = SettingsContentRootsProvider.EP_NAME.extensionList.firstOrNull()
                 ?: return listOfNotNull(project.basePath?.let(VfsUtilCore::pathToUrl))
  return provider.rootUrls(project)
}

/**
 * Collapses [roots] to the topmost ones, dropping every root that lies under another.
 *
 * A module content root can sit inside another one, and a settings context anchors on the topmost of them. Only
 * then does the context chain reach every directory between a file and its anchor, so a config in any of them
 * applies to that file.
 *
 * @param roots root urls or paths, in any order
 * @return the non-overlapping roots, sorted
 */
fun collapseNestedRoots(roots: Collection<String>): List<String> {
  val distinctRoots = roots.toSet()
  return distinctRoots.filterNot { it.hasAncestorIn(distinctRoots) }.sorted()
}

/**
 * Checks whether one of [roots] is an ancestor of this root. An ancestor ends at a `/` of this root, so `/p` is
 * an ancestor of `/p/q`, but not of `/p-x`.
 *
 * @return `true` if [roots] holds the part of this root before one of its `/` separators
 */
private fun String.hasAncestorIn(roots: Set<String>): Boolean {
  return indices.any { this[it] == '/' && substring(0, it) in roots }
}
