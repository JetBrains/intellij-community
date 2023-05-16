// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl

import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.QueueProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.function.Predicate
import javax.swing.SwingUtilities
import kotlin.coroutines.coroutineContext

private val LOG = Logger.getInstance(VcsInitialization::class.java)
private val EP_NAME = ExtensionPointName<VcsStartupActivity>("com.intellij.vcsStartupActivity")

@Service(Service.Level.PROJECT)
class VcsInitialization(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val lock = Any()

  private enum class Status {
    PENDING,
    RUNNING_INIT,
    RUNNING_POST,
    FINISHED
  }

  // guarded by myLock
  private var myStatus = Status.PENDING
  private val initActivities = ArrayList<VcsStartupActivity>()
  private val postActivities = ArrayList<VcsStartupActivity>()

  @Volatile
  private var future: Job? = null

  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      // Fix "MessageBusImpl is already disposed: (disposed temporarily)" during LightPlatformTestCase
      val disposable = (project as ProjectEx).earlyDisposable
      Disposer.register(disposable) { cancelBackgroundInitialization() }
    }
  }

  companion object {
    fun getInstance(project: Project): VcsInitialization {
      return project.getService(VcsInitialization::class.java)
    }
  }

  private fun startInitialization() {
    future = coroutineScope.launch {
      @Suppress("DialogTitleCapitalization")
      withBackgroundProgress(project, VcsBundle.message("impl.vcs.initialization")) {
        execute()
      }
    }
  }

  fun add(vcsInitObject: VcsInitObject, runnable: Runnable) {
    if (project.isDefault) {
      LOG.warn("ignoring initialization activity for default project", Throwable())
      return
    }

    val wasScheduled = scheduleActivity(vcsInitObject, runnable)
    if (!wasScheduled) {
      coroutineScope.launch {
        blockingContext {
          runnable.run()
        }
      }
    }
  }

  private fun scheduleActivity(vcsInitObject: VcsInitObject, runnable: Runnable): Boolean {
    synchronized(lock) {
      val activity = ProxyVcsStartupActivity(vcsInitObject, runnable)
      return if (isInitActivity(activity)) {
        if (myStatus == Status.PENDING) {
          initActivities.add(activity)
          true
        }
        else {
          LOG.warn(String.format("scheduling late initialization: %s", activity))
          false
        }
      }
      else {
        if (myStatus == Status.PENDING || myStatus == Status.RUNNING_INIT) {
          postActivities.add(activity)
          true
        }
        else {
          if (LOG.isDebugEnabled) {
            LOG.debug(String.format("scheduling late post activity: %s", activity))
          }
          false
        }
      }
    }
  }

  private suspend fun execute() {
    LOG.assertTrue(!project.isDefault)
    try {
      runInitStep(Status.PENDING, Status.RUNNING_INIT, { it: VcsStartupActivity -> isInitActivity(it) }, initActivities)
      runInitStep(Status.RUNNING_INIT, Status.RUNNING_POST, { it: VcsStartupActivity -> !isInitActivity(it) }, postActivities)
    }
    finally {
      synchronized(lock) { myStatus = Status.FINISHED }
    }
  }

  private suspend fun runInitStep(current: Status,
                          next: Status,
                          extensionFilter: Predicate<in VcsStartupActivity>,
                          pendingActivities: MutableList<out VcsStartupActivity>) {
    val activities: MutableList<VcsStartupActivity> = ArrayList()
    val unfilteredActivities = EP_NAME.extensionList
    synchronized(lock) {
      assert(myStatus == current)
      myStatus = next
      for (activity in unfilteredActivities) {
        if (extensionFilter.test(activity)) {
          activities.add(activity)
        }
      }
      activities.addAll(pendingActivities)
      pendingActivities.clear()
    }
    runActivities(activities)
  }

  private suspend fun runActivities(activities: MutableList<VcsStartupActivity>) {
    val future = future
    if (future != null && future.isCancelled) {
      return
    }

    activities.sortWith(Comparator.comparingInt { it.order })
    for (activity in activities) {
      coroutineContext.ensureActive()
      if (LOG.isDebugEnabled) {
        LOG.debug(String.format("running activity: %s", activity))
      }
      val logActivity = StartUpMeasurer.startActivity("VcsInitialization (" + activity.javaClass.name + ")",
                                                      ActivityCategory.DEFAULT)
      QueueProcessor.runSafely { activity.runActivity(project) }
      logActivity.end()
    }
  }

  private fun cancelBackgroundInitialization() {
    val future = future ?: return
    future.cancel()

    // do not leave VCS initialization run in background when the project is closed
    LOG.debug {
      "cancelBackgroundInitialization() future=${future} from ${Thread.currentThread()}" +
      " with write access=${ApplicationManager.getApplication().isWriteAccessAllowed}"
    }
    future.cancel()
    if (ApplicationManager.getApplication().isWriteAccessAllowed) {
      // dispose happens without prior project close (most likely light project case in tests)
      // get out of write action and wait there
      SwingUtilities.invokeLater { waitNotRunning() }
    }
    else {
      waitNotRunning()
    }
  }

  private fun waitNotRunning() {
    val success = waitFor { status: Status -> status == Status.PENDING || status == Status.FINISHED }
    if (!success) {
      LOG.warn("Failed to wait for VCS initialization cancellation for project $project", Throwable())
    }
  }

  @TestOnly
  fun waitFinished() {
    val success = waitFor { status: Status -> status == Status.FINISHED }
    if (!success) {
      LOG.error("Failed to wait for VCS initialization completion for project $project", Throwable())
    }
  }

  private fun waitFor(predicate: Predicate<in Status>): Boolean {
    require(!project.isDefault)
    // have to wait for task completion to avoid running it in background for closed project
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() < start + 10000) {
      synchronized(lock) {
        if (predicate.test(myStatus)) {
          return true
        }
      }
      TimeoutUtil.sleep(10)
    }
    return false
  }

  internal class StartUpActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
      getInstance(project).startInitialization()
    }
  }

  internal class ShutDownProjectListener : ProjectCloseListener {
    override fun projectClosing(project: Project) {
      if (project.isDefault) {
        return
      }

      project.serviceIfCreated<VcsInitialization>()?.cancelBackgroundInitialization()
    }
  }

  private class ProxyVcsStartupActivity(vcsInitObject: VcsInitObject, private val runnable: Runnable) : VcsStartupActivity {
    private val order: Int = vcsInitObject.order

    override fun runActivity(project: Project) {
      runnable.run()
    }

    override fun getOrder(): Int = order

    override fun toString(): String = "ProxyVcsStartupActivity{runnable=${runnable}, order=${order}}" //NON-NLS
  }
}
private fun isInitActivity(activity: VcsStartupActivity): Boolean {
  return activity.order < VcsInitObject.AFTER_COMMON.order
}