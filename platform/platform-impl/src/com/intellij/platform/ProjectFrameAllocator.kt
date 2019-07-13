// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.WindowManagerImpl
import java.nio.file.Path
import javax.swing.JComponent

internal open class ProjectFrameAllocator {
  open fun run(task: Runnable, file: Path): Boolean {
    task.run()
    return true
  }

  open fun projectCreated(project: Project) {
  }

  open fun projectOpened(project: Project) {
  }
}

internal class ProjectUiFrameAllocator(val options: OpenProjectTask) : ProjectFrameAllocator() {
  // volatile not required because created in run (before executing run task)
  private var frame: IdeFrameImpl? = null

  override fun run(task: Runnable, file: Path): Boolean {
    var progressCompleted = false
    val component = showFrame(file)
    TransactionGuard.getInstance().submitTransactionAndWait {
      progressCompleted = ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(task, "Loading Project...", true, null, component)
    }
    return progressCompleted
  }

  private fun showFrame(file: Path): JComponent {
    var options = options
    val windowManager = WindowManager.getInstance() as WindowManagerImpl
    val freeRootFrame = windowManager.rootFrame
    if (freeRootFrame != null) {
      return freeRootFrame.component
    }

    if (options.frame?.bounds == null) {
      val recentProjectManager = RecentProjectsManager.getInstance()
      if (recentProjectManager is RecentProjectsManagerBase) {
        val info = recentProjectManager.getProjectMetaInfo(file)
        if (info != null) {
          options = options.copy()
          options.projectWorkspaceId = info.projectWorkspaceId
          options.frame = info.frame
        }
      }
    }

    val showFrameActivity = StartUpMeasurer.start("show frame")
    val frame = windowManager.showFrame(options)
    this.frame = frame
    showFrameActivity.end()
    // runProcessWithProgressSynchronously still processes EDT events
    ApplicationManager.getApplication().invokeLater(
      {
        val activity = StartUpMeasurer.start("init frame")
        if (frame.isDisplayable) {
          frame.init()
        }
        activity.end()
      }, ModalityState.any())
    return frame.component
  }

  override fun projectCreated(project: Project) {
    ApplicationManager.getApplication().invokeLater(
      {
        if (!project.isDisposed) {
          (WindowManager.getInstance() as WindowManagerImpl).assignFrame(frame ?: return@invokeLater, project)
        }
      }, ModalityState.any())
  }

  override fun projectOpened(project: Project) {
    if (options.sendFrameBack) {
      frame?.isAutoRequestFocus = true
    }
  }
}