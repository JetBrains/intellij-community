// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.configurationStore.saveSettings
import com.intellij.conversion.CannotConvertException
import com.intellij.diagnostic.launchAndMeasure
import com.intellij.diagnostic.runActivity
import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.idea.SplashManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.withModalContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.restoreOpenedFiles
import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.progress.util.ProgressDialogUI
import com.intellij.openapi.progress.util.ProgressDialogWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.*
import com.intellij.platform.ProjectSelfieUtil
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.ScreenUtil
import com.intellij.ui.scale.ScaleContext
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Frame
import java.awt.Image
import java.awt.Window
import java.io.EOFException
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlin.math.min

internal open class ProjectFrameAllocator(private val options: OpenProjectTask) {
  open suspend fun <T : Any> run(task: suspend CoroutineScope.(saveTemplateJob: Job?) -> T): T {
    return coroutineScope {
      task(saveTemplateAsync(options))
    }
  }

  /**
   * Project is loaded and is initialized, project services and components can be accessed.
   * But start-up and post start-up activities are not yet executed.
   * Executed under a modal progress dialog.
   */
  open suspend fun projectLoaded(project: Project): Job? = null

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

  override suspend fun <T : Any> run(task: suspend CoroutineScope.(saveTemplateJob: Job?) -> T): T {
    return coroutineScope {
      val deferredWindow = async(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        val frameManager = createFrameManager()
        val frameHelper = frameManager.createFrameHelper(this@ProjectUiFrameAllocator)
        frameHelper.init()
        val window = frameManager.getWindow()
        // implOptions == null - not via recents project - show frame immediately
        if (options.showFrameAsap || options.implOptions == null) {
          frameManager.getWindow().isVisible = true
        }

        deferredProjectFrameHelper.complete(frameHelper)
        window
      }

      withModalContext {
        // execute saveTemplateAsync under modal progress - write-safe context for saving template settings
        val saveTemplateDeferred = saveTemplateAsync(options)

        val showIndicatorJob = showModalIndicatorForProjectLoading(
          windowDeferred = deferredWindow,
          title = getProgressTitle(),
          isVisibleManaged = options.isVisibleManaged,
        )
        try {
          task(saveTemplateDeferred)
        }
        finally {
          showIndicatorJob.cancel()
        }
      }
    }
  }

  @NlsContexts.ProgressTitle
  private fun getProgressTitle(): String {
    val projectName = options.projectName ?: (projectStoreBaseDir.fileName ?: projectStoreBaseDir).toString()
    return IdeUICustomization.getInstance().projectMessage("progress.title.project.loading.name", projectName)
  }

  // executed in EDT
  private fun createFrameManager(): ProjectUiFrameManager {
    options.frameManager?.let {
      return it
    }

    (WindowManager.getInstance() as WindowManagerImpl).removeAndGetRootFrame()?.let { freeRootFrame ->
      return object : ProjectUiFrameManager {
        override suspend fun createFrameHelper(allocator: ProjectUiFrameAllocator) = freeRootFrame

        override fun getWindow() = freeRootFrame.frameOrNull!!
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

  override suspend fun projectLoaded(project: Project): Job? {
    val windowManager = WindowManager.getInstance() as WindowManagerImpl
    val frameHelper = deferredProjectFrameHelper.await()
    return withContext(Dispatchers.EDT) {
      runActivity("project frame assigning") {
        windowManager.assignFrame(frameHelper, project)
      }

      val fileEditorManager = FileEditorManager.getInstance(project) as? FileEditorManagerImpl ?: return@withContext null
      // not as a part of a project modal dialog
      project.coroutineScope.buildUi(editorSplitters = fileEditorManager.init(),
                                     fileEditorManager = fileEditorManager,
                                     frameHelper = frameHelper,
                                     project = project)
    }
  }

  private fun CoroutineScope.buildUi(editorSplitters: EditorsSplitters,
                                     fileEditorManager: FileEditorManagerImpl,
                                     frameHelper: ProjectFrameHelper,
                                     project: Project): Job {
    val reopeningEditorJob = launchAndMeasure("editor reopening") {
      restoreOpenedFiles(fileEditorManager, editorSplitters, project, frameHelper)
    }

    launchAndMeasure("tool window pane creation") {
      val toolWindowManager = ToolWindowManager.getInstance(project) as? ToolWindowManagerImpl ?: return@launchAndMeasure
      toolWindowManager.init(frameHelper, editorSplitters, reopeningEditorJob)
    }
    launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val rootPane = frameHelper.rootPane!!
      runActivity("north components updating") {
        rootPane.updateNorthComponents()
      }

      runActivity("toolbar updating") {
        rootPane.initOrCreateToolbar(project)
      }

      if (!options.isVisibleManaged) {
        frameHelper.frameOrNull?.isVisible = true
      }
    }
    return reopeningEditorJob
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
          frameHelper?.frameOrNull,
          IdeBundle.message("error.cannot.convert.project", cannotConvertException.message),
          IdeBundle.message("title.cannot.convert.project")
        )
      }

      if (frameHelper != null) {
        // projectLoaded was called, but then due to some error, say cancellation, still projectNotLoaded is called
        if (frameHelper.project == null) {
          Disposer.dispose(frameHelper)
        }
        else {
          WindowManagerEx.getInstanceEx().releaseFrame(frameHelper)
        }
      }
    }
  }
}

