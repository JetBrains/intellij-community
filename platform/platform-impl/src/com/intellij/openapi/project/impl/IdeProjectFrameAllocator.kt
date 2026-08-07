// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.concurrency.captureThreadContext
import com.intellij.conversion.CannotConvertException
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.StartUpPerformanceService
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.featureStatistics.fusCollectors.FileEditorCollector.EmptyStateCause
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.frame
import com.intellij.ide.frameInfo
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.recentProjectMetaInfo
import com.intellij.ide.util.runOnceForProject
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.CoroutineSupport
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.ui
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.stopOpenFilesActivity
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ReadmeShownUsageCollector.README_OPENED_ON_START_TS
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.await
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameTypeService
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.openapi.wm.ex.normalizeProjectFrameKey
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.WelcomeScreenTabService
import com.intellij.openapi.wm.impl.FrameBoundsConverter
import com.intellij.openapi.wm.impl.FrameInfo
import com.intellij.openapi.wm.impl.FrameInfoHelper
import com.intellij.openapi.wm.impl.FrameLoadingState
import com.intellij.openapi.wm.impl.FrameTitleBuilder
import com.intellij.openapi.wm.impl.IDE_FRAME_EVENT_LOG
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.IdeProjectFrameHelper
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.openapi.wm.impl.checkForNonsenseBounds
import com.intellij.openapi.wm.impl.updateFullScreenState
import com.intellij.platform.diagnostic.telemetry.impl.getTraceActivity
import com.intellij.platform.diagnostic.telemetry.impl.rootTask
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.bootstrap.hideSplash
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiManager
import com.intellij.toolWindow.computeToolWindowBeans
import com.intellij.ui.ScreenUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.SimpleMessageBusConnection
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Dimension
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.event.WindowEvent
import java.awt.event.WindowStateListener
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

