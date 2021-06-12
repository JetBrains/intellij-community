// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util

import com.intellij.testFramework.FileEditorManagerTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.*
import org.junit.Assert
import java.awt.EventQueue

abstract class ProgressWindowTestCase<Process> : FileEditorManagerTestCase() { // necessary since setup may query ui
  protected val TIMEOUT_MS = 30_000L

  abstract fun Process.use(block: () -> Unit)
  abstract fun createProcess(): Process
  abstract suspend fun runProcessOnEdt(process: Process, block: () -> Unit)
  abstract suspend fun createAndRunProcessOffEdt(deferredProcess: CompletableDeferred<Process>, mayComplete: Semaphore)
  abstract fun showDialog(process: Process)

  abstract fun assertUninitialized(process: Process)
  abstract fun assertInitialized(process: Process)

  fun `test can start off EDT, still running when processing EventQueue`(): Unit = runBlocking {
    withTimeout(TIMEOUT_MS) {
      val mayComplete = Semaphore(1)
      val deferredProcess = CompletableDeferred<Process>()

      launch(Dispatchers.Default) {
        assertIsNotDispatchThread()
        createAndRunProcessOffEdt(deferredProcess, mayComplete)
      }

      try {
        val process = deferredProcess.await()
        assertUninitialized(process)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() // tries to initialize on EDT
        assertInitialized(process)
      }
      finally {
        mayComplete.up()
      }
    }
  }

  fun `test can create off EDT, disposed after processing EventQueue`(): Unit = runBlocking {
    withTimeout(TIMEOUT_MS) {
      val process = createProcessOffEdt()
      process.use {
        assertUninitialized(process)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() // tries to initialize on EDT
      } // dispose
      assertInitialized(process)
    }
  }

  fun `test can create off EDT, already disposed when processing EventQueue`(): Unit = runBlocking {
    withTimeout(TIMEOUT_MS) {
      val process = createProcessOffEdt()
      process.use {
        assertUninitialized(process)
      } // dispose
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() // tries to initialize on EDT
      assertUninitialized(process)
    }
  }

  fun `test will initialize on-demand on EDT`(): Unit = runBlocking {
    withTimeout(TIMEOUT_MS) {
      val process = createProcessOffEdt()
      assertUninitialized(process)
      runProcessOnEdt(process) {
        showDialog(process)
        assertInitialized(process)
      }
    }
  }

  private suspend fun createProcessOffEdt(): Process =
    withContext(Dispatchers.Default) {
      assertIsNotDispatchThread()
      createProcess()
    }

  private fun assertIsNotDispatchThread() {
    Assert.assertFalse("should not be running on dispatch thread", EventQueue.isDispatchThread())
  }
}