// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.concurrency.currentThreadContext
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ThreadingSupport.RunnableWithTransferredWriteAction
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.application.readLockCompensationTimeout
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.platform.locking.impl.getGlobalThreadingSupport
import com.intellij.util.SlowOperations
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.AppScheduledExecutorService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.ui.EDT
import io.opentelemetry.api.metrics.BatchCallback
import io.opentelemetry.api.metrics.Meter
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.awt.event.InvocationEvent
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Count read & write actions executed, and report to OpenTelemetry Metrics  */
internal class OTelReadWriteActionsMonitor(meter: Meter) : AutoCloseable {
  private val batchCallback: BatchCallback
  private val readActionsExecuted = AtomicInteger()
  private val writeActionsExecuted = AtomicInteger()

  init {
    val raExecutionsCounter = meter.counterBuilder("ReadAction.executionsCount")
      .setDescription("Total read actions executed")
      .buildObserver()
    val waExecutionCounter = meter.counterBuilder("WriteAction.executionsCount")
      .setDescription("Total write actions executed")
      .buildObserver()
    batchCallback = meter.batchCallback(
      Runnable {
        raExecutionsCounter.record(readActionsExecuted.get().toLong())
        waExecutionCounter.record(writeActionsExecuted.get().toLong())
      },
      raExecutionsCounter,
      waExecutionCounter
    )
  }

  fun readActionExecuted() {
    readActionsExecuted.incrementAndGet()
  }

  fun writeActionExecuted() {
    writeActionsExecuted.incrementAndGet()
  }

  override fun close() {
    batchCallback.close()
  }
}

/**
 * Helps to rethrow exceptions coming from [actual] bypassing an exception-intolerant layer defined by [transformer]
 */
@ApiStatus.Internal
internal fun rethrowExceptions(transformer: (Runnable) -> Runnable, actual: Runnable): Runnable {
  val exception: AtomicReference<Throwable> = AtomicReference(null)
  val localTransformer = { r: Runnable -> if (actual is ContextAwareRunnable) ContextAwareRunnable { r.run() } else r }
  val wrapped = transformer(localTransformer {
    try {
      actual.run()
    }
    catch (_: ProcessCanceledException) {
      // An aborted runnable should simply stop its execution and NOT signal its parent about the failure
    }
    catch (e: Throwable) {
      exception.set(e)
    }
  })
  return Runnable {
    try {
      wrapped.run()
    }
    catch (e: ProcessCanceledException) {
      // Throwing PCE from `invokeAndWait` is a potentially very dangerous change.
      // This is definitely TODO, but not for now
    }
    val caughtException = exception.get()
    if (caughtException != null) {
      throw caughtException
    }
  }
}

@Volatile
private var compensationTimeout: Duration? = if (readLockCompensationTimeout == -1) {
  null
}
else {
  readLockCompensationTimeout.milliseconds
}


@TestOnly
@ApiStatus.Internal
fun setCompensationTimeout(timeout: Duration?): Duration? {
  val currentTimeout = compensationTimeout
  compensationTimeout = timeout
  return currentTimeout
}

internal fun runnableUnitFunction(runnable: Runnable): () -> Unit = runnable::run
internal fun rethrowCheckedExceptions(f: ThrowableRunnable<*>): () -> Unit = f::run
internal fun <T> rethrowCheckedExceptions(f: ThrowableComputable<T, *>): () -> T = f::compute

@TestOnly
@ApiStatus.Experimental
object TestOnlyThreading {
  @JvmStatic
  fun <T> releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack(action: () -> T): T {
    val application = ApplicationManager.getApplication()
    if (application == null) {
      return action()
    }
    if (application.isWriteIntentLockAcquired) {
      return getGlobalThreadingSupport().releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack(action)
    } else {
      return action()
    }
  }
}

@ApiStatus.Internal
object InternalThreading {
  private val backgroundWriteActionCounter = AtomicInteger(0)

  @JvmStatic
  fun incrementBackgroundWriteActionCount() {
    backgroundWriteActionCounter.incrementAndGet()
  }

  @JvmStatic
  fun isBackgroundWriteActionRunning(): Boolean {
    return backgroundWriteActionCounter.get() > 0
  }

  @JvmStatic
  fun decrementBackgroundWriteActionCount() {
    backgroundWriteActionCounter.decrementAndGet()
  }

  object RunInBackgroundWriteActionMarker
    : CoroutineContext.Element,
      CoroutineContext.Key<RunInBackgroundWriteActionMarker> {
    override val key: CoroutineContext.Key<*> get() = this
  }

  @Internal
  @JvmStatic
  fun isBackgroundWriteActionAllowed(): Boolean =
    currentThreadContext()[RunInBackgroundWriteActionMarker] != null



  @RequiresBackgroundThread(generateAssertion = false)
  @RequiresWriteLock(generateAssertion = false)
  @Throws(Throwable::class)
  @JvmStatic
  fun invokeAndWaitWithTransferredWriteAction(runnable: Runnable) {
    val lock = getGlobalThreadingSupport()
    assert(lock.isWriteAccessAllowed()) { "Transferring of write action is permitted only if write lock is acquired" }
    assert(!EDT.isCurrentThreadEdt()) { "Transferring of write action is permitted only on background thread" }
    val exceptionRef = Ref.create<Throwable?>()
    val capturedRunnable = AppScheduledExecutorService.captureContextCancellationForRunnableThatDoesNotOutliveContextScope {
      try {
        lock.allowTakingLocksInsideAndRun {
          // we can appear here if someone tries to acquire a read action in a forced slow-op section
          // the users have no control over computations that run inside transferred write action, hence we reset the slow-op section
          SlowOperations.startSection(SlowOperations.RESET).use {
            (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity(runnable)
          }
        }
      }
      catch (e: Throwable) {
        exceptionRef.set(e)
      }
    }
    lock.transferWriteActionAndBlock({ toRun: RunnableWithTransferredWriteAction ->
                                       val event = TransferredWriteActionEvent(toRun)
                                       try {
                                         IdeEventQueue.getInstance().doPostEvent(event, true)
                                         event.blockingWait()
                                       }
                                       catch (e: InterruptedException) {
                                         exceptionRef.set(e)
                                       }
                                     }, capturedRunnable)
    exceptionRef.get()?.let { throw it }
  }

  @ApiStatus.Internal
  class TransferredWriteActionEvent private constructor(
    val action: AtomicReference<RunnableWithTransferredWriteAction>,
    val job: CompletableJob = Job(currentThreadContext()[Job])) : InvocationEvent(InternalThreading, {
    execute(action, job)
  }) {

    companion object {
      fun execute(action: AtomicReference<RunnableWithTransferredWriteAction>, job: CompletableJob) {
        try {
          val action = action.getAndSet(null) ?: return
          action.run()
        } finally {
          job.complete()
        }
      }
    }

    constructor(action: RunnableWithTransferredWriteAction) : this(AtomicReference(action))

    fun execute() {
      execute(action, job)
    }

    fun blockingWait() {
      job.asCompletableFuture().join()
    }
  }
}

