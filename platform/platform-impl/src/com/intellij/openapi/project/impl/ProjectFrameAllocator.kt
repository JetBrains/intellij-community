// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package com.intellij.openapi.project.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.configurationStore.saveSettings
import com.intellij.conversion.CannotConvertException
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.featureStatistics.fusCollectors.FileEditorCollector.EmptyStateCause
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.util.runOnceForProject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ReadmeShownUsageCollector.README_OPENED_ON_START_TS
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.*
import com.intellij.platform.diagnostic.telemetry.impl.getTraceActivity
import com.intellij.platform.diagnostic.telemetry.impl.rootTask
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.bootstrap.getAndUnsetSplashProjectFrame
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiManager
import com.intellij.toolWindow.computeToolWindowBeans
import com.intellij.ui.ScreenUtil
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Frame
import java.awt.GraphicsDevice
import java.awt.Rectangle
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JFrame
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

private typealias FrameAllocatorTask = suspend (saveTemplateJob: Job?, projectInitObserver: ProjectInitObserver?) -> Unit

internal sealed interface ProjectInitObserver {
  fun beforeInitRawProject(project: Project): Job

  val rawProjectDeferred: CompletableDeferred<Project>
}

internal open class ProjectFrameAllocator(private val options: OpenProjectTask) {
  open suspend fun run(task: FrameAllocatorTask) {
    return coroutineScope {
      task(scheduleSaveTemplate(options), null)
    }
  }

  open suspend fun projectNotLoaded(cannotConvertException: CannotConvertException?) {
    cannotConvertException?.let { throw cannotConvertException }
  }

  open fun projectOpened(project: Project) {
  }
}

private fun CoroutineScope.scheduleSaveTemplate(options: OpenProjectTask): Job? {
  if (options.isNewProject && options.useDefaultProjectAsTemplate && options.project == null) {
    return launch(CoroutineName("save default project") + Dispatchers.IO) {
      saveSettings(ProjectManager.getInstance().defaultProject, forceSavingAllSettings = true)
    }
  }
  else {
    return null
  }
}

private class FrameAllocatorProjectInitObserver(
  private val coroutineScope: CoroutineScope,
  private val deferredProjectFrameHelper: Deferred<ProjectFrameHelper>,
) : ProjectInitObserver {
  override fun beforeInitRawProject(project: Project): Job {
    val task = coroutineScope.launch {
      (project.serviceAsync<FileEditorManager>() as? FileEditorManagerImpl)?.initJob?.join()
    }

    coroutineScope.launch {
      val frameHelper = deferredProjectFrameHelper.await()

      launch {
        val windowManager = ApplicationManager.getApplication().serviceAsync<WindowManager>() as WindowManagerImpl
        withContext(Dispatchers.EDT) {
          windowManager.assignFrame(frameHelper, project)
          frameHelper.setRawProject(project)
        }
      }

      launch {
        task.join()
        val fileEditorManager = project.serviceAsync<FileEditorManager>() as FileEditorManagerImpl
        withContext(Dispatchers.EDT) {
          frameHelper.rootPane.getToolWindowPane().setDocumentComponent(fileEditorManager.mainSplitters)
        }
      }

      launch {
        rawProjectDeferred.join()
        span("project frame assigning") {
          frameHelper.setProject(project)
        }
      }
    }

    return task
  }

  override val rawProjectDeferred = CompletableDeferred<Project>()
}

