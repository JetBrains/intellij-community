// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("Propagation")
@file:Internal
@file:Suppress("TestOnlyProblems", "NAME_SHADOWING")

package com.intellij.util.concurrency

import com.intellij.concurrency.ContextCallable
import com.intellij.concurrency.ContextRunnable
import com.intellij.concurrency.captureThreadContext
import com.intellij.openapi.progress.*
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.SchedulingWrapper.MyScheduledFutureTask
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask

private object Holder {
  var propagateThreadContext: Boolean = Registry.`is`("ide.propagate.context")
  var propagateThreadCancellation: Boolean = Registry.`is`("ide.propagate.cancellation")
}

@TestOnly
fun runWithContextPropagationEnabled(runnable: Runnable) {
  val propagateThreadContext = Holder.propagateThreadContext
  Holder.propagateThreadContext = true
  try {
    runnable.run()
  }
  finally {
    Holder.propagateThreadContext = propagateThreadContext
  }
}

@TestOnly
fun runWithCancellationPropagationEnabled(runnable: Runnable) {
  val propagateThreadCancellation = Holder.propagateThreadCancellation
  Holder.propagateThreadCancellation = true
  try {
    runnable.run()
  }
  finally {
    Holder.propagateThreadCancellation = propagateThreadCancellation
  }
}

internal val isPropagateThreadContext: Boolean
  get() = Holder.propagateThreadContext

internal val isPropagateCancellation: Boolean
  get() = Holder.propagateThreadCancellation

internal fun capturePropagationAndCancellationContext(command: Runnable): Runnable {
  var command = command
  if (isPropagateCancellation) {
    val currentJob = Cancellation.currentJob()
    val childJob = Job(currentJob)
    command = CancellationRunnable(childJob, command)
  }
  return capturePropagationContext(command)
}

fun capturePropagationAndCancellationContext(
  command: Runnable,
  expired: Condition<*>,
): Pair<Runnable, Condition<*>> {
  var command = command
  var expired = expired
  if (isPropagateCancellation) {
    val currentJob = Cancellation.currentJob()
    val childJob = Job(currentJob)
    expired = cancelIfExpired(expired, childJob)
    command = CancellationRunnable(childJob, command)
  }
  return Pair.create(capturePropagationContext(command), expired)
}

private fun <T> cancelIfExpired(expiredCondition: Condition<in T>, childJob: Job): Condition<T> {
  return Condition { t: T ->
    val expired = expiredCondition.value(t)
    if (expired) {
      // Cancel to avoid a hanging child job which will prevent completion of the parent one.
      childJob.cancel(null)
      true
    }
    else {
      // Treat runnable as expired if its job was already cancelled.
      childJob.isCancelled
    }
  }
}

private fun capturePropagationContext(runnable: Runnable): Runnable {
  if (isPropagateThreadContext) {
    return captureThreadContext(runnable)
  }
  return runnable
}

internal fun <V> capturePropagationAndCancellationContext(callable: Callable<V>): FutureTask<V> {
  if (isPropagateCancellation) {
    val currentJob = Cancellation.currentJob()
    val childDeferred = CompletableDeferred<V>(currentJob)
    val cancellationCallable = CancellationCallable(childDeferred, callable)
    return CancellationFutureTask(childDeferred, capturePropagationContext(cancellationCallable))
  }
  return FutureTask(capturePropagationContext(callable))
}

internal fun <V> capturePropagationAndCancellationContext(
  wrapper: SchedulingWrapper,
  callable: Callable<V>,
  ns: Long,
): MyScheduledFutureTask<V> {
  if (isPropagateCancellation) {
    val currentJob = Cancellation.currentJob()
    val childDeferred = CompletableDeferred<V>(currentJob)
    val cancellationCallable = CancellationCallable(childDeferred, callable)
    return CancellationScheduledFutureTask(wrapper, childDeferred, capturePropagationContext(cancellationCallable), ns)
  }
  return wrapper.MyScheduledFutureTask(capturePropagationContext(callable), ns)
}

private fun <V> capturePropagationContext(callable: Callable<V>): Callable<V> {
  if (isPropagateThreadContext) {
    return ContextCallable(false, callable)
  }
  return callable
}

internal fun capturePropagationAndCancellationContext(
  wrapper: SchedulingWrapper,
  runnable: Runnable,
  ns: Long,
  period: Long,
): MyScheduledFutureTask<*> {
  if (isPropagateCancellation) {
    val currentJob = Cancellation.currentJob()
    val childJob = Job(currentJob)
    val cancellationRunnable = PeriodicCancellationRunnable(childJob, runnable)
    return CancellationScheduledFutureTask<Void>(wrapper, childJob, wrapWithPropagationContext(cancellationRunnable), ns, period)
  }
  return wrapper.MyScheduledFutureTask<Void>(wrapWithPropagationContext(runnable), null, ns, period)
}

private fun wrapWithPropagationContext(runnable: Runnable): Runnable {
  if (isPropagateThreadContext) {
    return ContextRunnable(false, runnable)
  }
  return runnable
}
