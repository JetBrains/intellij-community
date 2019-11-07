// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform

import com.intellij.conversion.CannotConvertException
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.runActivity
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.idea.SplashManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.*
import com.intellij.ui.ScreenUtil
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAwt
import java.awt.Dimension
import java.awt.Frame
import java.awt.Image
import java.io.EOFException
import java.nio.file.Path

internal open class ProjectFrameAllocator {
  open fun run(task: Runnable): Boolean {
    task.run()
    return true
  }

  /**
   * Project is loaded and is initialized, project services and components can be accessed.
   */
  open fun projectLoaded(project: Project) {}

  open fun projectNotLoaded(error: CannotConvertException?) {
    if (error != null) {
      ProjectManagerImpl.showCannotConvertMessage(error, null)
    }
  }

  open fun projectOpened(project: Project) {}
}

internal class ProjectUiFrameAllocator(private var options: OpenProjectTask, private val projectFile: Path) : ProjectFrameAllocator() {
  // volatile not required because created in run (before executing run task)
  private var frameHelper: ProjectFrameHelper? = null

  private var isFrameBoundsCorrect = false

  @Volatile
  private var cancelled = false

  override fun run(task: Runnable): Boolean {
    var completed = false
    TransactionGuard.getInstance().submitTransactionAndWait {
      val frame = createFrameIfNeeded()
      completed = ProgressManager.getInstance().runProcessWithProgressSynchronously({
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

        task.run()
      }, "Loading ${projectFile.fileName} Project", true, null, frame.rootPane)
    }
    return completed
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
        val info = recentProjectManager.getProjectMetaInfo(projectFile)
        if (info != null) {
          options = options.copy()
          options.frame = info.frame
          options.projectWorkspaceId = info.projectWorkspaceId
        }
      }
    }

    var projectSelfie: Image? = null
    if (options.projectWorkspaceId != null && Registry.`is`("ide.project.loading.show.last.state")) {
      try {
        projectSelfie = ProjectSelfieUtil.readProjectSelfie(options.projectWorkspaceId!!, ScaleContext.create(frame))
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
      frameInfo = (WindowManager.getInstance() as WindowManagerImpl).defaultFrameInfo
    }
    else {
      isFrameBoundsCorrect = true
    }

    if (frameInfo != null) {
      restoreFrameState(frameHelper, frameInfo)
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

    runActivity("create a frame", ActivityCategory.MAIN) {
      var frame = SplashManager.getAndUnsetProjectFrame() as IdeFrameImpl?
      if (frame == null) {
        frame = createNewProjectFrame()
        if (options.sendFrameBack) {
          frame.isAutoRequestFocus = false
        }
      }
      return frame
    }
  }

  override fun projectLoaded(project: Project) {
    ApplicationManager.getApplication().invokeLater(Runnable {
      val frame = frameHelper ?: return@Runnable

      val projectFrameBounds = ProjectFrameBounds.getInstance(project)
      if (isFrameBoundsCorrect) {
        // update to ensure that project stores correct frame bounds
        projectFrameBounds.markDirty(if (FrameInfoHelper.isMaximized(frame.frame.extendedState)) null else frame.frame.bounds)
      }
      else {
        val frameInfo = projectFrameBounds.getFrameInfoInDeviceSpace()
        if (frameInfo?.bounds != null) {
          restoreFrameState(frame, frameInfo)
        }
      }

      (WindowManager.getInstance() as WindowManagerImpl).assignFrame(frame, project)
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

      frame?.frame?.dispose()
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
fun createNewProjectFrame(): IdeFrameImpl {
  val frame = IdeFrameImpl()
  SplashManager.hideBeforeShow(frame)

  val size = ScreenUtil.getMainScreenBounds().size
  size.width = Math.min(1400, size.width - 20)
  size.height = Math.min(1000, size.height - 40)
  frame.size = size
  frame.setLocationRelativeTo(null)

  if (UIUtil.DISABLE_AUTO_REQUEST_FOCUS &&
      !ApplicationManager.getApplication().isActive) {
    frame.isAutoRequestFocus = false
  }
  frame.minimumSize = Dimension(340, frame.minimumSize.height)
  return frame
}