internal class ProjectUiFrameAllocator(@JvmField val options: OpenProjectTask,
                                       private val projectStoreBaseDir: Path) : ProjectFrameAllocator(options) {
  private val deferredProjectFrameHelper = CompletableDeferred<ProjectFrameHelper>()

  override suspend fun run(task: FrameAllocatorTask) {
    coroutineScope {
      val debugTask = launch {
        delay(10.seconds)
        logger<ProjectFrameAllocator>().warn("Cannot load project in 10 seconds: ${dumpCoroutines()}")
      }

      try {
        doRun(outOfLoadingScope = this, task = task, deferredProjectFrameHelper = deferredProjectFrameHelper)
      }
      finally {
        debugTask.cancel()
      }
    }
  }

  private suspend fun doRun(outOfLoadingScope: CoroutineScope,
                            task: FrameAllocatorTask,
                            deferredProjectFrameHelper: CompletableDeferred<ProjectFrameHelper>) {
    coroutineScope {
      val loadingScope = this
      val projectInitObserver = FrameAllocatorProjectInitObserver(coroutineScope = loadingScope,
                                                                  deferredProjectFrameHelper = deferredProjectFrameHelper)

      val rawProjectDeferred = projectInitObserver.rawProjectDeferred
      async(CoroutineName("project frame creating")) {
        createFrameManager(loadingScope = loadingScope, deferredProjectFrameHelper = deferredProjectFrameHelper)
      }

      val startOfWaitingForReadyFrame = AtomicLong(-1)

      val reopeningEditorJob = outOfLoadingScope.launch {
        val project = rawProjectDeferred.await()
        span("restoreEditors") {
          restoreEditors(project = project, deferredProjectFrameHelper = deferredProjectFrameHelper)
        }

        val start = startOfWaitingForReadyFrame.get()
        if (start != -1L) {
          StartUpMeasurer.addCompletedActivity(start, "editor reopening and frame waiting", getTraceActivity())
        }
      }

      scheduleInitFrame(rawProjectDeferred = rawProjectDeferred,
                        reopeningEditorJob = reopeningEditorJob,
                        deferredProjectFrameHelper = deferredProjectFrameHelper)
      task(scheduleSaveTemplate(options), projectInitObserver)
      startOfWaitingForReadyFrame.set(System.nanoTime())
    }
  }

  private suspend fun createFrameManager(loadingScope: CoroutineScope,
                                         deferredProjectFrameHelper: CompletableDeferred<ProjectFrameHelper>) {
    val frame = options.frame
                ?: (ApplicationManager.getApplication().serviceIfCreated<WindowManager>() as? WindowManagerImpl)?.removeAndGetRootFrame()

    if (frame != null) {
      val loadingState = MutableLoadingState(done = loadingScope.coroutineContext.job)
      withContext(Dispatchers.EDT) {
        val frameHelper = ProjectFrameHelper(frame = frame, loadingState = loadingState)

        completeFrameAndCloseOnCancel(frameHelper, deferredProjectFrameHelper) {
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
      val loadingState = MutableLoadingState(done = loadingScope.coroutineContext.job)
      val frameHelper = withContext(Dispatchers.EDT) {
        val frameHelper = ProjectFrameHelper(frame = preAllocated, loadingState = loadingState)
        frameHelper.init()
        frameHelper
      }
      completeFrameAndCloseOnCancel(frameHelper, deferredProjectFrameHelper) {}
      return
    }

    val frameInfo = getFrameInfo()
    val frameProducer = createNewProjectFrameProducer(frameInfo = frameInfo)
    val loadingState = MutableLoadingState(done = loadingScope.coroutineContext.job)
    withContext(Dispatchers.EDT) {
      val frameHelper = ProjectFrameHelper(frameProducer.create(), loadingState = loadingState)
      // must be after preInit (frame decorator is required to set a full-screen mode)
      frameHelper.frame.isVisible = true
      updateFullScreenState(frameHelper, frameInfo)

      completeFrameAndCloseOnCancel(frameHelper, deferredProjectFrameHelper) {
        span("ProjectFrameHelper.init") {
          frameHelper.init()
        }
      }
    }
    return
  }

  private suspend inline fun completeFrameAndCloseOnCancel(frameHelper: ProjectFrameHelper,
                                                           deferredProjectFrameHelper: CompletableDeferred<ProjectFrameHelper>,
                                                           task: () -> Unit) {
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
      (WindowManager.getInstance() as WindowManagerImpl).releaseFrame(frameHelper)
    }
  }

  private fun getFrameInfo(): FrameInfo? {
    return options.frameInfo ?: RecentProjectsManagerBase.getInstanceEx().getProjectMetaInfo(projectStoreBaseDir)?.frame
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
        (WindowManager.getInstance() as WindowManagerImpl).releaseFrame(frameHelper)
      }
    }
  }
}

private fun CoroutineScope.scheduleInitFrame(rawProjectDeferred: CompletableDeferred<Project>,
                                             reopeningEditorJob: Job,
                                             deferredProjectFrameHelper: Deferred<ProjectFrameHelper>) {
  launch {
    val project = rawProjectDeferred.await()
    span("initFrame") {
      launch(CoroutineName("tool window pane creation")) {
        val toolWindowManager = async { project.serviceAsync<ToolWindowManager>() as? ToolWindowManagerImpl }
        val taskListDeferred = async(CoroutineName("toolwindow init command creation")) {
          computeToolWindowBeans(project = project)
        }
        toolWindowManager.await()?.init(frameHelperDeferred = deferredProjectFrameHelper,
                                        reopeningEditorJob = reopeningEditorJob,
                                        taskListDeferred = taskListDeferred)
      }

      @Suppress("DEPRECATION")
      project.coroutineScope.launch(rootTask()) {
        val frameHelper = deferredProjectFrameHelper.await()
        frameHelper.installDefaultProjectStatusBarWidgets(project)
        frameHelper.updateTitle(FrameTitleBuilder.getInstance().getProjectTitle(project), project)
      }
    }
  }
}

