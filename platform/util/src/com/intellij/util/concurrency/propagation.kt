// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("Propagation")
@file:Internal
@file:Suppress("NAME_SHADOWING")

package com.intellij.util.concurrency

import com.intellij.concurrency.ContextAwareCallable
import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.SchedulingWrapper.MyScheduledFutureTask
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.function.BiConsumer
import java.util.function.Function
import kotlin.Any
import kotlin.Boolean
import kotlin.Long
import kotlin.OptIn
import kotlin.Pair
import kotlin.Suppress
import kotlin.Throwable
import kotlin.Unit
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import com.intellij.openapi.util.Pair as JBPair

private object Holder {
  var propagateThreadContext: Boolean = Registry.`is`("ide.propagate.context")
  var propagateThreadCancellation: Boolean = Registry.`is`("ide.propagate.cancellation")
  var checkIdeAssertion: Boolean = Registry.`is`("ide.check.context.assertion")
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

internal val isCheckContextAssertions: Boolean
  get() = Holder.checkIdeAssertion

@Internal
class BlockingJob(val blockingJob: CompletableJob) : AbstractCoroutineContextElement(BlockingJob) {
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
    val parentBlockingJob = currentThreadContext[BlockingJob]
    if (parentBlockingJob != null) {
      val parentJob = parentBlockingJob.blockingJob
      val childJob = Job(parent = parentJob)
      // the job from `BlockingJob` is `SupervisorJob`, so we need to handle incoming exceptions manually
      childJob.invokeOnCompletion {
        when (it) {
          null -> Unit
          is ProcessCanceledException -> Unit
          is CancellationException -> Unit
          // any other kind of exception is not related to control flow
          else -> parentJob.completeExceptionally(it)
        }
      }
      Pair(parentBlockingJob + childJob, childJob)
    }
    else {
      Pair(EmptyCoroutineContext, null)
    }
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

private fun isContextAwareComputation(runnable: Any) : Boolean {
  return runnable is Continuation<*> || runnable is ContextAwareRunnable || runnable is ContextAwareCallable<*>
}

/**
 * Runs [action] under [job], where any external cancellation of [job] is delayed until [action] is finished.
 *
 * Consider the following code
 * ```
 * blockingContextScope {
 *   executeOnPooledThread {
 *     doSomethingWithoutCheckingCancellationForOneHour()
 *   }
 *   throw NPE
 * }
 * ```
 * When `throw NPE` is reached, it is important to resume `blockingContextScope` when spawned `executeOnPooledThread` terminates exceptionally.
 * This is why we reuse coroutine algorithms to ensure proper cancellation in our structured concurrency framework.
 * In the case above, the lambda in `executeOnPooledThread` needs to be executed under `runAsCoroutine`
 */
@Internal
@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
fun <T> runAsCoroutine(job: CompletableJob, completeOnFinish: Boolean = true, action: () -> T): T {
  val deferred = GlobalScope.async(
    // we need to have job in CoroutineContext so that `Deferred` becomes its child and properly delays cancellation
    context = job,
    start = CoroutineStart.UNDISPATCHED) {
    action()
  }
  deferred.invokeOnCompletion {
    when (it) {
      null -> if (completeOnFinish) {
        job.complete()
      }
      // `deferred` is an integral part of `Job`, so manual cancellation within `action` should lead to the cancellation of `Job`
      is CancellationException -> job.cancel(it)
      // Regular exceptions and PCE get propagated to `job` via parent-child relations between Jobs
    }
  }
  return deferred.getCompleted()
}

/**
 * A runnable-friendly overload for usage in Java
 * @see runAsCoroutine
 */
fun runAsCoroutine(job: CompletableJob, r: Runnable): Unit = runAsCoroutine(job, action = r::run)

internal fun capturePropagationAndCancellationContext(command: Runnable): Runnable {
  if (isContextAwareComputation(command)) {
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
  if (isContextAwareComputation(command)) {
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

fun <T, U> captureBiConsumerThreadContext(f: BiConsumer<T, U>): BiConsumer<T, U> {
  val (childContext, childJob) = createChildContext()
  var f = f
  if (childContext != EmptyCoroutineContext) {
    f = ContextBiConsumer(false, childContext, f)
  }
  if (childJob != null) {
    f = CancellationBiConsumer(childJob, f)
  }
  return f
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

internal fun <T, R> capturePropagationAndCancellationContext(function: Function<T, R>): Function<T, R> {
  val (childContext, childJob) = createChildContext()
  var f = function
  if (childContext != EmptyCoroutineContext) {
    f = ContextFunction(childContext, f)
  }
  if (childJob != null) {
    f = CancellationFunction(childJob, f)
  }
  return f
}

internal fun <V> capturePropagationAndCancellationContext(
  wrapper: SchedulingWrapper,
  callable: Callable<V>,
  ns: Long,
): MyScheduledFutureTask<V> {
  if (isContextAwareComputation(callable)) {
    return wrapper.MyScheduledFutureTask(callable, ns)
  }
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

fun contextAwareCallable(r : Runnable) : Callable<*> = ContextAwareCallable {
  r.run()
}
