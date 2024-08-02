// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("Propagation")
@file:Internal
@file:Suppress("NAME_SHADOWING", "OPT_IN_USAGE")

package com.intellij.util.concurrency

import com.intellij.concurrency.*
import com.intellij.concurrency.client.captureClientIdInBiConsumer
import com.intellij.concurrency.client.captureClientIdInCallable
import com.intellij.concurrency.client.captureClientIdInFunction
import com.intellij.concurrency.client.captureClientIdInRunnable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.CeProcessCanceledException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Ref
import com.intellij.util.SmartList
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.SchedulingWrapper.MyScheduledFutureTask
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.function.BiConsumer
import java.util.function.Function
import kotlin.Pair
import kotlin.coroutines.*
import kotlin.coroutines.cancellation.CancellationException
import com.intellij.openapi.util.Pair as JBPair

private val LOG = Logger.getInstance("#com.intellij.concurrency")

private object Holder {
  // we need context propagation to be configurable
  // in order to disable it in RT modules
  var propagateThreadContext: Boolean = SystemProperties.getBooleanProperty("ide.propagate.context", true)
  val checkIdeAssertion: Boolean = SystemProperties.getBooleanProperty("ide.check.context.assertion", false)

  var useImplicitBlockingContext: Boolean = SystemProperties.getBooleanProperty("ide.enable.implicit.blocking.context", true)
}

@TestOnly
@ApiStatus.Internal
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
@ApiStatus.Internal
fun runWithImplicitBlockingContextEnabled(runnable: Runnable) {
  val propagateThreadContext = Holder.useImplicitBlockingContext
  Holder.useImplicitBlockingContext = true
  try {
    runnable.run()
  }
  finally {
    Holder.useImplicitBlockingContext = propagateThreadContext
  }
}

internal val isPropagateThreadContext: Boolean
  get() = Holder.propagateThreadContext

internal val isCheckContextAssertions: Boolean
  get() = Holder.checkIdeAssertion

internal val useImplicitBlockingContext: Boolean
  get() = Holder.useImplicitBlockingContext

@Internal
class BlockingJob(val blockingJob: Job) : AbstractCoroutineContextElement(BlockingJob), IntelliJContextElement {

  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

  companion object : CoroutineContext.Key<BlockingJob>
}

@OptIn(DelicateCoroutinesApi::class)
@Internal
data class ChildContext internal constructor(
  val context: CoroutineContext,
  val continuation: Continuation<Unit>?,
  val ijElements: List<IntelliJContextElement>,
) {

  val job: Job? get() = continuation?.context?.job

  fun runInChildContext(action: Runnable) {
    runInChildContext(completeOnFinish = true, action::run)
  }

  fun <T> runInChildContext(completeOnFinish: Boolean, action: () -> T): T {
    return if (continuation == null) {
      applyContextActions().use {
        action()
      }
    }
    else {
      runAsCoroutine(continuation, completeOnFinish) { applyContextActions().use { action() } }
    }
  }

  @DelicateCoroutinesApi
  fun applyContextActions(installThreadContext: Boolean = true): AccessToken {
    val installToken = if (installThreadContext) {
      installThreadContext(context, replace = false)
    }
    else {
      AccessToken.EMPTY_ACCESS_TOKEN
    }
    return object : AccessToken() {
      override fun finish() {
        installToken.finish()
        for (elem in ijElements.reversed()) {
          elem.afterChildCompleted(context)
        }
      }
    }
  }
}

@Internal
fun createChildContext(debugName: @NonNls String) : ChildContext = doCreateChildContext(debugName, false)

@Internal
fun createChildContextWithContextJob(debugName: @NonNls String) : ChildContext = doCreateChildContext(debugName, true)

/**
 * Use `unconditionalCancellationPropagation` only when you are sure that the current context will always outlive a child computation.
 * This is the case with `invokeAndWait`, as it parks the thread before computation is finished,
 * but it is not the case with `invokeLater`
 */
@Internal
private fun doCreateChildContext(debugName: @NonNls String, unconditionalCancellationPropagation: Boolean): ChildContext {
  val currentThreadContext = currentThreadContext()
  val isStructured = unconditionalCancellationPropagation || currentThreadContext[BlockingJob] != null

  val (childContext, ijElements) = gatherAppliedChildContext(currentThreadContext, isStructured)

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
    val continuation: Continuation<Unit> = childContinuation(debugName, parentBlockingJob)
    Pair((currentThreadContext[BlockingJob] ?: EmptyCoroutineContext) + continuation.context.job, continuation)
  }
  else {
    Pair(EmptyCoroutineContext, null)
  }

  return ChildContext(childContext.minusKey(Job) + cancellationContext, childContinuation, ijElements)
}

private fun gatherAppliedChildContext(parentContext: CoroutineContext, isStructured: Boolean): Pair<CoroutineContext, List<IntelliJContextElement>> {
  val ijElements = SmartList<IntelliJContextElement>()
  val newContext = parentContext.fold<CoroutineContext>(EmptyCoroutineContext) { old, elem ->
    old + produceChildContextElement(parentContext, elem, isStructured, ijElements)
  }
  return Pair(newContext, ijElements)
}

