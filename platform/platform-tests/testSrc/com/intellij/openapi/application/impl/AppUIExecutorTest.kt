// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import kotlinx.coroutines.experimental.Runnable
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.yield
import java.util.concurrent.LinkedBlockingQueue

/**
 * @author eldar
 */
class AppUIExecutorTest : LightPlatformTestCase() {
  override fun runInDispatchThread() = false
  override fun invokeTestRunnable(runnable: Runnable) = runnable.run()

  fun `test coroutine onUiThread`() {
    val executor = AppUIExecutor.onUiThread(ModalityState.any())
    runBlocking(executor as AsyncExecution) {
      ApplicationManager.getApplication().assertIsDispatchThread()
    }
  }

  fun `test coroutine withExpirable`() {
    val queue = LinkedBlockingQueue<String>()
    val disposable = Disposable {
      queue.add("disposed")
    }.also { Disposer.register(testRootDisposable, it) }

    val executor = AppUIExecutor.onUiThread(ModalityState.any())
      .expireWith(disposable)

    queue.add("start")
    runBlocking(executor as AsyncExecution) {
      ApplicationManager.getApplication().assertIsDispatchThread()

      queue.add("coroutine start")
      Disposer.dispose(disposable)
      queue.add("coroutine before delay")
      try {
        yield()
      }
      catch (e: Exception) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        queue.add("coroutine delay thrown ${e}")
        throw e
      }
      queue.add("coroutine after delay")
    }
    queue.add("end")

    assertOrderedEquals(queue,
                        "start",
                        "coroutine start",
                        "disposed",
                        "coroutine before delay",
                        "coroutine delay thrown",
                        "end")
  }
}