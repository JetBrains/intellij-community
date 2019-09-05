// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex
import org.jetbrains.annotations.CalledInAwt

class VcsLogStatusBarProgress(project: Project, vcsLogProgress: VcsLogProgress) : Disposable {
  private val statusBar: StatusBarEx by lazy {
    (WindowManager.getInstance() as WindowManagerEx).findFrameFor(project)!!.statusBar as StatusBarEx
  }
  private var progress: MyProgressIndicator? = null

  init {
    vcsLogProgress.addProgressIndicatorListener(MyProgressListener(), this)
  }

  @CalledInAwt
  fun start() {
    if (progress == null) {
      progress = MyProgressIndicator().also { statusBar.addProgress(it, it.taskInfo) }
    }
  }

  @CalledInAwt
  fun stop() {
    progress?.let { it.finish(it.taskInfo) }
    progress = null
  }

  override fun dispose() {
    stop()
  }

  inner class MyProgressListener : VcsLogProgress.ProgressListener {
    override fun progressStarted(keys: MutableCollection<out VcsLogProgress.ProgressKey>) {
      if (keys.contains(VcsLogPersistentIndex.INDEXING)) {
        start()
      }
    }

    override fun progressStopped() {
      stop()
    }

    override fun progressChanged(keys: MutableCollection<out VcsLogProgress.ProgressKey>) {
      if (keys.contains(VcsLogPersistentIndex.INDEXING)) {
        start()
      }
      else {
        stop()
      }
    }
  }

  class MyProgressIndicator : AbstractProgressIndicatorExBase() {
    internal val taskInfo = MyTaskInfo()

    init {
      setOwnerTask(taskInfo)
      dontStartActivity()
    }
  }

  class MyTaskInfo : TaskInfo {
    override fun getTitle(): String = "Vcs log indexing..."

    override fun getCancelText(): String = ""

    override fun getCancelTooltipText(): String = ""

    override fun isCancellable(): Boolean = false
  }
}