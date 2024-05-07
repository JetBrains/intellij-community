// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("Propagation")
@file:Internal
@file:Suppress("NAME_SHADOWING")

package com.intellij.util.concurrency

import com.intellij.concurrency.ContextAwareCallable
import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.concurrency.client.captureClientIdInBiConsumer
import com.intellij.concurrency.client.captureClientIdInCallable
import com.intellij.concurrency.client.captureClientIdInFunction
import com.intellij.concurrency.client.captureClientIdInRunnable
import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.progress.CeProcessCanceledException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Ref
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.SchedulingWrapper.MyScheduledFutureTask
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.function.BiConsumer
import java.util.function.Function
import kotlin.Pair
import kotlin.coroutines.*
import kotlin.coroutines.cancellation.CancellationException
import com.intellij.openapi.util.Pair as JBPair

private object Holder {
  // we need context propagation to be configurable
  // in order to disable it in RT modules
  var propagateThreadContext: Boolean = SystemProperties.getBooleanProperty("ide.propagate.context", true)
  var checkIdeAssertion: Boolean = SystemProperties.getBooleanProperty("ide.check.context.assertion", false)
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

internal val isPropagateThreadContext: Boolean
  get() = Holder.propagateThreadContext

internal val isCheckContextAssertions: Boolean
  get() = Holder.checkIdeAssertion

@Internal
class BlockingJob(val blockingJob: Job) : AbstractCoroutineContextElement(BlockingJob) {
  companion object : CoroutineContext.Key<BlockingJob>
}

@Internal
data class ChildContext internal constructor(
  val context: CoroutineContext,
  val continuation: Continuation<Unit>?,
) {

  val job: Job? get() = continuation?.context?.job

  fun runAsCoroutine(action: Runnable) {
    runAsCoroutine(completeOnFinish = true, action::run)
  }

  fun <T> runAsCoroutine(completeOnFinish: Boolean, action: () -> T): T {
    return if (continuation == null) {
      action()
    }
    else {
      runAsCoroutine(continuation, completeOnFinish, action)
    }
  }
}

@Internal
fun createChildContext() : ChildContext = doCreateChildContext(false)

@Internal
fun createChildContextWithContextJob() : ChildContext = doCreateChildContext(true)

/**
 * Use `unconditionalCancellationPropagation` only when you are sure that the current context will always outlive a child computation.
 * This is the case with `invokeAndWait`, as it parks the thread before computation is finished,
 * but it is not the case with `invokeLater`
 */
@Internal
private fun doCreateChildContext(unconditionalCancellationPropagation: Boolean): ChildContext {
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

  val parentBlockingJob =
    if (unconditionalCancellationPropagation) currentThreadContext[Job]
    else currentThreadContext[BlockingJob]?.blockingJob
  val (cancellationContext, childContinuation) = if (parentBlockingJob != null) {
    val continuation: Continuation<Unit> = childContinuation(parentBlockingJob)
    Pair((currentThreadContext[BlockingJob] ?: EmptyCoroutineContext) + continuation.context.job, continuation)
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
  return ChildContext(childContext + cancellationContext, childContinuation)
}

@OptIn(DelicateCoroutinesApi::class)
private fun childContinuation(parent: Job): Continuation<Unit> {
  lateinit var continuation: Continuation<Unit>
  GlobalScope.launch(
    parent + Dispatchers.Unconfined,
    start = CoroutineStart.UNDISPATCHED,
  ) {
    suspendCancellableCoroutine {
      continuation = it
    }
  }
  return continuation
}

internal fun captureRunnableThreadContext(command: Runnable): Runnable {
  return capturePropagationContext(command)
}

internal fun <V> captureCallableThreadContext(callable: Callable<V>): Callable<V> {
  val (childContext, childContinuation) = createChildContext()
  var callable = captureClientIdInCallable(callable)
  if (childContext != EmptyCoroutineContext) {
    callable = ContextCallable(true, childContext, callable)
  }
  if (childContinuation != null) {
    callable = CancellationCallable(childContinuation, callable)
  }
  return callable
}

private fun isContextAwareComputation(runnable: Any): Boolean {
  return runnable is Continuation<*> || runnable is ContextAwareRunnable || runnable is ContextAwareCallable<*> || runnable is CancellationFutureTask<*>
}

/**
 * Runs [action] in a separate child coroutine of [continuation] job to prevent transition
 * from `Cancelling` to `Cancelled` state immediately after the [continuation] job is cancelled.
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
 * When `throw NPE` is reached, it is important to not resume `blockingContextScope` until `executeOnPooledThread` is completed.
 * This is why we reuse coroutine algorithms to ensure proper cancellation in our structured concurrency framework.
 * In the case above, the lambda in `executeOnPooledThread` needs to be executed under `runAsCoroutine`.
 *
 * ## Exception guarantees
 * This function is intended to be executed in blocking context, hence it always emits [ProcessCanceledException]
 *
 * @param completeOnFinish whether to complete [continuation] on the computation finish. Most of the time, this is the desired default behavior.
 * However, sometimes in non-linear execution scenarios (such as NonBlockingReadAction), more precise control over the completion of a job is needed.
 */
@Internal
@Throws(ProcessCanceledException::class)
@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
fun <T> runAsCoroutine(continuation: Continuation<Unit>, completeOnFinish: Boolean, action: () -> T): T {
  // Even though catching and restoring PCE is unnecessary,
  // we still would like to have it thrown, as it indicates _where the canceled job was accessed_,
  // in addition to the original exception indicating _where the canceled job was canceled_
  val originalPCE: Ref<ProcessCanceledException> = Ref(null)
  val deferred = GlobalScope.async(
    // we need to have a job in CoroutineContext so that `Deferred` becomes its child and properly delays cancellation
    context = continuation.context,
    start = CoroutineStart.UNDISPATCHED) {
    try {
      action()
    }
    catch (e: ProcessCanceledException) {
      originalPCE.set(e)
      throw CancellationException("Masking ProcessCanceledException: ${e.message}", e)
    }
  }
  deferred.invokeOnCompletion {
    when (it) {
      null -> if (completeOnFinish) {
        continuation.resume(Unit)
      }
      // `deferred` is an integral part of `job`, so manual cancellation within `action` should lead to the cancellation of `job`
      is CancellationException ->
        // We have scheduled periodic runnables, which use `runAsCoroutine` several times on the same `Continuation`.
        // When the context `Job` gets canceled and a corresponding `SchedulingExecutorService` does not,
        // we appear in a situation where the `SchedulingExecutorService` still launches its tasks with a canceled `Continuation`
        //
        // Multiple resumption of a single continuation is disallowed; hence, we need to prevent this situation
        // by avoiding resumption in the case of a dead coroutine scope
        if (!continuation.context.job.isCompleted) {
          continuation.resumeWithException(it)
        }
      // Regular exceptions get propagated to `job` via parent-child relations between Jobs
    }
  }
  originalPCE.get()?.let { throw it }
  try {
    return deferred.getCompleted()
  } catch (ce : CancellationException) {
    throw CeProcessCanceledException(ce)
  }
}

internal fun capturePropagationContext(r: Runnable, forceUseContextJob : Boolean = false): Runnable {
  var command = captureClientIdInRunnable(r)
  if (isContextAwareComputation(r)) {
    return command
  }
  val (childContext, childContinuation) =
    if (forceUseContextJob) createChildContextWithContextJob()
    else createChildContext()
  if (childContext != EmptyCoroutineContext) {
    command = ContextRunnable(true, childContext, command)
  }
  if (childContinuation != null) {
    command = CancellationRunnable(childContinuation, command)
  }
  return command
}

fun capturePropagationContext(r: Runnable, expired: Condition<*>): JBPair<Runnable, Condition<*>> {
  var command = captureClientIdInRunnable(r)
  if (isContextAwareComputation(r)) {
    return JBPair.create(command, expired)
  }
  val (childContext, childContinuation) = createChildContext()
  var expired = expired
  if (childContext != EmptyCoroutineContext) {
    command = ContextRunnable(true, childContext, command)
  }
  if (childContinuation != null) {
    command = CancellationRunnable(childContinuation, command)
    val childJob = childContinuation.context.job
    expired = cancelIfExpired(expired, childJob)
  }
  return JBPair.create(command, expired)
}

fun <T, U> captureBiConsumerThreadContext(f: BiConsumer<T, U>): BiConsumer<T, U> {
  val (childContext, childContinuation) = createChildContext()
  var f = captureClientIdInBiConsumer(f)
  if (childContext != EmptyCoroutineContext) {
    f = ContextBiConsumer(false, childContext, f)
  }
  if (childContinuation != null) {
    f = CancellationBiConsumer(childContinuation, f)
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

internal fun <V> capturePropagationContext(c: Callable<V>): FutureTask<V> {
  var callable = captureClientIdInCallable(c)
  if (isContextAwareComputation(c)) {
    return FutureTask(callable)
  }
  val (childContext, childContinuation) = createChildContext()
  if (childContext != EmptyCoroutineContext) {
    callable = ContextCallable(false, childContext, callable)
  }
  if (childContinuation != null) {
    callable = CancellationCallable(childContinuation, callable)
    val childJob = childContinuation.context.job
    return CancellationFutureTask(childJob, callable)
  }
  else {
    return FutureTask(callable)
  }
}

internal fun <T, R> capturePropagationContext(function: Function<T, R>): Function<T, R> {
  val (childContext, childContinuation) = createChildContext()
  var f = captureClientIdInFunction(function)
  if (childContext != EmptyCoroutineContext) {
    f = ContextFunction(childContext, f)
  }
  if (childContinuation != null) {
    f = CancellationFunction(childContinuation, f)
  }
  return f
}

internal fun <V> capturePropagationContext(wrapper: SchedulingWrapper, c: Callable<V>, ns: Long): MyScheduledFutureTask<V> {
  var callable = captureClientIdInCallable(c)
  if (isContextAwareComputation(c)) {
    return wrapper.MyScheduledFutureTask(callable, ns)
  }
  val (childContext, childContinuation) = createChildContext()
  if (childContext != EmptyCoroutineContext) {
    callable = ContextCallable(false, childContext, callable)
  }
  if (childContinuation != null) {
    callable = CancellationCallable(childContinuation, callable)
    val childJob = childContinuation.context.job
    return CancellationScheduledFutureTask(wrapper, childJob, callable, ns)
  }
  else {
    return wrapper.MyScheduledFutureTask(callable, ns)
  }
}

internal fun capturePropagationContext(
  wrapper: SchedulingWrapper,
  runnable: Runnable,
  ns: Long,
  period: Long,
): MyScheduledFutureTask<*> {
  val (childContext, childContinuation) = createChildContext()
  var runnable = captureClientIdInRunnable(runnable)
  if (childContext != EmptyCoroutineContext) {
    runnable = ContextRunnable(false, childContext, runnable)
  }
  if (childContinuation != null) {
    runnable = PeriodicCancellationRunnable(childContinuation, runnable)
    val childJob = childContinuation.context.job
    return CancellationScheduledFutureTask<Void>(wrapper, childJob, runnable, ns, period)
  }
  else {
    return wrapper.MyScheduledFutureTask<Void>(runnable, null, ns, period)
  }
}

fun contextAwareCallable(r: Runnable): Callable<*> = ContextAwareCallable {
  r.run()
}
