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
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.ProjectFrameBounds
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.ui.scale.ScaleContext
import org.jetbrains.annotations.CalledInAwt
import java.awt.Image
import java.io.EOFException
import java.nio.file.Path

internal open class ProjectFrameAllocator {
  open fun run(task: Runnable, file: Path): Boolean {
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

internal class ProjectUiFrameAllocator(private var options: OpenProjectTask) : ProjectFrameAllocator() {
  // volatile not required because created in run (before executing run task)
  private var ideFrame: IdeFrameImpl? = null

  override fun run(task: Runnable, file: Path): Boolean {
    var completed = false
    TransactionGuard.getInstance().submitTransactionAndWait {
      val frame = createFrame(file)
      completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        {
          ApplicationManager.getApplication().invokeLater {
            runActivity("init frame") {
              initFrame(frame)
            }
          }
          task.run()
        }, "Loading Project...", true, null, frame.component)
    }
    return completed
  }

  @CalledInAwt
  private fun initFrame(frame: IdeFrameImpl) {
    val windowManager = WindowManager.getInstance() as WindowManagerImpl
    var frameInfo = options.frame
    if (frameInfo?.bounds == null) {
      frameInfo = windowManager.defaultFrameInfo
    }
    if (frameInfo != null) {
      windowManager.restoreFrameState(frame, frameInfo)
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

    frame.preInit(projectSelfie)
    frame.isVisible = true
    frame.init()
  }

  private fun createFrame(file: Path): IdeFrameImpl {
    val windowManager = WindowManager.getInstance() as WindowManagerImpl
    val freeRootFrame = windowManager.rootFrame
    if (freeRootFrame != null) {
      return freeRootFrame
    }

    if (options.frame?.bounds == null) {
      val recentProjectManager = RecentProjectsManager.getInstance()
      if (recentProjectManager is RecentProjectsManagerBase) {
        val info = recentProjectManager.getProjectMetaInfo(file)
        if (info != null) {
          options = options.copy()
          options.frame = info.frame
          options.projectWorkspaceId = info.projectWorkspaceId
        }
      }
    }

    val frame = runActivity("create a frame") {
      windowManager.createFrame(options)
    }
    ideFrame = frame
    return frame
  }

  override fun projectLoaded(project: Project) {
    ApplicationManager.getApplication().invokeLater {
      if (project.isDisposed) {
        return@invokeLater
      }

      val frame = ideFrame ?: return@invokeLater
      val windowManager = WindowManager.getInstance() as WindowManagerImpl

      if (options.frame?.bounds == null) {
        val frameInfo = ProjectFrameBounds.getInstance(project).getFrameInfoInDeviceSpace()
        if (frameInfo?.bounds != null) {
          windowManager.restoreFrameState(frame, frameInfo)
        }
      }

      windowManager.assignFrame(frame, project)
    }
  }

  override fun projectNotLoaded(error: CannotConvertException?) {
    ApplicationManager.getApplication().invokeLater {
      val frame = ideFrame
      ideFrame = null

      if (error != null) {
        ProjectManagerImpl.showCannotConvertMessage(error, frame)
      }

      frame?.dispose()
    }
  }

  override fun projectOpened(project: Project) {
    if (options.sendFrameBack) {
      ideFrame?.isAutoRequestFocus = true
    }

  }
}