internal class IdeProjectFrameAllocator(
  private val options: OpenProjectTask,
  private val projectStoreBaseDir: Path,
) : ProjectFrameAllocator {
  private val deferredProjectFrameHelper = CompletableDeferred<IdeProjectFrameHelper>()

  override suspend fun preInitProject(project: Project) {
    (project.serviceAsync<FileEditorManager>() as? FileEditorManagerImpl)?.initJob?.join()
  }

  override suspend fun runInBackground(projectInitObservable: ProjectInitObservable) {
    coroutineScope {
      val app = ApplicationManager.getApplication()
      val projectLoadingTimeoutWatcher = if (app == null || app.isInternal || app.isUnitTestMode) {
        launch(CoroutineName("project loading timeout watcher")) {
          delay(10.seconds)
          // logged only during development, let's not spam users
          logger<ProjectFrameAllocator>().warn("Cannot load project in 10 seconds: ${dumpCoroutines()}")
        }
      }
      else {
        null
      }

      try {
        val project = projectInitObservable.awaitProjectInit()
        hideSplashWhenEditorOrToolWindowShown(project)
      }
      finally {
        projectLoadingTimeoutWatcher?.cancel()
      }
    }
  }

  override suspend fun run(projectInitObservable: ProjectInitObservable) {
    span("frame allocator foreground") {
      coroutineScope {
        val job = currentCoroutineContext().job
        val frameSettingsDeferred = async(CoroutineName("project frame settings resolving")) {
          resolveFrameSettings()
        }

        launch(CoroutineName("project frame creating")) {
          val loadingState = MutableLoadingState(done = job)
          span("frame creation") {
            createFrameManager(loadingState, frameSettingsDeferred.await())
          }
        }.invokeOnCompletion { cause ->
          if (cause is CancellationException) {
            job.cancel(cause)
          }
        }

        val frameHelperInitJob = launch {
          val project = projectInitObservable.awaitProjectPreInit()
          val frameHelper = deferredProjectFrameHelper.await()

          launch {
            val windowManager = serviceAsync<WindowManager>() as WindowManagerImpl
            span("frame assignment", Dispatchers.ui(CoroutineSupport.UiDispatcherKind.STRICT)) {
              windowManager.assignFrame(frameHelper, project)
              frameHelper.setRawProject(project)
            }
          }

          launch {
            val fileEditorManager = project.serviceAsync<FileEditorManager>() as FileEditorManagerImpl
            fileEditorManager.initJob.join()
            span("frame document component install", Dispatchers.UiWithModelAccess) {
              frameHelper.toolWindowPane.setDocumentComponent(fileEditorManager.mainSplitters)
            }
          }

          launch {
            span("project frame assigning") {
              frameHelper.setProject(project)
            }
          }
        }

        val reopeningEditorJob = launch {
          val project = projectInitObservable.awaitProjectInit()
          span("restoreEditors") {
            val fileEditorManager = project.serviceAsync<FileEditorManager>() as FileEditorManagerImpl
            restoreEditors(
              project = project,
              fileEditorManager = fileEditorManager,
              opensFileAfterProjectOpen = options.opensFileAfterProjectOpen,
            )
          }

          val start = projectInitObservable.projectInitTimestamp
          if (start != -1L) {
            StartUpMeasurer.addCompletedActivity(start, "editor reopening and frame waiting", getTraceActivity())
          }
        }

        val toolWindowInitJob = launch {
          val project = projectInitObservable.awaitProjectInit()
          span("initFrame") {
            launch(CoroutineName("tool window pane creation")) {
              val deferredToolWindowManager = async { project.serviceAsync<ToolWindowManager>() as? ToolWindowManagerImpl }
              val taskListDeferred = async(CoroutineName("toolwindow init command creation")) {
                computeToolWindowBeans(project, frameSettingsDeferred.await().projectFrameTypeId)
              }

              val toolWindowManager = deferredToolWindowManager.await() ?: return@launch
              val projectFrameTypeId = frameSettingsDeferred.await().projectFrameTypeId
              val projectFrameHelper = deferredProjectFrameHelper.await()
              val toolWindowPane = withContext(Dispatchers.UI) {
                projectFrameHelper.toolWindowPane
              }
              span("tool window manager init") {
                toolWindowManager.init(
                  pane = toolWindowPane,
                  reopeningEditorJob = reopeningEditorJob,
                  taskListDeferred = taskListDeferred,
                  projectFrameTypeId = projectFrameTypeId,
                )
              }
              serviceAsync<ProjectFrameCapabilitiesService>().getUiPolicy(project)?.let { projectFrameUiPolicy ->
                applyProjectFrameUiPolicy(toolWindowManager, project, projectFrameUiPolicy)
              }
            }
          }
        }

        launch {
          val project = projectInitObservable.awaitProjectInit()
          val startUpContextElementToPass = FUSProjectHotStartUpMeasurer.getStartUpContextElementToPass() ?: EmptyCoroutineContext

          val onNoEditorsLeft = captureThreadContext { FUSProjectHotStartUpMeasurer.reportNoMoreEditorsOnStartup(System.nanoTime()) }

          @Suppress("UsagesOfObsoleteApi")
          (project as ComponentManagerEx).getCoroutineScope().launch(startUpContextElementToPass + rootTask()) {
            val frameHelper = deferredProjectFrameHelper.await()
            launch {
              frameHelper.installDefaultProjectStatusBarWidgets(project)
              frameHelper.updateTitle(serviceAsync<FrameTitleBuilder>().getProjectTitle(project), project)
            }

            frameHelperInitJob.join() // initDockableContentFactory depends on it
            reopeningEditorJob.join()

            span("post open editors") {
              postOpenEditors(
                frameHelper = frameHelper,
                fileEditorManager = project.serviceAsync<FileEditorManager>() as FileEditorManagerImpl,
                toolWindowInitJob = toolWindowInitJob,
                project = project,
              )
            }
          }.invokeOnCompletion { throwable ->
            if (throwable != null) {
              onNoEditorsLeft()
              // `postOpenEditors` never ran, or never reached its own release
              releaseStartupEmptyStatePresentationHold(project)
            }
          }
        }
      }
    }
  }

  private suspend fun createFrameManager(loadingState: FrameLoadingState, frameSettings: ResolvedFrameSettings) {
    val frame = getFrame()

    withContext(Dispatchers.ui(CoroutineSupport.UiDispatcherKind.STRICT)) {
      if (frame != null) {
        if (!frame.isVisible) {
          throw CancellationException("Pre-allocated frame was already closed")
        }
        val frameHelper =
          IdeProjectFrameHelper(frame = frame, loadingState = loadingState, projectFrameTypeId = frameSettings.projectFrameTypeId)
        completeFrameAndCloseOnCancel(frameHelper) {
          if (options.forceOpenInNewFrame) {
            frameHelper.updateFullScreenState(frameSettings.frameInfo.fullScreen)
          }
          span("ProjectFrameHelper.init") {
            frameHelper.init()
          }
          frameHelper.setInitBounds(frameSettings.frameInfo.bounds)
        }
      }
      else {
        val frameHelper = IdeProjectFrameHelper(
          createIdeFrame(frameSettings.frameInfo),
          loadingState = loadingState,
          projectFrameTypeId = frameSettings.projectFrameTypeId,
        )
        // must be after preInit (frame decorator is required to set a full-screen mode)
        withContext(Dispatchers.UiWithModelAccess) {
          frameHelper.frame.isVisible = true
        }
        completeFrameAndCloseOnCancel(frameHelper) {
          frameHelper.updateFullScreenState(frameSettings.frameInfo.fullScreen)

          span("ProjectFrameHelper.init") {
            frameHelper.init()
          }
        }
      }
    }
  }

  private suspend inline fun completeFrameAndCloseOnCancel(
    frameHelper: IdeProjectFrameHelper,
    task: () -> Unit,
  ) {
    try {
      task()
      if (!deferredProjectFrameHelper.isCancelled) {
        deferredProjectFrameHelper.complete(frameHelper)
        return
      }
    }
    catch (@Suppress("IncorrectCancellationExceptionHandling") _: CancellationException) {
    }

    // make sure that in case of some error we close the frame for a not loaded project
    withContext(Dispatchers.ui(CoroutineSupport.UiDispatcherKind.STRICT) + NonCancellable) {
      (serviceAsync<WindowManager>() as WindowManagerImpl).releaseFrame(frameHelper)
    }
  }

  private fun getFrame(): IdeFrameImpl? {
    return options.frame
           ?: (serviceIfCreated<WindowManager>() as? WindowManagerImpl)?.removeAndGetRootFrame()
  }

  private suspend fun getRecentProjectMetaInfo(): RecentProjectMetaInfo? {
    return (serviceAsync<RecentProjectsManager>() as RecentProjectsManagerBase).getProjectMetaInfo(projectStoreBaseDir)
  }

  private suspend fun resolveFrameSettings(): ResolvedFrameSettings {
    var frameInfo: FrameInfo? = options.frameInfo
    var projectFrameTypeId: String? = options.projectFrameTypeId

    val recentProjectMetaInfoFromOptions = options.recentProjectMetaInfo
    if (frameInfo == null) {
      frameInfo = recentProjectMetaInfoFromOptions?.frame
    }
    if (projectFrameTypeId == null) {
      projectFrameTypeId = recentProjectMetaInfoFromOptions?.projectFrameTypeId
    }

    if (frameInfo == null || projectFrameTypeId == null) {
      val recentProjectMetaInfo = getRecentProjectMetaInfo()
      if (frameInfo == null) {
        frameInfo = recentProjectMetaInfo?.frame
      }
      if (projectFrameTypeId == null) {
        projectFrameTypeId = recentProjectMetaInfo?.projectFrameTypeId
      }
    }

    // this is the one choke point that sees every effective frame type id, so an id nothing declares
    // (a typo, or a plugin that is gone) is reported here instead of silently degrading to "no policy"
    val normalizedFrameTypeId = projectFrameTypeId.normalizeProjectFrameKey()
    if (normalizedFrameTypeId != null && serviceAsync<ProjectFrameTypeService>().findDescriptor(normalizedFrameTypeId) == null) {
      logger<ProjectFrameAllocator>()
        .warn("No <projectFrameType id=\"$normalizedFrameTypeId\"> is declared - frame-type policy is ignored for $projectStoreBaseDir")
    }

    return ResolvedFrameSettings(frameInfo = frameInfo ?: FrameInfo(), projectFrameTypeId = projectFrameTypeId)
  }

  private data class ResolvedFrameSettings(
    @JvmField val frameInfo: FrameInfo,
    @JvmField val projectFrameTypeId: String?,
  )

  override suspend fun projectNotLoaded(cannotConvertException: CannotConvertException?) {
    val frameHelper = if (deferredProjectFrameHelper.isCompleted) {
      deferredProjectFrameHelper.await()
    }
    else {
      deferredProjectFrameHelper.cancel("projectNotLoaded")
      null
    }

    withContext(Dispatchers.EDT) {
      if (cannotConvertException != null) {
        Messages.showErrorDialog(
          frameHelper?.frame,
          IdeBundle.message("error.cannot.convert.project", cannotConvertException.message),
          IdeBundle.message("title.cannot.convert.project")
        )
      }

      if (frameHelper != null) {
        // projectLoaded was called, but then due to some error, say cancellation, still projectNotLoaded is called
        (serviceAsync<WindowManager>() as WindowManagerImpl).releaseFrame(frameHelper)
      }
    }
  }
}

