// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VirtualThreads")
@file:ApiStatus.Experimental

package com.intellij.concurrency.virtualThreads

import com.intellij.concurrency.installThreadContext
import com.intellij.util.concurrency.createChildContextWithContextJob
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ThreadFactory
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
 * [action] receives a [ThreadFactory] as an argument, which can be used to create new threads. The spawned threads get canceled whenever the job of the [receiving scope][this] is canceled.
 *
 * This function can be useful when one passes control to other's code, for example, when running plugin listeners.
 * These listeners may be written in java and hence block; to avoid CPU underutilization, one can wrap external code in a virtual thread.
 *
 * @param start whether the coroutine should start immediately or not. [CoroutineStart.UNDISPATCHED] is unsupported.
 * @throws IllegalArgumentException if [start] is [CoroutineStart.UNDISPATCHED]
 * @return a [Deferred] with the value of [action] which completes when [action] and all threads spawned by the received [ThreadFactory] are terminated.
 */
@OptIn(InternalCoroutinesApi::class)
fun <T> CoroutineScope.asyncAsVirtualThread(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: (ThreadFactory) -> T,
): Deferred<T> {
  if (start == CoroutineStart.UNDISPATCHED) {
    throw IllegalArgumentException("CoroutineStart.UNDISPATCHED is unsupported for virtual threads")
  }
  return async(start = start, context = Dispatchers.Default) {
    val currentJob = coroutineContext.job
    // we do not use suspendCancellableCoroutine here by design -- we do not need to trigger prompt cancellation of the outer `async`,
    // because the virtual thread might still be running
    @Suppress("SSBasedInspection")
    suspendCoroutine { externalContinuation ->
      val newContext = (externalContinuation.context + context).minusKey(ContinuationInterceptor.Key)

      val name = newContext[CoroutineName]?.name
      val coroutineName = "Virtual thread" + if (name != null) ": $name" else ""

      val factory = ThreadFactory { r ->
        val innerChildContext = createChildContextWithContextJob(coroutineName)
        val thread = virtualThread(start = false, name = name, contextClassLoader = null) {
          try {
            innerChildContext.runInChildContext {
              try {
                installThreadContext(newContext, true) {
                  r.run()
                }
              } catch (e : InterruptedException) {
                throw CancellationException("The virtual thread was interrupted", e)
              }
            }
          } catch (_: Throwable) {
            // the exception already got processed by the context job
            // if we throw it from here, it will get to Thread.UncaughtExceptionHandler
            // since we are providing a bridge from coroutines, we don't want to integrate with java
          }
        }

        val job = requireNotNull(innerChildContext.job)
        job.invokeOnCompletion(true) {
          thread.interrupt()
        }

        thread
      }

      val thread = virtualThread(start = true, name = name, contextClassLoader = null) {
        installThreadContext(newContext, true) {
          try {
            val actionResult = action(factory)
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
  action: (ThreadFactory) -> Unit,
): Job {
  return asyncAsVirtualThread(context, start, action)
}
