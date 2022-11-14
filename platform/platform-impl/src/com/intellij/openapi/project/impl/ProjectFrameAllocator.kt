// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.configurationStore.saveSettings
import com.intellij.conversion.CannotConvertException
import com.intellij.diagnostic.*
import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.util.RunOnceUtil
import com.intellij.idea.SplashManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.util.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.platform.ProjectSelfieUtil
import com.intellij.ui.*
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.*
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.io.EOFException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

private typealias FrameAllocatorTask<T> = suspend CoroutineScope.(saveTemplateJob: Job?, projectLoaded: (project: Project) -> Unit) -> T

internal open class ProjectFrameAllocator(private val options: OpenProjectTask) {
  open suspend fun <T : Any> run(task: FrameAllocatorTask<T>): T {
    return coroutineScope {
      task(saveTemplateAsync(options)) {}
    }
  }

  open suspend fun projectNotLoaded(cannotConvertException: CannotConvertException?) {
    cannotConvertException?.let { throw cannotConvertException }
  }

  open fun projectOpened(project: Project) {}
}

private fun CoroutineScope.saveTemplateAsync(options: OpenProjectTask): Job? {
  if (options.isNewProject && options.useDefaultProjectAsTemplate && options.project == null) {
    return launch(Dispatchers.IO) {
      saveSettings(ProjectManager.getInstance().defaultProject, forceSavingAllSettings = true)
    }
  }
  else {
    return null
  }
}

internal class ProjectUiFrameAllocator(val options: OpenProjectTask, val projectStoreBaseDir: Path) : ProjectFrameAllocator(options) {
  private val deferredProjectFrameHelper = CompletableDeferred<ProjectFrameHelper>()

