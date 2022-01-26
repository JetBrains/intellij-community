// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.configurationStore.saveSettings
import com.intellij.conversion.CannotConvertException
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.runActivity
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.plugins.StartupAbortedException
import com.intellij.idea.SplashManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.ProgressResult
import com.intellij.openapi.progress.impl.ProgressRunner
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.*
import com.intellij.platform.ProjectSelfieUtil
import com.intellij.ui.ComponentUtil
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.ScreenUtil
import com.intellij.ui.scale.ScaleContext
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Frame
import java.awt.Image
import java.io.EOFException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import javax.swing.JComponent
import kotlin.math.min

internal open class ProjectFrameAllocator(private val options: OpenProjectTask) {
  open fun <T : Any> run(task: (indicator: ProgressIndicator?) -> T?): CompletableFuture<T?> {
    if (options.isNewProject && options.useDefaultProjectAsTemplate && options.project == null) {
      runBlocking {
        saveSettings(ProjectManager.getInstance().defaultProject, forceSavingAllSettings = true)
      }
    }
    return CompletableFuture.completedFuture(task(ProgressManager.getInstance().progressIndicator))
  }

  /**
   * Project is loaded and is initialized, project services and components can be accessed.
   */
  open fun projectLoaded(project: Project) {}

  open fun projectNotLoaded(error: CannotConvertException?) {
    error?.let { throw error }
  }

  open fun projectOpened(project: Project) {}
}

internal class ProjectUiFrameAllocator(val options: OpenProjectTask, val projectStoreBaseDir: Path) : ProjectFrameAllocator(options) {
  // volatile not required because created in run (before executing run task)
  var frameManager: ProjectUiFrameManager? = null

  @Volatile
  var cancelled = false
    private set

  override fun <T : Any> run(task: (indicator: ProgressIndicator?) -> T?): CompletableFuture<T?> {
    if (options.isNewProject && options.useDefaultProjectAsTemplate && options.project == null) {
      invokeAndWaitIfNeeded {
        SaveAndSyncHandler.getInstance().saveSettingsUnderModalProgress(ProjectManager.getInstance().defaultProject)
      }
    }

    frameManager = createFrameManager()

    val progress = (ApplicationManager.getApplication() as ApplicationImpl)
      .createProgressWindowAsyncIfNeeded(getProgressTitle(), true, true, null, frameManager!!.getComponent(), null)

    val progressRunner = ProgressRunner<T?>(Function { indicator ->
      frameManager!!.init(this@ProjectUiFrameAllocator)
      try {
        task(indicator)
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Exception) {
        if (e is StartupAbortedException || e is PluginException) {
          StartupAbortedException.logAndExit(e, null)
        }
        else {
          logger<ProjectFrameAllocator>().error(e)
          projectNotLoaded(e as? CannotConvertException)
        }
        null
      }
    })
      .onThread(ProgressRunner.ThreadToUse.FJ)
      .modal()
      .withProgress(progress)

    val progressResultFuture: CompletableFuture<ProgressResult<T?>>
    if (ApplicationManager.getApplication().isDispatchThread) {
      progressResultFuture = CompletableFuture.completedFuture(progressRunner.submitAndGet())
    }
    else {
      progressResultFuture = progressRunner.submit()
    }

    return progressResultFuture.thenCompose { result ->
      when (result.throwable) {
        null -> CompletableFuture.completedFuture(result.result)
        is ProcessCanceledException -> CompletableFuture.completedFuture(null)
        else -> CompletableFuture.failedFuture(result.throwable)
      }
    }
  }

  @NlsContexts.ProgressTitle
  private fun getProgressTitle(): String {
    val projectName = options.projectName ?: (projectStoreBaseDir.fileName ?: projectStoreBaseDir).toString()
    return IdeUICustomization.getInstance().projectMessage("progress.title.project.loading.name", projectName)
  }

  private fun createFrameManager(): ProjectUiFrameManager {
    if (options.frameManager is ProjectUiFrameManager) {
      return options.frameManager as ProjectUiFrameManager
    }

    return invokeAndWaitIfNeeded {
      val freeRootFrame = (WindowManager.getInstance() as WindowManagerImpl).removeAndGetRootFrame()
      if (freeRootFrame != null) {
        return@invokeAndWaitIfNeeded DefaultProjectUiFrameManager(frame = freeRootFrame.frame!!, frameHelper = freeRootFrame)
      }

      runActivity("create a frame") {
        val preAllocated = SplashManager.getAndUnsetProjectFrame() as IdeFrameImpl?
        if (preAllocated == null) {
          if (options.frameManager is FrameInfo) {
            SingleProjectUiFrameManager(options.frameManager as FrameInfo, createNewProjectFrame(forceDisableAutoRequestFocus = false, frameInfo = options.frameManager as FrameInfo))
          }
          else {
            DefaultProjectUiFrameManager(frame = createNewProjectFrame(forceDisableAutoRequestFocus = false, frameInfo = null), frameHelper = null)
          }
        }
        else {
          SplashProjectUiFrameManager(preAllocated)
        }
      }
    }
  }

