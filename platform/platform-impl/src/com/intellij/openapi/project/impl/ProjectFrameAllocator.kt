// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl

import com.intellij.conversion.CannotConvertException
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.runActivity
import com.intellij.diagnostic.runMainActivity
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.plugins.StartupAbortedException
import com.intellij.idea.SplashManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.*
import com.intellij.platform.ProjectSelfieUtil
import com.intellij.ui.ComponentUtil
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.ScreenUtil
import com.intellij.ui.scale.ScaleContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAwt
import java.awt.Dimension
import java.awt.Frame
import java.awt.Image
import java.io.EOFException
import java.nio.file.Path
import kotlin.math.min

internal open class ProjectFrameAllocator {
  open fun <T : Any> run(task: () -> T?): T? {
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

internal class ProjectUiFrameAllocator(private var options: OpenProjectTask, private val projectStoreBaseDir: Path) : ProjectFrameAllocator() {
  // volatile not required because created in run (before executing run task)
  private var frameHelper: ProjectFrameHelper? = null

  private var isFrameBoundsCorrect = false

  @Volatile
  private var cancelled = false

  override fun <T : Any> run(task: () -> T?): T? {
    var result: T? = null
    val progressTitle = IdeUICustomization.getInstance().projectMessage("progress.title.project.loading.name", options.projectName ?: projectStoreBaseDir.fileName.toString())
    ApplicationManager.getApplication().invokeAndWait {
      val frame = createFrameIfNeeded()
      val progressTask = object : Task.Modal(null, progressTitle, true) {
        override fun run(indicator: ProgressIndicator) {
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

          result = task()
        }

        override fun onThrowable(error: Throwable) {
          if (error is StartupAbortedException || error is PluginException) {
            StartupAbortedException.logAndExit(error)
          }
          else {
            logger<ProjectFrameAllocator>().error(error)
            projectNotLoaded(error as? CannotConvertException)
          }
        }
      }

      // VfsUtil.markDirtyAndRefresh wants write-safe context
      // but no API to start runProcessWithProgressSynchronously in a write-safe context for now
      if (!(ProgressManager.getInstance() as CoreProgressManager).runProcessWithProgressSynchronously(progressTask, frame.rootPane)) {
        result = null
      }
    }
    return result
  }

  @CalledInAwt
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

    if (options.frame?.bounds == null) {
      val recentProjectManager = RecentProjectsManager.getInstance()
      if (recentProjectManager is RecentProjectsManagerBase) {
        val info = recentProjectManager.getProjectMetaInfo(projectStoreBaseDir)
        if (info != null) {
          options = options.copy(frame = info.frame, projectWorkspaceId = info.projectWorkspaceId)
        }
      }
    }

    var projectSelfie: Image? = null
    if (options.projectWorkspaceId != null && Registry.`is`("ide.project.loading.show.last.state")) {
      try {
        projectSelfie = ProjectSelfieUtil.readProjectSelfie(options.projectWorkspaceId!!,
                                                            ScaleContext.create(frame))
      }
      catch (e: Throwable) {
        if (e.cause !is EOFException) {
          logger<ProjectFrameAllocator>().warn(e)
        }
      }
    }

    val frameHelper = ProjectFrameHelper(frame, projectSelfie)

    // must be after preInit (frame decorator is required to set full screen mode)
    var frameInfo = options.frame
    if (frameInfo?.bounds == null) {
      isFrameBoundsCorrect = false
      frameInfo = (WindowManager.getInstance() as WindowManagerImpl).defaultFrameInfoHelper.info
    }
    else {
      isFrameBoundsCorrect = true
    }

    if (frameInfo != null) {
      restoreFrameState(frameHelper, frameInfo)
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
      return freeRootFrame.frame
    }

    runMainActivity("create a frame") {
      return SplashManager.getAndUnsetProjectFrame() as IdeFrameImpl?
             ?: createNewProjectFrame(
               forceDisableAutoRequestFocus = options.sendFrameBack)
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
          projectFrameBounds.markDirty(if (FrameInfoHelper.isMaximized(frameHelper.frame.extendedState)) null else frameHelper.frame.bounds)
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
  val frame = frameHelper.frame
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
fun createNewProjectFrame(forceDisableAutoRequestFocus: Boolean): IdeFrameImpl {
  val frame = IdeFrameImpl()
  SplashManager.hideBeforeShow(frame)

  val size = ScreenUtil.getMainScreenBounds().size
  size.width = min(1400, size.width - 20)
  size.height = min(1000, size.height - 40)
  frame.size = size
  frame.setLocationRelativeTo(null)

  if (forceDisableAutoRequestFocus || (!ApplicationManager.getApplication().isActive && ComponentUtil.isDisableAutoRequestFocus())) {
    frame.isAutoRequestFocus = false
  }
  frame.minimumSize = Dimension(340, frame.minimumSize.height)
  return frame
}