private suspend fun hideSplashWhenEditorOrToolWindowShown(project: Project) {
  suspendCancellableCoroutine { continuation ->
    val completed = AtomicBoolean(false)
    val connection = project.messageBus.simpleConnect()

    fun hideSplashAndResume() {
      if (!completed.compareAndSet(false, true)) {
        return
      }
      try {
        hideSplash()
        continuation.resume(Unit)
      }
      finally {
        connection.disconnect()
      }
    }

    try {
      connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
        override fun toolWindowShown(toolWindow: ToolWindow) {
          hideSplashAndResume()
        }
      })
      connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
        override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
          hideSplashAndResume()
        }
      })
      continuation.invokeOnCancellation {
        if (completed.compareAndSet(false, true)) {
          connection.disconnect()
        }
      }
    }
    catch (e: Throwable) {
      if (completed.compareAndSet(false, true)) {
        connection.disconnect()
      }
      throw e
    }
  }
}

private fun applyProjectFrameUiPolicy(
  toolWindowManager: ToolWindowManager,
  project: Project,
  projectFrameUiPolicy: ProjectFrameUiPolicy,
) {
  val startupToolWindowId = projectFrameUiPolicy.startupToolWindowIdToActivate
  val toolWindowIdsToHideOnStartup = projectFrameUiPolicy.toolWindowIdsToHideOnStartup
  val pendingToolWindowIds = ConcurrentHashMap.newKeySet<String>().apply {
    startupToolWindowId?.let(::add)
    addAll(toolWindowIdsToHideOnStartup)
  }
  if (pendingToolWindowIds.isEmpty()) {
    return
  }

  var connection: SimpleMessageBusConnection? = null

  fun disconnectIfDone() {
    if (project.isDisposed || pendingToolWindowIds.isEmpty()) {
      connection?.disconnect()
      connection = null
    }
  }

  fun applyPolicyForToolWindowId(toolWindowId: String) {
    if (!pendingToolWindowIds.contains(toolWindowId)) {
      return
    }
    val toolWindow = toolWindowManager.getToolWindow(toolWindowId) ?: return
    if (!pendingToolWindowIds.remove(toolWindowId)) {
      return
    }

    toolWindowManager.invokeLater {
      if (project.isDisposed) {
        return@invokeLater
      }

      if (startupToolWindowId == toolWindowId) {
        toolWindow.activate(null)
      }
      if (toolWindowId in toolWindowIdsToHideOnStartup) {
        toolWindow.hide()
      }
    }

    disconnectIfDone()
  }

  pendingToolWindowIds.toList().forEach(::applyPolicyForToolWindowId)
  if (pendingToolWindowIds.isEmpty()) {
    return
  }

  val expectedToolWindowManager = toolWindowManager
  val messageBusConnection = project.messageBus.connect(project)
  connection = messageBusConnection
  messageBusConnection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
    override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) {
      if (toolWindowManager !== expectedToolWindowManager) {
        return
      }

      ids.forEach(::applyPolicyForToolWindowId)
    }
  })

  // Cover ids registered between initial check and listener subscription.
  pendingToolWindowIds.toList().forEach(::applyPolicyForToolWindowId)
  disconnectIfDone()
}