  override suspend fun <T : Any> run(task: FrameAllocatorTask<T>): T {
    return coroutineScope {
      val debugTask = launch {
        delay(10.seconds)
        logger<ProjectFrameAllocator>().warn("Cannot load project in 10 seconds: ${dumpCoroutines()}")
      }

      val finished = AtomicBoolean()
      val frameLoadingStateRef = AtomicReference<ProjectFrameHelper.MutableLoadingState?>()
      val loadingScope = this
      val job = coroutineContext.job
      launchAndMeasure("project frame helper initialization", Dispatchers.EDT) {
        val frameHelper = createFrameManager().createFrameHelper(this@ProjectUiFrameAllocator)
        frameHelper.loadingState?.let { loadingState ->
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

        val window = frameHelper.init()
        deferredProjectFrameHelper.complete(frameHelper)
        if (!options.isVisibleManaged) {
          window.isVisible = true
        }
      }

      val saveTemplateDeferred = saveTemplateAsync(options)

      @Suppress("DEPRECATION")
      val deferredToolbarActionGroups = ApplicationManager.getApplication().coroutineScope.async {
        runActivity("toolbar action groups computing") {
          MainToolbar.computeActionGroups()
        }
      }

      // use current context for executing async tasks to make sure that we pass correct modality
      // if someone uses runBlockingModal to call openProject
      try {
        val startOfWaitingForEditors = AtomicLong(-1)
        val result = task(saveTemplateDeferred) { project ->
          val reopeningEditorJob = async {
            projectLoaded(project, openProjectScope = this, deferredToolbarActionGroups = deferredToolbarActionGroups)
          }
          reopeningEditorJob.invokeOnCompletion {
            val start = startOfWaitingForEditors.get()
            if (start != -1L) {
              StartUpMeasurer.addCompletedActivity(start, "editor reopening waiting", ActivityCategory.DEFAULT, null)
            }
          }
        }
        startOfWaitingForEditors.set(System.nanoTime())
        result
      }
      finally {
        finished.set(true)
        try {
          frameLoadingStateRef.get()?.loading?.complete(Unit)
        }
        finally {
          debugTask.cancel()
        }
      }
    }
  }

  // executed in EDT
  private fun createFrameManager(): ProjectUiFrameManager {
    options.frameManager?.let {
      return it
    }

    (WindowManager.getInstance() as WindowManagerImpl).removeAndGetRootFrame()?.let { freeFrame ->
      return object : ProjectUiFrameManager {
        override suspend fun createFrameHelper(allocator: ProjectUiFrameAllocator): ProjectFrameHelper {
          return ProjectFrameHelper(freeFrame, selfie = null, withLoadingState = true)
        }
      }
    }

    return runActivity("create a frame") {
      val preAllocated = SplashManager.getAndUnsetProjectFrame() as IdeFrameImpl?
      if (preAllocated == null) {
        val frameInfo = options.frameInfo
        if (frameInfo == null) {
          DefaultProjectUiFrameManager(frame = createNewProjectFrame(null))
        }
        else {
          SingleProjectUiFrameManager(frameInfo = frameInfo, frame = createNewProjectFrame(frameInfo))
        }
      }
      else {
        SplashProjectUiFrameManager(preAllocated)
      }
    }
  }

  private suspend fun projectLoaded(project: Project,
                                    openProjectScope: CoroutineScope,
                                    deferredToolbarActionGroups: Deferred<List<Pair<ActionGroup, String>>>): Job? {
    // wait for reopeningEditorJob
    // 1. part of open project task
    // 2. runStartupActivities can consume a lot of CPU and editor restoring can be delayed, but it is a bad UX

    val frameHelper = deferredProjectFrameHelper.await()
    val fileEditorManager = project.serviceAsync<FileEditorManager>().await() as? FileEditorManagerImpl ?: return null
    val editorComponent = fileEditorManager.init()

    service<StartUpPerformanceService>().addActivityListener(project)

    val reopeningEditorJob = openProjectScope.launch {
      frameHelper.rootPane.getToolWindowPane().setDocumentComponent(editorComponent)
      editorComponent.restoreEditors(requestFocus = true)
    }

    val windowManager = ApplicationManager.getApplication().serviceAsync<WindowManager>().await() as WindowManagerImpl
    withContext(Dispatchers.EDT) {
      runActivity("project frame assigning") {
        windowManager.assignFrame(frameHelper, project)
        frameHelper.setProject(project)
      }
    }

    openProjectScope.initFrame(frameHelper = frameHelper,
                               project = project,
                               reopeningEditorJob = reopeningEditorJob,
                               deferredToolbarActionGroups = deferredToolbarActionGroups)

    @Suppress("DEPRECATION")
    project.coroutineScope.launch {
      reopeningEditorJob.join()

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

      if (!hasOpenFiles && !isNotificationSilentMode(project)) {
        project.putUserData(FileEditorManagerImpl.NOTHING_WAS_OPENED_ON_START, true)
        findAndOpenReadmeIfNeeded(project)
      }
    }
    return reopeningEditorJob
  }

  private fun CoroutineScope.initFrame(frameHelper: ProjectFrameHelper,
                                       project: Project,
                                       reopeningEditorJob: Job,
                                       deferredToolbarActionGroups: Deferred<List<Pair<ActionGroup, String>>>) {
    launch {
      frameHelper.installDefaultProjectStatusBarWidgets(project)
      frameHelper.updateTitle(FrameTitleBuilder.getInstance().getProjectTitle(project))
    }

    launchAndMeasure("tool window pane creation") {
      val toolWindowManager = project.serviceAsync<ToolWindowManager>().await() as? ToolWindowManagerImpl ?: return@launchAndMeasure
      toolWindowManager.init(frameHelper = frameHelper, reopeningEditorsJob = reopeningEditorJob)
    }

    launch {
      val toolbarActionGroups = deferredToolbarActionGroups.await()
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        runActivity("toolbar init") {
          frameHelper.rootPane.initOrCreateToolbar(toolbarActionGroups)
        }

        runActivity("north components updating") {
          frameHelper.rootPane.updateNorthComponents()
        }
      }
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
        Disposer.dispose(frameHelper)
      }
    }
  }
}

private fun restoreFrameState(frameHelper: ProjectFrameHelper, frameInfo: FrameInfo) {
  val bounds = frameInfo.bounds?.let { FrameBoundsConverter.convertFromDeviceSpaceAndFitToScreen(it) }
  val frame = frameHelper.frame
  if (bounds != null && FrameInfoHelper.isMaximized(frameInfo.extendedState) && frame.extendedState == Frame.NORMAL) {
    frame.rootPane.putClientProperty(IdeFrameImpl.NORMAL_STATE_BOUNDS, bounds)
  }
  if (bounds != null) {
    frame.bounds = bounds
  }
  frame.extendedState = frameInfo.extendedState

  if (frameInfo.fullScreen && FrameInfoHelper.isFullScreenSupportedInCurrentOs()) {
    frameHelper.toggleFullScreen(true)
  }
}