private fun produceChildContextElement(parentContext: CoroutineContext, element: CoroutineContext.Element, isStructured: Boolean, ijElements: MutableList<IntelliJContextElement>): CoroutineContext {
  return when {
    element is IntelliJContextElement -> {
      val forked = element.produceChildElement(parentContext, isStructured)
      if (forked != null) {
        ijElements.add(forked)
        forked
      }
      else {
        EmptyCoroutineContext
      }
    }
    isStructured -> element
    else -> {
      EmptyCoroutineContext
    }
  }
}

@OptIn(DelicateCoroutinesApi::class)
private fun childContinuation(debugName: @NonNls String, parent: Job): Continuation<Unit> {
  if (parent.isCompleted) {
    LOG.warn("Attempt to create a child continuation for an already completed job", Throwable())
  }
  lateinit var continuation: Continuation<Unit>
  GlobalScope.launch(
    parent + CoroutineName("IJ Structured concurrency: $debugName") + Dispatchers.Unconfined,
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
  val childContext = createChildContext(callable.toString())
  var callable = captureClientIdInCallable(callable)
  callable = ContextCallable(true, childContext, callable)
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
 * ## Impact on thread context
 * This function is often used in combination with [ContextRunnable].
 * It is important that [runAsCoroutine] runs _on top of_ [ContextRunnable], as [async] in this function exposes the scope of [GlobalScope].
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
  val childContext =
    if (forceUseContextJob) createChildContextWithContextJob(r.toString())
    //TODO: do we really need .toString() here? It allocates ~6 Gb during indexing
    else createChildContext(r.toString())
  command = ContextRunnable(childContext, command)
  return command
}

@ApiStatus.Internal
fun capturePropagationContext(r: Runnable, expired: Condition<*>): JBPair<Runnable, Condition<*>> {
  var command = captureClientIdInRunnable(r)
  if (isContextAwareComputation(r)) {
    return JBPair.create(command, expired)
  }
  val childContext = createChildContext(r.toString())
  var expired = expired
  command = ContextRunnable(childContext, command)
  val cont = childContext.continuation
  if (cont != null) {
    val childJob = cont.context.job
    expired = cancelIfExpired(expired, childJob)
  }
  return JBPair.create(command, expired)
}

@ApiStatus.Internal
fun <T, U> captureBiConsumerThreadContext(f: BiConsumer<T, U>): BiConsumer<T, U> {
  val childContext = createChildContext(f.toString())
  var f = captureClientIdInBiConsumer(f)
  f = ContextBiConsumer(childContext, f)
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
  val callable = captureClientIdInCallable(c)
  if (isContextAwareComputation(c)) {
    return FutureTask(callable)
  }
  val childContext = createChildContext(c.toString())
  val wrappedCallable = ContextCallable(false, childContext, callable)
  val cont = childContext.continuation
  if (cont != null) {
    val childJob = cont.context.job
    return CancellationFutureTask(childJob, wrappedCallable)
  }
  else {
    return FutureTask(wrappedCallable)
  }
}

internal fun <T, R> capturePropagationContext(function: Function<T, R>): Function<T, R> {
  val childContext = createChildContext(function.toString())
  var f = captureClientIdInFunction(function)
  f = ContextFunction(childContext, f)
  return f
}

internal fun <V> capturePropagationContext(wrapper: SchedulingWrapper, c: Callable<V>, ns: Long): MyScheduledFutureTask<V> {
  val callable = captureClientIdInCallable(c)
  if (isContextAwareComputation(c)) {
    return wrapper.MyScheduledFutureTask(callable, ns)
  }
  val childContext = createChildContext("$c (scheduled: $ns)")
  val wrappedCallable = ContextCallable(false, childContext, callable)

  val cont = childContext.continuation
  if (cont != null) {
    val childJob = cont.context.job
    return CancellationScheduledFutureTask(wrapper, childJob, wrappedCallable, ns)
  }
  else {
    return wrapper.MyScheduledFutureTask(wrappedCallable, ns)
  }
}

internal fun capturePropagationContext(
  wrapper: SchedulingWrapper,
  runnable: Runnable,
  ns: Long,
  period: Long,
): MyScheduledFutureTask<*> {
  val childContext = createChildContext("$runnable (scheduled: $ns, period: $period)")
  val capturedRunnable1 = captureClientIdInRunnable(runnable)
  val capturedRunnable2 = Runnable {
    installThreadContext(childContext.context, false).use {
      childContext.applyContextActions(false).use {
        capturedRunnable1.run()
      }
    }
  }
  val cont = childContext.continuation
  if (cont != null) {
    val capturedRunnable3 = PeriodicCancellationRunnable(childContext.continuation, capturedRunnable2)
    val childJob = cont.context.job
    return CancellationScheduledFutureTask<Void>(wrapper, childJob, capturedRunnable3, ns, period)
  }
  else {
    return wrapper.MyScheduledFutureTask<Void>(capturedRunnable2, null, ns, period)
  }
}

@ApiStatus.Internal
fun contextAwareCallable(r: Runnable): Callable<*> = ContextAwareCallable {
  r.run()
}
