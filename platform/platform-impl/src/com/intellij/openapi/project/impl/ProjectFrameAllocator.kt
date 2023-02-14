// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.configurationStore.saveSettings
import com.intellij.conversion.CannotConvertException
import com.intellij.diagnostic.*
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.util.RunOnceUtil
import com.intellij.idea.getAndUnsetSplashProjectFrame
import com.intellij.idea.hideSplashBeforeShow
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.platform.ProjectSelfieUtil
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.toolWindow.computeToolWindowBeans
import com.intellij.ui.ScreenUtil
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.io.EOFException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

private typealias FrameAllocatorTask<T> = suspend CoroutineScope.(saveTemplateJob: Job?,
                                                                  rawProjectDeferred: CompletableDeferred<Project>?) -> T

internal open class ProjectFrameAllocator(private val options: OpenProjectTask) {
  open suspend fun <T : Any> run(task: FrameAllocatorTask<T>): T {
    return coroutineScope {
      task(saveTemplateAsync(options), null)
    }
  }

  open suspend fun projectNotLoaded(cannotConvertException: CannotConvertException?) {
    cannotConvertException?.let { throw cannotConvertException }
  }

  open fun projectOpened(project: Project) {
  }
}

private fun CoroutineScope.saveTemplateAsync(options: OpenProjectTask): Job? {
  if (options.isNewProject && options.useDefaultProjectAsTemplate && options.project == null) {
    return launch(Dispatchers.IO + CoroutineName("save default project")) {
      saveSettings(ProjectManager.getInstance().defaultProject, forceSavingAllSettings = true)
    }
  }
  else {
    return null
  }
}

