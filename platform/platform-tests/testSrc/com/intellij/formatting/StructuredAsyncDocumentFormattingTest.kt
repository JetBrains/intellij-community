// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingNotificationService
import com.intellij.formatting.service.FormattingService
import com.intellij.formatting.service.structuredAsyncDocumentFormattingScope
import com.intellij.internal.DebugAttachDetector
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.utils.vfs.getDocument
import com.intellij.util.application
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertContains

@TestApplication
@SystemProperty("intellij.async.formatting.ignoreHeadless", "true")
class StructuredAsyncDocumentFormattingTest {
  companion object {
    private fun doCreateTestParams(nonSyncOnly: Boolean, nonModalOnly: Boolean): List<TestTask.Params> {
      val syncModeValues = if (nonSyncOnly) listOf(false) else BOOLEANS
      val modalProgressValues = if (nonModalOnly) listOf(false) else BOOLEANS
      return cartesianParams(
        isBlocking = listOf(true),
        CancellationCheckType.entries,
        runUnderBgProgress = BOOLEANS,
        syncMode = syncModeValues,
        runUnderModalProgress = modalProgressValues,
      ) -
      // these are expectedly non-cancellable, since they only check for an indicator that does not exist in the blocking task
      cartesianParams(
        isBlocking = listOf(true),
        cancellationCheck = listOf(CancellationCheckType.INDICATOR),
        runUnderBgProgress = listOf(false),
        syncMode = syncModeValues,
        runUnderModalProgress = modalProgressValues,
      ).toSet() +
      cartesianParams(
        isBlocking = listOf(false),
        listOf(CancellationCheckType.SEMAPHORE),
        runUnderBgProgress = BOOLEANS,
        syncMode = syncModeValues,
        runUnderModalProgress = modalProgressValues,
      )
    }

    @JvmStatic
    fun createTestParams(): List<TestTask.Params> = doCreateTestParams(nonSyncOnly = false, nonModalOnly = false)

    // sync-mode/under-modal-progress tasks block the EDT, so subsequent tasks cannot run until they finish
    @JvmStatic
    fun createNonSyncTestParams(): List<TestTask.Params> = doCreateTestParams(nonSyncOnly = true, nonModalOnly = true)

    // sync-mode tasks use raw runBlocking, which ignores outer context cancellation
    @JvmStatic
    fun createCancellableTestParams(): List<TestTask.Params> = doCreateTestParams(nonSyncOnly = true, nonModalOnly = false)

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

  private fun runWithModalProgressBlockingIfNeeded(isNeeded: Boolean, action: () -> Unit) =
    if (isNeeded) runWithModalProgressBlocking(project.get(), "test") {
      action()
    }
    else {
      action()
    }

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
          structuredAsyncDocumentFormattingScope {
            withContext(Dispatchers.EDT) {
              startedOnEdt.release()
              runWithModalProgressBlockingIfNeeded(task.params.runUnderModalProgress) {
                application.invokeAndWait {
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
  @MethodSource("createCancellableTestParams")
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
  @MethodSource("createNonSyncTestParams")
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
  @MethodSource("createNonSyncTestParams")
  fun `subsequent request is either rejected or previous is cancelled`(params: TestTask.Params) = timeoutRunBlocking {
    val (job, taskInfos) = run {
      val tasks = List(10) { i -> TestTask(params, id = i) }
      launchAsyncFormattingSequence(formattingService, tasks, psiFile, beforeStartingTask = { _, prevStartedOnEdt, _ ->
        prevStartedOnEdt.timeoutAwait()
      })
    }

    taskInfos.last().startedOnEdt.timeoutAwait()

    val (startedTasks, rejectedTasks) = taskInfos.partition { (task, _, job) ->
      assertNoTimeout(
        action = {
          select {
            task.isStarted.onAwait { true }
            job.onJoin { false }
          }
        },
        message = { "Task#${task.id} should have started or its job completed by now" }
      )
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
  @MethodSource("createNonSyncTestParams")
  fun `async formatting is not started if previous request could not be canceled`(params: TestTask.Params) = timeoutRunBlocking {
    // non-blocking tasks are too unpredictable here
    //  (FormattingTask.run returns immediately; actual work is detached)
    assumeTrue(params.isBlocking)
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
  @MethodSource("createNonSyncTestParams")
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

  @ParameterizedTest
  @MethodSource("createTestParams")
  // Ideally, tasks would not modify the document manually,
  // but for backwards compatibility and some existing use-cases, we need to support it.
  fun `task can modify document`(params: TestTask.Params) = timeoutRunBlocking {
    // the non-blocking test task does not propagate the context needed to make the doc update possible (modality)
    assumeTrue(params.isBlocking)
    val task = object : TestTask(params) {
      override fun doRun(formattingRequest: AsyncFormattingRequest) {
        WriteCommandAction.runWriteCommandAction(project.get()) {
          formattingRequest.context.virtualFile?.getDocument()?.setText("after")
        }
        formattingRequest.onTextReady(null)
      }
    }
    val job = launchAsyncFormatting(formattingService, task, psiFile)
    assertNoTimeout({ job.join() })
    assertEquals("after", psiFile.fileDocument.text)
  }
}

private class FooAsyncDocumentFormattingService(
  var timeoutMillis: Long = DEFAULT_TEST_TIMEOUT.inWholeMilliseconds,
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
  val id: Int = 0,
) {
  data class Params(
    val isBlocking: Boolean,
    val cancellationType: CancellationCheckType,
    val runUnderBgProgress: Boolean,
    val syncMode: Boolean,
    val runUnderModalProgress: Boolean,
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

  protected open fun doRun(formattingRequest: AsyncFormattingRequest) {
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
    get() = params.runUnderBgProgress

  fun cancel(): Boolean {
    if (!cancelable) return false
    check(isCancelled.release()) { "FormattingTask.cancel should only be called once" }
    return true
  }

  private fun signalStarted() = isStarted.assertRelease { "`run` should not be called multiple times" }

  @OptIn(DelicateCoroutinesApi::class)
  fun run(formattingRequest: AsyncFormattingRequest) {
    if (params.isBlocking) {
      signalStarted()
      doRun(formattingRequest)
    }
    else {
      // simulate something like ProcessHandler, which does not block the task and does not get tied to the context job
      GlobalScope.launch {
        signalStarted()
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
private fun <T : Any> TestFixture<Project>.replacedServiceFixture(
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
  action: suspend () -> T,
  timeout: Long = DEFAULT_AWAIT_TIMEOUT,
  message: () -> String = { "Timed out waiting for condition" },
): T {
  if (DebugAttachDetector.isAttached()) return action()
  return withTimeoutOrNull(timeout) { action() } ?: fail(message())
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
  runUnderBgProgress: List<Boolean>,
  syncMode: List<Boolean>,
  runUnderModalProgress: List<Boolean>,
) = cartesianProduct(isBlocking, cancellationCheck, runUnderBgProgress, syncMode, runUnderModalProgress).map {
  TestTask.Params(it[0] as Boolean, it[1] as CancellationCheckType, it[2] as Boolean, it[3] as Boolean, it[4] as Boolean)
}

private fun cartesianProduct(vararg lists: List<Any>): List<List<Any>> =
  lists.toList().fold(listOf(emptyList())) { acc, list -> acc.flatMap { partial -> list.map { partial + it } } }