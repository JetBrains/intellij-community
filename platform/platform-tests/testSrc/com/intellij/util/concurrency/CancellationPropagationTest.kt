// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.callable
import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.concurrency.runnable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.impl.assertReferenced
import com.intellij.openapi.application.impl.pumpEDT
import com.intellij.openapi.application.impl.withModality
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.progress.*
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.TestLoggerFactory.TestLoggerAssertionError
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.getValue
import com.intellij.util.setValue
import com.intellij.util.ui.EDT
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import org.jetbrains.concurrency.AsyncPromise
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.reflect.Method
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertNotNull

/**
 * Rough cancellation equivalents with respect to structured concurrency are provided in comments.
 */
@TestApplication
@ExtendWith(CancellationPropagationTest.Enabler::class)
class CancellationPropagationTest {

  class Enabler : InvocationInterceptor {

    override fun interceptTestMethod(
      invocation: InvocationInterceptor.Invocation<Void>,
      invocationContext: ReflectiveInvocationContext<Method>,
      extensionContext: ExtensionContext,
    ) {
      runWithCancellationPropagationEnabled {
        invocation.proceed()
      }
    }
  }

  private val service = AppExecutorUtil.getAppExecutorService()
  private val scheduledService = AppExecutorUtil.getAppScheduledExecutorService()

  /**
   * ```
   * launch(Dispatchers.IO) {
   *   runnable.run()
   * }
   * ```
   */
  @Test
  fun `executeOnPooledThread(Runnable)`(): Unit = timeoutRunBlocking {
    doTest {
      ApplicationManager.getApplication().executeOnPooledThread(it.runnable())
    }
    doTestJobIsCancelledByFuture {
      ApplicationManager.getApplication().executeOnPooledThread(it.runnable())
    }
  }

  /**
   * ```
   * async(Dispatchers.IO) {
   *   callable.call()
   * }
   * ```
   */
  @Test
  fun `executeOnPooledThread(Callable)`(): Unit = timeoutRunBlocking {
    doTest {
      ApplicationManager.getApplication().executeOnPooledThread(it.callable())
    }
    doTestJobIsCancelledByFuture {
      ApplicationManager.getApplication().executeOnPooledThread(it.callable())
    }
  }

  /**
   * ```
   * launch(Dispatchers.EDT + modalityState.asContextElement()) {
   *   if (expiredCondition) {
   *     throw CancellationException() // cancel current `launch` job
   *   }
   *   runnable.run()
   * }
   * ```
   */
  @Test
  fun invokeLater(): Unit = timeoutRunBlocking {
    val application = ApplicationManager.getApplication()
    doTest {
      application.invokeLater(it.runnable())
    }
    doTest {
      application.invokeLater(it.runnable(), Conditions.alwaysFalse<Nothing?>())
    }
    doTest {
      application.invokeLater(it.runnable(), ModalityState.any())
    }
    doTest {
      application.invokeLater(it.runnable(), ModalityState.any(), Conditions.alwaysFalse<Nothing?>())
    }
    pumpEDT()
  }

  @Test
  fun `cancelled invokeLater is not executed`(): Unit = timeoutRunBlocking {
    launch {
      blockingContextScope {
        ApplicationManager.getApplication().withModality {
          val runnable = Runnable {
            fail()
          }
          ApplicationManager.getApplication().invokeLater(runnable, ModalityState.nonModal(), Conditions.alwaysFalse<Nothing?>())
          assertReferenced(LaterInvocator::class.java, runnable) // the runnable is queued
          this@launch.cancel()
        }
      }
    }.join()
    pumpEDT()
  }

  @Test
  fun `expired invokeLater does not prevent completion of parent job`(): Unit = timeoutRunBlocking {
    installThreadContext(coroutineContext).use {
      val expired = AtomicBoolean(false)
      ApplicationManager.getApplication().withModality {
        val runnable = Runnable {
          fail()
        }
        ApplicationManager.getApplication().invokeLater(runnable, ModalityState.nonModal(), Condition<Nothing?> { expired.get() })
        assertReferenced(LaterInvocator::class.java, runnable) // the runnable is queued
        expired.set(true)
      }
    }
    pumpEDT()
  }

