// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.testFramework.FileEditorManagerTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.SkipInHeadlessEnvironment
import kotlinx.coroutines.*
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

@SkipInHeadlessEnvironment
class BackgroundableProcessIndicatorTest : FileEditorManagerTestCase() {
  private val TIMEOUT_MS = 30_000L

  fun `test can start off EDT`(): Unit = runBlocking {
    withTimeout(TIMEOUT_MS) {
      val statusBar = frame().statusBar as StatusBarEx
      val barrier = CyclicBarrier(2)

      launch(Dispatchers.Default) {
        suspendCancellableCoroutine<Unit> { cont ->
          val task = testTask {
            cont.resume(Unit)
          }
          val indicator = BackgroundableProcessIndicator(task.project, task, task, statusBar)
          cont.invokeOnCancellation { indicator.cancel() }
          barrier.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
          ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator)
        }
      }

      barrier.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
  }

  private fun frame(): ProjectFrameHelper = ProjectFrameHelper(IdeFrameImpl(), null).also {
    Disposer.register(testRootDisposable, it)
    it.init()
  }

  private inline fun testTask(crossinline block: (ProgressIndicator) -> Unit = {}): Task.Backgroundable =
    object : Task.Backgroundable(project, "Test Title") {
      override fun run(indicator: ProgressIndicator) {
        block(indicator)
      }
    }
}