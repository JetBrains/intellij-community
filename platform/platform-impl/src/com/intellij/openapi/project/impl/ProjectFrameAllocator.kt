// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package com.intellij.openapi.project.impl

import com.intellij.conversion.CannotConvertException
import com.intellij.openapi.project.Project

internal fun interface FrameAllocatorTask {
  suspend fun execute(projectInitObserver: ProjectInitObserver?)
}

internal sealed interface ProjectInitObserver {
  fun notifyProjectPreInit(project: Project)
  fun notifyProjectInit(project: Project)
}

internal interface ProjectFrameAllocator {
  suspend fun run(task: FrameAllocatorTask)
  suspend fun preInitProject(project: Project)
  suspend fun projectNotLoaded(cannotConvertException: CannotConvertException?)
}

internal class HeadlessProjectFrameAllocator : ProjectFrameAllocator {
  override suspend fun run(task: FrameAllocatorTask) {
    task.execute( null)
  }

  override suspend fun preInitProject(project: Project) = Unit

  override suspend fun projectNotLoaded(cannotConvertException: CannotConvertException?) {
    cannotConvertException?.let { throw cannotConvertException }
  }
}
