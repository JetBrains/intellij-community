// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingNotificationService
import com.intellij.formatting.service.FormattingService
import com.intellij.internal.DebugAttachDetector
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
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
import kotlinx.coroutines.selects.select
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junitpioneer.jupiter.params.DisableIfDisplayName
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertContains

@TestApplication
@SystemProperty("intellij.async.formatting.ignoreHeadless", "true")
@SystemProperty("intellij.progress.task.ignoreHeadless", "true")
class AsyncDocumentFormattingBlockingContextScopeTest {
  companion object {
    private fun doCreateTestParams(asyncOnly: Boolean): List<TestTask.Params> {
      val syncModeValues = if (asyncOnly) listOf(false) else BOOLEANS
      return cartesianParams(
        isBlocking = listOf(true),
        CancellationCheckType.entries,
        runUnderProgress = BOOLEANS,
        syncMode = syncModeValues
      ) -
      // these are expectedly non-cancellable, since they only check for an indicator that does not exist in the blocking task
      cartesianParams(
        isBlocking = listOf(true),
        cancellationCheck = listOf(CancellationCheckType.INDICATOR),
        runUnderProgress = listOf(false),
        syncMode = syncModeValues
      ).toSet() +
      cartesianParams(
        isBlocking = listOf(false),
        listOf(CancellationCheckType.SEMAPHORE),
        runUnderProgress = BOOLEANS,
        syncMode = syncModeValues
      )
    }

    @JvmStatic
    fun createTestParams(): List<TestTask.Params> = doCreateTestParams(asyncOnly = false)
    @JvmStatic
    fun createAsyncTestParams(): List<TestTask.Params> = doCreateTestParams(asyncOnly = true)

    private val project = projectFixture()
  }
  private val psiFile by project.moduleFixture().sourceRootFixture().psiFileFixture("foo", "before")
  private val notificationService by project.replacedServiceFixture(FormattingNotificationService::class.java) {
    RecordingFormattingNotificationService()
  }
  private val formattingService by extensionPointFixture(FormattingService.EP_NAME) { FooAsyncDocumentFormattingService() }

  private suspend fun CoroutineScope.launchAsyncFormatting(
    formattingService: FooAsyncDocumentFormattingService,
    task: TestTask,
    psiFile: PsiFile,
  ): Job = launchAsyncFormattingSequence(formattingService, listOf(task), psiFile) { _, _, _ -> }.holder

  private data class LaunchedSequencedTasks(
    /** parent of all the tasks in [taskInfos] */
    val holder: Job,
    val taskInfos: List<LaunchedTaskInfo>,
  )

  private data class LaunchedTaskInfo(
    val task: TestTask,
    val startedOnEdt: SuspendLatch,
    val job: Job,
  )

  private suspend fun CoroutineScope.launchAsyncFormattingSequence(
    formattingService: FooAsyncDocumentFormattingService,
    tasks: List<TestTask>,
    psiFile: PsiFile,
    beforeStartingTask: suspend (prevTask: TestTask, prevStartedOnEdt: SuspendLatch, job: Job) -> Unit,
  ): LaunchedSequencedTasks {
    val deferredTasksAndJobs = CompletableDeferred<List<LaunchedTaskInfo>>()
    val job = launch(Dispatchers.IO) {
      deferredTasksAndJobs.complete(tasks.map { task ->
        val startedOnEdt = SuspendLatch("startedOnEdt#${task.id}")
        LaunchedTaskInfo(task, startedOnEdt, launch(start = CoroutineStart.LAZY) {
          blockingContextScope {
            invokeLater {
              startedOnEdt.release()
              runWriteAction {
                val doc = psiFile.fileDocument
                val oldSyncMode = doc.getUserData(AsyncDocumentFormattingService.FORMAT_DOCUMENT_SYNCHRONOUSLY)
                try {
                  doc.putUserData(AsyncDocumentFormattingService.FORMAT_DOCUMENT_SYNCHRONOUSLY, task.params.syncMode)
                  formattingService.nextTask.set(task)
                  formattingService.testFormatDocument(psiFile)
                }
                finally {
                  doc.putUserData(AsyncDocumentFormattingService.FORMAT_DOCUMENT_SYNCHRONOUSLY, oldSyncMode)
                }
              }
            }
          }
        })
      })
    }
    val taskInfos = deferredTasksAndJobs.await()

    taskInfos.first().job.start()
    taskInfos.asSequence()
      .drop(1)
      .forEachIndexed { index, (_, _, job) ->
        val (prevTask, prevStartedOnEdt, prevJob) = taskInfos[index]
        beforeStartingTask(prevTask, prevStartedOnEdt, prevJob)
        job.start()
      }
    return LaunchedSequencedTasks(job, taskInfos)
  }