internal class ProjectUiFrameAllocator(val options: OpenProjectTask,
                                       private val projectStoreBaseDir: Path) : ProjectFrameAllocator(options) {
  private val deferredProjectFrameHelper = CompletableDeferred<ProjectFrameHelper>()

  override suspend fun <T : Any> run(task: FrameAllocatorTask<T>): T {
    return coroutineScope {
      val debugTask = launch {
        delay(10.seconds)
        logger<ProjectFrameAllocator>().warn("Cannot load project in 10 seconds: ${dumpCoroutines()}")
      }

      val finished = AtomicBoolean()
      val frameLoadingStateRef = AtomicReference<MutableLoadingState?>()
      val loadingScope = this
      val job = coroutineContext.job
      launchAndMeasure("project frame creating") {
        createFrameManager { _, loadingState ->
          if (finished.get()) {
            loadingState.loading.complete(Unit)
          }
          else {
            frameLoadingStateRef.set(loadingState)
            loadingScope.launch {
              try {
                loadingState.loading.join()
              }
              catch (ignore: CancellationException) {
              }
              if (loadingState.loading.isCancelled) {
                job.cancel()
              }
            }
          }
        }
      }

      val saveTemplateDeferred = saveTemplateAsync(options)

      @Suppress("DEPRECATION")
      val deferredToolbarActionGroups = ApplicationManager.getApplication().coroutineScope.async {
        runActivity("toolbar action groups computing") {
          MainToolbar.computeActionGroups()
        }
      }

      // use the current context for executing async tasks to make sure that we pass the correct modality
      // if someone uses runBlockingModal to call openProject
      val anyEditorOpened = CompletableDeferred<Unit>()
      try {
        val startOfWaitingForReadyFrame = AtomicLong(-1)
        val rawProjectDeferred = CompletableDeferred<Project>()
        launch {
          val project = rawProjectDeferred.await()
          launchAndMeasure("fileEditorProvider preloading", Dispatchers.IO) {
            FileEditorProvider.EP_FILE_EDITOR_PROVIDER.extensionList
          }

          launch(CoroutineName("assign project to frame")) {
            val windowManager = ApplicationManager.getApplication().serviceAsync<WindowManager>().await() as WindowManagerImpl
            val frameHelper = deferredProjectFrameHelper.await()
            withContext(Dispatchers.EDT) {
              runActivity("project frame assigning") {
                windowManager.assignFrame(frameHelper, project)
                frameHelper.setProject(project)
              }
            }
          }

          val reopeningEditorJob = launch(CoroutineName("restoreEditors")) {
            restoreEditors(project = project, deferredProjectFrameHelper = deferredProjectFrameHelper, anyEditorOpened = anyEditorOpened)
          }

          launch(CoroutineName("initFrame")) {
            initFrame(deferredProjectFrameHelper = deferredProjectFrameHelper,
                      project = project,
                      reopeningEditorJob = reopeningEditorJob,
                      deferredToolbarActionGroups = deferredToolbarActionGroups)
          }
        }
          .invokeOnCompletion {
            // make sure that anyEditorOpened is completed even if some error occurred
            try {
              anyEditorOpened.complete(Unit)
            }
            finally {
              val start = startOfWaitingForReadyFrame.get()
              if (start != -1L) {
                StartUpMeasurer.addCompletedActivity(start, "editor reopening and frame waiting", ActivityCategory.DEFAULT, null)
              }
            }
          }

        val result = task(saveTemplateDeferred, rawProjectDeferred)
        startOfWaitingForReadyFrame.set(System.nanoTime())
        result
      }
      finally {
        finished.set(true)
        try {
          try {
            anyEditorOpened.join()
          }
          finally {
            frameLoadingStateRef.get()?.loading?.complete(Unit)
          }
        }
        finally {
          debugTask.cancel()
        }
      }
    }
  }

  private suspend fun createFrameManager(watcher: (frameHelper: ProjectFrameHelper, loadingState: MutableLoadingState) -> Unit) {
    var frame = options.frame
    if (frame == null) {
      val windowManager = ApplicationManager.getApplication().serviceAsync<WindowManager>().await() as WindowManagerImpl
      frame = windowManager.removeAndGetRootFrame()
    }

    if (frame != null) {
      val loadingState = MutableLoadingState(withContext(Dispatchers.IO) {
        readProjectSelfie(projectWorkspaceId = options.projectWorkspaceId, device = frame.graphicsConfiguration.device)
      })
      val frameHelper = withContext(Dispatchers.EDT) {
        ProjectFrameHelper(frame = frame, loadingState = loadingState)
      }

      updateFullScreenState(frameHelper, getFrameInfo())

      watcher(frameHelper, loadingState)

      // in a separate EDT task, as EDT is used for write actions and frame initialization, should not slow down project opening
      withContext(Dispatchers.EDT) {
        frameHelper.init()
      }
      deferredProjectFrameHelper.complete(frameHelper)
      return
    }

    val preAllocated = getAndUnsetSplashProjectFrame() as IdeFrameImpl?
    if (preAllocated != null) {
      val frameHelper = withContext(Dispatchers.EDT) {
        val loadingState = MutableLoadingState(selfie = null)
        val frameHelper = ProjectFrameHelper(frame = preAllocated, loadingState = loadingState)
        watcher(frameHelper, loadingState)
        frameHelper.init()
        frameHelper
      }
      deferredProjectFrameHelper.complete(frameHelper)
      return
    }

    val frameInfo = getFrameInfo()
    val frameProducer = createNewProjectFrame(frameInfo = frameInfo)
    val loadingState = MutableLoadingState(withContext(Dispatchers.IO) {
      readProjectSelfie(projectWorkspaceId = options.projectWorkspaceId, device = frameProducer.deviceOrDefault)
    })
    val frameHelper = withContext(Dispatchers.EDT) {
      val frameHelper = ProjectFrameHelper(frameProducer.create(), loadingState = loadingState)
      // must be after preInit (frame decorator is required to set a full-screen mode)
      frameHelper.frame.isVisible = true
      updateFullScreenState(frameHelper, frameInfo)
      frameHelper
    }

    watcher(frameHelper, loadingState)

    // in a separate EDT task, as EDT is used for write actions and frame initialization, should not slow down project opening
    withContext(Dispatchers.EDT) {
      frameHelper.init()
    }
    deferredProjectFrameHelper.complete(frameHelper)
  }

  private fun getFrameInfo(): FrameInfo? {
    return options.frameInfo ?: RecentProjectsManagerBase.getInstanceEx().getProjectMetaInfo(projectStoreBaseDir)?.frame
  }

  private fun updateFullScreenState(frameHelper: ProjectFrameHelper, frameInfo: FrameInfo?) {
    if (frameInfo != null && frameInfo.fullScreen && FrameInfoHelper.isFullScreenSupportedInCurrentOs()) {
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

private suspend fun restoreEditors(project: Project,
                                   deferredProjectFrameHelper: CompletableDeferred<ProjectFrameHelper>,
                                   anyEditorOpened: CompletableDeferred<Unit>) {
  val fileEditorManager = project.serviceAsync<FileEditorManager>().await() as? FileEditorManagerImpl ?: return

  service<StartUpPerformanceService>().addActivityListener(project)
  val (editorComponent, editorState) = withContext(Dispatchers.EDT) { fileEditorManager.init() }

  val frameHelper = coroutineScope {
    // only after FileEditorManager.init - DaemonCodeAnalyzer uses FileEditorManager
    launch {
      project.serviceAsync<WolfTheProblemSolver>().join()
      project.serviceAsync<DaemonCodeAnalyzer>().join()
    }

    val frameHelper = deferredProjectFrameHelper.await()
    launch(Dispatchers.EDT) {
      frameHelper.rootPane.getToolWindowPane().setDocumentComponent(editorComponent)
    }

    if (editorState != null) {
      runActivity(StartUpMeasurer.Activities.EDITOR_RESTORING) {
        editorComponent.restoreEditors(state = editorState, onStartup = true)
      }
    }
    anyEditorOpened.complete(Unit)
    frameHelper
  }

  @Suppress("DEPRECATION")
  project.coroutineScope.launch {
    val hasOpenFiles = fileEditorManager.hasOpenFiles()
    if (!hasOpenFiles) {
      EditorsSplitters.stopOpenFilesActivity(project)
    }

    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      // read state of dockable editors
      fileEditorManager.initDockableContentFactory()

      frameHelper.postInit()
      hasOpenFiles
    }

    project.getUserData(ProjectImpl.CREATION_TIME)?.let { startTime ->
      LifecycleUsageTriggerCollector.onProjectOpenFinished(project, TimeoutUtil.getDurationMillis(startTime), frameHelper.isTabbedWindow)
    }

    if (!hasOpenFiles && !isNotificationSilentMode(project)) {
      project.putUserData(FileEditorManagerImpl.NOTHING_WAS_OPENED_ON_START, true)
      findAndOpenReadmeIfNeeded(project)
    }
  }
}

private fun CoroutineScope.initFrame(deferredProjectFrameHelper: Deferred<ProjectFrameHelper>,
                                     project: Project,
                                     reopeningEditorJob: Job,
                                     deferredToolbarActionGroups: Deferred<List<Pair<ActionGroup, String>>>) {
  launchAndMeasure("tool window pane creation") {
    val toolWindowManager = project.serviceAsync<ToolWindowManager>().await() as? ToolWindowManagerImpl ?: return@launchAndMeasure

    val taskListDeferred = async {
      runActivity("toolwindow init command creation") {
        computeToolWindowBeans(project)
      }
    }
    val frameHelper = deferredProjectFrameHelper.await()
    toolWindowManager.init(frameHelper = frameHelper, reopeningEditorJob = reopeningEditorJob, taskListDeferred = taskListDeferred)
  }

  launch {
    val frameHelper = deferredProjectFrameHelper.await()
    val toolbarActionGroups = deferredToolbarActionGroups.await()
    withContext(Dispatchers.EDT) {
      runActivity("toolbar init") {
        frameHelper.rootPane.initToolbar(toolbarActionGroups)
      }

      runActivity("north components updating") {
        frameHelper.rootPane.updateNorthComponents()
      }
    }
  }

  launch {
    val frameHelper = deferredProjectFrameHelper.await()
    frameHelper.installDefaultProjectStatusBarWidgets(project)
    frameHelper.updateTitle(FrameTitleBuilder.getInstance().getProjectTitle(project), project)
  }
}

internal interface ProjectFrameProducer {
  val device: GraphicsDevice?

  val deviceOrDefault: GraphicsDevice
    get() = device ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice

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
internal fun createNewProjectFrame(frameInfo: FrameInfo?): ProjectFrameProducer {
  val deviceBounds = frameInfo?.bounds
  if (deviceBounds == null) {
    return object : ProjectFrameProducer {
      override val device = null

      override fun create(): IdeFrameImpl {
        val frame = IdeFrameImpl()
        hideSplashBeforeShow(frame)
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
        hideSplashBeforeShow(frame)
        if (isMaximized && frame.extendedState == Frame.NORMAL && boundsAndDevice != null) {
          frame.normalBounds = boundsAndDevice.first
        }
        applyBoundsOrDefault(frame, boundsAndDevice?.first)
        frame.extendedState = state
        frame.minimumSize = Dimension(340, frame.minimumSize.height)
        return frame
      }
    }
  }
}

internal interface ProjectUiFrameManager {
  // executed in EDT
  suspend fun createFrameHelper(allocator: ProjectUiFrameAllocator): ProjectFrameHelper
}

private fun readProjectSelfie(projectWorkspaceId: String?, device: GraphicsDevice): Image? {
  if (projectWorkspaceId != null && ProjectSelfieUtil.isEnabled) {
    try {
      return ProjectSelfieUtil.readProjectSelfie(projectWorkspaceId, device)
    }
    catch (e: Throwable) {
      if (e.cause !is EOFException) {
        logger<ProjectFrameAllocator>().warn(e)
      }
    }
  }
  return null
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

private fun findAndOpenReadmeIfNeeded(project: Project) {
  if (!AdvancedSettings.getBoolean("ide.open.readme.md.on.startup")) {
    return
  }

  RunOnceUtil.runOnceForProject(project, "ShowReadmeOnStart") {
    val projectDir = project.guessProjectDir() ?: return@runOnceForProject
    val files = mutableListOf(".github/README.md", "README.md", "docs/README.md")
    if (SystemInfoRt.isFileSystemCaseSensitive) {
      files += files.map { it.lowercase() }
    }
    val readme = files.firstNotNullOfOrNull(projectDir::findFileByRelativePath) ?: return@runOnceForProject
    if (!readme.isDirectory) {
      @Suppress("DEPRECATION")
      project.coroutineScope.launch(Dispatchers.EDT) {
        TextEditorWithPreview.openPreviewForFile(project, readme)
      }
    }
  }
}

private class MutableLoadingState(override var selfie: Image?) : FrameLoadingState {
  override val loading: CompletableDeferred<Unit> = CompletableDeferred()
}