  @Test
  fun edtExecutorService(): Unit = timeoutRunBlocking {
    val service = EdtExecutorService.getInstance()
    doExecutorServiceTest(service)
    doTest {
      service.execute(it)
    }
    doTest {
      service.execute(it)
    }
    doTest {
      service.submit(it)
    }
    doTest {
      service.submit(it.callable())
    }
  }

  @Test
  fun appExecutorService(): Unit = timeoutRunBlocking {
    doExecutorServiceTest(service)
    doTestInvokeAnyCancelsRunningCallables(service)
  }

  @Test
  fun appScheduledExecutorService(): Unit = timeoutRunBlocking {
    doScheduledExecutorServiceTest(scheduledService)
    doTestInvokeAnyCancelsRunningCallables(scheduledService)
  }

  @Test
  fun boundedApplicationPoolExecutor(): Unit = timeoutRunBlocking {
    doExecutorServiceTest(AppExecutorUtil.createBoundedApplicationPoolExecutor("Bounded", 1))
  }

  @Test
  fun boundedApplicationPoolExecutor2(): Unit = timeoutRunBlocking {
    val bounded2 = AppExecutorUtil.createBoundedApplicationPoolExecutor("Bounded-2", 2)
    doExecutorServiceTest(bounded2)
    doTestInvokeAnyCancelsRunningCallables(bounded2)
  }

  @Test
  fun boundedScheduledExecutorService(): Unit = timeoutRunBlocking {
    doScheduledExecutorServiceTest(AppExecutorUtil.createBoundedScheduledExecutorService("Bounded-Scheduled", 1))
  }

  @Test
  fun boundedScheduledExecutorService2(): Unit = timeoutRunBlocking {
    val bounded2 = AppExecutorUtil.createBoundedScheduledExecutorService("Bounded-Scheduled-2", 2)
    doScheduledExecutorServiceTest(bounded2)
    doTestInvokeAnyCancelsRunningCallables(bounded2)
  }

  private suspend fun doTest(submit: (() -> Unit) -> Unit) {
    var result: Boolean by AtomicReference(false)
    blockingContextScope {
      val parentJob = checkNotNull(Cancellation.currentJob())
      submit { // switch to another thread
        assertCurrentJobIsChildOf(parentJob)
        assertTrue(parentJob.isActive)
        result = true
      }
    }
    // `blockingContextScope` suspends until it does everything
    assert(result)
  }

  private suspend fun doExecutorServiceTest(service: ExecutorService) {
    doTest {
      // launch {
      //   runnable.run()
      // }
      service.execute(it.runnable())
    }
    doTest {
      // async {
      //   runnable.run()
      // }
      service.submit(it.runnable())
    }
    doTest {
      // async {
      //   callable.call()
      // }
      service.submit(it.callable())
    }
    doTest {
      // coroutineScope {
      //   val result = select { // await the fastest callable
      //     callables.map { callable ->
      //       async {
      //         callable.call()
      //       }.onAwait { it }
      //     }
      //   }
      //   coroutineContext.cancelChildren() // cancel the rest of callables
      //   return@coroutineScope result
      // }
      service.invokeAny(listOf(it.callable()))
    }
    doTest {
      // callables.map { callable ->
      //   async {
      //     callable.call()
      //   }
      // }.awaitAll()
      service.invokeAll(listOf(it.callable()))
    }
    doTestJobIsCancelledByFuture {
      service.submit(it.runnable())
    }
    doTestJobIsCancelledByFuture {
      service.submit(it.callable())
    }
  }

