// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package com.intellij.openapi.project.impl

import com.intellij.conversion.CannotConvertException
import com.intellij.openapi.observable.util.whenDisposedOrNow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.wm.IdeFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly

/**
 * Allows querying project init state
 * NB: order of pre-init and init is not guaranteed to allow for parallel processing
 */
@Internal
sealed interface ProjectInitObservable {
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

@Internal
interface ProjectFrameAllocator {
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

@Internal
class HeadlessProjectFrameAllocator : ProjectFrameAllocator {
  override suspend fun runInBackground(projectInitObservable: ProjectInitObservable) {
  }

  override suspend fun run(projectInitObservable: ProjectInitObservable) {
  }

  override suspend fun preInitProject(project: Project) {
    project.getOrCreateIdeFrameDeferred().complete(null)
  }

  override suspend fun projectNotLoaded(cannotConvertException: CannotConvertException?) {
    cannotConvertException?.let { throw cannotConvertException }
  }
}

private val IDE_FRAME_DEFERRED_KEY = Key.create<CompletableDeferred<IdeFrame?>>("Project.IdeFrameDeferred")
private val POST_OPEN_EDITORS_DEFERRED_KEY = Key.create<CompletableDeferred<Unit>>("Project.PostOpenEditorsDeferred")

internal fun Project.getOrCreateIdeFrameDeferred(): CompletableDeferred<IdeFrame?> {
  return getOrCreateDeferred(IDE_FRAME_DEFERRED_KEY)
}

internal fun Project.getOrCreatePostOpenEditorsDeferred(): CompletableDeferred<Unit> {
  return getOrCreateDeferred(POST_OPEN_EDITORS_DEFERRED_KEY)
}

private fun <T> Project.getOrCreateDeferred(key: Key<CompletableDeferred<T>>): CompletableDeferred<T> {
  val newDeferred = CompletableDeferred<T>()
  val actualDeferred = (this as UserDataHolderEx).putUserDataIfAbsent(key, newDeferred)
  if (newDeferred === actualDeferred) {
    this.whenDisposedOrNow {
      if (!newDeferred.isCompleted) {
        newDeferred.cancel(CancellationException("Project is disposed"))
      }
    }
  }
  return actualDeferred
}

/**
 * Work includes the empty editor state presentation and the [Project] view focus restore.
 *
 * @return `null` when the [Project] was opened without a frame
 */
@TestOnly
@Internal
fun Project.getPostOpenEditorsDeferred(): Deferred<Unit>? = getUserData(POST_OPEN_EDITORS_DEFERRED_KEY)
