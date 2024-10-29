// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.util.runOnceForProject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.stopOpenFilesActivity
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ReadmeShownUsageCollector.README_OPENED_ON_START_TS
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.*
import com.intellij.platform.diagnostic.telemetry.impl.getTraceActivity
import com.intellij.platform.diagnostic.telemetry.impl.rootTask
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.bootstrap.getAndUnsetSplashProjectFrame
import com.intellij.platform.ide.bootstrap.hideSplash
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiManager
import com.intellij.toolWindow.computeToolWindowBeans
import com.intellij.ui.ScreenUtil
import com.intellij.util.TimeoutUtil
import com.intellij.util.messages.SimpleMessageBusConnection
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Frame
import java.awt.GraphicsDevice
import java.awt.Rectangle
import java.nio.file.Path
import java.time.Instant
import javax.swing.JFrame
import kotlin.coroutines.EmptyCoroutineContext
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
      launch {
        delay(10.seconds)
        logger<ProjectFrameAllocator>().warn("Cannot load project in 10 seconds: ${dumpCoroutines()}")
      }

      launch {
        val project = projectInitObservable.awaitProjectInit()
        val connection = project.messageBus.connect(this)
        hideSplashWhenEditorOrToolWindowShown(connection)
      }
    }
  }

  override suspend fun run(projectInitObservable: ProjectInitObservable) {
    coroutineScope {
      val job = currentCoroutineContext().job

      async(CoroutineName("project frame creating")) {
        val loadingState = MutableLoadingState(done = job)
        createFrameManager(loadingState)
      }

      launch {
        val project = projectInitObservable.awaitProjectPreInit()
        val frameHelper = deferredProjectFrameHelper.await()

        launch {
          val windowManager = serviceAsync<WindowManager>() as WindowManagerImpl
          withContext(Dispatchers.EDT) {
            windowManager.assignFrame(frameHelper, project)
            frameHelper.setRawProject(project)
          }
        }

        launch {
          val fileEditorManager = project.serviceAsync<FileEditorManager>() as FileEditorManagerImpl
          fileEditorManager.initJob.join()
          withContext(Dispatchers.EDT) {
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
          restoreEditors(project = project, fileEditorManager = fileEditorManager)
        }

        val start = projectInitObservable.projectInitTimestamp
        if (start != -1L) {
          StartUpMeasurer.addCompletedActivity(start, "editor reopening and frame waiting", getTraceActivity())
        }
      }

      val toolWindowInitJob = launch {
        val project = projectInitObservable.awaitProjectInit()
        span<Unit>("initFrame") {
          launch(CoroutineName("tool window pane creation")) {
            val toolWindowManager = async { project.serviceAsync<ToolWindowManager>() as? ToolWindowManagerImpl }
            val taskListDeferred = async(CoroutineName("toolwindow init command creation")) {
              computeToolWindowBeans(project = project)
            }
            val toolWindowPane = withContext(Dispatchers.EDT) {
              deferredProjectFrameHelper.await().toolWindowPane
            }
            toolWindowManager.await()?.init(
              toolWindowPane,
              reopeningEditorJob = reopeningEditorJob,
              taskListDeferred = taskListDeferred,
            )
          }
        }
      }

      launch {
        val project = projectInitObservable.awaitProjectInit()
        val startUpContextElementToPass = FUSProjectHotStartUpMeasurer.getStartUpContextElementToPass() ?: EmptyCoroutineContext

        val onNoEditorsLeft = blockingContext {
          captureThreadContext(Runnable { FUSProjectHotStartUpMeasurer.reportNoMoreEditorsOnStartup(System.nanoTime()) })
        }

        @Suppress("UsagesOfObsoleteApi")
        (project as ComponentManagerEx).getCoroutineScope().launch(startUpContextElementToPass + rootTask()) {
          val frameHelper = deferredProjectFrameHelper.await()
          launch {
            frameHelper.installDefaultProjectStatusBarWidgets(project)
            frameHelper.updateTitle(serviceAsync<FrameTitleBuilder>().getProjectTitle(project), project)
          }

          reopeningEditorJob.join()
          postOpenEditors(
            frameHelper = frameHelper,
            fileEditorManager = project.serviceAsync<FileEditorManager>() as FileEditorManagerImpl,
            toolWindowInitJob = toolWindowInitJob,
            project = project,
          )
        }.invokeOnCompletion { throwable ->
          if (throwable != null) {
            onNoEditorsLeft.run()
          }
        }
      }
    }
  }

  private suspend fun createFrameManager(loadingState: FrameLoadingState) {
    val frame = options.frame
                ?: (ApplicationManager.getApplication().serviceIfCreated<WindowManager>() as? WindowManagerImpl)?.removeAndGetRootFrame()

    if (frame != null) {
      withContext(Dispatchers.EDT) {
        val frameHelper = IdeProjectFrameHelper(frame = frame, loadingState = loadingState)

        completeFrameAndCloseOnCancel(frameHelper) {
          if (options.forceOpenInNewFrame) {
            updateFullScreenState(frameHelper, getFrameInfo())
          }

          frameHelper.init()
          frameHelper.setInitBounds(getFrameInfo()?.bounds)
        }
      }
      return
    }

    val preAllocated = getAndUnsetSplashProjectFrame() as IdeFrameImpl?
    if (preAllocated != null) {
      val frameHelper = withContext(Dispatchers.EDT) {
        val frameHelper = IdeProjectFrameHelper(frame = preAllocated, loadingState = loadingState)
        frameHelper.init()
        frameHelper
      }
      completeFrameAndCloseOnCancel(frameHelper) {}
      return
    }

    val frameInfo = getFrameInfo()
    val frameProducer = createNewProjectFrameProducer(frameInfo = frameInfo)
    withContext(Dispatchers.EDT) {
      val frameHelper = IdeProjectFrameHelper(frameProducer.create(), loadingState = loadingState)
      // must be after preInit (frame decorator is required to set a full-screen mode)
      frameHelper.frame.isVisible = true
      updateFullScreenState(frameHelper, frameInfo)

      completeFrameAndCloseOnCancel(frameHelper) {
        span("ProjectFrameHelper.init") {
          frameHelper.init()
        }
      }
    }
    return
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
    catch (ignore: CancellationException) {
    }

    // make sure that in case of some error we close frame for a not loaded project
    withContext(Dispatchers.EDT + NonCancellable) {
      (serviceAsync<WindowManager>() as WindowManagerImpl).releaseFrame(frameHelper)
    }
  }

  private suspend fun getFrameInfo(): FrameInfo? {
    return options.frameInfo
           ?: (serviceAsync<RecentProjectsManager>() as RecentProjectsManagerBase).getProjectMetaInfo(projectStoreBaseDir)?.frame
  }

  private fun updateFullScreenState(frameHelper: ProjectFrameHelper, frameInfo: FrameInfo?) {
    if (frameInfo?.fullScreen == true && FrameInfoHelper.isFullScreenSupportedInCurrentOs()) {
      frameHelper.toggleFullScreen(true)
    }
  }

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

private suspend fun hideSplashWhenEditorOrToolWindowShown(connection: SimpleMessageBusConnection) {
  val splashHiddenDeferred = CompletableDeferred<Unit>()

  fun hideSplashAndComplete() {
    hideSplash()
    connection.disconnect()
    splashHiddenDeferred.complete(Unit)
  }

  connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
    override fun toolWindowShown(toolWindow: ToolWindow) {
      hideSplashAndComplete()
    }
  })
  connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
      hideSplashAndComplete()
    }
  })
  splashHiddenDeferred.await()
}

