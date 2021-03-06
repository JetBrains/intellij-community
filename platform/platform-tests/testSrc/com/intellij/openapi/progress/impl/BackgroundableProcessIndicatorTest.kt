// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressWindowTestCase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.use
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.testFramework.SkipInHeadlessEnvironment
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@SkipInHeadlessEnvironment
class BackgroundableProcessIndicatorTest : ProgressWindowTestCase<Pair<Task.Backgroundable, BackgroundableProcessIndicator>>() {
  private lateinit var statusBar: IdeStatusBarImpl

  override fun setUp(): Unit = super.setUp().also {
    val frameHelper = ProjectFrameHelper(IdeFrameImpl(), null)
    Disposer.register(testRootDisposable, frameHelper)
    frameHelper.init()
    statusBar = frameHelper.statusBar as IdeStatusBarImpl
  }

  override fun Pair<Task.Backgroundable, BackgroundableProcessIndicator>.use(block: () -> Unit) {
    second.use { block() }
  }

  override fun createProcess(): Pair<Task.Backgroundable, BackgroundableProcessIndicator> {
    val task = TestTask(project)
    val indicator = BackgroundableProcessIndicator(task.project, task, task, statusBar)
    return Pair(task, indicator)
  }

  override suspend fun runProcessOnEdt(process: Pair<Task.Backgroundable, BackgroundableProcessIndicator>, block: () -> Unit) {
    ProgressManager.getInstance().runProcess(block, process.second)
  }

  override suspend fun createAndRunProcessOffEdt(deferredProcess: CompletableDeferred<Pair<Task.Backgroundable, BackgroundableProcessIndicator>>, mayComplete: Semaphore) {
    suspendCancellableCoroutine<Unit> { cont ->
      val task = object : TestTask(project) {
        override fun run(indicator: ProgressIndicator) {
          mayComplete.waitFor(TIMEOUT_MS)
          cont.resume(Unit)
        }
      }
      val indicator = BackgroundableProcessIndicator(task.project, task, task, statusBar)
      cont.invokeOnCancellation { indicator.cancel() }

      deferredProcess.complete(Pair(task, indicator))
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator)
    }
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

  private open class TestTask(project: Project) : Task.Backgroundable(project, "Test Task") {
    override fun run(indicator: ProgressIndicator): Unit = Unit
  }
}