  @ParameterizedTest
  // test non-sync-mode only: the formatting request is not cancellable by scope in sync mode on EDT
  @MethodSource("createAsyncTestParams")
  fun `async formatting task under blockingContextScope is cancelable`(params: TestTask.Params) = timeoutRunBlocking {
    assumeFalse(params.syncMode) { "Async formatting is not cancellable in sync mode on EDT" }
    val task = TestTask(params)
    val job = launchAsyncFormatting(formattingService, task, psiFile)

    task.isStarted.timeoutAwait()
    task.isCancelled.assertFalse()
    assertNoTimeout({ job.cancelAndJoin() }) { "Async formatting task should be cancelable by outer scope" }
    task.isCancelled.assertTrue { "Cancelling scope should call FormattingTask.cancel" }
    assertTrue(notificationService.errorsReported.isEmpty())
    assertEquals("before", psiFile.fileDocument.text)
  }

  @ParameterizedTest
  @MethodSource("createTestParams")
  fun `async formatting task succeeds`(params: TestTask.Params) = timeoutRunBlocking {
    val task = TestTask(params)
    val job = launchAsyncFormatting(formattingService, task, psiFile)

    task.isStarted.timeoutAwait()
    task.isFinishWork.assertRelease { "Task should be working" }
    task.isCancelled.assertFalse { "Task should not be canceled" }
    assertNoTimeout({ job.join() })
    task.isCancelled.assertFalse { "Task was not canceled on a successful run" }
    assertEquals("after", psiFile.fileDocument.text)
    assertTrue(notificationService.errorsReported.isEmpty())
  }

  @OptIn(DelicateCoroutinesApi::class)
  @ParameterizedTest
  @MethodSource("createTestParams")
  fun `async formatting task expires`(params: TestTask.Params) = timeoutRunBlocking {
    val formattingTimeout = 100L
    formattingService.timeoutMillis = formattingTimeout
    val task = TestTask(params)
    val job = launchAsyncFormatting(formattingService, task, psiFile)

    task.isStarted.timeoutAwait()
    task.isFinishWork.assertFalse { "Formatting task should be working" }

    assertNoTimeout({ job.join() }, timeout = 3 * formattingTimeout)

    task.isCancelled.assertTrue { "Formatting task should be canceled when expired" }
    assertEquals(1, notificationService.errorsReported.size) { "Expiry should be reported" }
    assertContains(notificationService.errorsReported.first(), "does not respond")
    assertEquals("before", psiFile.fileDocument.text)
  }

