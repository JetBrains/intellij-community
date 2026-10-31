// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package com.intellij.openapi.project.impl

import com.intellij.conversion.CannotConvertException
import com.intellij.ide.frameOwner
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.observable.util.whenDisposedOrNow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.IdeProjectFrameHelper
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

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
 * The allocator of the frame of a project that opens with [options]. A frame guest joins the frame of its owner, with and
 * without a frame. A headless application and a unit test allocate no frame. Every other project gets a frame of its own.
 */
internal fun createProjectFrameAllocator(options: OpenProjectTask, projectStoreBaseDir: Path): ProjectFrameAllocator {
  options.frameOwner?.let { return FrameGuestProjectFrameAllocator(it) }
  val app = ApplicationManager.getApplication()
  if (app.isHeadlessEnvironment || app.isUnitTestMode) {
    return HeadlessProjectFrameAllocator()
  }
  return IdeProjectFrameAllocator(options, projectStoreBaseDir)
}

/**
 * Opens a project as a frame guest of [frameOwner]: the project gets no frame of its own, and the frame of [frameOwner] shows it.
 * The opener asks for it with `OpenProjectImplOptions.frameOwner`.
 *
 * The window manager records the guest before the startup activities of the guest run, so a lookup of its frame works from the
 * start, see `WindowManagerEx.addFrameGuest`. With a frame of [frameOwner], the status bar of the frame shows the status text of
 * the guest, the editor area of the frame gets a card with the editors of the guest, the saved editors of the guest come back into
 * the card, and the tool-window manager of the guest gets a default pane that no frame shows, so a tool window of the guest can
 * register. Without a frame of [frameOwner], as in a unit test, the guest opens as in a headless application.
 */
internal class FrameGuestProjectFrameAllocator(private val frameOwner: Project) : ProjectFrameAllocator {
  @Volatile
  private var guest: Project? = null

  /** A headless application and a unit test allocate no frame, and the frame deferred of a test window manager never completes. */
  private val isHeadless: Boolean
    get() = ApplicationManager.getApplication().let { it.isHeadlessEnvironment || it.isUnitTestMode }

  override suspend fun runInBackground(projectInitObservable: ProjectInitObservable) {
  }

  override suspend fun preInitProject(project: Project) {
    guest = project
    windowManager().addFrameGuest(frameOwner, project)
    if (!isHeadless) {
      // the guest restores its editors in [run], as a project with a frame of its own does
      project.getOrCreatePostOpenEditorsDeferred()
    }
  }

  override suspend fun run(projectInitObservable: ProjectInitObservable) {
    if (isHeadless) {
      projectInitObservable.awaitProjectPreInit().getOrCreateIdeFrameDeferred().complete(null)
      return
    }
    val frameHelper = windowManager().getIdeFrameDeferred(frameOwner).await() as? IdeProjectFrameHelper
    val project = projectInitObservable.awaitProjectPreInit()
    try {
      if (frameHelper == null || frameHelper.isDisposed()) {
        project.getOrCreateIdeFrameDeferred().complete(null)
        return
      }
      withContext(Dispatchers.UI) {
        frameHelper.joinFrame(project)
      }
      projectInitObservable.awaitProjectInit()
      val fileEditorManager = FileEditorManager.getInstanceAsync(project) as FileEditorManagerImpl
      fileEditorManager.initJob.join()
      // DaemonCodeAnalyzer uses FileEditorManager
      coroutineScope { preloadDaemonServices(project) }
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        // the state of the dockable editors, as for a project with a frame of its own
        fileEditorManager.initDockableContentFactory()
        frameHelper.editorArea.addGuestEditors(project, fileEditorManager.mainSplitters)
      }
      (project.serviceAsync<ToolWindowManager>() as? ToolWindowManagerImpl)?.initDetachedDefaultPane(frameHelper.frame)
      // the card is in the frame, so a restored editor shows as soon as the guest is the active project
      restoreFrameGuestEditors(project, fileEditorManager)
      project.getOrCreateIdeFrameDeferred().complete(frameHelper)
    }
    finally {
      project.getOrCreatePostOpenEditorsDeferred().complete(Unit)
    }
  }

  /** A project that never opened fires no close event, so the guest leaves here. */
  override suspend fun projectNotLoaded(cannotConvertException: CannotConvertException?) {
    guest?.let { windowManager().removeFrameGuest(it) }
    cannotConvertException?.let { throw cannotConvertException }
  }

  private suspend fun windowManager(): WindowManagerEx = serviceAsync<WindowManager>() as WindowManagerEx
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
