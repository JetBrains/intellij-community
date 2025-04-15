// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point that allows VCS implementations to provide additional paths to refresh in local changes after a commit operation.
 *
 * see [LocalChangesCommitter]
 */
@ApiStatus.Internal
interface VcsPathsToRefreshProvider {
  companion object {
    val EP_NAME: ProjectExtensionPointName<VcsPathsToRefreshProvider> =
      ProjectExtensionPointName("com.intellij.vcs.pathsToRefreshProvider")

    fun collectPathsToRefreshForVcs(project: Project, vcs: AbstractVcs): Collection<FilePath> {
      return EP_NAME.getExtensions(project)
        .filter { it.getVcsName() == vcs.name }
        .flatMap { it.collectPathsToRefresh(project) }
    }
  }

  fun getVcsName(): String

  /**
   * Collects additional paths to refresh in Local Changes after a commit operation.
   *
   * @param project the project
   * @return a collection of file paths to refresh
   */
  fun collectPathsToRefresh(project: Project): Collection<FilePath>
}
