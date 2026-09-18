// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Runs the standard "Update Project" flow for a recent project without opening its frame.
 *
 * The welcome screen uses this to update a recent version control project in place. An implementation
 * reuses the configured update behavior on an already-loaded project. [RecentProjectsService]
 * loads and holds the project, so the updater does not load or dispose it.
 */
@ApiStatus.Internal
interface RecentProjectUpdater {
  /** Returns true when the recent project at [projectPath] has a repository this updater can update. */
  fun canUpdate(projectPath: Path): Boolean

  /** Updates the already-loaded recent [project], located at [projectPath], without opening its frame. */
  suspend fun update(project: Project, projectPath: Path)

  companion object {
    private val EP_NAME: ExtensionPointName<RecentProjectUpdater> = ExtensionPointName("com.intellij.recentProjectUpdater")

    /** Returns the first updater that can update the recent project at [projectPath], or null. */
    fun findUpdater(projectPath: Path): RecentProjectUpdater? =
      EP_NAME.findFirstSafe { it.canUpdate(projectPath) }
  }
}