private suspend fun restoreEditors(project: Project, fileEditorManager: FileEditorManagerImpl) {
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
    if (editorState == null) {
      serviceAsync<StartUpPerformanceService>().editorRestoringTillHighlighted()
      return@coroutineScope
    }

    span("editor restoring") {
      editorComponent.createEditors(state = editorState)
    }

    span("editor reopening post-processing", Dispatchers.EDT) {
      for (window in editorComponent.windows().toList()) {
        // clear empty splitters
        if (window.tabCount == 0) {
          window.removeFromSplitter()
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
  withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
    // read the state of dockable editors
    fileEditorManager.initDockableContentFactory()

    frameHelper.postInit()
  }

  project.getUserData(ProjectImpl.CREATION_TIME)?.let { startTime ->
    blockingContext {
      LifecycleUsageTriggerCollector.onProjectOpenFinished(project, TimeoutUtil.getDurationMillis(startTime), frameHelper.isTabbedWindow)
    }
  }

  // check after `initDockableContentFactory` - editor in a docked window
  if (!fileEditorManager.hasOpenFiles()) {
    stopOpenFilesActivity(project)
    if (!isNotificationSilentMode(project)) {
      openProjectViewIfNeeded(project, toolWindowInitJob)
      findAndOpenReadmeIfNeeded(project)
    }
    blockingContext {
      FUSProjectHotStartUpMeasurer.reportNoMoreEditorsOnStartup(System.nanoTime())
    }
  }
}

private suspend fun focusSelectedEditor(editorComponent: EditorsSplitters) {
  val composite = editorComponent.currentWindow?.selectedComposite ?: return
  composite.waitForAvailable()
  val textEditor = composite.selectedEditor as? TextEditor
  if (textEditor == null) {
    FUSProjectHotStartUpMeasurer.firstOpenedUnknownEditor(composite.file, System.nanoTime())
    composite.preferredFocusedComponent?.requestFocusInWindow()
  }
  else {
    blockingContext {
      AsyncEditorLoader.performWhenLoaded(textEditor.editor) {
        FUSProjectHotStartUpMeasurer.firstOpenedEditor(composite.file)
        composite.preferredFocusedComponent?.requestFocusInWindow()
      }
    }
  }
}

internal interface ProjectFrameProducer {
  val device: GraphicsDevice?

  fun create(): IdeFrameImpl
}

internal fun applyBoundsOrDefault(frame: JFrame, bounds: Rectangle?) {
  if (bounds == null) {
    setDefaultSize(frame)
    frame.setLocationRelativeTo(null)
  }
  else {
    frame.bounds = bounds
  }
}

private fun setDefaultSize(frame: JFrame) {
  val size = ScreenUtil.getMainScreenBounds().size
  size.width = min(1400, size.width - 20)
  size.height = min(1000, size.height - 40)
  frame.size = size
  frame.minimumSize = Dimension(340, frame.minimumSize.height)
}

@ApiStatus.Internal
internal fun createNewProjectFrameProducer(frameInfo: FrameInfo?): ProjectFrameProducer {
  val deviceBounds = frameInfo?.bounds
  if (deviceBounds == null) {
    return object : ProjectFrameProducer {
      override val device = null

      override fun create(): IdeFrameImpl {
        val frame = IdeFrameImpl()
        setDefaultSize(frame)
        frame.setLocationRelativeTo(null)
        return frame
      }
    }
  }
  else {
    val boundsAndDevice = FrameBoundsConverter.convertFromDeviceSpaceAndFitToScreen(deviceBounds)
    val state = frameInfo.extendedState
    val isMaximized = FrameInfoHelper.isMaximized(state)
    val graphicsDevice = boundsAndDevice?.second
    return object : ProjectFrameProducer {
      override val device = graphicsDevice

      override fun create(): IdeFrameImpl {
        val frame = IdeFrameImpl()
        if (isMaximized && frame.extendedState == Frame.NORMAL && boundsAndDevice != null) {
          frame.normalBounds = boundsAndDevice.first
          frame.screenBounds = ScreenUtil.getScreenDevice(boundsAndDevice.first)?.defaultConfiguration?.bounds
          if (IDE_FRAME_EVENT_LOG.isDebugEnabled) { // avoid unnecessary concatenation
            IDE_FRAME_EVENT_LOG.debug("Loaded saved normal bounds ${frame.normalBounds} for the screen ${frame.screenBounds}")
          }
        }
        applyBoundsOrDefault(frame, boundsAndDevice?.first)
        frame.extendedState = state
        frame.minimumSize = Dimension(340, frame.minimumSize.height)
        return frame
      }
    }
  }
}

internal val OpenProjectTask.frameInfo: FrameInfo?
  get() = (implOptions as OpenProjectImplOptions?)?.frameInfo

internal val OpenProjectTask.frame: IdeFrameImpl?
  get() = (implOptions as OpenProjectImplOptions?)?.frame

internal data class OpenProjectImplOptions(
  @JvmField val recentProjectMetaInfo: RecentProjectMetaInfo,
  @JvmField val frameInfo: FrameInfo? = null,
  @JvmField val frame: IdeFrameImpl? = null,
)

private suspend fun openProjectViewIfNeeded(project: Project, toolWindowInitJob: Job) {
  if (!serviceAsync<RegistryManager>().`is`("ide.open.project.view.on.startup")) {
    return
  }

  toolWindowInitJob.join()

  // todo should we use `runOnceForProject(project, "OpenProjectViewOnStart")` or not?
  val toolWindowManager = project.serviceAsync<ToolWindowManager>()
  withContext(Dispatchers.EDT) {
    if (toolWindowManager.activeToolWindowId == null) {
      //maybe readaction
      writeIntentReadAction {
        toolWindowManager.getToolWindow("Project")?.activate(null)
      }
    }
  }
}

private suspend fun findAndOpenReadmeIfNeeded(project: Project) {
  if (!AdvancedSettings.getBoolean("ide.open.readme.md.on.startup")) {
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
      readme.putUserData(TextEditorWithPreview.DEFAULT_LAYOUT_FOR_FILE, TextEditorWithPreview.Layout.SHOW_PREVIEW)
      (project.serviceAsync<FileEditorManager>() as FileEditorManagerEx).openFile(readme, FileEditorOpenOptions(requestFocus = true))

      readme.putUserData(README_OPENED_ON_START_TS, Instant.now())
      FUSProjectHotStartUpMeasurer.openedReadme(readme, System.nanoTime())
    }
  }
}

private class MutableLoadingState(override val done: Job) : FrameLoadingState