@Suppress("DuplicatedCode")
private fun CoroutineScope.showModalIndicatorForProjectLoading(
  windowDeferred: Deferred<Window>,
  title: @NlsContexts.ProgressTitle String,
  isVisibleManaged: Boolean,
): Job {
  return launch(Dispatchers.IO) {
    delay(300L)
    val mainJob = this@showModalIndicatorForProjectLoading.coroutineContext.job
    val window = windowDeferred.await()
    withContext(Dispatchers.EDT) {
      val ui = ProgressDialogUI()
      ui.progressBar.isIndeterminate = true
      ui.initCancellation(TaskCancellation.cancellable()) {
        mainJob.cancel("button cancel")
      }
      ui.backgroundButton.isVisible = false
      ui.updateTitle(title)
      val dialog = ProgressDialogWrapper(
        panel = ui.panel,
        cancelAction = {
          mainJob.cancel("dialog cancel")
        },
        peerFactory = { GlassPaneDialogWrapperPeer(window, it) }
      )
      dialog.setUndecorated(true)
      dialog.pack()
      launch { // will be run in an inner event loop
        val focusComponent = ui.cancelButton
        val previousFocusOwner = SwingUtilities.getWindowAncestor(focusComponent)?.mostRecentFocusOwner
        focusComponent.requestFocusInWindow()
        try {
          awaitCancellation()
        }
        finally {
          previousFocusOwner?.requestFocusInWindow()
        }
      }.invokeOnCompletion {
        dialog.close(DialogWrapper.OK_EXIT_CODE)
      }
      if (!isVisibleManaged) {
        window.isVisible = true
      }
      // will spin an inner event loop
      dialog.show()
    }
  }
}

private fun restoreFrameState(frameHelper: ProjectFrameHelper, frameInfo: FrameInfo) {
  val bounds = frameInfo.bounds?.let { FrameBoundsConverter.convertFromDeviceSpaceAndFitToScreen(it) }
  val frame = frameHelper.frameOrNull!!
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

  fun getWindow(): Window
}

private class SplashProjectUiFrameManager(private val frame: IdeFrameImpl) : ProjectUiFrameManager {
  override fun getWindow() = frame

  override suspend fun createFrameHelper(allocator: ProjectUiFrameAllocator): ProjectFrameHelper {
    runActivity("project frame initialization") {
      return ProjectFrameHelper(frame, null)
    }
  }
}

private class DefaultProjectUiFrameManager(private val frame: IdeFrameImpl) : ProjectUiFrameManager {
  override fun getWindow() = frame

  override suspend fun createFrameHelper(allocator: ProjectUiFrameAllocator): ProjectFrameHelper {
    runActivity("project frame initialization") {
      val info = RecentProjectsManagerBase.getInstanceEx().getProjectMetaInfo(allocator.projectStoreBaseDir)
      val frameInfo = info?.frame
      val frameHelper = ProjectFrameHelper(frame, readProjectSelfie(info?.projectWorkspaceId, frame))
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
  override fun getWindow() = frame

  override suspend fun createFrameHelper(allocator: ProjectUiFrameAllocator): ProjectFrameHelper {
    runActivity("project frame initialization") {
      val frameHelper = ProjectFrameHelper(frame, readProjectSelfie(allocator.options.projectWorkspaceId, frame))
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
  @JvmField val frameInfo: FrameInfo? = null,
  @JvmField val frameManager: ProjectUiFrameManager? = null,
  @JvmField val isVisibleManaged: Boolean = false,
)