  override fun projectLoaded(project: Project) {
    ApplicationManager.getApplication().invokeLater({
      val frameHelper = frameManager?.frameHelper ?: return@invokeLater

      val windowManager = WindowManager.getInstance() as WindowManagerImpl
      runActivity("project frame assigning") {
        windowManager.assignFrame(frameHelper, project)
      }
      runActivity("tool window pane creation") {
        ToolWindowManagerEx.getInstanceEx(project).init(frameHelper)
      }
    }, project.disposed)
  }

  override fun projectNotLoaded(error: CannotConvertException?) {
    cancelled = true

    ApplicationManager.getApplication().invokeLater {
      val frameHelper = frameManager?.frameHelper
      frameManager = null

      if (error != null) {
        ProjectManagerImpl.showCannotConvertMessage(error, frameHelper?.frame)
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

  override fun projectOpened(project: Project) {
    frameManager!!.projectOpened(project)
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
internal fun createNewProjectFrame(forceDisableAutoRequestFocus: Boolean, frameInfo: FrameInfo?): IdeFrameImpl {
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

  if (forceDisableAutoRequestFocus || (!ApplicationManager.getApplication().isActive && ComponentUtil.isDisableAutoRequestFocus())) {
    frame.isAutoRequestFocus = false
  }
  frame.minimumSize = Dimension(340, frame.minimumSize.height)
  return frame
}

internal interface ProjectUiFrameManager {
  val frameHelper: ProjectFrameHelper?

  fun init(allocator: ProjectUiFrameAllocator)

  fun getComponent(): JComponent

  fun projectOpened(project: Project) {
  }
}

private class SplashProjectUiFrameManager(private val frame: IdeFrameImpl) : ProjectUiFrameManager {
  override var frameHelper: ProjectFrameHelper? = null
    private set

  override fun getComponent(): JComponent = frame.rootPane

  override fun init(allocator: ProjectUiFrameAllocator) {
    assert(frameHelper == null)
    ApplicationManager.getApplication().invokeLater {
      if (allocator.cancelled) {
        return@invokeLater
      }

      assert(frameHelper == null)

      runActivity("project frame initialization") {
        val frameHelper = ProjectFrameHelper(frame, null)
        frameHelper.init()
        // otherwise, not painted if frame is already visible
        frame.validate()
        this.frameHelper = frameHelper
      }
    }
  }
}

private class DefaultProjectUiFrameManager(private val frame: IdeFrameImpl, frameHelper: ProjectFrameHelper?) : ProjectUiFrameManager {
  override var frameHelper: ProjectFrameHelper? = frameHelper
    private set

  override fun getComponent(): JComponent = frame.rootPane

  override fun init(allocator: ProjectUiFrameAllocator) {
    if (frameHelper != null) {
      return
    }

    ApplicationManager.getApplication().invokeLater {
      if (allocator.cancelled) {
        return@invokeLater
      }

      runActivity("project frame initialization") {
        doInit(allocator)
      }
    }
  }

  private fun doInit(allocator: ProjectUiFrameAllocator) {
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
    this.frameHelper = frameHelper
  }
}

private class SingleProjectUiFrameManager(private val frameInfo: FrameInfo, private val frame: IdeFrameImpl) : ProjectUiFrameManager {
  override var frameHelper: ProjectFrameHelper? = null
    private set

  override fun getComponent(): JComponent = frame.rootPane

  override fun init(allocator: ProjectUiFrameAllocator) {
    ApplicationManager.getApplication().invokeLater {
      if (allocator.cancelled) {
        return@invokeLater
      }

      runActivity("project frame initialization") {
        doInit(allocator)
      }
    }
  }

  private fun doInit(allocator: ProjectUiFrameAllocator) {
    val options = allocator.options
    val frameHelper = ProjectFrameHelper(frame, readProjectSelfie(options.projectWorkspaceId, frame))

    if (frameInfo.fullScreen && FrameInfoHelper.isFullScreenSupportedInCurrentOs()) {
      frameHelper.toggleFullScreen(true)
    }

    frame.isVisible = true
    frameHelper.init()
    this.frameHelper = frameHelper
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
