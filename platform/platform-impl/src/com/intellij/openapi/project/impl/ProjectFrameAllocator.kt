// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package com.intellij.openapi.project.impl

import com.intellij.conversion.CannotConvertException
import com.intellij.openapi.project.Project

internal sealed interface ProjectInitObservable {
  val projectInitTimestamp: Long
  suspend fun awaitProjectPreInit(): Project
  suspend fun awaitProjectInit(): Project
}

internal interface ProjectFrameAllocator {
  suspend fun runInBackground(projectInitObservable: ProjectInitObservable)
  suspend fun run(projectInitObservable: ProjectInitObservable)
  suspend fun preInitProject(project: Project)
  suspend fun projectNotLoaded(cannotConvertException: CannotConvertException?)
}

internal class HeadlessProjectFrameAllocator : ProjectFrameAllocator {
  override suspend fun runInBackground(projectInitObservable: ProjectInitObservable) = Unit

  override suspend fun run(projectInitObservable: ProjectInitObservable) = Unit

  override suspend fun preInitProject(project: Project) = Unit

  override suspend fun projectNotLoaded(cannotConvertException: CannotConvertException?) {
    cannotConvertException?.let { throw cannotConvertException }
  }
}