private suspend fun restoreEditors(
  project: Project,
  fileEditorManager: FileEditorManagerImpl,
  opensFileAfterProjectOpen: Boolean,
) {
  coroutineScope {
    // only after FileEditorManager.init - DaemonCodeAnalyzer uses FileEditorManager
    // DaemonCodeAnalyzer wants DaemonCodeAnalyzerSettings
    val daemonCodeAnalyzerSettingsJob = launch {
      serviceAsync<DaemonCodeAnalyzerSettings>()
    }
    launch {
      // WolfTheProblemSolver uses PsiManager
      project.serviceAsync<PsiManager>()
      project.serviceAsync<WolfTheProblemSolver>()
    }
    launch {
      daemonCodeAnalyzerSettingsJob.join()
      project.serviceAsync<DaemonCodeAnalyzer>()
    }

    val (editorComponent, editorState) = fileEditorManager.init()
    // the empty state may be built as soon as restoring is over, in parallel with the rest of the project open, but it must not be
    // shown until project open is done opening editors of its own — the welcome tab and the README below are two of those.
    // `ModalityState.any()`, like the release in `postOpenEditors`, so a modal dialog during startup cannot reorder the two, and
    // `Dispatchers.EDT` because settling the empty state may mount or dispose components, which needs the write-intent lock.
    withContext(NonCancellable + Dispatchers.EDT + ModalityState.any().asContextElement()) {
      editorComponent.beginStartupEmptyStatePresentationHold()
      if (opensFileAfterProjectOpen) {
        // a file named on the command line is opened after project open has returned, so project open's own release does not cover it;
        // `openFileFromCommandLine` releases this second hold
        editorComponent.beginStartupEmptyStatePresentationHold()
      }
      if (editorState == null) {
        // there is nothing to restore, so preparation may start at once
        editorComponent.finishStartupEditorRestore()
      }
    }
    if (editorState == null) {
      WelcomeScreenTabService.getInstance(fileEditorManager.project).openTab()
      serviceAsync<StartUpPerformanceService>().editorRestoringTillHighlighted()
      return@coroutineScope
    }

    span("editor restoring") {
      editorComponent.createEditors(state = editorState)
    }

    span("editor reopening post-processing", Dispatchers.UI) {
      for (window in editorComponent.windows().toList()) {
        // clear empty splitters
        if (window.tabCount == 0) {
          withContext(Dispatchers.EDT) {
            // write-intent lock is required for now because we update actions synchronously here
            window.removeFromSplitter()
          }
          window.logEmptyStateIfMainSplitter(cause = EmptyStateCause.PROJECT_OPENED)
        }
      }

      focusSelectedEditor(editorComponent)
    }
  }
}

