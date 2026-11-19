// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.workspace.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.workspace.ProjectWorkspace
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/** Creates workspaces for host projects. */
@ApiStatus.Internal
@ApiStatus.OverrideOnly
interface ProjectWorkspaceFactory {
  /**
   * Returns a new workspace for the host [project], or `null` if this factory does not recognize [identityFile].
   * Use [identityFile] to identify the host. The project store has no path yet.
   *
   * The manager holds its lock during this call. Only construct the workspace here.
   * The workspace must use identity equality and delegate to a [ProjectWorkspaceComponentManager] for [project].
   */
  fun createWorkspace(project: Project, identityFile: Path): ProjectWorkspace?
}
