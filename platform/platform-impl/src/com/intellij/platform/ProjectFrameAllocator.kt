// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform

import com.intellij.conversion.CannotConvertException
import com.intellij.diagnostic.runActivity
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.*
import com.intellij.ui.scale.ScaleContext
import org.jetbrains.annotations.CalledInAwt
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
  private var ideFrame: ProjectFrameHelper? = null
  private var isNewFrame = false

  override fun run(task: Runnable): Boolean {
    var completed = false
    TransactionGuard.getInstance().submitTransactionAndWait {
      ideFrame = createFrame()
      completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        {
          if (isNewFrame) {
            ApplicationManager.getApplication().invokeLater {
              ideFrame?.let { frame ->
                runActivity("init frame") {
                  initNewFrame(frame)
                }
              }
            }
          }
          task.run()
        }, "Loading Project...", true, null, ideFrame!!.component)
    }
    return completed
  }

  @CalledInAwt
  private fun initNewFrame(frame: ProjectFrameHelper) {
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
        projectSelfie = ProjectSelfieUtil.readProjectSelfie(options.projectWorkspaceId!!, ScaleContext.create(frame.frame))
      }
      catch (e: Throwable) {
        if (e.cause !is EOFException) {
          logger<ProjectFrameAllocator>().warn(e)
        }
      }
    }

    frame.preInit(projectSelfie)

    // must be after preInit (frame decorator is required to set full screen mode)
    var frameInfo = options.frame
    if (frameInfo?.bounds == null) {
      frameInfo = (WindowManager.getInstance() as WindowManagerImpl).defaultFrameInfo
    }
    if (frameInfo != null) {
      restoreFrameState(frame, frameInfo)
    }

    frame.frame.isVisible = true
    frame.init()
  }

  private fun createFrame(): ProjectFrameHelper {
    val windowManager = WindowManager.getInstance() as WindowManagerImpl
    @Suppress("UsePropertyAccessSyntax")
    val freeRootFrame = windowManager.getAndRemoveRootFrame()
    if (freeRootFrame != null) {
      return freeRootFrame
    }

    runActivity("create a frame") {
      isNewFrame = true
      val frame = windowManager.createFrame()
      if (options.sendFrameBack) {
        frame.frame.isAutoRequestFocus = false
      }
      return frame
    }
  }

  override fun projectLoaded(project: Project) {
    ApplicationManager.getApplication().invokeLater(Runnable {
      val frame = ideFrame ?: return@Runnable

      if (isNewFrame && options.frame?.bounds == null) {
        val frameInfo = ProjectFrameBounds.getInstance(project).getFrameInfoInDeviceSpace()
        if (frameInfo?.bounds != null) {
          restoreFrameState(frame, frameInfo)
        }
      }

      (WindowManager.getInstance() as WindowManagerImpl).assignFrame(frame, project)
    }, project.disposed)
  }

  override fun projectNotLoaded(error: CannotConvertException?) {
    ApplicationManager.getApplication().invokeLater {
      val frame = ideFrame
      ideFrame = null

      if (error != null) {
        ProjectManagerImpl.showCannotConvertMessage(error, frame?.frame)
      }

      frame?.frame?.dispose()
    }
  }

  override fun projectOpened(project: Project) {
    if (options.sendFrameBack) {
      ideFrame?.frame?.isAutoRequestFocus = true
    }

  }
}

private fun restoreFrameState(frame: ProjectFrameHelper, frameInfo: FrameInfo) {
  val deviceBounds = frameInfo.bounds
  val bounds = if (deviceBounds == null) null else WindowManagerImpl.convertFromDeviceSpaceAndValidateFrameBounds(deviceBounds)
  frame.setExtendedState(frameInfo, bounds)
  if (bounds != null) {
    frame.frame.bounds = bounds
  }

  if (frameInfo.fullScreen && FrameInfoHelper.isFullScreenSupportedInCurrentOs()) {
    frame.toggleFullScreen(true)
  }
}