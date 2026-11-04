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
import com.intellij.openapi.progress.impl.PerProjectTaskInfoEntityCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
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

  /**
   * Ends the open as cancelled. The project manager then closes the project that the open made. With a `ProjectLoadingCancelled`
   * cause, `openProjectAsync` gives `null`. An allocator calls it when the frame of the project is gone.
   */
  fun cancelOpening(cause: CancellationException)
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
  options.frameOwner?.let { return GuestProjectFrameAllocator(it) }
  val app = ApplicationManager.getApplication()
  if (app.isHeadlessEnvironment || app.isUnitTestMode) {
    return HeadlessProjectFrameAllocator()
  }
  return IdeProjectFrameAllocator(options, projectStoreBaseDir)
}

/**
 * The open of a frame guest while it is in progress, see [GuestProjectFrameAllocator].
 *
 * The close of the frame owner and the end of the open race for the outcome, and the first one wins. The owner close asks
 * the open to end as cancelled with [endForOwnerClose]. The allocator marks the end of the open with [markOpened], and the
 * guest is an open project from then on, which closes like any other.
 */
internal class FrameGuestOpening {
  private enum class Outcome { OWNER_CLOSES, OPENED }

  private val outcome = CompletableDeferred<Outcome>()

  /**
   * Asks the open to end as cancelled, because the frame owner closes.
   *
   * @return `true` when the open was in progress and ends as cancelled now; `false` when the open completed already
   */
  fun endForOwnerClose(): Boolean = outcome.complete(Outcome.OWNER_CLOSES)

  /**
   * Marks the end of the open.
   *
   * @return `true` when the open completed before any close of the owner; `false` when the owner close asked for the end first
   */
  fun markOpened(): Boolean = outcome.complete(Outcome.OPENED)

  /** Suspends until the owner close asks for the end of the open. The caller is cancelled before that when the open completes. */
  suspend fun awaitOwnerClose() {
    outcome.await()
  }
}

private val FRAME_GUEST_OPENING_KEY = Key.create<FrameGuestOpening>("Project.FrameGuestOpening")

/** The open of this frame guest while it is in progress, or `null` for a project that is open, or that is no frame guest. */
internal val Project.frameGuestOpening: FrameGuestOpening?
  get() = getUserData(FRAME_GUEST_OPENING_KEY)

/**
 * Opens a project as a frame guest of [frameOwner]: the project gets no frame of its own, and the frame of [frameOwner] shows it.
 * The opener asks for it with `OpenProjectImplOptions.frameOwner`.
 *
 * The window manager records the guest before the startup activities of the guest run, so a lookup of its frame works from the
 * start, see `WindowManagerEx.addFrameGuest`. With a frame of [frameOwner], the status bar of the frame shows the status text and
 * the background tasks of the guest, the editor area of the frame gets a card with the editors of the guest, the saved editors of
 * the guest come back into the card, and the tool-window manager of the guest gets a default pane that no frame shows, so a tool
 * window of the guest can register. Without a frame of [frameOwner], as in a unit test, the guest opens as in a headless application.
 *
 * The owner can close while the guest still opens. The owner close leaves such a guest alone, see
 * `ProjectManagerImpl.closeFrameGuests`, and [runInBackground] ends the open as cancelled instead. The open then ends through
 * the cancel path of the project manager: the guest closes once, leaves the record, and `openProjectAsync` gives `null`.
 * The owner close and the end of the open can race; the [FrameGuestOpening] handle on the guest decides who was first.
 */
internal class GuestProjectFrameAllocator(private val frameOwner: Project) : ProjectFrameAllocator {
  @Volatile
  private var guest: Project? = null

  private val opening = FrameGuestOpening()

  /**
   * A headless application and a unit test allocate no frame. The frame deferred of `TestWindowManager` is a new deferred
   * that never completes, because its Java `CompletableDeferred(null)` call picks the `Job` overload.
   */
  private val isHeadless: Boolean
    get() = ApplicationManager.getApplication().let { it.isHeadlessEnvironment || it.isUnitTestMode }

