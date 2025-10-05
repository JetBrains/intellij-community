// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingNotificationService
import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContextScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import com.intellij.testFramework.replaceService
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration
import kotlin.test.assertContains

@TestApplication
@SystemProperty("intellij.async.formatting.ignoreHeadless", "true")
@SystemProperty("intellij.progress.task.ignoreHeadless", "true")
class AsyncDocumentFormattingBlockingContextScopeTest {
  private val project = projectFixture()
  private val psiFile by project.moduleFixture().sourceRootFixture().psiFileFixture("foo", "before")
  private val notificationService by project.replacedServiceFixture(FormattingNotificationService::class.java) { RecordingFormattingNotificationService() }
  private val formattingService by extensionPointFixture(FormattingService.EP_NAME) { FooAsyncDocumentFormattingService() }

  private fun CoroutineScope.launchAsyncFormatting(
    formattingService: FooAsyncDocumentFormattingService,
    // all but the last task are expected to be canceled by the subsequent tasks
    tasks: List<TestTask>,
    waitForIntermediateTaskStart: Boolean,
    psiFile: PsiFile,
  ): Job {
    var next = 0
    formattingService.taskFactory = { tasks[next++] }
    val job = launch(Dispatchers.IO) {
      blockingContextScope {
        repeat(tasks.size) {
          invokeLater {
            runWriteAction {
              formattingService.testFormatDocument(psiFile)
            }
          }
          if (it != tasks.lastIndex && waitForIntermediateTaskStart) {
            // mustn't wait on EDT, FormattingProgressTask.queue() needs EDT to start
            timeoutRunBlocking { tasks[it].taskStarted.acquire() }
            tasks[it].taskStarted.release()
          }
        }
      }
    }
    return job
  }

  @ParameterizedTest
  @CsvSource(
    "false, MANAGER, false",
    "false, MANAGER, true",
    "false, SEMAPHORE, false",
    "false, SEMAPHORE, true",
    // it is expected to hang, since it only checks for an indicator that does not exist in the blocking task
    // "false, INDICATOR, false",
    "false, INDICATOR, true",

    "true,, false",
    "true,, true",
  )
  fun `async formatting task under blockingContextScope is cancelable`(
    nonBlocking: Boolean,
    cancellationType: CancellationCheckType?,
    runUnderProgress: Boolean,
  ) = timeoutRunBlocking {
    val tasks = List(3) { TestTask.make(nonBlocking, cancellationType, runUnderProgress) }
    val job = launchAsyncFormatting(formattingService, tasks, true, psiFile)
    // should not time out the test
    tasks.last().taskStarted.acquire()
    assertTrue(tasks.dropLast(1).all { it.taskCancelled.hasNoPermits }) { "Running task should be cancelled by subsequent formatting" }
    assertTrue(tasks.last().taskCancelled.hasPermits)
    // should not time out the test
    job.cancelAndJoin()
    assertTrue(tasks.last().taskCancelled.hasNoPermits)
    assertTrue(notificationService.errorsReported.isEmpty())
    assertEquals("before", psiFile.fileDocument.text)
  }

  @ParameterizedTest
  @CsvSource(
    "false, MANAGER, false",
    "false, MANAGER, true",
    "false, SEMAPHORE, false",
    "false, SEMAPHORE, true",
    // it is expected to hang, since it only checks for an indicator that does not exist in the blocking task
    // "false, INDICATOR, false",
    "false, INDICATOR, true",

    "true,, false",
    "true,, true",
  )
  fun `async formatting task succeeds`(
    nonBlocking: Boolean,
    cancellationType: CancellationCheckType?,
    runUnderProgress: Boolean,
  ) = timeoutRunBlocking {
    val tasks = List(15) { TestTask.make(nonBlocking, cancellationType, runUnderProgress) }

    val job = launchAsyncFormatting(formattingService, tasks, true, psiFile)
    // should not time out the test
    tasks.last().taskStarted.acquire()
    assertTrue(tasks.dropLast(1).all { it.taskCancelled.hasNoPermits }) { "Running task should be cancelled by subsequent formatting" }
    assertTrue(tasks.last().taskIsWorking.tryAcquire()) { "Task should be working" }
    assertTrue(tasks.last().taskCancelled.hasPermits) { "Task should not be canceled" }
    // should not time out the test
    job.join()
    assertTrue(tasks.last().taskCancelled.hasPermits) { "Task was not canceled on a successful run" }
    assertEquals("after", psiFile.fileDocument.text)
    assertTrue(notificationService.errorsReported.isEmpty())
  }