  private suspend fun doScheduledExecutorServiceTest(service: ScheduledExecutorService) {
    doExecutorServiceTest(service)
    doTest {
      // async {
      //   delay(timeout)
      //   runnable.run()
      // }
      service.schedule(it.runnable(), 10, TimeUnit.MILLISECONDS)
    }
    doTest {
      // async {
      //   delay(timeout)
      //   callable.call()
      // }
      service.schedule(it.callable(), 10, TimeUnit.MILLISECONDS)
    }
    doTestJobIsCancelledByFuture {
      // launch {
      //   delay(initialDelay)
      //   while (isActive) {
      //     runnable.run()
      //     delay(10)
      //   }
      // }
      service.scheduleWithFixedDelay(it.runnable(), 5, 10, TimeUnit.MILLISECONDS)
    }
    doTestScheduleWithFixedDelay(service)
  }

  private suspend fun doTestJobIsCancelledByFuture(submit: (() -> Unit) -> Future<*>) {
    val started = Semaphore(1)
    val cancelled = Semaphore(1)

    var result: Result<Unit> by AtomicReference(Result.failure(IllegalStateException("Runnable is not completed")))
    blockingContextScope {
      val computationEndSemaphore = Semaphore(1)
      val future = submit {
        result = runCatching<Unit> {
          started.up()
          cancelled.timeoutWaitUp()
          assertThrows<CeProcessCanceledException> {
            Cancellation.checkCancelled()
          }
        }
        computationEndSemaphore.up()
      }
      started.timeoutWaitUp()
      future.cancel(false)
      cancelled.up()
      assertThrows<CancellationException> {
        future.timeoutGet()
      }
      computationEndSemaphore.timeoutWaitUp()
    }
    assert(result.isSuccess)
  }

  private suspend fun doTestInvokeAnyCancelsRunningCallables(service: ExecutorService): Unit = coroutineScope {
    val started = Semaphore(2)
    val callable1CanFinish = Semaphore(1)
    val cs = this
    val c1: Callable<Int> = childCallable(cs) {
      started.up()
      callable1CanFinish.timeoutWaitUp()
      42
    }
    val c2: Callable<Int> = childCallable(cs) {
      started.up()
      throw assertThrows<CeProcessCanceledException> {
        while (cs.isActive) {
          Cancellation.checkCancelled()
        }
      }
    }
    val deferred = async(Dispatchers.IO) {
      blockingContextScope {
        service.invokeAny(listOf(c1, c2))
      }
    }
    started.timeoutWaitUp()
    callable1CanFinish.up()
    assertEquals(42, deferred.await())
  }

  private suspend fun doTestScheduleWithFixedDelay(service: ScheduledExecutorService) {
    val throwable = object : Throwable() {}
    val rootJob = withRootJob { currentJob ->
      val counter = AtomicInteger()
      var fixedDelayJob by AtomicReference<Job>()
      val runnable = Runnable {
        when (counter.getAndIncrement()) {
          0 -> {
            fixedDelayJob = assertCurrentJobIsChildOf(parent = currentJob)
            assertDoesNotThrow {
              Cancellation.checkCancelled()
            }
          }
          1 -> {
            assertSame(fixedDelayJob, Cancellation.currentJob()) // job is the same
            assertDoesNotThrow {
              Cancellation.checkCancelled()
            }
          }
          2 -> {
            assertSame(fixedDelayJob, Cancellation.currentJob()) // job is still the same
            assertDoesNotThrow {
              Cancellation.checkCancelled()
            }
            throw throwable
          }
          else -> {
            fail()
          }
        }
      }
      val error = LoggedErrorProcessor.executeAndReturnLoggedError(Runnable {
        val future = service.scheduleWithFixedDelay(runnable, 10, 10, TimeUnit.NANOSECONDS)
        waitAssertCompletedWith(future, throwable::class)
      })
      assertInstanceOf(throwable::class.java, error)
    }
    rootJob.join()
    val ce = assertThrows<CancellationException> {
      rootJob.ensureActive()
    }
    //suppressed until this one is fixed: https://youtrack.jetbrains.com/issue/KT-52379
    @Suppress("AssertBetweenInconvertibleTypes")
    assertSame(throwable, ce.cause)
  }

