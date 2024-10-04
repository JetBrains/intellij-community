// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl

import com.intellij.diagnostic.CoroutineTracerShim
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.function.Predicate
import javax.swing.SwingUtilities
import kotlin.coroutines.coroutineContext

private val LOG = logger<VcsInitialization>()
private val EP_NAME = ExtensionPointName<VcsStartupActivity>("com.intellij.vcsStartupActivity")

/**
 * An ordered pipeline for initialization of VCS-related services. Typically, it should not be needed by plugins.
 *
 * @see ProjectLevelVcsManager.runAfterInitialization
 * @see ProjectActivity
 */
@Suppress("DeprecatedCallableAddReplaceWith")
interface VcsStartupActivity {
  /**
   * @see VcsInitObject.getOrder
   */
  val order: Int

  @Deprecated("Implement execute")
  fun runActivity(project: Project) {
    throw AbstractMethodError()
  }

  suspend fun execute(project: Project) {
    @Suppress("DEPRECATION")
    runActivity(project)
  }
}

@ApiStatus.Internal
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
  private var status = Status.PENDING
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
    fun getInstance(project: Project): VcsInitialization = project.service()
  }

  private fun startInitializationJob(job: Job) {
    future = job
    job.start()
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
      if (isInitActivity(activity)) {
        if (status == Status.PENDING) {
          initActivities.add(activity)
          return true
        }
        else {
          LOG.warn("scheduling late initialization: $activity")
          return false
        }
      }
      else {
        if (status == Status.PENDING || status == Status.RUNNING_INIT) {
          postActivities.add(activity)
          return true
        }
        else {
          LOG.debug { "scheduling late post activity: $activity" }
          return false
        }
      }
    }
  }

  internal suspend fun execute() {
    LOG.assertTrue(!project.isDefault)
    try {
      runInitStep(current = Status.PENDING,
                  next = Status.RUNNING_INIT,
                  extensionFilter = { isInitActivity(it) },
                  pendingActivities = initActivities)
      runInitStep(current = Status.RUNNING_INIT,
                  next = Status.RUNNING_POST,
                  extensionFilter = { !isInitActivity(it) },
                  pendingActivities = postActivities)
    }
    finally {
      // Dispatchers.IO due to using of synchronized
      withContext(NonCancellable + Dispatchers.IO) {
        synchronized(lock) { status = Status.FINISHED }
      }
    }
  }

  private suspend fun runInitStep(current: Status,
                                  next: Status,
                                  extensionFilter: Predicate<VcsStartupActivity>,
                                  pendingActivities: MutableList<VcsStartupActivity>) {
    val activities = ArrayList<VcsStartupActivity>()
    val unfilteredActivities = EP_NAME.extensionList
    coroutineContext.ensureActive()
    synchronized(lock) {
      assert(status == current)
      status = next
      for (activity in unfilteredActivities) {
        if (extensionFilter.test(activity)) {
          activities.add(activity)
        }
      }
      activities.addAll(pendingActivities)
      pendingActivities.clear()
    }

    activities.sortWith(Comparator.comparingInt { it.order })
    runActivities(activities)
  }

  private suspend fun runActivities(activities: List<VcsStartupActivity>) {
    CoroutineTracerShim.coroutineTracer.span("VcsInitialization.runActivities") {
      for (activity in activities) {
        try {
          CoroutineTracerShim.coroutineTracer.span(activity.javaClass.name) {
            LOG.debug { "running activity: $activity" }
            activity.execute(project)
          }
        }
        catch (e: CancellationException) {
          // do not abort initialization if one of the callbacks threw PCE
          coroutineContext.ensureActive()
          LOG.warn(IllegalStateException(e))
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }
    }
  }

  private fun cancelBackgroundInitialization() {
    val future = future ?: return
    future.cancel()

    // do not leave VCS initialization run in the background when the project is closed
    LOG.debug {
      "cancelBackgroundInitialization() future=${future} from ${Thread.currentThread()}" +
      " with write access=${ApplicationManager.getApplication().isWriteAccessAllowed}"
    }
    if (ApplicationManager.getApplication().isWriteAccessAllowed) {
      // dispose happens without a prior project close (a most likely light project case in tests)
      // get out of write action and wait there
      SwingUtilities.invokeLater { waitNotRunning() }
    }
    else {
      waitNotRunning()
    }
  }

  private fun waitNotRunning() {
    val success = waitFor { it == Status.PENDING || it == Status.FINISHED }
    if (!success) {
      LOG.warn("Failed to wait for VCS initialization cancellation for project $project", Throwable())
    }
  }

  @TestOnly
  fun waitFinished() {
    val success = waitFor { it == Status.FINISHED }
    if (!success) {
      LOG.error("Failed to wait for VCS initialization completion for project $project", Throwable())
    }
  }

  private fun waitFor(predicate: (Status) -> Boolean): Boolean {
    require(!project.isDefault)
    // have to wait for task completion to avoid running it in the background for a closed project
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() < start + 10000) {
      synchronized(lock) {
        if (predicate(status)) {
          return true
        }
      }
      TimeoutUtil.sleep(10)
    }
    return false
  }

  internal class StartUpActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
      val vcsInitialization = project.serviceAsync<VcsInitialization>()
      coroutineScope {
        val task = launch(context = CoroutineName("VcsInitialization"), start = CoroutineStart.LAZY) {
          vcsInitialization.execute()
        }
        vcsInitialization.startInitializationJob(task)
      }
    }
  }

  private class ProxyVcsStartupActivity(vcsInitObject: VcsInitObject, private val runnable: Runnable) : VcsStartupActivity {
    override val order = vcsInitObject.order

    override suspend fun execute(project: Project) {
      runnable.run()
    }

    override fun toString(): String = "ProxyVcsStartupActivity{runnable=${runnable}, order=${order}}" //NON-NLS
  }
}

private fun isInitActivity(activity: VcsStartupActivity): Boolean {
  return activity.order < VcsInitObject.AFTER_COMMON.order
}