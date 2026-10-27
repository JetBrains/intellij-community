// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package com.intellij.openapi.project.impl

import com.intellij.conversion.CannotConvertException
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.observable.util.whenDisposedOrNow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal

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

/**
 * Put it into `OpenProjectTask.implOptions` to show a project in the frame of [frameOwner] instead of a frame of its own.
 * The project becomes a frame guest, see [ProjectFrameHelper.addGuestProject], through [FrameGuestProjectFrameAllocator].
 */
@Internal
class OpenProjectAsFrameGuest(@JvmField val frameOwner: Project)

/**
 * Opens a project as a frame guest of [frameOwner]: the project gets no frame of its own, and the frame of [frameOwner] shows it.
 *
 * The guest joins the frame before its startup activities run, so a lookup of its frame works from the start. The tool-window
 * manager of the guest gets a default pane that no frame shows, so a tool window of the guest can register. The caller puts the
 * editors of the guest into the frame. Without a frame of [frameOwner], the guest opens as in a headless application.
 */
@Internal
class FrameGuestProjectFrameAllocator(private val frameOwner: Project) : ProjectFrameAllocator {
  override suspend fun runInBackground(projectInitObservable: ProjectInitObservable) {
  }

  override suspend fun run(projectInitObservable: ProjectInitObservable) {
    val frameHelper = serviceAsync<WindowManager>().getIdeFrameDeferred(frameOwner).await() as? ProjectFrameHelper
    val project = projectInitObservable.awaitProjectPreInit()
    if (frameHelper == null || frameHelper.isDisposed()) {
      project.getOrCreateIdeFrameDeferred().complete(null)
      return
    }
    withContext(Dispatchers.UI) {
      frameHelper.addGuestProject(project)
    }
    projectInitObservable.awaitProjectInit()
    // DaemonCodeAnalyzer uses FileEditorManager
    project.serviceAsync<FileEditorManager>()
    coroutineScope { preloadDaemonServices(project) }
    (project.serviceAsync<ToolWindowManager>() as? ToolWindowManagerImpl)?.initDetachedDefaultPane(frameHelper.frame)
    project.getOrCreateIdeFrameDeferred().complete(frameHelper)
  }

  override suspend fun preInitProject(project: Project) {
  }

  override suspend fun projectNotLoaded(cannotConvertException: CannotConvertException?) {
    cannotConvertException?.let { throw cannotConvertException }
  }
}

private val IDE_FRAME_DEFERRED_KEY = Key.create<CompletableDeferred<IdeFrame?>>("Project.IdeFrameDeferred")
private val POST_OPEN_EDITORS_DEFERRED_KEY = Key.create<CompletableDeferred<Unit>>("Project.PostOpenEditorsDeferred")
private val FRAME_CONTENT_PAINTED_DEFERRED_KEY = Key.create<CompletableDeferred<Unit>>("Project.FrameContentPaintedDeferred")

internal fun Project.getOrCreateIdeFrameDeferred(): CompletableDeferred<IdeFrame?> {
  return getOrCreateDeferred(IDE_FRAME_DEFERRED_KEY)
}

internal fun Project.getOrCreatePostOpenEditorsDeferred(): CompletableDeferred<Unit> {
  return getOrCreateDeferred(POST_OPEN_EDITORS_DEFERRED_KEY)
}

/**
 * Completes when the frame content of the project first paints.
 *
 * Only a project that owns such frame content completes it, for example the non-modal welcome screen.
 * The start-up manager waits for it before the post-startup activities of a project with
 * [com.intellij.openapi.wm.ex.ProjectFrameCapability.SUPPRESS_BACKGROUND_ACTIVITIES].
 * The wait has a time limit, so a project that never completes it starts its activities later.
 */
@Internal
fun Project.getOrCreateFrameContentPaintedDeferred(): CompletableDeferred<Unit> {
  return getOrCreateDeferred(FRAME_CONTENT_PAINTED_DEFERRED_KEY)
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
 * Completes after startup restores the editors and presents the empty editor state.
 * On a project open without editors, it also completes after the startup focus step ran.
 * That step can focus the default tool window, see `ToolWindowManagerImpl.focusDefaultToolWindow`.
 * The deferred does not wait for the readiness of a tool window.
 *
 * @return `null` when the [Project] was opened without a frame
 */
@Internal
fun Project.getPostOpenEditorsDeferred(): Deferred<Unit>? = getUserData(POST_OPEN_EDITORS_DEFERRED_KEY)