  @Test
  fun `future is completed before job is completed`() {
    var childFuture1 by AtomicReference<Future<*>>()
    var childFuture2 by AtomicReference<Future<*>>()
    val childFuture1Set = Semaphore(1)
    val childFuture2Set = Semaphore(1)
    val rootJob = withRootJob {
      childFuture1 = service.submit {
        childFuture2 = service.submit { // execute -> submit
          childFuture1Set.timeoutWaitUp()
          waitAssertCompletedNormally(childFuture1) // key point: the future is done, but the Job is not
        }
        childFuture2Set.up()
      }
      childFuture1Set.up()
    }
    childFuture2Set.timeoutWaitUp()
    waitAssertCompletedNormally(childFuture2)
    waitAssertCompletedNormally(rootJob)
  }

  @Test
  fun `child is cancelled by parent job`() {
    var childFuture by AtomicReference<Future<*>>()
    val lock = Semaphore(2)
    val rootJob = withRootJob {
      childFuture = service.submit {
        lock.up()
        neverEndingStory()
      }
      lock.up()
    }
    lock.timeoutWaitUp()
    rootJob.cancel()
    waitAssertCompletedWith(childFuture, CeProcessCanceledException::class)
    rootJob.timeoutJoinBlocking()
  }

  @Test
  fun `cancelled child does not fail parent`() {
    var childFuture1 by AtomicReference<Future<*>>()
    var childFuture2 by AtomicReference<Future<*>>()
    val childFuture1CanThrow = Semaphore(1)
    val childFuture2CanFinish = Semaphore(1)

    val lock = Semaphore(3)
    val rootJob = withRootJob {
      childFuture1 = service.submit {
        lock.up()
        childFuture1CanThrow.timeoutWaitUp()
        throw CancellationException()
      }
      childFuture2 = service.submit {
        lock.up()
        childFuture2CanFinish.timeoutWaitUp()
        ProgressManager.checkCanceled()
      }
      lock.up()
    }
    lock.timeoutWaitUp()

    childFuture1CanThrow.up()
    waitAssertCompletedWithCancellation(childFuture1)
    childFuture2CanFinish.up()
    waitAssertCompletedNormally(childFuture2)
    waitAssertCompletedNormally(rootJob)
  }

  @Test
  fun `failed child fails parent`() {
    class E : Throwable()

    var childFuture1 by AtomicReference<Future<*>>()
    var childFuture2 by AtomicReference<Future<*>>()
    val childFuture1CanThrow = Semaphore(1)
    val childFuture2CanFinish = Semaphore(1)

    val lock = Semaphore(3)
    val rootJob = withRootJob {
      childFuture1 = service.submit {
        lock.up()
        childFuture1CanThrow.timeoutWaitUp()
        throw E()
      }
      childFuture2 = service.submit {
        lock.up()
        childFuture2CanFinish.timeoutWaitUp()
        Cancellation.checkCancelled()
      }
      lock.up()
    }
    lock.timeoutWaitUp()

    childFuture1CanThrow.up()
    waitAssertCompletedWith(childFuture1, E::class)
    childFuture2CanFinish.up()
    waitAssertCompletedWith(childFuture2, CeProcessCanceledException::class)
    waitAssertCancelled(rootJob)
  }

  @Test
  fun `unhandled exception from execute`() {
    val t = Throwable()
    val canThrow = Semaphore(1)
    withRootJob {
      service.execute {
        canThrow.timeoutWaitUp()
        throw t
      }
    }
    val loggedError = loggedError(canThrow)
    assertSame(t, loggedError)
  }

