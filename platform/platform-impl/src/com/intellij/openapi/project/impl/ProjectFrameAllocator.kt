// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl

import com.intellij.configurationStore.saveSettings
import com.intellij.conversion.CannotConvertException
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.runActivity
import com.intellij.diagnostic.runMainActivity
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.plugins.StartupAbortedException
import com.intellij.idea.SplashManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.*
import com.intellij.platform.ProjectSelfieUtil
import com.intellij.ui.ComponentUtil
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.ScreenUtil
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Frame
import java.awt.Image
import java.io.EOFException
import java.nio.file.Path
import kotlin.math.min

internal open class ProjectFrameAllocator(private val options: OpenProjectTask) {
  open fun <T : Any> run(task: () -> T?): T? {
    if (options.isNewProject && options.useDefaultProjectAsTemplate && options.project == null) {
      runBlocking {
        saveSettings(ProjectManager.getInstance().defaultProject, forceSavingAllSettings = true)
      }
    }
    return task()
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

internal class ProjectUiFrameAllocator(private val options: OpenProjectTask, private val projectStoreBaseDir: Path) : ProjectFrameAllocator(options) {
  // volatile not required because created in run (before executing run task)
  private var frameHelper: ProjectFrameHelper? = null

  private var isFrameBoundsCorrect = false
  private var isFrameBoundsRestored = false

  @Volatile
  private var cancelled = false

  override fun <T : Any> run(task: () -> T?): T? {
    var result: T? = null
    val frame = invokeAndWaitIfNeeded {
      if (options.isNewProject && options.useDefaultProjectAsTemplate && options.project == null) {
        SaveAndSyncHandler.getInstance().saveSettingsUnderModalProgress(ProjectManager.getInstance().defaultProject)
      }

      createFrameIfNeeded()
    }

    val progressTask = object : Runnable {
      override fun run() {
        if (frameHelper == null) {
          ApplicationManager.getApplication().invokeLater {
            if (cancelled) {
              return@invokeLater
            }

            runActivity("project frame initialization") {
              initNewFrame(frame)
            }
          }
        }

        try {
          result = task()
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: Exception) {
          if (e is StartupAbortedException || e is PluginException) {
            StartupAbortedException.logAndExit(e)
          }
          else {
            logger<ProjectFrameAllocator>().error(e)
            projectNotLoaded(e as? CannotConvertException)
          }
        }
      }
    }

    if (ApplicationManagerEx.getApplicationEx().runProcessWithProgressSynchronously(progressTask, getProgressTitle(), false, true, null, frame.rootPane, null)) {
      return result
    }
    // cancelled
    return null
  }

  @NlsContexts.ProgressTitle
  private fun getProgressTitle(): String {
    val projectName = options.projectName ?: (projectStoreBaseDir.fileName ?: projectStoreBaseDir).toString()
    return IdeUICustomization.getInstance().projectMessage("progress.title.project.loading.name", projectName)
  }

  @RequiresEdt
  private fun initNewFrame(frame: IdeFrameImpl) {
    if (frame.isVisible) {
      val frameHelper = ProjectFrameHelper(frame, null)
      frameHelper.init()
      // otherwise not painted if frame already visible
      frame.validate()
      this.frameHelper = frameHelper
      isFrameBoundsCorrect = true
      return
    }

    val options = options
    var frameInfo = options.frame
    val bounds = frameInfo?.bounds
    var projectWorkspaceId = options.projectWorkspaceId
    if (bounds == null) {
      val info = (RecentProjectsManager.getInstance() as? RecentProjectsManagerBase)?.getProjectMetaInfo(projectStoreBaseDir)
      if (info != null) {
        projectWorkspaceId = info.projectWorkspaceId
        frameInfo = info.frame
      }
    }

    var projectSelfie: Image? = null
    if (options.projectWorkspaceId != null && Registry.`is`("ide.project.loading.show.last.state", false)) {
      try {
        projectSelfie = ProjectSelfieUtil.readProjectSelfie(projectWorkspaceId!!, ScaleContext.create(frame))
      }
      catch (e: Throwable) {
        if (e.cause !is EOFException) {
          logger<ProjectFrameAllocator>().warn(e)
        }
      }
    }

    val frameHelper = ProjectFrameHelper(frame, projectSelfie)

    // must be after preInit (frame decorator is required to set full screen mode)
    if (frameInfo?.bounds == null) {
      isFrameBoundsCorrect = false
      (WindowManager.getInstance() as WindowManagerImpl).defaultFrameInfoHelper.info?.let {
        restoreFrameState(frameHelper, it)
      }
    }
    else {
      isFrameBoundsCorrect = true
      if (isFrameBoundsRestored) {
        if (frameInfo.fullScreen && FrameInfoHelper.isFullScreenSupportedInCurrentOs()) {
          frameHelper.toggleFullScreen(true)
        }
      }
      else {
        restoreFrameState(frameHelper, frameInfo)
      }
    }

    if (options.sendFrameBack && frame.isAutoRequestFocus) {
      logger<ProjectFrameAllocator>().error("isAutoRequestFocus must be false")
    }
    frame.isVisible = true
    frameHelper.init()
    this.frameHelper = frameHelper
  }

  private fun createFrameIfNeeded(): IdeFrameImpl {
    val freeRootFrame = (WindowManager.getInstance() as WindowManagerImpl).removeAndGetRootFrame()
    if (freeRootFrame != null) {
      isFrameBoundsCorrect = true
      frameHelper = freeRootFrame
      return freeRootFrame.frame!!
    }

    runMainActivity("create a frame") {
      val preAllocated = SplashManager.getAndUnsetProjectFrame() as IdeFrameImpl?
      if (preAllocated == null) {
        isFrameBoundsRestored = options.frame?.bounds != null
        return createNewProjectFrame(forceDisableAutoRequestFocus = options.sendFrameBack, frameInfo = options.frame)
      }
      else {
        isFrameBoundsRestored = true
        return preAllocated
      }
    }
  }

  override fun projectLoaded(project: Project) {
    ApplicationManager.getApplication().invokeLater(Runnable {
      val frameHelper = frameHelper ?: return@Runnable

      val windowManager = WindowManager.getInstance() as WindowManagerImpl
      runActivity("project frame assigning") {
        val projectFrameBounds = ProjectFrameBounds.getInstance(project)
        if (isFrameBoundsCorrect) {
          // update to ensure that project stores correct frame bounds
          val frame = frameHelper.frame!!
          projectFrameBounds.markDirty(if (FrameInfoHelper.isMaximized(frame.extendedState)) null else frame.bounds)
        }
        else {
          val frameInfo = projectFrameBounds.getFrameInfoInDeviceSpace()
          if (frameInfo?.bounds != null) {
            restoreFrameState(frameHelper, frameInfo)
          }
        }

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
      val frame = frameHelper
      frameHelper = null

      if (error != null) {
        ProjectManagerImpl.showCannotConvertMessage(error, frame?.frame)
      }

      if (frame != null) {
        Disposer.dispose(frame)
      }
    }
  }

  override fun projectOpened(project: Project) {
    if (options.sendFrameBack) {
      frameHelper?.frame?.isAutoRequestFocus = true
    }
  }
}

private fun restoreFrameState(frameHelper: ProjectFrameHelper, frameInfo: FrameInfo) {
  val deviceBounds = frameInfo.bounds
  val bounds = if (deviceBounds == null) null else FrameBoundsConverter.convertFromDeviceSpaceAndFitToScreen(deviceBounds)
  val state = frameInfo.extendedState
  val isMaximized = FrameInfoHelper.isMaximized(state)
  val frame = frameHelper.frame!!
  if (bounds != null && isMaximized && frame.extendedState == Frame.NORMAL) {
    frame.rootPane.putClientProperty(IdeFrameImpl.NORMAL_STATE_BOUNDS, bounds)
  }
  if (bounds != null) {
    frame.bounds = bounds
  }
  frame.extendedState = state

  if (frameInfo.fullScreen && FrameInfoHelper.isFullScreenSupportedInCurrentOs()) {
    frameHelper.toggleFullScreen(true)
  }
}

@ApiStatus.Internal
fun createNewProjectFrame(forceDisableAutoRequestFocus: Boolean, frameInfo: FrameInfo?): IdeFrameImpl {
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