  override suspend fun runInBackground(projectInitObservable: ProjectInitObservable) {
    // the subscription comes before the first suspension, so no close of the owner is missed
    val connection = ApplicationManager.getApplication().messageBus.simpleConnect()
    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosing(project: Project) {
        if (project === frameOwner) opening.endForOwnerClose()
      }

      override fun projectClosed(project: Project) {
        if (project === frameOwner) opening.endForOwnerClose()
      }
    })
    try {
      if (frameOwner.isDisposed || !frameOwner.isOpen) opening.endForOwnerClose()
      // `ProjectManagerImpl.doOpenAsync` cancels this job when the open ends, see [ProjectFrameAllocator.runInBackground].
      // The cancel ends the wait, so the wait never hangs.
      opening.awaitOwnerClose()
    }
    finally {
      connection.disconnect()
      if (opening.markOpened()) {
        // the open completed: the guest is an open project from now on, and the owner close closes it like any other
        guest?.putUserData(FRAME_GUEST_OPENING_KEY, null)
      }
      else {
        projectInitObservable.cancelOpening(ProjectLoadingCancelled("The frame owner ${frameOwner.name} closes"))
      }
    }
  }

  override suspend fun preInitProject(project: Project) {
    guest = project
    // the handle comes first, so an owner close that sees the guest in the record sees its open too
    project.putUserData(FRAME_GUEST_OPENING_KEY, opening)
    windowManager().addFrameGuest(frameOwner, project)
    if (!isHeadless) {
      // the guest restores its editors in [run], as a project with a frame of its own does
      project.getOrCreatePostOpenEditorsDeferred()
    }
  }

  override suspend fun run(projectInitObservable: ProjectInitObservable) {
    val project = projectInitObservable.awaitProjectPreInit()
    if (!windowManager().isFrameGuest(project)) {
      // the record refused the join and logged the error
      throw ProjectLoadingCancelled("$project cannot join the frame of $frameOwner")
    }
    if (isHeadless) {
      project.getOrCreateIdeFrameDeferred().complete(null)
      return
    }
    val ownerFrame = windowManager().getIdeFrameDeferred(frameOwner)
    // `join` throws nothing for a cancelled deferred: the owner is disposed before the guest joined its frame
    ownerFrame.join()
    if (ownerFrame.isCancelled) {
      throw ProjectLoadingCancelled("The frame owner ${frameOwner.name} closed before ${project.name} joined its frame")
    }
    val frameHelper = ownerFrame.await() as? IdeProjectFrameHelper
    try {
      if (frameHelper == null || frameHelper.isDisposed()) {
        project.getOrCreateIdeFrameDeferred().complete(null)
        return
      }
      withContext(Dispatchers.UI) {
        frameHelper.addGuest(project)
      }
      projectInitObservable.awaitProjectInit()
      // the status bar of the frame shows the background tasks of the guest, as it shows the status text
      project.serviceAsync<PerProjectTaskInfoEntityCollector>().startCollectingOwnTasks()
      val fileEditorManager = FileEditorManager.getInstanceAsync(project) as FileEditorManagerImpl
      fileEditorManager.initJob.join()
      // DaemonCodeAnalyzer uses FileEditorManager
      coroutineScope { preloadDaemonServices(project) }
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        // the state of the dockable editors, as for a project with a frame of its own
        fileEditorManager.initDockableContentFactory()
        // the owner can close while the guest opens, and a released frame shows no card
        if (!frameHelper.isDisposed()) {
          frameHelper.editorArea.addGuestEditors(project, fileEditorManager.mainSplitters)
        }
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
    guest?.let {
      it.putUserData(FRAME_GUEST_OPENING_KEY, null)
      windowManager().removeFrameGuest(it)
    }
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
