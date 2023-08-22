// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup

import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskManager
import com.intellij.task.impl.ProjectTaskManagerImpl
import org.jetbrains.concurrency.asDeferred

internal class PlatformBuildWarmupSupport(val project: Project) : ProjectBuildWarmupSupport {
  override fun getBuilderId() = "PLATFORM"
  @Deprecated("Return type is not descriptive enough", ReplaceWith("buildProjectWithStatus(rebuild).message"))
  override suspend fun buildProject(rebuild: Boolean): String {
    return buildProjectWithStatus(rebuild).message
  }

  override suspend fun buildProjectWithStatus(rebuild: Boolean): WarmupBuildStatus.InvocationStatus {
    ProjectTaskManagerImpl.putBuildOriginator(project, this.javaClass)
    val projectTaskManager = ProjectTaskManager.getInstance(project)

    val result = (if (rebuild) projectTaskManager.rebuildAllModules() else projectTaskManager.buildAllModules()).asDeferred().await()
    if (result?.hasErrors() == true) {
      return WarmupBuildStatus.Failure("Build finished with errors")
    }
    if (result?.isAborted == true) {
      return WarmupBuildStatus.Failure("Build is aborted")
    }
    return WarmupBuildStatus.Success("Build finished successfully")
  }
}