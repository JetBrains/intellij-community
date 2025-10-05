// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.CommonBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.index.VcsLogBigRepositoriesList
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex
import com.intellij.vcs.log.util.VcsLogUtil

internal class VcsLogStatusBarProgress(project: Project, logProviders: Map<VirtualFile, VcsLogProvider>, private val roots: Set<VirtualFile>,
                                       vcsLogProgress: VcsLogProgress) : Disposable {
  private val disposableFlag = Disposer.newCheckedDisposable()
  private val vcsName = VcsLogUtil.getVcsDisplayName(project, roots.mapNotNull { logProviders[it] })
  private val statusBar: StatusBarEx by lazy {
    (WindowManager.getInstance() as WindowManagerEx).findFrameFor(project)!!.statusBar as StatusBarEx
  }
  private val alarm = lazy { Alarm(Alarm.ThreadToUse.SWING_THREAD, this) }
  private var progress: MyProgressIndicator? = null

  init {
    vcsLogProgress.addProgressIndicatorListener(MyProgressListener(), this)
    Disposer.register(this, disposableFlag)
  }

  @RequiresEdt
  fun start() {
    alarm.value.addRequest(Runnable {
      if (progress == null) {
        progress = MyProgressIndicator().also { p ->
          p.start()
          statusBar.addProgress(p, p.taskInfo)
        }
      }
    }, Registry.intValue("vcs.log.index.progress.delay.millis"))
  }

  @RequiresEdt
  fun stop() {
    if (alarm.isInitialized()) alarm.value.cancelAllRequests()
    progress?.let { p ->
      p.stop()
      p.finish(p.taskInfo)
    }
    progress = null
  }

  override fun dispose() {
    stop()
  }

  inner class MyProgressListener : VcsLogProgress.ProgressListener {
    override fun progressChanged(keys: Collection<VcsLogProgress.ProgressKey>) {
      if (disposableFlag.isDisposed) return
      if (keys.contains(VcsLogPersistentIndex.INDEXING)) {
        start()
      }
      else {
        stop()
      }
    }
  }

  inner class MyProgressIndicator : ProgressIndicatorBase() {
    internal val taskInfo = MyTaskInfo()

    init {
      setOwnerTask(taskInfo)
      dontStartActivity()

      addStateDelegate(object : AbstractProgressIndicatorExBase() {
        override fun cancel() {
          val bigRepositoriesList = VcsLogBigRepositoriesList.getInstance()
          roots.forEach { bigRepositoriesList.addRepository(it) }
          text2 = VcsLogBundle.message("vcs.log.status.bar.indexing.cancel.cancelling")
          LOG.info("Indexing for ${roots.map { it.presentableUrl }} was cancelled from the status bar.")
        }
      })
    }
  }

  internal inner class MyTaskInfo : TaskInfo {
    override fun getTitle(): String = VcsLogBundle.message("vcs.log.status.bar.indexing", vcsName.capitalize())

    override fun getCancelText(): String = CommonBundle.getCancelButtonText()

    override fun getCancelTooltipText(): String = VcsLogBundle.message("vcs.log.status.bar.indexing.cancel.tooltip", vcsName.capitalize())

    override fun isCancellable(): Boolean = true

    override fun getStatusBarIndicatorWeight(): Int = 1500
  }

  companion object {
    private val LOG = Logger.getInstance(VcsLogStatusBarProgress::class.java)
  }
}