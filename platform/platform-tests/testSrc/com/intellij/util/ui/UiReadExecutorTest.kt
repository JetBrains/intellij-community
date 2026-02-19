// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.CoroutineSupport
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.ui
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.ComponentUtil.forceMarkAsShowing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
class UiReadExecutorTest {

  @BeforeEach
  fun cleanEDTQueue() {
    UIUtil.pump()
  }

  @Test
  fun `doesn't execute after disposable is disposed`() = edtTest {
    val disposable = Disposer.newDisposable()
    val label = JLabel().also { container.add(it) }
    val executor = UiReadExecutor.conflatedUiReadExecutor(label, disposable, "test-disposable")

    val run = AtomicBoolean(false)

    // Block read actions so executeWithReadAccess enqueues into the conflated flow
    val waCanFinish = Job(coroutineContext.job)
    val waStarted = Job(coroutineContext.job)
    launch(Dispatchers.Default) {
      backgroundWriteAction {
        waStarted.complete()
        waCanFinish.asCompletableFuture().join()
      }
    }
    waStarted.asCompletableFuture().join()

    // Enqueue an action to be executed later (after write finishes)
    executor.executeWithReadAccess(Runnable { run.set(true) })

    // Dispose the disposable before the write action is finished — this should cancel the collector
    Disposer.dispose(disposable)

    // Unblock write action; since the collector is cancelled, the runnable must never execute
    waCanFinish.complete()
    delay(10.milliseconds)
    assertEquals(false, run.get())

    // Also verify that additional submissions after disposal do not execute either
    val wa2 = Job(coroutineContext.job)
    launch(Dispatchers.Default) {
      backgroundWriteAction {
        wa2.asCompletableFuture().join()
      }
    }
    Thread.sleep(10)
    executor.executeWithReadAccess(Runnable { run.set(true) })
    wa2.complete()
    delay(10.milliseconds)
    assertEquals(false, run.get())
  }

  private val container = JPanel().also { forceMarkAsShowing(it, true) }

  @Test
  fun `executes immediately when read is available`(@TestDisposable disposable: Disposable) = edtTest {
    val label = JLabel().also { container.add(it) }
    val executor = UiReadExecutor.conflatedUiReadExecutor(label, disposable, "test-immediate")
    var counter = 0

    // Should run synchronously inside executeWithReadAccess when read is available
    executor.executeWithReadAccess(Runnable { counter++ })
    assertEquals(1, counter)
  }

  @Test
  fun `defers under write action and runs after it ends`(@TestDisposable disposable: Disposable) = edtTest {
    val label = JLabel().also { container.add(it) }
    forceMarkAsShowing(label, true)
    val executor = UiReadExecutor.conflatedUiReadExecutor(label, disposable, "test-deferred")
    var run = false

    val waCanFinish = Job(coroutineContext.job)

    launch(Dispatchers.Default) {
      backgroundWriteAction {
        waCanFinish.asCompletableFuture().join()
      }
    }
    Thread.sleep(10)

    // while write action holds, tryRunReadAction should fail and the action is enqueued (conflated flow)
    executor.executeWithReadAccess(Runnable { run = true })
    // still under write action, must not be executed yet
    assertEquals(false, run)

    // write action finished -> collector on EDT should run the action soon
    waCanFinish.complete()
    // give a tiny bit of time for coroutine scheduling on EDT
    delay(10.milliseconds)
    assertTrue(run)
  }

  @Test
  fun `conflates multiple actions while blocked by write action`(@TestDisposable disposable: Disposable) = edtTest {
    val label = JLabel().also { container.add(it) }
    val executor = UiReadExecutor.conflatedUiReadExecutor(label, disposable, "test-conflate")
    var executedWith = 0

    val waCanFinish = Job(coroutineContext.job)

    launch(Dispatchers.Default) {
      backgroundWriteAction {
        waCanFinish.asCompletableFuture().join()
      }
    }
    Thread.sleep(10)

    // enqueue several actions; only the latest should run after write finishes
    repeat(5) { i ->
      executor.executeWithReadAccess(Runnable {
        if (i < 4) {
          Assertions.fail<Nothing>()
        }
        executedWith = i + 1
      })
    }
    // nothing should execute under write action
    assertEquals(0, executedWith)

    waCanFinish.complete()
    // give a tiny bit of time for coroutine scheduling on EDT
    delay(10.milliseconds)
    assertEquals(5, executedWith)
  }

  private fun edtTest(block: suspend CoroutineScope.() -> Unit) {
    withForcedRespectIsShowingClientProperty {
      timeoutRunBlocking {
        withContext(Dispatchers.ui(CoroutineSupport.UiDispatcherKind.RELAX), block)
      }
    }
  }
}