  @Test
  fun `infinite re-scheduling does not grow job chain`(): Unit = timeoutRunBlocking {
    val chainCounter = AtomicInteger(0)
    blockingContextScope {
      val job = assertNotNull(Cancellation.currentJob())
      fun chain(remaining: Int) {
        ApplicationManager.getApplication().executeOnPooledThread {
          assertCurrentJobIsChildOf(job)
          if (remaining > 0) {
            chain(remaining - 1)
          }
        }
        chainCounter.incrementAndGet()
      }
      chain(1000)
    }
    assertEquals(1001, chainCounter.get())
  }

  @Test
  fun `prepareThreadContext resets blocking job`(): Unit = timeoutRunBlocking {
    blockingContextScope {
      val job = assertNotNull(Cancellation.currentJob())
      ApplicationManager.getApplication().executeOnPooledThread {
        runCatching {
          val blockingJob = assertNotNull(currentThreadContext()[BlockingJob])
          assertSame(job, blockingJob.blockingJob)
          prepareThreadContext { prepared ->
            assertNull(prepared[BlockingJob])
          }
          runBlockingCancellable {
            assertNull(coroutineContext[BlockingJob])
          }
        }
      }
    }
  }

  @Test
  fun `blockingContextScope suspends until all children are cancelled`() = timeoutRunBlocking {
    val allowedToProceed = Semaphore(1)
    val scheduled = Semaphore(2)
    var cancelled: Boolean by AtomicReference(false)
    var blockingJobRef: Job? by AtomicReference(null)
    val job = withRootJob {
      blockingJobRef = it
      ApplicationManager.getApplication().executeOnPooledThread {
        scheduled.up()
        allowedToProceed.timeoutWaitUp()
        try {
          Cancellation.checkCancelled()
        }
        catch (e: CeProcessCanceledException) {
          cancelled = true
          throw e
        }
      }
      scheduled.up()
    }
    scheduled.timeoutWaitUp()
    scheduled.timeoutWaitUp()
    assertTrue(job.isActive)
    blockingJobRef!!.cancel(null)
    assertTrue(job.isActive)
    assertFalse(cancelled)
    allowedToProceed.up()
    job.join()
    assertTrue(cancelled)
  }

  @SystemProperty("intellij.progress.task.ignoreHeadless", "true")
  @Test
  fun `Tasks are awaited`() = timeoutRunBlocking {
    doTest {
      object : Task.Modal(null, "", true) {
        override fun run(indicator: ProgressIndicator) {
          it()
        }
      }.queue()
    }
  }

  class MyException : RuntimeException("intentional error")

  @Test
  fun `blockingContextScope saves error`(): Unit =
    timeoutRunBlocking {
      try {
        blockingContextScope {
          ApplicationManager.getApplication().executeOnPooledThread {
            throw MyException()
          }
        }
      }
      catch (e: TestLoggerAssertionError) {
        // TODO: We need to reconsider approach to context propagation in executors
        // the capturing should occur after top-level error-handling in the stack,
        // because cancellation framework changes thrown exceptions
        // desired outcome: the catch arm should catch MyException, but not TestLoggerAssertionError
        assertInstanceOf<MyException>(e.cause)
      }
    }

  @Test
  fun `blockingContextScope fails with exception even in cancelled state`() : Unit = timeoutRunBlocking {
    // this scenario represents a case where an error occurs during the cleanup after cancellation
    // we should not silently swallow the error, since it is important to know that the cleanup went wrong
    try {
      blockingContextScope {
        val job = currentThreadContext().job
        ApplicationManager.getApplication().executeOnPooledThread {
          try {
            job.cancel()
            Cancellation.checkCancelled()
            fail("should be cancelled")
          }
          catch (e: ProcessCanceledException) {
            throw MyException()
          }
        }
      }
    } catch (e : TestLoggerAssertionError) {
      // TODO: the same as in `blockingContextScope save error`
      assertInstanceOf<MyException>(e.cause)
    }
  }

