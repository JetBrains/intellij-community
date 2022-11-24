// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup

import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskManager
import org.jetbrains.concurrency.asDeferred

internal class PlatformBuildWarmupSupport(val project: Project) : ProjectBuildWarmupSupport {
  override fun getBuilderId() = "PLATFORM"

  override suspend fun buildProject(rebuild: Boolean): String {
    val projectTaskManager = ProjectTaskManager.getInstance(project)

    val result = (if (rebuild) projectTaskManager.rebuildAllModules() else projectTaskManager.buildAllModules()).asDeferred().await()
    return "Platform build has finished: hasErrors=${result?.hasErrors()}, isAborted=${result?.isAborted}"
  }
}