  @ParameterizedTest
  // test non-sync-mode only:
  // requests are serialized on a single thread in sync mode, so subsequent requests cannot start before prev finish
  @MethodSource("createAsyncTestParams")
  fun `async formatting request cancels previously started tasks`(params: TestTask.Params) = timeoutRunBlocking {
    val (job, taskInfos) = run {
      val tasks = List(10) { i -> TestTask(params, id = i) }
      launchAsyncFormattingSequence(formattingService, tasks, psiFile, beforeStartingTask = { prevTask, _, _ ->
        prevTask.isStarted.timeoutAwait()
      })
    }

    val lastTask = taskInfos.last().task
    lastTask.isStarted.timeoutAwait()
    lastTask.isCancelled.assertFalse()
    taskInfos.dropLast(1).forEach { (task, _) ->
      task.isStarted.assertTrue { "Created task should also be started" }
      task.isCancelled.assertTrue { "Task should be canceled by subsequent requests" }
    }
    assertNoTimeout({ job.cancelAndJoin() })
    lastTask.isCancelled.assertTrue()
    assertTrue(notificationService.errorsReported.isEmpty())
    assertEquals("before", psiFile.fileDocument.text)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @ParameterizedTest
  // test non-sync-mode only:
  // requests are serialized on a single thread in sync mode, so subsequent requests cannot start before prev finish
  @MethodSource("createAsyncTestParams")
  fun `subsequent request is either rejected or previous is cancelled`(params: TestTask.Params) = timeoutRunBlocking {
    val (job, taskInfos) = run {
      val tasks = List(10) { i -> TestTask(params, id = i) }
      launchAsyncFormattingSequence(formattingService, tasks, psiFile, beforeStartingTask = { _, prevStartedOnEdt, _ ->
        prevStartedOnEdt.timeoutAwait()
      })
    }

    taskInfos.last().startedOnEdt.timeoutAwait()

    val (startedTasks, rejectedTasks) = taskInfos.partition { (task, _, job) ->
      assertNoTimeout({
                        select {
                          task.isStarted.onAwait { true }
                          job.onJoin { false }
                        }
                      }) { "Task#${task.id} should have started or its job completed by now" }
    }
    rejectedTasks.forEach { it.task.isCreated.assertFalse { "Rejected task should not be created" } }
    val runningTasks = startedTasks.filter { !it.task.isCancelled.check() }
    assert(runningTasks.size == 1)
    val runningTask = runningTasks.first()
    with(runningTask.task) {
      isCreated.assertTrue()
      isStarted.assertTrue()
      isCancelled.assertFalse()
    }
    runningTask.task.isFinishWork.assertRelease()
    assertNoTimeout({ job.join() })
    assertEquals("after", psiFile.fileDocument.text)
  }

  @ParameterizedTest
  // non-blocking tasks are too unpredictable here
  //  (FormattingTask.run returns immediately; actual work is detached)
  @DisableIfDisplayName(contains = ["isBlocking=false"])
  // test non-sync-mode only:
  // requests are serialized on a single thread in sync mode, so subsequent requests cannot start before prev finish
  @MethodSource("createAsyncTestParams")
  fun `async formatting is not started if previous request could not be canceled`(params: TestTask.Params) = timeoutRunBlocking {
    val (job, taskInfos) = run {
      val tasks = List(10) { i -> TestTask(params, cancelable = false, id = i) }
      launchAsyncFormattingSequence(formattingService, tasks, psiFile, beforeStartingTask = { _, prevStartedOnEdt, _ ->
        prevStartedOnEdt.timeoutAwait()
      })
    }

    taskInfos.last().startedOnEdt.timeoutAwait()
    taskInfos.drop(1).map { it.job }.joinAll()
    val firstTask = taskInfos.first().task
    firstTask.isStarted.timeoutAwait()
    with(firstTask) {
      isCreated.assertTrue()
      isCancelled.assertFalse()
    }
    with(taskInfos.first().job) {
      assertTrue(isActive)
      assertTrue(!isCancelled)
    }
    firstTask.isFinishWork.assertRelease()
    assertNoTimeout({ job.join() })
    // it depends on a race: cancellation is ignored if all subsequent requests arrive before the task is started
    //assertEquals("before", psiFile.fileDocument.text)
  }


  @Disabled // TODO enable me when IJPL-211314 is fixed, until then, it will be flaky
  @ParameterizedTest
  // test non-sync-mode only: the formatting request is not cancellable by scope in sync mode on EDT
  @MethodSource("createAsyncTestParams")
  fun `task is started if it was already created and scope was canceled`(params: TestTask.Params) = timeoutRunBlocking {
    val (job, taskInfos) = run {
      val tasks = List(25) { i -> TestTask(params, cancelable = true, id = i) }
      launchAsyncFormattingSequence(formattingService, tasks, psiFile, beforeStartingTask = { prevTask, _, prevJob ->
        prevTask.isCreated.timeoutAwait()
        assertNoTimeout({ prevJob.cancelAndJoin() })
      })
    }

    taskInfos.last().let { (task, _, job) ->
      task.isCreated.timeoutAwait()
      assertNoTimeout({ job.cancelAndJoin() })
    }

    taskInfos.forEach { (task, _, _) ->
      task.isStarted.assertTrue { "Created task#${task.id} should also be started" }
      task.isCancelled.assertTrue { "Task#${task.id} should be canceled by scope cancellation" }
    }

    assertNoTimeout({ job.join() })
  }
}

private class FooAsyncDocumentFormattingService(
  var timeoutMillis: Long = DEFAULT_TEST_TIMEOUT.inWholeMilliseconds
) : AsyncDocumentFormattingService() {
  val nextTask = AtomicReference<TestTask?>(null)

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
    val task = nextTask.getAndSet(null) ?: error("No formatting task prepared in the test!")
    task.isCreated.release()
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

open class TestTask(
  val params: Params,
  private val cancelable: Boolean = true,
  val id: Int = 0
) {
  data class Params(
    val isBlocking: Boolean,
    val cancellationType: CancellationCheckType,
    val runUnderProgress: Boolean,
    val syncMode: Boolean,
  )

  val isCreated = SuspendLatch("isCreated#$id")
  val isStarted = SuspendLatch("isStarted#$id")
  val isCancelled = SuspendLatch("isCancelled#$id")
  // release it to signal the task to stop simulating work and call `onTextReady`
  val isFinishWork = SuspendLatch("stopWorking#$id")

  protected fun testCheckCanceled(): Boolean {
    if (!cancelable) return false
    when (params.cancellationType) {
      CancellationCheckType.MANAGER -> ProgressManager.checkCanceled()
      CancellationCheckType.SEMAPHORE -> if (isCancelled.check()) return true
      CancellationCheckType.INDICATOR -> {
        val indicator = ProgressManager.getInstance().progressIndicator
        indicator?.checkCanceled()
      }
    }
    return false
  }

  protected fun doRun(formattingRequest: AsyncFormattingRequest) {
    isStarted.assertRelease { "`run` should not be called multiple times" }
    // simulate work
    while (true) {
      if (testCheckCanceled()) break
      if (isFinishWork.check()) break
      Thread.sleep(10)
    }
    if (!testCheckCanceled()) {
      formattingRequest.onTextReady("after")
    }
  }

  val isRunUnderProgress: Boolean
    get() = params.runUnderProgress

  fun cancel(): Boolean {
    if (!cancelable) return false
    check(isCancelled.release()) { "FormattingTask.cancel should only be called once" }
    return true
  }

  @OptIn(DelicateCoroutinesApi::class)
  fun run(formattingRequest: AsyncFormattingRequest) {
    if (params.isBlocking) {
      doRun(formattingRequest)
    }
    else {
      // simulate something like ProcessHandler, which does not block the task and does not get tied to the context job
      GlobalScope.launch {
        doRun(formattingRequest)
      }
    }
  }
}

private class RecordingFormattingNotificationService : FormattingNotificationService {
  val errorsReported = ContainerUtil.createConcurrentList<String>()

  override fun reportError(
    groupId: String,
    displayId: String?,
    title: @NlsContexts.NotificationTitle String,
    message: @NlsContexts.NotificationContent String,
    vararg actions: AnAction?,
  ) {
    errorsReported.add(message)
  }

  override fun reportErrorAndNavigate(
    groupId: String,
    displayId: String?,
    title: @NlsContexts.NotificationTitle String,
    message: @NlsContexts.NotificationContent String,
    context: FormattingContext,
    offset: Int,
  ) {
    errorsReported.add(message)
  }
}

@TestOnly
fun <T : Any> TestFixture<Project>.replacedServiceFixture(
  serviceInterface: Class<in T>,
  createService: suspend () -> T,
): TestFixture<T> = testFixture {
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

private const val DEFAULT_AWAIT_TIMEOUT = 500L

private suspend fun <T : Any> assertNoTimeout(
  block: suspend () -> T,
  timeout: Long = DEFAULT_AWAIT_TIMEOUT,
  message: () -> String = { "Timed out waiting for condition" },
) : T {
  if (DebugAttachDetector.isAttached()) return block()
  return withTimeoutOrNull(timeout) { block() } ?: fail(message())
}

/** Initially, [check] returns false. After [release] is called, [check] returns true. */
class SuspendLatch(val debugName: String = "") {
  private val result = CompletableDeferred<Unit>()
  suspend fun await() {
    logDebug { "await" }
    result.await()
  }
  val onAwait = result.onAwait
  suspend fun timeoutAwait(message: () -> String = { "Timed out waiting for latch $debugName" }, timeout: Long = DEFAULT_AWAIT_TIMEOUT) =
    assertNoTimeout({ await() }, timeout, message)

  fun release() = result.complete(Unit).also { logDebug { "release = $it" } }
  fun assertRelease(message: () -> String = { "Latch $debugName should be released" }) = assertTrue(release(), message)
  fun check() = result.isCompleted
  fun assertTrue(msg: () -> String = { "Latch $debugName should be released" }) = assertTrue(check(), msg)
  fun assertFalse(msg: () -> String = { "Latch $debugName should be locked" }) = assertFalse(check(), msg)
  private fun logDebug(msg: () -> String) {
    if (debugName.isNotEmpty()) LOG.debug("latch ${debugName}: ${msg()}")
  }
  override fun toString(): String = "$debugName = ${check()}"
  companion object {
    private val LOG = logger<SuspendLatch>()
  }
}

private val BOOLEANS = listOf(false, true)

@Suppress("SameParameterValue")
private fun cartesianParams(
  isBlocking: List<Boolean>,
  cancellationCheck: List<CancellationCheckType>,
  runUnderProgress: List<Boolean>,
  syncMode: List<Boolean>,
) = cartesianProduct(isBlocking, cancellationCheck, runUnderProgress, syncMode).map {
  TestTask.Params(it[0] as Boolean, it[1] as CancellationCheckType, it[2] as Boolean, it[3] as Boolean)
}

private fun cartesianProduct(vararg lists: List<Any>): List<List<Any>> =
  lists.toList().fold(listOf(emptyList())) { acc, list -> acc.flatMap { partial -> list.map { partial + it } } }