  @OptIn(DelicateCoroutinesApi::class)
  @ParameterizedTest
  @CsvSource(
    // blocking formatting tasks cannot currently be expired as AsyncDocumentFormattingService waits in the same thread
    "true,, false",
    "true,, true"
  )
  fun `async formatting task expires`(
    nonBlocking: Boolean,
    cancellationType: CancellationCheckType?,
    runUnderProgress: Boolean,
  ) = timeoutRunBlocking {
    val formattingTimeout = 100L
    formattingService.timeoutMillis = formattingTimeout

    val tasks = List(3) { TestTask.make(nonBlocking, cancellationType, runUnderProgress) }

    val job = launchAsyncFormatting(formattingService, tasks, true, psiFile)
    // should not time out the test
    tasks.last().taskStarted.acquire()
    assertTrue(tasks.dropLast(1).all { it.taskCancelled.hasNoPermits }) { "Running task should be cancelled by subsequent formatting" }
    assertTrue(tasks.last().taskIsWorking.hasPermits) { "Formatting task should be working" }

    withTimeout(3 * formattingTimeout) {
      job.join()
    }

    assertTrue(tasks.last().taskCancelled.hasNoPermits) { "Formatting task should be canceled when expired" }
    assertEquals(1, notificationService.errorsReported.size)
    assertContains(notificationService.errorsReported.first(), "does not respond")
    assertEquals("before", psiFile.fileDocument.text)
  }

  @ParameterizedTest
  @CsvSource(
    "false, MANAGER, false",
    "false, MANAGER, true",
    "false, SEMAPHORE, false",
    "false, SEMAPHORE, true",
    // it is expected to hang, since it only checks for an indicator that does not exist in the blocking task
    // "false, INDICATOR, false",
    "false, INDICATOR, true",

    "true,, false",
    "true,, true",
  )
  fun `async formatting task cancels scheduled tasks`(
    nonBlocking: Boolean,
    cancellationType: CancellationCheckType?,
    runUnderProgress: Boolean,
  ) = timeoutRunBlocking {
    repeat(100) {
      thisLogger().warn("repeat: $it")
      val tasks = List(10) { TestTask.make(nonBlocking, cancellationType, runUnderProgress) }
      val job = launchAsyncFormatting(formattingService, tasks, false, psiFile)
      // should not time out the test
      tasks.last().taskStarted.acquire()
      assertTrue(tasks.last().taskCancelled.hasPermits)
      tasks.dropLast(1).forEach {
        assertTrue(it.taskCancelled.hasNoPermits || it.taskStarted.hasNoPermits) {
          "Obsolete task should have either been canceled or not started"
        }
      }
      job.cancelAndJoin()
      assertTrue(tasks.last().taskCancelled.hasNoPermits)
      assertTrue(notificationService.errorsReported.isEmpty())
      assertEquals("before", psiFile.fileDocument.text)
    }
  }

  // TODO test that we do not start a new computation if a previous one could not be canceled
  @ParameterizedTest
  @CsvSource(
    "false, MANAGER, false",
    "false, MANAGER, true",
    "false, SEMAPHORE, false",
    "false, SEMAPHORE, true",
    // it is expected to hang, since it only checks for an indicator that does not exist in the blocking task
    // "false, INDICATOR, false",
    "false, INDICATOR, true",

    "true,, false",
    "true,, true",
  )
  fun `async formatting is not started if previous request could not be canceled`(
    nonBlocking: Boolean,
    cancellationType: CancellationCheckType?,
    runUnderProgress: Boolean,
  ) = timeoutRunBlocking {

    val tasks = List(10) {
      TestTask.make(nonBlocking, cancellationType, runUnderProgress)
    }
  }


}

private class FooAsyncDocumentFormattingService(var timeoutMillis: Long = DEFAULT_TEST_TIMEOUT.inWholeMilliseconds) : AsyncDocumentFormattingService() {
  @Volatile
  lateinit var taskFactory: () -> TestTask

  fun testFormatDocument(psiFile: PsiFile) {
    formatDocument(
      psiFile.fileDocument,
      emptyList(),
      FormattingContext.create(psiFile, psiFile.textRange, CodeStyle.getSettings(psiFile), FormattingMode.REFORMAT),
      false,
      false
    )
  }

