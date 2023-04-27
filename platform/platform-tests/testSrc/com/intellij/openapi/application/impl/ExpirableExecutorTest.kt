// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ExpirableExecutor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.*
import java.util.concurrent.Executor
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class ExpirableExecutorTest : LightPlatformTestCase() {
  fun `test coroutine on background thread`() = runBlocking {
    checkBackgroundCoroutine(AppExecutorUtil.getAppExecutorService())
    checkBackgroundCoroutine(AppExecutorUtil.createBoundedApplicationPoolExecutor("Bounded", 1))
  }

  private suspend fun checkBackgroundCoroutine(executor: Executor) {
    val appExecutor = ExpirableExecutor.on(executor)
    GlobalScope.async(appExecutor.coroutineDispatchingContext()) {
      ApplicationManager.getApplication().assertIsNonDispatchThread()
    }.join()
  }

  fun `test coroutine canceled once expired`() {
    val disposable = Disposable { }.also { Disposer.register(testRootDisposable, it) }
    val context = ExpirableExecutor.on(AppExecutorUtil.getAppExecutorService())
      .expireWith(disposable)
      .coroutineDispatchingContext()

    val startSignal = Semaphore(0)

    runBlocking {
      val job = launch(context + CoroutineName("blabla")) {
        startSignal.tryAcquire(10, TimeUnit.SECONDS)
        this.ensureActive()
      }
      Disposer.dispose(disposable)
      startSignal.release()
      job.join()
      assertTrue(job.isCompleted && job.isCancelled)
    }
  }
}