private suspend fun postOpenEditors(
  frameHelper: IdeProjectFrameHelper,
  fileEditorManager: FileEditorManagerImpl,
  project: Project,
  toolWindowInitJob: Job,
) {
  val startupEmptyStatePresentationHoldReleased = AtomicBoolean()

  suspend fun releaseStartupEmptyStatePresentationHold() {
    if (!startupEmptyStatePresentationHoldReleased.compareAndSet(false, true)) {
      return
    }
    // `Dispatchers.EDT` rather than the strict UI dispatcher: releasing may mount or dispose the empty state right here, and both take
    // the write-intent lock, which `Dispatchers.UI` forbids taking at all
    withContext(NonCancellable + Dispatchers.EDT + ModalityState.any().asContextElement()) {
      if (!project.isDisposed) {
        // project open is done opening editors: whatever the editor area shows now is what it keeps
        fileEditorManager.mainSplitters.endStartupEmptyStatePresentationHold()
      }
    }
  }

  try {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      // read the state of dockable editors
      fileEditorManager.initDockableContentFactory()

      frameHelper.postInit()
    }

    project.getUserData(ProjectImpl.CREATION_TIME)?.let { startTime ->
      LifecycleUsageTriggerCollector.onProjectOpenFinished(project, TimeoutUtil.getDurationMillis(startTime), frameHelper.isTabbedWindow)
    }

    // check after `initDockableContentFactory` - editor in a docked window
    if (!fileEditorManager.hasOpenFiles()) {
      stopOpenFilesActivity(project)
      // An editor area whose empty state claims focus is where the caret belongs on a project that restored no editors, the same way it
      // would belong in a restored editor. The request is made here, where "project open restored nothing" is known, and honoured when
      // the empty state is presented — the hold released below. An editor opened after this point (the README) drops it again.
      // Not on a remote dev host, which does not focus anything of its own on project open either — see `openProjectViewIfNeeded`.
      val emptyStateClaimKept = AtomicBoolean(true)
      var emptyStateFocusSettled: Deferred<Unit>? = null
      val emptyStateTakesFocus = !AppMode.isRemoteDevHost() &&
                                 withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                                   val splitters = fileEditorManager.mainSplitters
                                   splitters.emptyStateClaimsFocus().also { takesFocus ->
                                     if (takesFocus) {
                                       // The Project view below is opened without focus for this claim, which is a promise made before
                                       // there is a component to keep it with. Where it cannot be kept — a provider that builds nothing,
                                       // an empty state that is never presented — the Project view is focused after all, either here or
                                       // at the point it is opened, whichever comes second.
                                       emptyStateFocusSettled = splitters.requestEmptyStateFocusWhenPresentedAsync(onFocusUnclaimed = {
                                         emptyStateClaimKept.set(false)
                                         focusProjectViewIfOpened(project)
                                       })
                                     }
                                   }
                                 }
      if (!isNotificationSilentMode(project)) {
        finishEmptyEditorStartupBeforeProjectView(
          finishOpeningStartupEditors = { findAndOpenReadmeIfNeeded(project) },
          presentEmptyEditor = {
            releaseStartupEmptyStatePresentationHold()
            val settled = emptyStateFocusSettled
            if (settled != null && withTimeoutOrNull(EMPTY_STATE_FOCUS_SETTLEMENT_TIMEOUT) { settled.await() } == null) {
              // A broken or unexpectedly slow provider must not hold project open forever. Let Project view own focus; if the empty
              // state eventually mounts, its existing focus-owner guard prevents it from stealing that focus back.
              settled.cancel()
              emptyStateClaimKept.set(false)
            }
          },
          openProjectView = {
            openProjectViewIfNeeded(project, toolWindowInitJob, focusProjectView = {
              !emptyStateTakesFocus || !emptyStateClaimKept.get()
            })
          },
        )
      }
      FUSProjectHotStartUpMeasurer.reportNoMoreEditorsOnStartup(System.nanoTime())
    }
  }
  finally {
    releaseStartupEmptyStatePresentationHold()
  }
}

