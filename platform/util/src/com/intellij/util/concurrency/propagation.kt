// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("Propagation")
@file:Internal
@file:Suppress("NAME_SHADOWING")

package com.intellij.util.concurrency

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.SchedulingWrapper.MyScheduledFutureTask
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import com.intellij.openapi.util.Pair as JBPair

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

@Internal
class BlockingJob(val blockingJob: Job) : AbstractCoroutineContextElement(BlockingJob) {
  companion object : CoroutineContext.Key<BlockingJob>
}

@Internal
fun createChildContext(): Pair<CoroutineContext, CompletableJob?> {
  val currentThreadContext = currentThreadContext()

  // Problem: a task may infinitely reschedule itself
  //   => each re-scheduling adds a child Job and completes the current Job
  //   => the current Job cannot become completed because it has a newly added child
  //   => Job chain grows indefinitely.
  //
  // How it's handled:
  // - initially, the current job is only present in the context.
  // - the current job is installed to the context by `blockingContext`.
  // - a new child job is created, it becomes current inside scheduled task.
  // - initial current job is saved as BlockingJob into child context.
  // - BlockingJob is used to attach children.
  //
  // Effectively, the chain becomes a 1-level tree,
  // as jobs of all scheduled tasks are attached to the initial current Job.
  val (cancellationContext, childJob) = if (isPropagateCancellation) {
    val parentJob = currentThreadContext[BlockingJob]?.blockingJob
                    ?: currentThreadContext[Job]
    // Put BlockingJob into context so that the child of the child context would see and use it.
    val parentJobContext = parentJob?.let(::BlockingJob) ?: EmptyCoroutineContext
    val childJob = Job(parent = parentJob)
    Pair(parentJobContext + childJob, childJob)
  }
  else {
    Pair(EmptyCoroutineContext, null)
  }
  val childContext = if (isPropagateThreadContext) {
    currentThreadContext.minusKey(Job)
  }
  else {
    EmptyCoroutineContext
  }
  return Pair(childContext + cancellationContext, childJob)
}

internal fun captureRunnableThreadContext(command: Runnable): Runnable {
  return capturePropagationAndCancellationContext(command)
}

internal fun <V> captureCallableThreadContext(callable: Callable<V>): Callable<V> {
  val (childContext, childJob) = createChildContext()
  var callable = callable
  if (childContext != EmptyCoroutineContext) {
    callable = ContextCallable(true, childContext, callable)
  }
  if (childJob != null) {
    callable = CancellationCallable(childJob, callable)
  }
  return callable
}

private fun isContextAwareRunnable(runnable: Runnable) : Boolean {
  return runnable is Continuation<*> || runnable is ContextAwareRunnable
}

internal fun capturePropagationAndCancellationContext(command: Runnable): Runnable {
  if (isContextAwareRunnable(command)) {
    return command
  }
  val (childContext, childJob) = createChildContext()
  var command = command
  if (childContext != EmptyCoroutineContext) {
    command = ContextRunnable(true, childContext, command)
  }
  if (childJob != null) {
    command = CancellationRunnable(childJob, command)
  }
  return command
}

fun capturePropagationAndCancellationContext(
  command: Runnable,
  expired: Condition<*>,
): JBPair<Runnable, Condition<*>> {
  if (isContextAwareRunnable(command)) {
    return JBPair.create(command, expired)
  }
  val (childContext, childJob) = createChildContext()
  var command = command
  var expired = expired
  if (childContext != EmptyCoroutineContext) {
    command = ContextRunnable(true, childContext, command)
  }
  if (childJob != null) {
    command = CancellationRunnable(childJob, command)
    expired = cancelIfExpired(expired, childJob)
  }
  return JBPair.create(command, expired)
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

internal fun <V> capturePropagationAndCancellationContext(callable: Callable<V>): FutureTask<V> {
  val (childContext, childJob) = createChildContext()
  var callable = callable
  if (childContext != EmptyCoroutineContext) {
    callable = ContextCallable(false, childContext, callable)
  }
  if (childJob != null) {
    return CancellationFutureTask(childJob, CancellationCallable(childJob, callable))
  }
  else {
    return FutureTask(callable)
  }
}

internal fun <V> capturePropagationAndCancellationContext(
  wrapper: SchedulingWrapper,
  callable: Callable<V>,
  ns: Long,
): MyScheduledFutureTask<V> {
  val (childContext, childJob) = createChildContext()
  var callable = callable
  if (childContext != EmptyCoroutineContext) {
    callable = ContextCallable(false, childContext, callable)
  }
  if (childJob != null) {
    return CancellationScheduledFutureTask(wrapper, childJob,
                                           CancellationCallable(childJob, callable), ns)
  }
  else {
    return wrapper.MyScheduledFutureTask(callable, ns)
  }
}

internal fun capturePropagationAndCancellationContext(
  wrapper: SchedulingWrapper,
  runnable: Runnable,
  ns: Long,
  period: Long,
): MyScheduledFutureTask<*> {
  val (childContext, childJob) = createChildContext()
  var runnable = runnable
  if (childContext != EmptyCoroutineContext) {
    runnable = ContextRunnable(false, childContext, runnable)
  }
  if (childJob != null) {
    runnable = PeriodicCancellationRunnable(childJob, runnable)
    return CancellationScheduledFutureTask<Void>(wrapper, childJob, runnable, ns, period)
  }
  else {
    return wrapper.MyScheduledFutureTask<Void>(runnable, null, ns, period)
  }
}
