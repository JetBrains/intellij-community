// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VirtualThreads")
@file:ApiStatus.Experimental

package com.intellij.concurrency.virtualThreads

import com.intellij.concurrency.installThreadContext
import com.intellij.diagnostic.recordVirtualThreadForCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Creates a new [virtual thread][Thread.Builder.OfVirtual] that runs the specified [block] of code.
 *
 * This function is opposed to [kotlin.concurrent.thread], which creates a new *platform* thread
 */
fun virtualThread(
  start: Boolean = true,
  name: String? = null,
  contextClassLoader: ClassLoader? = null,
  block: () -> Unit,
): Thread {
  val thread = IntelliJVirtualThreads.ofVirtual().apply {
    if (name != null) {
      name(name)
    }
  }.unstarted(block)
  if (contextClassLoader != null) {
    thread.contextClassLoader = contextClassLoader
  }
  return if (start) {
    thread.apply { start() }
  }
  else {
    thread
  }
}

/**
 * Executes [action] in a virtual thread on top of [Dispatchers.Default].
 * The coroutine dispatcher is forcefully overridden, so any attempt to specify the dispatcher in the [receiving scope][this] or [context] would have no effect.
 * [action] gets canceled whenever the job of the [receiving scope][this] is canceled.
 *
 * The [thread context][com.intellij.concurrency.currentThreadContext] inside action corresponds to [context] added to the context of the [receiving scope][this].
 *
 * This function can be useful when one passes control to other's code, for example, when running plugin listeners.
 * These listeners may be written in java and hence block; to avoid CPU underutilization, one can wrap external code in a virtual thread.
 *
 * @param start whether the coroutine should start immediately or not. [CoroutineStart.UNDISPATCHED] is unsupported.
 * @throws IllegalArgumentException if [start] is [CoroutineStart.UNDISPATCHED]
 * @return a [Deferred] with the value of [action] which completes when [action] is terminated.
 */
@OptIn(InternalCoroutinesApi::class)
fun <T> CoroutineScope.asyncAsVirtualThread(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: () -> T,
): Deferred<T> {
  if (start == CoroutineStart.UNDISPATCHED) {
    throw IllegalArgumentException("CoroutineStart.UNDISPATCHED is unsupported for virtual threads")
  }
  return async(start = start, context = Dispatchers.Default) {
    val currentJob = coroutineContext.job
    // we do not use suspendCancellableCoroutine here by design -- we do not need to trigger prompt cancellation of the outer `async`,
    // because the virtual thread might still be running
    @Suppress("SuspendCoroutineLacksCancellationGuarantees")
    suspendCoroutine { externalContinuation ->
      val newContext = (externalContinuation.context + context).minusKey(ContinuationInterceptor.Key)

      val name = newContext[CoroutineName]?.name
      val coroutineName = "Virtual thread" + if (name != null) ": $name" else ""

      val thread = virtualThread(start = true, name = coroutineName, contextClassLoader = null) {
        installThreadContext(newContext, true) {
          try {
            val actionResult = action()
            externalContinuation.resume(actionResult)
          }
          catch (e: Throwable) {
            if (e is InterruptedException) {
              externalContinuation.resumeWithException(CancellationException("The virtual thread was interrupted", e))
            } else {
              externalContinuation.resumeWithException(e)
            }
          }
        }
      }

      // exposing this mapping for better coroutine dumps
      recordVirtualThreadForCoroutine(currentJob, thread)

      currentJob.invokeOnCompletion(onCancelling = true) {
        thread.interrupt()
      }
    }
  }
}

/**
 * Similar to [asyncAsVirtualThread], but launches the coroutine without providing a result.
 * [launchAsVirtualThread] differs from [asyncAsVirtualThread] similarly how [launch] differs from [async].
 */
fun CoroutineScope.launchAsVirtualThread(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: () -> Unit,
): Job {
  return asyncAsVirtualThread(context, start, action)
}

/**
 * Executes [action] in a virtual thread on top of [Dispatchers.Default].
 *
 * If [action] gets blocked on a [java.util.concurrent.locks.LockSupport.park],
 * the virtual thread will be unmounted and the underlying platform thread will be reused. It is helpful to avoid thread starvation and CPU underutilization.
 *
 * Even if this function runs on a [Dispatchers.Default] dispatcher, there will be a forceful redispatch because a virtual threads needs to be created.
 */
suspend fun <T> inVirtualThread(action: () -> T): T {
  return coroutineScope {
    asyncAsVirtualThread(action = action).await()
  }
}
