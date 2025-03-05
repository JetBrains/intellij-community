// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

interface GradleExecutionAware : ExternalSystemExecutionAware {

  fun getBuildLayoutParameters(project: Project, projectPath: Path): BuildLayoutParameters? = null

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use getBuildLayoutParameters(Project, Path) instead")
  fun getBuildLayoutParameters(project: Project, projectPath: String): BuildLayoutParameters? {
    return getBuildLayoutParameters(project, Path.of(projectPath))
  }

  fun getDefaultBuildLayoutParameters(project: Project): BuildLayoutParameters? = null

  fun isGradleInstallationHomeDir(project: Project, homePath: Path): Boolean = false

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use isGradleInstallationHomeDir(Project, Path) instead")
  fun isGradleInstallationHomeDir(project: Project, homePath: String): Boolean {
    return isGradleInstallationHomeDir(project, Path.of(homePath))
  }
}