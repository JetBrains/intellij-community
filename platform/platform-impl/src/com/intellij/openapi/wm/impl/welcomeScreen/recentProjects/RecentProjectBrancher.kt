// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface RecentProjectBrancher {
  /** Returns true when the recent project at [projectPath] has a repository whose branches this brancher can manage. */
  fun canManageBranches(projectPath: Path): Boolean

  /**
   * Shows the branches popup of the already-loaded recent [project], located at [projectPath], at [anchor].
   */
  suspend fun showBranches(project: Project, projectPath: Path, anchor: RelativePoint?)

  companion object {
    private val EP_NAME: ExtensionPointName<RecentProjectBrancher> = ExtensionPointName("com.intellij.recentProjectBrancher")

    fun findBrancher(projectPath: Path): RecentProjectBrancher? =
      EP_NAME.findFirstSafe { it.canManageBranches(projectPath) }
  }
}
