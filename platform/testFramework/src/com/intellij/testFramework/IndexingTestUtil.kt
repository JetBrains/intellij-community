// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.UnindexedFilesScannerExecutor
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.namedChildScope
import kotlinx.coroutines.*
import org.junit.Assert
import kotlin.time.Duration.Companion.seconds

class IndexingTestUtil(private val project: Project) {

  private fun waitAfterWriteAction() {
    if (project.isDisposed) return

    val listenerDisposable = Disposer.newDisposable()
    val parentDisposable = UnindexedFilesScannerExecutor.getInstance(project)
    Disposer.register(parentDisposable, listenerDisposable)

    ApplicationManager.getApplication().addApplicationListener(object : ApplicationListener {
      /*
        ... DEADLOCK: there are two afterWriteActionFinished, but first one is not delivered yet :DEADLOCK ...
        at com.intellij.testFramework.IndexingTestUtil$waitAfterWriteAction$1.afterWriteActionFinished(IndexingTestUtil.kt:30)
        ...
        at jdk.proxy2.$Proxy45.afterWriteActionFinished(jdk.proxy2/Unknown Source)
        at com.intellij.openapi.application.impl.ApplicationImpl.fireAfterWriteActionFinished(ApplicationImpl.java:1015)
        at com.intellij.openapi.application.impl.ApplicationImpl.afterWriteActionFinished(ApplicationImpl.java:1170)
        ...
        at com.intellij.openapi.application.impl.ApplicationImpl.runWriteAction(ApplicationImpl.java:857)
        at com.intellij.openapi.application.WriteAction.run(WriteAction.java:84)
        at com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl.lambda$reloadPsi$18(PushedFilePropertiesUpdaterImpl.java:454)
        ...
        at com.intellij.openapi.project.MergingTaskQueue$QueuedTask.executeTask(MergingTaskQueue.java:329)
        at com.intellij.openapi.project.DumbServiceSyncTaskQueue.doRunTaskSynchronously(DumbServiceSyncTaskQueue.java:86)
        ...
        at com.intellij.openapi.project.DumbServiceSyncTaskQueue.processQueue(DumbServiceSyncTaskQueue.java:71)
        at com.intellij.openapi.project.DumbServiceSyncTaskQueue$1.afterWriteActionFinished(DumbServiceSyncTaskQueue.java:100)
        ...
        at jdk.proxy2.$Proxy45.afterWriteActionFinished(jdk.proxy2/Unknown Source)
        ...
        at com.intellij.openapi.application.impl.ApplicationImpl.fireAfterWriteActionFinished(ApplicationImpl.java:1015)
        ...
        at com.intellij.openapi.application.WriteAction.compute(WriteAction.java:95)
        ...
       */
      // non-volatile: only updated on writing thread
      private var nested: Int = 1 // 1 because at least one write action is currently happenings

      override fun beforeWriteActionStart(action: Any) {
        nested++
      }

      // invoked after all the write actions are finished (write lock is released)
      override fun afterWriteActionFinished(action: Any) {
        nested--
        assert(nested >= 0) { "We counted more finished write actions than started." }
        if (nested <= 0) { // may not be negative, but let's stay on safe side
          Disposer.dispose(listenerDisposable);
          waitNow();
        }
      }
    }, listenerDisposable);
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun waitNow() {
    thisLogger().debug("waitNow, thread=${Thread.currentThread()}")
    Assert.assertFalse("Should not be invoked from write action", ApplicationManager.getApplication().isWriteAccessAllowed)

    if (!isRunning()) return // TODO: CodeInsightTestFixtureImpl.configureInner via GroovyHighlightUsagesTest

    if (ApplicationManager.getApplication().isWriteIntentLockAcquired) {
      val scope = GlobalScope.namedChildScope("Indexing waiter", Dispatchers.IO)
      val waiting = scope.launch { suspendUntilIndexesAreReady() }
      try {
        PlatformTestUtil.waitWithEventsDispatching("Indexing timeout", { !waiting.isActive }, 600)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue() // make sure that all the scheduled write actions are executed
      }
      finally {
        waiting.cancel()
      }
    }
    else {
      runBlockingMaybeCancellable {
        withTimeout(600.seconds) {
          UnindexedFilesScannerExecutor.getInstance(project).waitUntilFinished()
          DumbServiceImpl.getInstance(project).waitUntilFinished()
        }
      }
    }
  }

  private fun isRunning(): Boolean {
    return UnindexedFilesScannerExecutor.getInstance(project).isRunning.value || DumbServiceImpl.getInstance(project).isRunning
  }

  suspend fun suspendUntilIndexesAreReady() {
    UnindexedFilesScannerExecutor.getInstance(project).waitUntilFinished()
    DumbServiceImpl.getInstance(project).waitUntilFinished()
  }

  fun waitUntilFinished() {
    thisLogger().debug(Throwable("waitUntilFinished, thread=${Thread.currentThread()}, " +
                                 "WA=${ApplicationManager.getApplication().isWriteAccessAllowed}"))
    if (ApplicationManager.getApplication().isWriteAccessAllowed) {
      waitAfterWriteAction()
    }
    else {
      waitNow()
    }
  }

  companion object {
    @JvmStatic
    fun waitUntilIndexesAreReadyInAllOpenedProjects() {
      // FileBasedIndexTumbler currently does not work in tests, because it cannot wait for DumbServiceSyncTaskQueue.
      // This method should probably be removed after DumbServiceSyncTaskQueue is dropped
      for (project in ProjectManager.getInstance().openProjects) {
        IndexingTestUtil(project).waitUntilFinished()
      }
    }

    @JvmStatic
    fun waitUntilIndexesAreReady(project: Project) {
      IndexingTestUtil(project).waitUntilFinished()
    }

    suspend fun suspendUntilIndexesAreReady(project: Project) {
      IndexingTestUtil(project).suspendUntilIndexesAreReady()
    }
  }
}