  @Test
  fun `merging update queue waits its children`(): Unit = timeoutRunBlocking {
    val queue = MergingUpdateQueue("test queue", 200, true, null)
    val allowCompleteUpdate = arrayOf(AtomicBoolean(false), AtomicBoolean(false))
    val updateCompleted = arrayOf(AtomicBoolean(false), AtomicBoolean(false))

    val queueingDone = Job()
    val blockingScopeJob = withRootJob {
      val currentJob = currentThreadContext().job

      for (i in 0..1) {
        queue.queue(Update.create(i) {
          while (!allowCompleteUpdate[i].get()) {
            // wait for permission
          }
          assert(currentJob.isActive) // parent is not finished
          assertCurrentJobIsChildOf(currentJob)
          updateCompleted[i].set(true)
        })
      }

      queueingDone.complete()
    }

    queueingDone.join()
    assertTrue(blockingScopeJob.isActive)
    repeat(2) { assertFalse(updateCompleted[it].get()) } // updates are not finished

    delay(400)
    assertTrue(blockingScopeJob.isActive)
    repeat(2) { assertFalse(updateCompleted[it].get()) } // updates are not finished even after queue starts processing

    allowCompleteUpdate[0].set(true)
    delay(100)
    assertTrue(updateCompleted[0].get()) // first activity should be finished
    assertFalse(updateCompleted[1].get()) // second activity is running
    assertTrue(blockingScopeJob.isActive)

    allowCompleteUpdate[1].set(true)
    blockingScopeJob.join()
    assertTrue(updateCompleted[1].get())
    assertTrue(queue.isEmpty)
  }

