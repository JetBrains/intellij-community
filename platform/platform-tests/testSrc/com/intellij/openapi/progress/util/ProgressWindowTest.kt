// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.use as disposable_use
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.CompletableDeferred

class ProgressWindowTest : ProgressWindowTestCase<ProgressWindowTest.TestProgressWindow>() {
  override fun TestProgressWindow.use(block: () -> Unit): Unit = disposable_use { block() }
  override fun createProcess(): TestProgressWindow = TestProgressWindow(project)
  override suspend fun runProcessOnEdt(process: TestProgressWindow, block: () -> Unit) {
    ProgressManager.getInstance().runProcess(block, process)
  }

  override suspend fun createAndRunProcessOffEdt(deferredProcess: CompletableDeferred<TestProgressWindow>, mayComplete: Semaphore) {
    val progressWindow = createProcess().also { deferredProcess.complete(it) }
    ProgressManager.getInstance().runProcess({ mayComplete.waitFor(TIMEOUT_MS) }, progressWindow)
  }

  override fun showDialog(process: TestProgressWindow) {
    process.showDialog()
  }

  override fun assertUninitialized(process: TestProgressWindow) {
    assertNull("Unexpected dialog instance", process.dialog)
  }

  override fun assertInitialized(process: TestProgressWindow) {
    assertNotNull("Dialog instance expected", process.dialog)
  }

  class TestProgressWindow internal constructor(project: Project) : ProgressWindow(true, project) {
    @Suppress("INACCESSIBLE_TYPE")
    internal val dialog: Any?
      get() = super.getDialog()

    public override fun showDialog(): Unit = super.showDialog()
  }
}