private suspend fun restoreEditors(project: Project, deferredProjectFrameHelper: Deferred<ProjectFrameHelper>) {
  val fileEditorManager = project.serviceAsync<FileEditorManager>() as? FileEditorManagerImpl ?: return
  coroutineScope {
    // only after FileEditorManager.init - DaemonCodeAnalyzer uses FileEditorManager
    launch {
      // WolfTheProblemSolver uses PsiManager
      project.serviceAsync<PsiManager>()
      project.serviceAsync<WolfTheProblemSolver>()
    }
    launch {
      project.serviceAsync<DaemonCodeAnalyzer>()
    }

    val (editorComponent, editorState) = fileEditorManager.init()
    if (editorState == null) {
      return@coroutineScope
    }

    span("editor restoring") {
      editorComponent.createEditors(state = editorState)
    }

    span("editor reopening post-processing", Dispatchers.EDT) {
      for (window in editorComponent.getWindows()) {
        // clear empty splitters
        if (window.tabCount == 0) {
          window.removeFromSplitter()
          window.logEmptyStateIfMainSplitter(cause = EmptyStateCause.PROJECT_OPENED)
        }
      }

      focusSelectedEditor(editorComponent)
    }
  }

  val elementToPass = FUSProjectHotStartUpMeasurer.getStartUpContextElementToPass()
  @Suppress("DEPRECATION")
  project.coroutineScope.launch {
    withContext(elementToPass ?: EmptyCoroutineContext) {
      val hasOpenFiles = fileEditorManager.hasOpenFiles()
      try {
        if (!hasOpenFiles) {
          EditorsSplitters.stopOpenFilesActivity(project)
        }

        val frameHelper = deferredProjectFrameHelper.await()
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          // read the state of dockable editors
          fileEditorManager.initDockableContentFactory()

          frameHelper.postInit()
        }

        project.getUserData(ProjectImpl.CREATION_TIME)?.let { startTime ->
          blockingContext {
            LifecycleUsageTriggerCollector.onProjectOpenFinished(project, TimeoutUtil.getDurationMillis(startTime),
                                                                 frameHelper.isTabbedWindow)
          }
        }

        if (!hasOpenFiles && !isNotificationSilentMode(project)) {
          project.putUserData(FileEditorManagerImpl.NOTHING_WAS_OPENED_ON_START, true)
          withContext(elementToPass ?: EmptyCoroutineContext) {
            findAndOpenReadmeIfNeeded(project)
          }
        }
      }
      finally {
        if (!hasOpenFiles) {
          FUSProjectHotStartUpMeasurer.reportNoMoreEditorsOnStartup()
        }
      }
    }
  }
}

private suspend fun focusSelectedEditor(editorComponent: EditorsSplitters) {
  val composite = editorComponent.currentWindow?.selectedComposite ?: return
  val editor = (composite.selectedEditor as? TextEditor)?.editor
  if (editor == null) {
    FUSProjectHotStartUpMeasurer.firstOpenedUnknownEditor(composite.file)
    composite.preferredFocusedComponent?.requestFocusInWindow()
  }
  else {
    blockingContext {
      AsyncEditorLoader.performWhenLoaded(editor) {
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

private suspend fun findAndOpenReadmeIfNeeded(project: Project) {
  if (!AdvancedSettings.getBoolean("ide.open.readme.md.on.startup")) {
    return
  }

  runOnceForProject(project = project, id = "ShowReadmeOnStart") {
    val projectDir = project.guessProjectDir() ?: return@runOnceForProject
    val files = mutableListOf(".github/README.md", "README.md", "docs/README.md")
    if (SystemInfoRt.isFileSystemCaseSensitive) {
      files += files.map { it.lowercase() }
    }
    val readme = files.firstNotNullOfOrNull(projectDir::findFileByRelativePath) ?: return@runOnceForProject
    if (!readme.isDirectory) {
      readme.putUserData(TextEditorWithPreview.DEFAULT_LAYOUT_FOR_FILE, TextEditorWithPreview.Layout.SHOW_PREVIEW)
      FileEditorManagerEx.getInstanceEx(project).openFile(readme, FileEditorOpenOptions(requestFocus = true))

      readme.putUserData(README_OPENED_ON_START_TS, Instant.now())
      FUSProjectHotStartUpMeasurer.openedReadme(readme)
    }
  }
}

private class MutableLoadingState(override val done: Job) : FrameLoadingState