@ApiStatus.Internal
internal fun createNewProjectFrame(frameInfo: FrameInfo?): IdeFrameImpl {
  val frame = IdeFrameImpl()
  SplashManager.hideBeforeShow(frame)

  val deviceBounds = frameInfo?.bounds
  if (deviceBounds == null) {
    val size = ScreenUtil.getMainScreenBounds().size
    size.width = min(1400, size.width - 20)
    size.height = min(1000, size.height - 40)
    frame.size = size
    frame.setLocationRelativeTo(null)
  }
  else {
    val bounds = FrameBoundsConverter.convertFromDeviceSpaceAndFitToScreen(deviceBounds)
    val state = frameInfo.extendedState
    val isMaximized = FrameInfoHelper.isMaximized(state)
    if (isMaximized && frame.extendedState == Frame.NORMAL) {
      frame.rootPane.putClientProperty(IdeFrameImpl.NORMAL_STATE_BOUNDS, bounds)
    }
    frame.bounds = bounds
    frame.extendedState = state
  }

  frame.minimumSize = Dimension(340, frame.minimumSize.height)
  return frame
}

internal interface ProjectUiFrameManager {
  // executed in EDT
  suspend fun createFrameHelper(allocator: ProjectUiFrameAllocator): ProjectFrameHelper
}

private class SplashProjectUiFrameManager(private val frame: IdeFrameImpl) : ProjectUiFrameManager {
  override suspend fun createFrameHelper(allocator: ProjectUiFrameAllocator): ProjectFrameHelper {
    runActivity("project frame initialization") {
      return ProjectFrameHelper(frame, null, withLoadingState = true)
    }
  }
}

private class DefaultProjectUiFrameManager(private val frame: IdeFrameImpl) : ProjectUiFrameManager {
  override suspend fun createFrameHelper(allocator: ProjectUiFrameAllocator): ProjectFrameHelper {
    runActivity("project frame initialization") {
      val info = RecentProjectsManagerBase.getInstanceEx().getProjectMetaInfo(allocator.projectStoreBaseDir)
      val frameInfo = info?.frame
      val frameHelper = ProjectFrameHelper(frame, readProjectSelfie(info?.projectWorkspaceId, frame), withLoadingState = true)
      // must be after preInit (frame decorator is required to set full screen mode)
      if (frameInfo?.bounds == null) {
        (WindowManager.getInstance() as WindowManagerImpl).defaultFrameInfoHelper.info?.let {
          restoreFrameState(frameHelper, it)
        }
      }
      else {
        restoreFrameState(frameHelper, frameInfo)
      }
      return frameHelper
    }
  }
}

private class SingleProjectUiFrameManager(private val frameInfo: FrameInfo, private val frame: IdeFrameImpl) : ProjectUiFrameManager {
  override suspend fun createFrameHelper(allocator: ProjectUiFrameAllocator): ProjectFrameHelper {
    runActivity("project frame initialization") {
      val frameHelper = ProjectFrameHelper(frame = frame,
                                           selfie = readProjectSelfie(allocator.options.projectWorkspaceId, frame),
                                           withLoadingState = true)
      if (frameInfo.fullScreen && FrameInfoHelper.isFullScreenSupportedInCurrentOs()) {
        frameHelper.toggleFullScreen(true)
      }
      return frameHelper
    }
  }
}

private fun readProjectSelfie(projectWorkspaceId: String?, frame: IdeFrameImpl): Image? {
  if (projectWorkspaceId != null && Registry.`is`("ide.project.loading.show.last.state", false)) {
    try {
      return ProjectSelfieUtil.readProjectSelfie(projectWorkspaceId, ScaleContext.create(frame))
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

internal val OpenProjectTask.frameManager: ProjectUiFrameManager?
  get() = (implOptions as OpenProjectImplOptions?)?.frameManager

private val OpenProjectTask.isVisibleManaged: Boolean
  get() = (implOptions as OpenProjectImplOptions?)?.isVisibleManaged ?: false

internal data class OpenProjectImplOptions(
  @JvmField val recentProjectMetaInfo: RecentProjectMetaInfo,
  @JvmField val frameInfo: FrameInfo? = null,
  @JvmField val frameManager: ProjectUiFrameManager? = null,
  @JvmField val isVisibleManaged: Boolean = false,
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