  @OptIn(DelicateCoroutinesApi::class)
  @Test
  fun `merging update queue cancels spawned tasks`() {
    val errorMessage = "intentionally failed"
    val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
    try {
      Thread.setDefaultUncaughtExceptionHandler { t, e ->
        assertTrue(EDT.isEdt(t))
        assertEquals(errorMessage, e.message)
      }
      LoggedErrorProcessor.executeWith<IllegalStateException>(object : LoggedErrorProcessor() {
        override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): MutableSet<Action> {
          assertEquals(errorMessage, t!!.message)
          return Action.NONE
        }
      })
      {
        timeoutRunBlocking {
          val queue = MergingUpdateQueue("test queue", 100, true, null)
          var updateAllowedToComplete by AtomicReference(false)
          var secondUpdateExecuted by AtomicReference(false)
          var immortalExecuted by AtomicReference(false)
          val immortalSemaphore = Job(null)

          supervisorScope {
            val queuingDone = Job()
            val deferred = GlobalScope.async {
              blockingContextScope {
                queue.queue(Update.create("id") {
                  while (!updateAllowedToComplete) {
                    // wait for permission
                  }
                  throw IllegalStateException(errorMessage)
                })
                queue.queue(Update.create("id2") {
                  secondUpdateExecuted = true
                })
                queuingDone.complete()
              }
            }
            queue.queue(Update.create("immortal") {
              immortalExecuted = true
              immortalSemaphore.complete()
            })

            queuingDone.join()
            assertTrue(deferred.isActive)

            updateAllowedToComplete = true
            try {
              deferred.await()
              fail<Unit>("The first update should throw")
            }
            catch (e: IllegalStateException) {
              assertEquals("intentionally failed", e.message)
            }
            assertTrue(queue.isEmpty) // all the tasks were processed
            assertFalse(secondUpdateExecuted) // this task should not be executed
            immortalSemaphore.join()
            assertTrue(immortalExecuted) // but other tasks do not get cancelled
          }
          pumpEDT()
        }
      }
    }
    finally {
      Thread.setDefaultUncaughtExceptionHandler(currentHandler)
    }
  }

  @Test
  fun `eating cancels tasks`(): Unit = timeoutRunBlocking {
    val queue = MergingUpdateQueue("test queue", 100, true, null)
    var firstExecuted by AtomicReference(false)
    var secondExecuted by AtomicReference(false)

    blockingContextScope {
      queue.queue(Update.create("id") {
        firstExecuted = true
      })
      queue.queue(Update.create("id") {
        secondExecuted = true
      })
    }
    // so `blockingContextScope` exists after all its spawned tasks exit
    assert(queue.isEmpty)
    assertFalse(firstExecuted) // eaten by the second
    assertTrue(secondExecuted) // not eaten and executed
  }


  @Test
  fun `non-blocking read action is awaited`() = timeoutRunBlocking {
    var allowedToCompleteRA by AtomicReference(false)
    val readActionCompletedSemaphore = Semaphore(1)
    val allowedToCompleteFinishOnUi = Semaphore(1)
    val finishedOnUi = Semaphore(1)
    val allowedToCompleteOnProcessed = Semaphore(1)
    val finishedOnProcessed = Semaphore(1)
    val allowedToCompleteThenAsync = Semaphore(1)
    val finishedThenAsync = Semaphore(1)
    var timesCancelled by AtomicReference(0)
    val readActionScheduled = Semaphore(2)
    val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Test NBRA", 1)
    val job = withRootJob { job ->
      ReadAction.nonBlocking(Callable {
        readActionScheduled.up()
        while (!allowedToCompleteRA) {
          try {
            ProgressManager.checkCanceled()
          }
          catch (e: ProcessCanceledException) {
            assertTrue(job.isActive)
            timesCancelled += 1
            throw e
          }
        }
        readActionCompletedSemaphore.up()
      }).finishOnUiThread(ModalityState.defaultModalityState()) {
        assertTrue(job.isActive)
        allowedToCompleteFinishOnUi.timeoutWaitUp()
        finishedOnUi.up()
      }.submit(executor)
        .onSuccess {
          assertTrue(job.isActive)
          allowedToCompleteOnProcessed.timeoutWaitUp()
          finishedOnProcessed.up()
        }.then {
          assertTrue(job.isActive)
          allowedToCompleteThenAsync.timeoutWaitUp()
          finishedThenAsync.up()
        }
      readActionScheduled.up()
    }
    readActionScheduled.timeoutWaitUp()
    readActionScheduled.timeoutWaitUp()
    assertTrue(job.isActive)
    assertEquals(0, timesCancelled)
    writeAction {
      // immediately return, just to cancel and reschedule read action
    }
    assertEquals(1, timesCancelled)
    allowedToCompleteRA = true
    readActionCompletedSemaphore.timeoutWaitUp()
    assertTrue(job.isActive)
    allowedToCompleteOnProcessed.up()
    finishedOnProcessed.timeoutWaitUp()
    assertTrue(job.isActive)
    allowedToCompleteThenAsync.up()
    finishedThenAsync.timeoutWaitUp()
    assertTrue(job.isActive)
    allowedToCompleteFinishOnUi.up()
    finishedOnUi.timeoutWaitUp()
    job.join()
    assertFalse(job.isCancelled)
  }

  @Test
  fun `synchronous non-blocking read action is awaited`() = timeoutRunBlocking {
    val dummyDisposable = Disposer.newDisposable()
    var allowedToCompleteRA by AtomicReference(false)
    val readActionCompletedSemaphore = Semaphore(1)
    val job = withRootJob { job ->
      ReadAction.nonBlocking(Callable {
        while (!allowedToCompleteRA) {
          assertTrue(job.isActive)
        }
        readActionCompletedSemaphore.up()
      }).expireWith(dummyDisposable)
        .executeSynchronously()
    }
    assertTrue(job.isActive)
    allowedToCompleteRA = true
    readActionCompletedSemaphore.timeoutWaitUp()
    job.join()
    Disposer.dispose(dummyDisposable)
    assertFalse(job.isCancelled)
  }

  @Test
  fun `failing promise`() = timeoutRunBlocking {
    withRootJob {
      AsyncPromise<Unit>().apply { setError("bad") }.then {}
    }.join()
  }
}