/**
 * Finishes every startup operation that may open an editor, then presents the empty editor before making the Project view visible.
 * Once a tool window owns focus, a claiming empty state deliberately refuses to steal it, so activating the Project view first would
 * lose the startup focus claim.
 */
internal suspend fun finishEmptyEditorStartupBeforeProjectView(
  finishOpeningStartupEditors: suspend () -> Unit,
  presentEmptyEditor: suspend () -> Unit,
  openProjectView: suspend () -> Unit,
) {
  finishOpeningStartupEditors()
  presentEmptyEditor()
  openProjectView()
}

/** Shows the Project view without making it active when the editor area keeps startup focus. */
internal fun presentProjectViewOnStartup(
  focusProjectView: Boolean,
  showProjectView: () -> Unit,
  activateProjectView: () -> Unit,
) {
  if (focusProjectView) {
    activateProjectView()
  }
  else {
    showProjectView()
  }
}

private val EMPTY_STATE_FOCUS_SETTLEMENT_TIMEOUT = 5.seconds

/**
 * Ends the startup presentation hold when [postOpenEditors] never got to release the hold [restoreEditors] took.
 *
 * This is the abandoning release rather than a paired one: restoring takes its hold uninterruptibly, so it may still be taken after
 * project open has been cancelled, and a paired release that arrives first would be spent on a hold that does not exist yet.
 */
private fun releaseStartupEmptyStatePresentationHold(project: Project) {
  val fileEditorManager = project.serviceIfCreated<FileEditorManager>() as? FileEditorManagerImpl ?: return
  // `mainSplitters` is a lateinit assigned inside `initJob`, so it exists only once that job has completed successfully — and where it
  // never did, restoring never returned from `init()` either, so no hold was ever taken
  fileEditorManager.initJob.invokeOnCompletion { throwable ->
    if (throwable != null) {
      return@invokeOnCompletion
    }
    ApplicationManager.getApplication().invokeLater(
      {
        if (!project.isDisposed) {
          fileEditorManager.mainSplitters.abandonStartupEmptyStatePresentationHold()
        }
      },
      ModalityState.any(),
    )
  }
}

private suspend fun focusSelectedEditor(editorComponent: EditorsSplitters) {
  val composite = editorComponent.currentWindow?.selectedComposite ?: return
  // TODO: this check for JB Client is made to keep the same behaviour in monolith,
  //   but in 253 we may remove this check and see what may be broken with async editor focus
  if (PlatformUtils.isJetBrainsClient()) {
    // in Remote Dev we cannot wait for composite availability synchronously,
    // since editors come from the backend and this is a too long process
    composite.coroutineScope.launch(Dispatchers.EDT + FUSProjectHotStartUpMeasurer.getContextElementWithEmptyProjectElementToPass()) {
      composite.waitForAvailable()
      focusSelectedEditorInComposite(composite)
    }
  }
  else {
    // let's focus the editor synchronously in local mode
    val isAvailable = withTimeoutOrNull(10.seconds) {
      composite.waitForAvailable()
      true
    }
    if (isAvailable == null) {
      logger<ProjectFrameAllocator>().warn(
        "Timed out waiting for editor to become available on project open (timeout=10s, file=${composite.file}, project=${composite.project.name})"
      )
      composite.coroutineScope.launch(Dispatchers.EDT) {
        composite.waitForAvailable()
        focusSelectedEditorInComposite(composite)
      }
      return
    }
    else {
      withContext(Dispatchers.EDT) {
        focusSelectedEditorInComposite(composite)
      }
    }
  }
}

private fun focusSelectedEditorInComposite(composite: EditorComposite) {
  val textEditor = composite.selectedEditor as? TextEditor
  val preferredFocusedComponent = composite.preferredFocusedComponent ?: return
  if (textEditor == null) {
    FUSProjectHotStartUpMeasurer.firstOpenedUnknownEditor(composite.file, System.nanoTime())
    preferredFocusedComponent.requestFocusInWindow()
  }
  else {
    AsyncEditorLoader.performWhenLoaded(textEditor.editor) {
      FUSProjectHotStartUpMeasurer.firstOpenedEditor(composite.file, composite.project, textEditor)
      preferredFocusedComponent.requestFocusInWindow()
    }
  }
}

