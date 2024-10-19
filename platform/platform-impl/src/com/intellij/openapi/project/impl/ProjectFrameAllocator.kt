// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package com.intellij.openapi.project.impl

import com.intellij.conversion.CannotConvertException
import com.intellij.openapi.project.Project

/**
 * Allows querying project init state
 * NB: order of pre-init and init is not guaranteed to allow for parallel processing
 */
internal sealed interface ProjectInitObservable {
  /**
   * Set when all project init activities are done or scheduled
   */
  val projectInitTimestamp: Long

  /**
   * Await project pre-init activities completion, like [ProjectFrameAllocator.preInitProject] and workspace preparation
   */
  suspend fun awaitProjectPreInit(): Project

  /**
   * Await full project initialization
   */
  suspend fun awaitProjectInit(): Project
}

internal sealed interface ProjectFrameAllocator {
  /**
   * A job that will be run in parallel with [run] and will be canceled when allocation is complete.
   */
  suspend fun runInBackground(projectInitObservable: ProjectInitObservable)

  /**
   * Allocate, set up and show the project frame
   */
  suspend fun run(projectInitObservable: ProjectInitObservable)

  /**
   * A job that should be run before project components creation
   */
  suspend fun preInitProject(project: Project)

  /**
   * Signaled when a project was not loaded for any reason like error or cancellation.
   */
  suspend fun projectNotLoaded(cannotConvertException: CannotConvertException?)
}

internal class HeadlessProjectFrameAllocator : ProjectFrameAllocator {
  override suspend fun runInBackground(projectInitObservable: ProjectInitObservable) {
  }

  override suspend fun run(projectInitObservable: ProjectInitObservable) {
  }

  override suspend fun preInitProject(project: Project) {
  }

  override suspend fun projectNotLoaded(cannotConvertException: CannotConvertException?) {
    cannotConvertException?.let { throw cannotConvertException }
  }
}