  override fun createFormattingTask(formattingRequest: AsyncFormattingRequest): FormattingTask {
    val task = taskFactory()
    return object : FormattingTask {
      override fun run() {
        task.run(formattingRequest)
      }

      override fun cancel(): Boolean = task.cancel()

      override fun isRunUnderProgress(): Boolean = task.isRunUnderProgress
    }
  }
  override fun getNotificationGroupId(): String = "Foo"
  override fun getName(): @NlsSafe String = "Foo"
  override fun getFeatures(): Set<FormattingService.Feature> = emptySet()
  override fun canFormat(file: PsiFile): Boolean = true

  override fun getTimeout(): Duration = Duration.ofMillis(timeoutMillis)
}

private abstract class TestTask(val cancellationType: CancellationCheckType, val runUnderProgress: Boolean) {
  // hasNoPermits => task was canceled
  val taskCancelled = Semaphore(1, 0)

  // hasPermits => task has started
  val taskStarted = Semaphore(1, 1)

  // hasPermits => task is working
  val taskIsWorking = Semaphore(1, 0)

  protected fun testCheckCanceled(): Boolean {
    when (cancellationType) {
      CancellationCheckType.MANAGER -> ProgressManager.checkCanceled()
      CancellationCheckType.SEMAPHORE -> if (taskCancelled.hasNoPermits) return true
      CancellationCheckType.INDICATOR -> {
        val indicator = ProgressManager.getInstance().progressIndicator
        indicator?.checkCanceled()
      }
    }
    return false
  }

  protected fun doRun(formattingRequest: AsyncFormattingRequest) {
    taskStarted.assertRelease { "`run` should not be called multiple times" }
    // simulate work
    while (true) {
      if (testCheckCanceled()) break
      if (taskIsWorking.hasNoPermits) break
      Thread.sleep(10)
    }
    if (!testCheckCanceled()) {
      formattingRequest.onTextReady("after")
    }
  }

  abstract fun run(formattingRequest: AsyncFormattingRequest)

  val isRunUnderProgress: Boolean
    get() = runUnderProgress

  fun cancel(): Boolean {
    check(taskCancelled.tryAcquire())
    return true
  }

  companion object {
    fun make(isNonBlocking: Boolean, cancellationType: CancellationCheckType?, runUnderProgress: Boolean): TestTask =
      if (isNonBlocking) NonBlocking(runUnderProgress)
      else Blocking(checkNotNull(cancellationType) { "cancellationType cannot be null for blocking task" }, runUnderProgress)
  }

  class Blocking(cancellationType: CancellationCheckType, runUnderProgress: Boolean) : TestTask(cancellationType, runUnderProgress) {
    override fun run(formattingRequest: AsyncFormattingRequest) {
      doRun(formattingRequest)
    }
  }


  class NonBlocking(isRunUnderProgress: Boolean) :
    //       since the job is detached, the other cancellation checks won't work
    TestTask(CancellationCheckType.SEMAPHORE,
             isRunUnderProgress) {
    @Volatile
    var job: Job? = null

    @OptIn(DelicateCoroutinesApi::class)
    override fun run(formattingRequest: AsyncFormattingRequest) {
      // simulate something like ProcessHandler, which does not block the task and does not get tied to the context job
      job = GlobalScope.launch {
        doRun(formattingRequest)
      }
    }
  }
}

private class RecordingFormattingNotificationService : FormattingNotificationService {
  val errorsReported = ContainerUtil.createConcurrentList<String>()

  override fun reportError(groupId: String, displayId: String?, title: @NlsContexts.NotificationTitle String, message: @NlsContexts.NotificationContent String, vararg actions: AnAction?) {
    errorsReported.add(message)
  }

  override fun reportErrorAndNavigate(groupId: String, displayId: String?, title: @NlsContexts.NotificationTitle String, message: @NlsContexts.NotificationContent String, context: FormattingContext, offset: Int) {
    errorsReported.add(message)
  }
}

private val Semaphore.hasNoPermits: Boolean
  get() = availablePermits == 0
private val Semaphore.hasPermits: Boolean
  get() = !hasNoPermits

private fun Semaphore.assertRelease(message: () -> String = { "Semaphore is not acquired" }) {
  try {
    release()
  }
  catch (_: IllegalStateException) {
    fail(message)
  }
}

@TestOnly
fun <T : Any> TestFixture<Project>.replacedServiceFixture(serviceInterface: Class<in T>, createService: suspend () -> T): TestFixture<T> = testFixture {
  val service = createService()
  val disposable = Disposer.newDisposable()
  this@replacedServiceFixture.init().replaceService(serviceInterface, service, disposable)
  initialized(service) {
    Disposer.dispose(disposable)
  }
}

enum class CancellationCheckType {
  MANAGER,
  SEMAPHORE,
  INDICATOR
}