internal fun applyBoundsOrDefault(frame: JFrame, bounds: Rectangle?, restoreOnlyLocation: Boolean = false) {
  if (bounds == null) {
    setDefaultSize(frame)
    frame.setLocationRelativeTo(null)
  }
  else {
    if (restoreOnlyLocation) {
      frame.location = bounds.location
      // we need to guarantee that the size is smaller than this screen to be able to maximize the frame after this
      setDefaultSize(frame, ScreenUtil.getScreenRectangle(bounds.location))
    }
    else {
      frame.bounds = bounds
    }
  }
}

private fun setDefaultSize(frame: JFrame, screen: Rectangle = ScreenUtil.getMainScreenBounds()) {
  val size = screen.size
  size.width = min(1400, size.width - 20)
  size.height = min(1000, size.height - 40)
  frame.size = size
  frame.minimumSize = Dimension(340, frame.minimumSize.height)
}

@ApiStatus.Internal
fun createIdeFrame(frameInfo: FrameInfo): IdeFrameImpl {
  val deviceBounds = frameInfo.bounds
  if (deviceBounds == null) {
    val frame = IdeFrameImpl()
    setDefaultSize(frame)
    frame.setLocationRelativeTo(null)
    return frame
  }
  else {
    checkForNonsenseBounds("IdeProjectFrameAllocatorKt.createNewProjectFrameProducer.deviceBounds", deviceBounds)
    val bounds = FrameBoundsConverter.convertFromDeviceSpaceAndFitToScreen(deviceBounds)
    val state = frameInfo.extendedState
    val isMaximized = FrameInfoHelper.isMaximized(state)
    val frame = IdeFrameImpl()
    val restoreNormalBounds = isMaximized && frame.extendedState == Frame.NORMAL && bounds != null

    // On macOS, setExtendedState(maximized) may UN-maximize the frame if the restored bounds are too large
    // (so the OS will "autodetect" it as already maximized).
    // Therefore, we only restore the location and use the default size (which is always computed to be less than the screen).
    applyBoundsOrDefault(frame, bounds, restoreOnlyLocation = isMaximized && SystemInfo.isMac)

    if (isMaximized && SystemInfo.isMac) {
      frame.isAboutToBeMaximized = true
      installMaximizeListener(frame)
      if (IDE_FRAME_EVENT_LOG.isDebugEnabled) {
        IDE_FRAME_EVENT_LOG.debug("Set about-to-be-maximized flag")
      }
    }

    frame.extendedState = state
    frame.minimumSize = Dimension(340, frame.minimumSize.height)

    // This has to be done after restoring the actual state, as otherwise setExtendedState() may overwrite the normal bounds.
    if (restoreNormalBounds) {
      frame.normalBounds = bounds
      frame.screenBounds = ScreenUtil.getScreenDevice(bounds)?.defaultConfiguration?.bounds
      if (IDE_FRAME_EVENT_LOG.isDebugEnabled) { // avoid unnecessary concatenation
        IDE_FRAME_EVENT_LOG.debug("Loaded saved normal bounds ${frame.normalBounds} for the screen ${frame.screenBounds}")
      }
    }
    return frame
  }
}

private fun installMaximizeListener(frame: IdeFrameImpl) {
  frame.addWindowStateListener(object : WindowStateListener {
    override fun windowStateChanged(e: WindowEvent) {
      if ((e.newState and Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
        frame.removeWindowStateListener(this)
        frame.isAboutToBeMaximized = false
        if (IDE_FRAME_EVENT_LOG.isDebugEnabled) {
          IDE_FRAME_EVENT_LOG.debug("Frame maximized at size=${frame.size}; cleared about-to-be-maximized flag")
        }
      }
    }
  })
}

/**
 * @param focusProjectView `false` when something else on this project's editor area takes focus instead — an empty state that claims
 * it — so the Project view is shown without activation rather than activated and then focused away from. Asked at the moment the
 * Project view is presented rather than in advance, because a claim on the editor area's focus can be given up before that moment.
 */
private suspend fun openProjectViewIfNeeded(project: Project, toolWindowInitJob: Job, focusProjectView: () -> Boolean) {
  if (!serviceAsync<RegistryManager>().`is`("ide.open.project.view.on.startup")) {
    return
  }

  toolWindowInitJob.join()

  // todo should we use `runOnceForProject(project, "OpenProjectViewOnStart")` or not?
  val toolWindowManager = project.serviceAsync<ToolWindowManager>()
  val focusRestore = withContext(Dispatchers.ui(CoroutineSupport.UiDispatcherKind.STRICT)) outer@{
    if (toolWindowManager.activeToolWindowId == null) {
      val shouldFocusProjectView = focusProjectView()
      val focusOwner = if (!shouldFocusProjectView && !AppMode.isRemoteDevHost()) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
      }
      else {
        null
      }
      val toolWindow = toolWindowManager.getToolWindow(ToolWindowId.PROJECT_VIEW)
      if (toolWindow != null) {
        // maybe readAction
        return@outer withContext(Dispatchers.EDT) {
          presentProjectViewOnStartup(
            focusProjectView = shouldFocusProjectView && !AppMode.isRemoteDevHost(),
            showProjectView = { toolWindow.show(null) },
            activateProjectView = { toolWindow.activate(null, true) },
          )
          focusOwner?.let { ProjectViewStartupFocusRestore(toolWindow, it, toolWindow.getReady(PROJECT_VIEW_STARTUP_REQUESTOR)) }
        }
      }
    }
    null
  }

  if (focusRestore != null) {
    try {
      withTimeoutOrNull(PROJECT_VIEW_STARTUP_READY_TIMEOUT) {
        focusRestore.ready.await()
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: RuntimeException) {
      logger<ProjectFrameAllocator>().warn("Project view did not become ready while preserving startup editor focus", e)
    }
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      restoreStartupEditorFocus(project, focusRestore)
    }
  }
}

