// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressWindowTestCase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.util.use
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.SkipInHeadlessEnvironment
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.*
import org.junit.Assume.assumeFalse

@SkipInHeadlessEnvironment
class BackgroundableProcessIndicatorTest : ProgressWindowTestCase<Pair<Task.Backgroundable, BackgroundableProcessIndicator>>() {
  private lateinit var statusBar: IdeStatusBarImpl

  override fun setUp(): Unit = super.setUp().also {
    assumeFalse("Cannot run headless", ApplicationManager.getApplication().isHeadlessEnvironment)
    statusBar = IdeStatusBarImpl((project as ComponentManagerEx).getCoroutineScope(), { null }, false)
  }

  override fun Pair<Task.Backgroundable, BackgroundableProcessIndicator>.use(block: () -> Unit) {
    second.use { block() }
  }

  override fun createProcess(): Pair<Task.Backgroundable, BackgroundableProcessIndicator> {
    val task = TestTask(project)
    val indicator = BackgroundableProcessIndicator(task.project, task, statusBar)
    return Pair(task, indicator)
  }

  override suspend fun runProcessOnEdt(process: Pair<Task.Backgroundable, BackgroundableProcessIndicator>, block: () -> Unit) {
    ProgressManager.getInstance().runProcess(block, process.second)
  }

  override suspend fun createAndRunProcessOffEdt(deferredProcess: CompletableDeferred<Pair<Task.Backgroundable, BackgroundableProcessIndicator>>, mayComplete: Semaphore) {
    val (_, indicator) = createProcess().also { deferredProcess.complete(it) }
    ProgressManager.getInstance().runProcess({ mayComplete.waitFor(TIMEOUT_MS) }, indicator)
  }

  override fun showDialog(process: Pair<Task.Backgroundable, BackgroundableProcessIndicator>) {
    process.second.showDialog()
  }

  override fun assertUninitialized(process: Pair<Task.Backgroundable, BackgroundableProcessIndicator>) {
    assertEmpty("Unexpected background processes", statusBar.backgroundProcesses)
  }

  override fun assertInitialized(process: Pair<Task.Backgroundable, BackgroundableProcessIndicator>) {
    assertContainsOrdered(statusBar.backgroundProcesses, process)
  }

  fun `test can start off EDT, already finished when processing EventQueue`(): Unit = runBlocking {
    withTimeout(TIMEOUT_MS) {
      val mayComplete = Semaphore(1)
      val deferredProcess = CompletableDeferred<Pair<Task.Backgroundable, BackgroundableProcessIndicator>>()

      val job = launch(Dispatchers.Default) {
        assertIsNotDispatchThread()
        createAndRunProcessOffEdt(deferredProcess, mayComplete)
      }

      val process = try {
        deferredProcess.await().also { process ->
          assertUninitialized(process)
        }
      }
      finally {
        mayComplete.up()
      }
      job.join()
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() // tries to initialize on EDT
      assertUninitialized(process)
    }
  }

  private open class TestTask(project: Project) : Task.Backgroundable(project, "Test Task") {
    override fun run(indicator: ProgressIndicator): Unit = Unit
  }
}