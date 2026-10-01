// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.concurrency.JobLauncher
import com.intellij.concurrency.JobSchedulerImpl
import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.configureInspections
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.replaceService
import com.intellij.util.Processor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
class MainPassesRunnerTest {
  private companion object {
    val project = projectFixture(openAfterCreation = true)
    val module = project.moduleFixture()
    val sourceRoot = module.sourceRootFixture()
    val firstFile = sourceRoot.psiFileFixture("First.txt", "first")
    val secondFile = sourceRoot.psiFileFixture("Second.txt", "second")
  }

  /**
   * A surviving inspection must release its read lock before [MainPassesRunner] retries with a write action.
   */
  @Test
  fun `retry write action completes while a service response is pending`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    assumeTrue(JobSchedulerImpl.getJobPoolParallelism() > 1)
    val files = listOf(firstFile.get().virtualFile, secondFile.get().virtualFile)
    val runnerThread = AtomicReference<Thread>()
    val requestStarted = CompletableDeferred<Job>()
    val response = CompletableDeferred<Unit>()
    val requestFinished = CompletableDeferred<Unit>()
    val requestClaimed = AtomicBoolean()
    val siblingCancelled = AtomicBoolean()
    val writeRequested = CompletableDeferred<Unit>()
    val writeFinished = CompletableDeferred<Unit>()
    val batchTaskIndicator = CompletableDeferred<ProgressIndicator>()
    val firstAttempt = AtomicBoolean()
    val launcher = JobLauncher.getInstance()
    ApplicationManager.getApplication().replaceService(JobLauncher::class.java, object : JobLauncher() {
      override fun <T> processConcurrentlyAsync(things: List<T>, thingProcessor: Processor<in T>, runnable: Runnable): Boolean {
        val coordinateFailure = Thread.currentThread() === runnerThread.get() && firstAttempt.compareAndSet(false, true)
        return launcher.processConcurrentlyAsync(things, Processor { item ->
          if (coordinateFailure) {
            batchTaskIndicator.complete(ProgressManager.getGlobalProgressIndicator()!!)
          }
          thingProcessor.process(item)
        }, Runnable {
          runnable.run()
          if (coordinateFailure) {
            runBlockingCancellable {
              withTimeout(5.seconds) {
                val indicator = batchTaskIndicator.await()
                while (!indicator.isCanceled) {
                  delay(1.milliseconds)
                }
              }
            }
          }
        })
      }
    }, disposable)
    val inspection = object : LocalInspectionTool() {
      override fun getShortName(): String = "WaitingService"
      override fun getDisplayName(): String = "Waiting service"
      override fun getGroupDisplayName(): String = "Test"

      override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitFile(file: PsiFile) {
          if (Thread.currentThread() !== runnerThread.get() && requestClaimed.compareAndSet(false, true)) {
            assertTrue(ApplicationManager.getApplication().isReadAccessAllowed)
            try {
              runBlockingCancellable {
                requestStarted.complete(coroutineContext.job)
                response.await()
              }
            }
            finally {
              requestFinished.complete(Unit)
            }
          }
          else {
            runBlockingCancellable { requestStarted.await() }
            if (siblingCancelled.compareAndSet(false, true)) {
              throw ProcessCanceledException()
            }
          }
        }
      }
    }
    val profile = withContext(Dispatchers.EDT) {
      configureInspections(arrayOf(inspection), project.get(), disposable)
    }
    readAction {
      ApplicationManager.getApplication().addApplicationListener(object : ApplicationListener {
        override fun beforeWriteActionStart(action: Any) {
          if (siblingCancelled.get()) {
            writeRequested.complete(Unit)
          }
        }

        override fun writeActionFinished(action: Any) {
          if (writeRequested.isCompleted) {
            writeFinished.complete(Unit)
          }
        }
      }, disposable)
    }
    val progress = ProgressIndicatorBase()
    val runner = async(Dispatchers.IO) {
      runnerThread.set(Thread.currentThread())
      try {
        ProgressManager.getInstance().runProcess(Computable {
          MainPassesRunner(project.get(), "Check code", profile).runMainPasses(files)
        }, progress)
      }
      catch (e: ProcessCanceledException) {
        if (!progress.isCanceled) throw e
        null
      }
    }
    try {
      requestStarted.await()
      writeRequested.await()
      if (withTimeoutOrNull(2.seconds) { writeFinished.await() } == null) {
        fail("The retry write action is blocked by an inspection waiting for a service response.\n" +
             ThreadDumper.dumpThreadsToString())
      }
      assertNotNull(withTimeoutOrNull(2.seconds) { requestFinished.await() },
                    "The inspection must stop without receiving the service response.")
      assertTrue(requestStarted.await().isCancelled)
      assertEquals(2, assertNotNull(runner.await()).size)
      assertFalse(progress.isCanceled)
    }
    finally {
      progress.cancel()
      response.complete(Unit)
      withContext(NonCancellable) {
        withTimeout(5.seconds) {
          runner.join()
          if (requestStarted.isCompleted) requestFinished.await()
        }
      }
    }
  }
}