@RequiresEdt
private fun restoreStartupEditorFocus(project: Project, restore: ProjectViewStartupFocusRestore) {
  val focusOwner = restore.focusOwner
  val currentFocusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
  if (!shouldRestoreStartupEditorFocus(focusOwner, currentFocusOwner, restore.toolWindow.component)) {
    return
  }
  IdeFocusManager.getInstance(project).requestFocusInProject(focusOwner, project)
}

/** Keeps a later user focus choice, while repairing focus stolen by Project view initialization. */
internal fun shouldRestoreStartupEditorFocus(
  startupFocusOwner: Component,
  currentFocusOwner: Component?,
  projectViewComponent: Component,
): Boolean {
  return startupFocusOwner.isShowing &&
         (currentFocusOwner == null || SwingUtilities.isDescendingFrom(currentFocusOwner, projectViewComponent))
}

private data class ProjectViewStartupFocusRestore(
  @JvmField val toolWindow: ToolWindow,
  @JvmField val focusOwner: Component,
  @JvmField val ready: ActionCallback,
)

private const val PROJECT_VIEW_STARTUP_REQUESTOR = "project view startup"
private val PROJECT_VIEW_STARTUP_READY_TIMEOUT = 5.seconds

/**
 * Focuses the Project view that [openProjectViewIfNeeded] showed without activation, because the editor area's empty state claimed that
 * focus and then found nothing to take it with.
 *
 * Does nothing when the Project view is not showing: it was never opened — notification silent mode, the registry key off — and there is
 * nothing here to focus. Where that is only because it has not been opened *yet*, the claim is already known to be given up by the time
 * it is, and it is opened focused instead.
 */
@RequiresEdt
private fun focusProjectViewIfOpened(project: Project) {
  if (project.isDisposed) {
    return
  }
  val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW) ?: return
  if (toolWindow.isVisible) {
    toolWindow.activate(null, true)
  }
}

private suspend fun findAndOpenReadmeIfNeeded(project: Project) {
  if (!AdvancedSettings.getBoolean("ide.open.readme.md.on.startup") ||
      FileEditorManagerKeys.DO_NOT_REOPEN_FILES.isIn(project)) {
    return
  }

  runOnceForProject(project = project, id = "ShowReadmeOnStart") {
    val projectDir = project.guessProjectDir() ?: return@runOnceForProject
    val files = mutableListOf(".github/README.md", "README.md", "docs/README.md")
    if (SystemInfoRt.isFileSystemCaseSensitive) {
      files.addAll(files.map { it.lowercase() })
    }
    val readme = files.firstNotNullOfOrNull(projectDir::findFileByRelativePath) ?: return@runOnceForProject
    if (!readme.isDirectory) {
      // Screen readers don't support JCEF preview (IJPL-59438)
      val layout =
        if (ScreenReader.isActive()) TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW else TextEditorWithPreview.Layout.SHOW_PREVIEW
      readme.putUserData(TextEditorWithPreview.DEFAULT_LAYOUT_FOR_FILE, layout)
      (project.serviceAsync<FileEditorManager>() as FileEditorManagerEx).openFile(readme, FileEditorOpenOptions(requestFocus = true))

      readme.putUserData(README_OPENED_ON_START_TS, Instant.now())
      FUSProjectHotStartUpMeasurer.openedReadme(readme, System.nanoTime())
    }
  }
}

private class MutableLoadingState(override val done: Job) : FrameLoadingState
