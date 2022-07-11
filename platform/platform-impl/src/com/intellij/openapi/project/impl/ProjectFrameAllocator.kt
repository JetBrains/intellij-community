// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.configurationStore.saveSettings
import com.intellij.conversion.CannotConvertException
import com.intellij.diagnostic.runActivity
import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.idea.SplashManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.progress.TaskSupport
import com.intellij.openapi.progress.withModalProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.ui.Messages
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
import kotlin.math.min

internal open class ProjectFrameAllocator(private val options: OpenProjectTask) {
  open suspend fun <T : Any> run(task: suspend () -> T): T {
    if (options.isNewProject && options.useDefaultProjectAsTemplate && options.project == null) {
      saveSettings(ProjectManager.getInstance().defaultProject, forceSavingAllSettings = true)
    }
    return task()
  }

  /**
   * Project is loaded and is initialized, project services and components can be accessed.
   * But start-up and post start-up activities are not yet executed.
   * Executed under a modal progress dialog.
   */
  open suspend fun projectLoaded(project: Project) {}

  open suspend fun projectNotLoaded(cannotConvertException: CannotConvertException?) {
    cannotConvertException?.let { throw cannotConvertException }
  }

  open fun projectOpened(project: Project) {}
}

internal class ProjectUiFrameAllocator(val options: OpenProjectTask, val projectStoreBaseDir: Path) : ProjectFrameAllocator(options) {
  private val deferredProjectFrameHelper = CompletableDeferred<ProjectFrameHelper>()

  override suspend fun <T : Any> run(task: suspend () -> T): T {
    if (options.isNewProject && options.useDefaultProjectAsTemplate && options.project == null) {
      withContext(Dispatchers.EDT) {
        SaveAndSyncHandler.getInstance().saveSettingsUnderModalProgress(ProjectManager.getInstance().defaultProject)
      }
    }

    return coroutineScope {
      val deferredWindow = async {
        val frameManager = createFrameManager()
        deferredProjectFrameHelper.complete(frameManager.init(this@ProjectUiFrameAllocator))
        frameManager.getWindow()
      }

      withModalProgressIndicator(owner = service<TaskSupport>().modalTaskOwner(deferredWindow), title = getProgressTitle()) {
        task()
      }
    }
  }

  @NlsContexts.ProgressTitle
  private fun getProgressTitle(): String {
    val projectName = options.projectName ?: (projectStoreBaseDir.fileName ?: projectStoreBaseDir).toString()
    return IdeUICustomization.getInstance().projectMessage("progress.title.project.loading.name", projectName)
  }

  private suspend fun createFrameManager(): ProjectUiFrameManager {
    if (options.frameManager is ProjectUiFrameManager) {
      return options.frameManager as ProjectUiFrameManager
    }

    val windowManager = WindowManager.getInstance() as WindowManagerImpl
    return withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      windowManager.removeAndGetRootFrame()?.let { freeRootFrame ->
        return@withContext DefaultProjectUiFrameManager(frame = freeRootFrame.frame!!, frameHelper = freeRootFrame)
      }

      runActivity("create a frame") {
        val preAllocated = SplashManager.getAndUnsetProjectFrame() as IdeFrameImpl?
        if (preAllocated == null) {
          val frameInfo = options.frameInfo
          if (frameInfo == null) {
            DefaultProjectUiFrameManager(frame = createNewProjectFrame(null), frameHelper = null)
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
  }

  override suspend fun projectLoaded(project: Project) {
    val windowManager = WindowManager.getInstance() as WindowManagerImpl
    val frameHelper = deferredProjectFrameHelper.await()
    withContext(Dispatchers.EDT) {
      runActivity("project frame assigning") {
        windowManager.assignFrame(frameHelper, project)
      }

      // not as a part of a project modal dialog
      val projectScope = (project as ProjectEx).coroutineScope
      projectScope.launch {
        val toolWindowManager = ToolWindowManager.getInstance(project) as? ToolWindowManagerImpl ?: return@launch
        // OpenFilesActivity inits component
        val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
        runActivity("tool window pane creation") {
          toolWindowManager.init(frameHelper, fileEditorManager)
        }
      }
      projectScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        val rootPane = frameHelper.rootPane!!
        runActivity("north components updating") {
          rootPane.updateNorthComponents()
        }

        runActivity("toolbar updating") {
          rootPane.initOrCreateToolbar(project)
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

private fun restoreFrameState(frameHelper: ProjectFrameHelper, frameInfo: FrameInfo) {
  val bounds = frameInfo.bounds?.let { FrameBoundsConverter.convertFromDeviceSpaceAndFitToScreen(it) }
  val frame = frameHelper.frame!!
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
  suspend fun init(allocator: ProjectUiFrameAllocator): ProjectFrameHelper

  fun getWindow(): Window
}

private class SplashProjectUiFrameManager(private val frame: IdeFrameImpl) : ProjectUiFrameManager {
  override fun getWindow() = frame

  override suspend fun init(allocator: ProjectUiFrameAllocator): ProjectFrameHelper {
    return withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      runActivity("project frame initialization") {
        val frameHelper = ProjectFrameHelper(frame, null)
        frameHelper.init()
        // otherwise, not painted if frame is already visible
        frame.validate()
        frameHelper
      }
    }
  }
}

private class DefaultProjectUiFrameManager(private val frame: IdeFrameImpl,
                                           private val frameHelper: ProjectFrameHelper?) : ProjectUiFrameManager {
  override fun getWindow() = frame

  override suspend fun init(allocator: ProjectUiFrameAllocator): ProjectFrameHelper {
    if (frameHelper != null) {
      return frameHelper
    }

    return withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      runActivity("project frame initialization") {
        doInit(allocator)
      }
    }
  }

  private fun doInit(allocator: ProjectUiFrameAllocator): ProjectFrameHelper {
    val info = (RecentProjectsManager.getInstance() as? RecentProjectsManagerBase)?.getProjectMetaInfo(allocator.projectStoreBaseDir)
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

    frame.isVisible = true
    frameHelper.init()
    return frameHelper
  }
}

private class SingleProjectUiFrameManager(private val frameInfo: FrameInfo, private val frame: IdeFrameImpl) : ProjectUiFrameManager {
  override fun getWindow() = frame

  override suspend fun init(allocator: ProjectUiFrameAllocator): ProjectFrameHelper {
    return withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      runActivity("project frame initialization") {
        doInit(allocator)
      }
    }
  }

  private fun doInit(allocator: ProjectUiFrameAllocator): ProjectFrameHelper {
    val options = allocator.options
    val frameHelper = ProjectFrameHelper(frame, readProjectSelfie(options.projectWorkspaceId, frame))

    if (frameInfo.fullScreen && FrameInfoHelper.isFullScreenSupportedInCurrentOs()) {
      frameHelper.toggleFullScreen(true)
    }

    frameHelper.init()
    frame.isVisible = true
    return frameHelper
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

internal data class OpenProjectImplOptions(
  @JvmField val frameInfo: FrameInfo? = null,
  @JvmField val frameManager: ProjectUiFrameManager? = null,
)