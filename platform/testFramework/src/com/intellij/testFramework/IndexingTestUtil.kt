// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.UnindexedFilesScannerExecutor
import com.intellij.openapi.util.Disposer
import com.intellij.util.indexing.UnindexedFilesScannerExecutorImpl
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class IndexingTestUtil private constructor() {
  companion object { // companion object for keeping API compatibility
    const val DEFAULT_TIMEOUT_MS: Long = 600_000

    @JvmStatic
    @JvmOverloads
    fun waitUntilIndexesAreReadyInAllOpenedProjects(indexWaitingTimeoutMs: Long = DEFAULT_TIMEOUT_MS) {
      for (project in ProjectManager.getInstance().openProjects) {
        IndexWaiter(project).waitUntilFinished(indexWaitingTimeoutMs.milliseconds)
      }
    }

    @JvmStatic
    @JvmOverloads
    fun waitUntilIndexesAreReady(project: Project, indexWaitingTimeoutMs: Long = DEFAULT_TIMEOUT_MS) {
      IndexWaiter(project).waitUntilFinished(indexWaitingTimeoutMs.milliseconds)
    }

    suspend fun suspendUntilIndexesAreReady(project: Project, indexWaitingTimeout: Duration = DEFAULT_TIMEOUT_MS.milliseconds) {
      IndexWaiter(project).suspendUntilIndexesAreReady(indexWaitingTimeout)
    }
  }
}

private class IndexWaiter(private val project: Project) {
  private fun waitAfterWriteAction(indexWaitingTimeout: Duration) {
    if (project.isDisposed) return

    val listenerDisposable = Disposer.newDisposable()
    val parentDisposable = UnindexedFilesScannerExecutor.getInstance(project) as Disposable
    Disposer.register(parentDisposable, listenerDisposable)

    ApplicationManager.getApplication().addApplicationListener(object : ApplicationListener {

      // Volatile: any thread could be a writer thread
      @Volatile
      private var nested: Int = 1 // 1 because at least one write action is currently happenings

      override fun beforeWriteActionStart(action: Any) {
        nested++
      }

      // invoked after all the write actions are finished (write lock is released)
      override fun afterWriteActionFinished(action: Any) {
        nested--
        assert(nested >= 0) { "We counted more finished write actions than started." }
        if (nested <= 0) { // may not be negative, but let's stay on the safe side
          Disposer.dispose(listenerDisposable)
          waitNow(indexWaitingTimeout)
        }
      }
    }, listenerDisposable)
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun waitNow(indexWaitingTimeout: Duration) {
    thisLogger().debug("waitNow, thread=${Thread.currentThread()}")
    Assert.assertFalse("Should not be invoked from write action", ApplicationManager.getApplication().isWriteAccessAllowed)

    if (!shouldWait()) {
      return // TODO: CodeInsightTestFixtureImpl.configureInner via GroovyHighlightUsagesTest
    }
    else {
      thisLogger().debug("waitNow will be waiting, thread=${Thread.currentThread()}")
    }

    if (ApplicationManager.getApplication().isDispatchThread) {
      do {
        PlatformTestUtil.waitWithEventsDispatching("Indexing timeout", { !shouldWait() }, 600)
      }
      while (dispatchAllEventsInIdeEventQueue()) // make sure that all the scheduled write actions are executed
    }
    else {
      runBlockingMaybeCancellable {
        suspendUntilIndexesAreReady(indexWaitingTimeout)
      }
    }
  }

  private fun dispatchAllEventsInIdeEventQueue(): Boolean {
    var hasDispatchedEvents = false
    while (PlatformTestUtil.dispatchNextEventIfAny() != null) {
      hasDispatchedEvents = true
    }
    return hasDispatchedEvents
  }

  private fun shouldWait(): Boolean {
    val dumbService = DumbServiceImpl.getInstance(project)

    dumbService.ensureInitialDumbTaskRequiredForSmartModeSubmitted() // TODO IJPL-578: don't submit

    val scannerExecutor = UnindexedFilesScannerExecutorImpl.getInstance(project)

    // Scheduled tasks will become running tasks soon. To avoid a race, we check scheduled tasks first
    if (scannerExecutor.hasQueuedTasks) {
      return if (scannerExecutor.scanningWaitsForNonDumbMode() && dumbService.isDumb) {
        val isEternal = DumbModeTestUtils.isEternalDumbTaskRunning(project)
        if (isEternal) {
          thisLogger().debug("Do not wait for queued scanning task, because eternal dumb task is running in the project [$project]")
        }
        !isEternal
      }
      else {
        true // wait for queued scanning tasks to complete
      }
    }
    // Scheduled tasks will become running tasks soon. To avoid a race, we check scheduled tasks first
    else if (dumbService.hasScheduledTasks()) {
      return true
    }
    else if (scannerExecutor.isRunning.value || dumbService.isRunning()) {
      return true
    }
    else if (dumbService.isDumb) {
      // DUMB_FULL_INDEX should wait until all the scheduled tasks are finished but should not wait for smart mode
      val isEternal = DumbModeTestUtils.isEternalDumbTaskRunning(project)
      if (isEternal) {
        thisLogger().debug("Do not wait for smart mode, because eternal dumb task is running in the project [$project]")
      }
      return !isEternal
    }
    else {
      return false
    }
  }

  suspend fun suspendUntilIndexesAreReady(timeout: Duration) {
    if (shouldWait()) {
      thisLogger().debug("suspendUntilIndexesAreReady will be waiting, thread=${Thread.currentThread()}")
    }

    try {
      withTimeout(timeout) {
        while (shouldWait()) {
          delay(1)
        }
      }
    }
    catch (e: TimeoutCancellationException) {
      thisLogger().warn(ThreadDumper.dumpThreadsToString(), e)
      throw e
    }
  }

  fun waitUntilFinished(indexWaitingTimeout: Duration) {
    thisLogger().debug("waitUntilFinished, thread=${Thread.currentThread()}, WA=${ApplicationManager.getApplication().isWriteAccessAllowed}")
    if (ApplicationManager.getApplication().isWriteAccessAllowed) {
      waitAfterWriteAction(indexWaitingTimeout)
    }
    else {
      waitNow(indexWaitingTimeout)
    }
  }
}
