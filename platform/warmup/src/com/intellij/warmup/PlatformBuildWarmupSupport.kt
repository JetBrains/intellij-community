// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup

import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskManager
import org.jetbrains.concurrency.asCompletableFuture
import java.util.concurrent.CompletableFuture

class PlatformBuildWarmupSupport(val project: Project) : ProjectBuildWarmupSupport {
  override fun getBuilderId(): String {
    return "PLATFORM"
  }

  override fun buildProject(rebuild: Boolean): CompletableFuture<Unit> {
    println("Starting platform build with ProjectTaskManager")
    val projectTaskManager = ProjectTaskManager.getInstance(project)
    val buildFuture = if (rebuild) projectTaskManager.rebuildAllModules() else projectTaskManager.buildAllModules()
    return buildFuture.then {result ->
      println("Platform build has finished: hasErrors=${result?.hasErrors()}, isAborted=${result?.isAborted}")
    }.asCompletableFuture()
  }
}