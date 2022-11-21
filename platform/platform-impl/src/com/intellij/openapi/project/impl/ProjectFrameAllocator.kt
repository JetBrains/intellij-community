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
import com.intellij.openapi.fileEditor.FileEditorProvider
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
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.platform.ProjectSelfieUtil
import com.intellij.toolWindow.computeToolWindowBeans
import com.intellij.ui.*
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

private typealias FrameAllocatorTask<T> = suspend CoroutineScope.(saveTemplateJob: Job?, initFrame: (project: Project) -> Unit) -> T

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

internal class ProjectUiFrameAllocator(val options: OpenProjectTask, private val projectStoreBaseDir: Path) : ProjectFrameAllocator(options) {
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

      // use current context for executing async tasks to make sure that we pass correct modality
      // if someone uses runBlockingModal to call openProject
      val anyEditorOpened = CompletableDeferred<Unit>()
      try {
        val startOfWaitingForReadyFrame = AtomicLong(-1)
        val result = task(saveTemplateDeferred) { project ->
          launchAndMeasure("fileEditorProvider preloading", Dispatchers.IO) {
            FileEditorProvider.EP_FILE_EDITOR_PROVIDER.extensionList
          }

          async {
            launch {
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
            reopeningEditorJob.invokeOnCompletion {
              // make sure that anyEditorOpened is completed even if some error occurred
              anyEditorOpened.complete(Unit)
            }

            launch(CoroutineName("initFrame")) {
              initFrame(deferredProjectFrameHelper = deferredProjectFrameHelper,
                        project = project,
                        reopeningEditorJob = reopeningEditorJob,
                        deferredToolbarActionGroups = deferredToolbarActionGroups)
            }

            reopeningEditorJob
          }.invokeOnCompletion {
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
        }
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

      watcher(frameHelper, loadingState)

      // in a separate EDT task, as EDT is used for write actions and frame initialization should not slow down project opening
      withContext(Dispatchers.EDT) {
        frameHelper.init()
      }
      deferredProjectFrameHelper.complete(frameHelper)
      return
    }

    val preAllocated = SplashManager.getAndUnsetProjectFrame() as IdeFrameImpl?
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

    val frameInfo = options.frameInfo ?: RecentProjectsManagerBase.getInstanceEx().getProjectMetaInfo(projectStoreBaseDir)?.frame
    val frameProducer = createNewProjectFrame(frameInfo = frameInfo)
    val loadingState = MutableLoadingState(withContext(Dispatchers.IO) {
      readProjectSelfie(projectWorkspaceId = options.projectWorkspaceId, device = frameProducer.deviceOrDefault)
    })
    val frameHelper = withContext(Dispatchers.EDT) {
      val frameHelper = ProjectFrameHelper(frameProducer.create(), loadingState = loadingState)
      // must be after preInit (frame decorator is required to set full screen mode)
      frameHelper.frame.isVisible = true
      if (frameInfo != null && frameInfo.fullScreen && FrameInfoHelper.isFullScreenSupportedInCurrentOs()) {
        frameHelper.toggleFullScreen(true)
      }
      frameHelper
    }

    watcher(frameHelper, loadingState)

    // in a separate EDT task, as EDT is used for write actions and frame initialization should not slow down project opening
    withContext(Dispatchers.EDT) {
      frameHelper.init()
    }
    deferredProjectFrameHelper.complete(frameHelper)
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

private suspend fun restoreEditors(project: Project,
                                   deferredProjectFrameHelper: CompletableDeferred<ProjectFrameHelper>,
                                   anyEditorOpened: CompletableDeferred<Unit>) {
  val fileEditorManager = project.serviceAsync<FileEditorManager>().await() as? FileEditorManagerImpl ?: return
  val editorComponent = fileEditorManager.init()

  service<StartUpPerformanceService>().addActivityListener(project)

  val frameHelper = deferredProjectFrameHelper.await()
  withContext(Dispatchers.EDT) {
    frameHelper.rootPane.getToolWindowPane().setDocumentComponent(editorComponent)
  }

  runActivity(StartUpMeasurer.Activities.EDITOR_RESTORING) {
    editorComponent.restoreEditors(onStartup = true, anyEditorOpened = anyEditorOpened)
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
    toolWindowManager.init(frameHelper = frameHelper, reopeningEditorsJob = reopeningEditorJob, taskListDeferred = taskListDeferred)
  }

  launch {
    val frameHelper = deferredProjectFrameHelper.await()
    val toolbarActionGroups = deferredToolbarActionGroups.await()
    withContext(Dispatchers.EDT) {
      runActivity("toolbar init") {
        frameHelper.rootPane.initOrCreateToolbar(toolbarActionGroups)
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

@ApiStatus.Internal
internal fun createNewProjectFrame(frameInfo: FrameInfo?): ProjectFrameProducer {
  val deviceBounds = frameInfo?.bounds
  if (deviceBounds == null) {
    val size = ScreenUtil.getMainScreenBounds().size
    size.width = min(1400, size.width - 20)
    size.height = min(1000, size.height - 40)
    return object : ProjectFrameProducer {
      override val device = null

      override fun create(): IdeFrameImpl {
        val frame = IdeFrameImpl()
        SplashManager.hideBeforeShow(frame)
        frame.size = size
        frame.minimumSize = Dimension(340, frame.minimumSize.height)
        frame.setLocationRelativeTo(null)
        return frame
      }
    }
  }
  else {
    val boundsAndDevice = FrameBoundsConverter.convertFromDeviceSpaceAndFitToScreen(deviceBounds)
    val state = frameInfo.extendedState
    val isMaximized = FrameInfoHelper.isMaximized(state)
    val graphicsDevice = boundsAndDevice.value
    return object : ProjectFrameProducer {
      override val device = graphicsDevice

      override fun create(): IdeFrameImpl {
        val frame = IdeFrameImpl()
        SplashManager.hideBeforeShow(frame)
        if (isMaximized && frame.extendedState == Frame.NORMAL) {
          frame.normalBounds = boundsAndDevice.key
        }
        frame.bounds = boundsAndDevice.key
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
