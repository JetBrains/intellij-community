// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.core.rwmutex.ReadPermit
import com.intellij.openapi.application.ThreadingSupport
import com.intellij.openapi.application.readLockCompensationTimeout
import com.intellij.openapi.application.useNestedLocking
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.coroutines.runSuspend
import com.intellij.util.ThrowableRunnable
import io.opentelemetry.api.metrics.BatchCallback
import io.opentelemetry.api.metrics.Meter
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.intellij.IntellijCoroutines
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
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

@ApiStatus.Internal
fun getGlobalThreadingSupport(): ThreadingSupport {
  if (useNestedLocking) {
    return NestedLocksThreadingSupport
  }
  else {
    return AnyThreadWriteThreadingSupport
  }
}

@OptIn(InternalCoroutinesApi::class)
@VisibleForTesting
internal fun acquireReadLockWithCompensation(action: suspend () -> ReadPermit): ReadPermit {
  val compensationTimeout = compensationTimeout
  return if (compensationTimeout == null) {
    runSuspend(action)
  }
  else {
    return IntellijCoroutines.runAndCompensateParallelism(compensationTimeout) {
      runSuspend {
        action()
      }
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
