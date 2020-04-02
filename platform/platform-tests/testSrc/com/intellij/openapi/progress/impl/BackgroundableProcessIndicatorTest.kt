// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.testFramework.FileEditorManagerTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.SkipInHeadlessEnvironment
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.*
import kotlin.coroutines.resume

@SkipInHeadlessEnvironment
class BackgroundableProcessIndicatorTest : FileEditorManagerTestCase() {
  private val TIMEOUT_MS = 30_000L

  fun `test can start off EDT, still running when adding progress`(): Unit = runBlocking {
    withTimeout(TIMEOUT_MS) {
      val frameHelper = createFrameHelper()
      val statusBar = frameHelper.statusBar as IdeStatusBarImpl
      val semaphore = Semaphore(1)
      val deferred = CompletableDeferred<Pair<Task.Backgroundable, BackgroundableProcessIndicator>>()

      launch(Dispatchers.Default) {
        suspendCancellableCoroutine<Unit> { cont ->
          assertIsNotDispatchThread()
          val task = object : TestTask(project) {
            override fun run(indicator: ProgressIndicator) {
              semaphore.waitFor()
              cont.resume(Unit)
            }
          }
          val indicator = BackgroundableProcessIndicator(task.project, task, task, statusBar)
          cont.invokeOnCancellation { indicator.cancel() }

          deferred.complete(Pair(task, indicator))
          ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator)
        }
      }

      try {
        val (task, indicator) = deferred.await()
        assertEmpty("Unexpected background processes before processing EDT event queue", statusBar.backgroundProcesses)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() // tries to add progress to statusbar on EDT
        assertContainsOrdered(statusBar.backgroundProcesses, Pair(task, indicator))
      }
      finally {
        semaphore.up()
      }
    }
  }

  fun `test can create off EDT, disposed after adding progress to statusbar`(): Unit = runBlocking {
    withTimeout(TIMEOUT_MS) {
      val frameHelper = createFrameHelper()
      val statusBar = frameHelper.statusBar as IdeStatusBarImpl

      val (task, indicator) =
        withContext(Dispatchers.Default) {
          assertIsNotDispatchThread()
          val task = TestTask(project)
          val indicator = BackgroundableProcessIndicator(task.project, task, task, statusBar)
          Pair(task, indicator)
        }

      assertEmpty("Unexpected background processes before processing EDT event queue", statusBar.backgroundProcesses)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() // tries to add progress to statusbar on EDT
      Disposer.dispose(indicator)
      assertContainsOrdered(statusBar.backgroundProcesses, Pair(task, indicator))
    }
  }

  fun `test can create off EDT, already disposed when adding progress to statusbar`(): Unit = runBlocking {
    withTimeout(TIMEOUT_MS) {
      val frameHelper = createFrameHelper()
      val statusBar = frameHelper.statusBar as IdeStatusBarImpl

      val indicator =
        withContext(Dispatchers.Default) {
          assertIsNotDispatchThread()
          val task = TestTask(project)
          BackgroundableProcessIndicator(task.project, task, task, statusBar)
        }

      assertEmpty("Unexpected background processes before processing EDT event queue", statusBar.backgroundProcesses)
      Disposer.dispose(indicator)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() // tries to add progress to statusbar on EDT
      assertEmpty("Unexpected background processes after processing EDT event queue", statusBar.backgroundProcesses)
    }
  }

  private fun assertIsNotDispatchThread() {
    assertFalse("should not be running on dispatch thread", ApplicationManager.getApplication().isDispatchThread)
  }

  private fun createFrameHelper(): ProjectFrameHelper = ProjectFrameHelper(IdeFrameImpl(), null).also {
    Disposer.register(testRootDisposable, it)
    it.init()
  }

  private open class TestTask(project: Project) : Task.Backgroundable(project, "Test Task") {
    override fun run(indicator: ProgressIndicator): Unit = Unit
  }
}