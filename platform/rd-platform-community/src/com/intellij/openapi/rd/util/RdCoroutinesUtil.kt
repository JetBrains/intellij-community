// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.openapi.rd.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.framework.util.withContext
import com.jetbrains.rd.util.threading.coroutines.async
import com.jetbrains.rd.util.threading.coroutines.launch
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val applicationThreadPool: CoroutineDispatcher
  get() = Dispatchers.IO
private val processIODispatcher: ExecutorCoroutineDispatcher
  get() = RdCoroutineHost.processIODispatcher

private val nonUrgentDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2)

private val uiDispatcher: CoroutineContext
  get() = RdCoroutineHost.instance.uiDispatcher
private val uiDispatcherWithInlining: CoroutineDispatcher
  get() = RdCoroutineHost.instance.uiDispatcherWithInlining
private val uiDispatcherAnyModality: CoroutineContext
  get() = RdCoroutineHost.instance.uiDispatcherAnyModality

/**
 * Use coroutineScope.launch(Dispatchers.EDT) or coroutineScope.launch(Dispatchers.EDT + modalityState.asContextElement())
 *
 * **Deprecated:** This method is deprecated as it launches coroutines directly tied to a [Lifetime],
 * which is equivalent to launching them from an application-level scope and cancelling them when the Lifetime is terminated.
 *
 * For project/client/plugin level scopes, it is recommended to obtain a [CoroutineScope] from the appropriate service.
 * This ensures coroutines are automatically cancelled and awaited when application/project is closed,
 * this scope also have included specific context elements like [com.intellij.codeWithMe.ClientId], [com.intellij.openapi.components.ComponentManager] for accessing project/client level services
 * through `com.intellij.serviceContainer.ContextKt#instance`.
 *
 * **Memory Leak Warning:** It is not recommended to manually manage coroutine cancellation with `lifetime.onTermination { job.cancel() }`
 * because if the lifetime significantly outlives the coroutine task, it can lead to memory leaks. This pattern causes the `Lifetime`
 * to retain references to coroutine jobs until termination. Over time, especially in long-lived scopes, this can accumulate and exhaust
 * memory resources.
 *
 * **Recommended Alternative:**
 * If you need to cancel a coroutine when a Lifetime is terminated, consider using [lifetimedCoroutineScope].
 * For launching coroutines at project/client/plugin level, obtain a coroutine scope from the appropriate service:
 * ```
 * val serviceScope = // obtain application/project/client/plugin level CoroutineScope
 * serviceScope.launch(Dispatchers.EDT) {
 *  lifetimedCoroutineScope(lifetime) {
 *      // your coroutine code here
 *   }
 * }
 * ```
 *
 * @deprecated Use application/project/client/plugin level [CoroutineScope] and [lifetimedCoroutineScope].
 */
@Deprecated("Use application/project/client/plugin level CoroutineScope. See KDoc for details.")
fun Lifetime.launchOnUi(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job {
  return launch(uiDispatcher + ModalityState.defaultModalityState().asContextElement() + context, start, action)
}

/**
 * Use coroutineScope.launch(Dispatchers.EDT) or coroutineScope.launch(Dispatchers.EDT + ModalityState.nonModal().asContextElement())
 *
 * **Deprecated:** This method is deprecated as it launches coroutines directly tied to a [Lifetime],
 * which is equivalent to launching them from an application-level scope and cancelling them when the Lifetime is terminated.
 *
 * For project/client/plugin level scopes, it is recommended to obtain a [CoroutineScope] from the appropriate service.
 * This ensures coroutines are automatically cancelled and awaited when application/project is closed,
 * this scope also have included specific context elements like [com.intellij.codeWithMe.ClientId], [com.intellij.openapi.components.ComponentManager] for accessing project/client level services
 * through `com.intellij.serviceContainer.ContextKt#instance`.
 *
 * **Memory Leak Warning:** It is not recommended to manually manage coroutine cancellation with `lifetime.onTermination { job.cancel() }`
 * because if the lifetime significantly outlives the coroutine task, it can lead to memory leaks. This pattern causes the `Lifetime`
 * to retain references to coroutine jobs until termination. Over time, especially in long-lived scopes, this can accumulate and exhaust
 * memory resources.
 *
 * **Recommended Alternative:**
 * If you need to cancel a coroutine when a Lifetime is terminated, consider using [lifetimedCoroutineScope].
 * For launching coroutines at project/client/plugin level, obtain a coroutine scope from the appropriate service:
 * ```
 * val serviceScope = // obtain application/project/client/plugin level CoroutineScope
 * serviceScope.launch {
 *  lifetimedCoroutineScope(lifetime) {
 *      // your coroutine code here
 *   }
 * }
 * ```
 *
 * @deprecated Use application/project/client/plugin level [CoroutineScope] and [lifetimedCoroutineScope].
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use application/project/client/plugin level CoroutineScope. See KDoc for details.")
fun Lifetime.launchOnUiNonModal(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(uiDispatcher + context, start, action)

@Deprecated("Do not use this method")
fun Lifetime.launchOnUiAllowInlining(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(uiDispatcherWithInlining + context, start, action)

@Deprecated("Please use launch with an explicit modality statement")
fun Lifetime.launchOnUiAnyModality(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job {
  if (ApplicationManager.getApplication().isDispatchThread && start == CoroutineStart.DEFAULT)
    // for backward compatibility
    return launch(uiDispatcherAnyModality + context, CoroutineStart.UNDISPATCHED, action)

  return launch(uiDispatcherAnyModality + context, start, action)
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use launchSyncIOBackground or launchBackground")
fun Lifetime.launchIOBackground(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(processIODispatcher + context, start, action)

/**
 * Use coroutineScope.launch(Dispatchers.Default) for CPU-bound tasks or coroutineScope.launch(Dispatchers.IO) for IO-bound tasks
 *
 * **Deprecated:** This method is deprecated as it launches coroutines directly tied to a [Lifetime],
 * which is equivalent to launching them from an application-level scope and cancelling them when the Lifetime is terminated.
 *
 * For project/client/plugin level scopes, it is recommended to obtain a [CoroutineScope] from the appropriate service.
 * This ensures coroutines are automatically cancelled and awaited when application/project is closed,
 * this scope also have included specific context elements like [com.intellij.codeWithMe.ClientId], [com.intellij.openapi.components.ComponentManager] for accessing project/client level services
 * through `com.intellij.serviceContainer.ContextKt#instance`.
 *
 * **Memory Leak Warning:** It is not recommended to manually manage coroutine cancellation with `lifetime.onTermination { job.cancel() }`
 * because if the lifetime significantly outlives the coroutine task, it can lead to memory leaks. This pattern causes the `Lifetime`
 * to retain references to coroutine jobs until termination. Over time, especially in long-lived scopes, this can accumulate and exhaust
 * memory resources.
 *
 * **Recommended Alternative:**
 * If you need to cancel a coroutine when a Lifetime is terminated, consider using [lifetimedCoroutineScope].
 * For launching coroutines at project/client/plugin level, obtain a coroutine scope from the appropriate service:
 * ```
 * val serviceScope = // obtain application/project/client/plugin level CoroutineScope
 * serviceScope.launch(Dispatchers.Default) {
 *  lifetimedCoroutineScope(lifetime) {
 *      // your coroutine code here
 *   }
 * }
 * ```
 *
 * @deprecated Use application/project/client/plugin level [CoroutineScope] and [lifetimedCoroutineScope].
 */
@Deprecated("Use application/project/client/plugin level CoroutineScope. See KDoc for details.")
fun Lifetime.launchBackground(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(applicationThreadPool + ModalityState.defaultModalityState().asContextElement() + context, start, action)

@ApiStatus.ScheduledForRemoval
@Deprecated("Use launch with a specific IO dispatcher for your purposes.")
fun Lifetime.launchSyncIOBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: () -> Unit
): Job = launch(processIODispatcher, start) { action() }

@Deprecated("Use launchBackground", ReplaceWith("launchBackground(start, action)"))
fun Lifetime.launchLongBackground(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
) = launchBackground(context, start, action)

@Deprecated("Use launch with a specific dispatcher for your purposes")
fun Lifetime.launchNonUrgentBackground(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(nonUrgentDispatcher + context, start, action)

/**
 * Use coroutineScope.async(Dispatchers.EDT) or coroutineScope.async(Dispatchers.EDT + modalityState.asContextElement)
 *
 * **Deprecated:** This method is deprecated as it launches coroutines directly tied to a [Lifetime],
 * which is equivalent to launching them from an application-level scope and cancelling them when the Lifetime is terminated.
 *
 * For project/client/plugin level scopes, it is recommended to obtain a [CoroutineScope] from the appropriate service.
 * This ensures coroutines are automatically cancelled and awaited when application/project is closed,
 * this scope also have included specific context elements like [com.intellij.codeWithMe.ClientId], [com.intellij.openapi.components.ComponentManager] for accessing project/client level services
 * through `com.intellij.serviceContainer.ContextKt#instance`.
 *
 * **Memory Leak Warning:** It is not recommended to manually manage coroutine cancellation with `lifetime.onTermination { job.cancel() }`
 * because if the lifetime significantly outlives the coroutine task, it can lead to memory leaks. This pattern causes the `Lifetime`
 * to retain references to coroutine jobs until termination. Over time, especially in long-lived scopes, this can accumulate and exhaust
 * memory resources.
 *
 * **Recommended Alternative:**
 * If you need to cancel a coroutine when a Lifetime is terminated, consider using [lifetimedCoroutineScope].
 * For launching coroutines at project/client/plugin level, obtain a coroutine scope from the appropriate service:
 * ```
 * val serviceScope = // obtain application/project/client/plugin level CoroutineScope
 * serviceScope.async(Dispatchers.EDT) {
 *  lifetimedCoroutineScope(lifetime) {
 *      // your coroutine code here
 *   }
 * }
 * ```
 *
 * @deprecated Use application/project/client/plugin level [CoroutineScope] and [lifetimedCoroutineScope].
 */
@Deprecated("Use application/project/client/plugin level CoroutineScope. See KDoc for details.")
fun <T> Lifetime.startOnUiAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = async(uiDispatcher + ModalityState.defaultModalityState().asContextElement() + context, start, action)

/**
 * Use coroutineScope.async(Dispatchers.EDT) or coroutineScope.async(Dispatchers.EDT + ModalityState.nonModal().asContextElement())
 *
 * **Deprecated:** This method is deprecated as it launches coroutines directly tied to a [Lifetime],
 * which is equivalent to launching them from an application-level scope and cancelling them when the Lifetime is terminated.
 *
 * For project/client/plugin level scopes, it is recommended to obtain a [CoroutineScope] from the appropriate service.
 * This ensures coroutines are automatically cancelled and awaited when application/project is closed,
 * this scope also have included specific context elements like [com.intellij.codeWithMe.ClientId], [com.intellij.openapi.components.ComponentManager] for accessing project/client level services
 * through `com.intellij.serviceContainer.ContextKt#instance`.
 *
 * **Memory Leak Warning:** It is not recommended to manually manage coroutine cancellation with `lifetime.onTermination { job.cancel() }`
 * because if the lifetime significantly outlives the coroutine task, it can lead to memory leaks. This pattern causes the `Lifetime`
 * to retain references to coroutine jobs until termination. Over time, especially in long-lived scopes, this can accumulate and exhaust
 * memory resources.
 *
 * **Recommended Alternative:**
 * If you need to cancel a coroutine when a Lifetime is terminated, consider using [lifetimedCoroutineScope].
 * For launching coroutines at project/client/plugin level, obtain a coroutine scope from the appropriate service:
 * ```
 * val serviceScope = // obtain application/project/client/plugin level CoroutineScope
 * serviceScope.async {
 *  lifetimedCoroutineScope(lifetime) {
 *      // your coroutine code here
 *   }
 * }
 * ```
 *
 * @deprecated Use application/project/client/plugin level [CoroutineScope] and [lifetimedCoroutineScope].
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use application/project/client/plugin level CoroutineScope. See KDoc for details.")
fun <T> Lifetime.startOnUiNonModalAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = async(uiDispatcher + context, start, action)

@ApiStatus.ScheduledForRemoval
@Deprecated("Do not use this method")
fun <T> Lifetime.startOnUiAllowInliningAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = async(uiDispatcherWithInlining + context, start, action)

@Deprecated("Please use async with modality specified explicitly")
fun <T> Lifetime.startOnUiAnyModalityAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> {
  if (ApplicationManager.getApplication().isDispatchThread && start == CoroutineStart.DEFAULT)
    // for backward compatibility
    return async(uiDispatcherAnyModality + context, CoroutineStart.UNDISPATCHED, action)

  return async(uiDispatcherAnyModality + context, start, action)
}

@Deprecated("Use startSyncIOBackgroundAsync or startBackgroundAsync")
fun <T> Lifetime.startIOBackgroundAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = async(processIODispatcher + context, start, action)

@ApiStatus.ScheduledForRemoval
@Deprecated("Use startBackgroundAsync", ReplaceWith("startBackgroundAsync(start, action)"))
fun <T> Lifetime.startLongBackgroundAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
) = startBackgroundAsync(context, start, action)

/**
 * Use coroutineScope.async(Dispatchers.Default) for CPU-bound tasks or coroutineScope.async(Dispatchers.IO) for IO-bound tasks
 *
 * **Deprecated:** This method is deprecated as it launches coroutines directly tied to a [Lifetime],
 * which is equivalent to launching them from an application-level scope and cancelling them when the Lifetime is terminated.
 *
 * For project/client/plugin level scopes, it is recommended to obtain a [CoroutineScope] from the appropriate service.
 * This ensures coroutines are automatically cancelled and awaited when application/project is closed,
 * this scope also have included specific context elements like [com.intellij.codeWithMe.ClientId], [com.intellij.openapi.components.ComponentManager] for accessing project/client level services
 * through `com.intellij.serviceContainer.ContextKt#instance`.
 *
 * **Memory Leak Warning:** It is not recommended to manually manage coroutine cancellation with `lifetime.onTermination { job.cancel() }`
 * because if the lifetime significantly outlives the coroutine task, it can lead to memory leaks. This pattern causes the `Lifetime`
 * to retain references to coroutine jobs until termination. Over time, especially in long-lived scopes, this can accumulate and exhaust
 * memory resources.
 *
 * **Recommended Alternative:**
 * If you need to cancel a coroutine when a Lifetime is terminated, consider using [lifetimedCoroutineScope].
 * For launching coroutines at project/client/plugin level, obtain a coroutine scope from the appropriate service:
 * ```
 * val serviceScope = // obtain application/project/client/plugin level CoroutineScope
 * serviceScope.async(Dispatchers.Default) {
 *  lifetimedCoroutineScope(lifetime) {
 *      // your coroutine code here
 *   }
 * }
 * ```
 *
 * @deprecated Use application/project/client/plugin level [CoroutineScope] and [lifetimedCoroutineScope].
 */
@Deprecated("Use application/project/client/plugin level CoroutineScope. See KDoc for details.")
fun <T> Lifetime.startBackgroundAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = async(applicationThreadPool + ModalityState.defaultModalityState().asContextElement() + context, start, action)

@ApiStatus.ScheduledForRemoval
@Deprecated("Use async with a specific IO dispatcher for your purposes.")
fun <T> Lifetime.startSyncIOBackgroundAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: () -> T
): Deferred<T> = async(processIODispatcher + context, start) { action() }

@ApiStatus.ScheduledForRemoval
@Deprecated("Use launch with a specific dispatcher for your purposes")
fun <T> Lifetime.startNonUrgentBackgroundAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = async(nonUrgentDispatcher + context, start, action)

@Deprecated("Use launch(Dispatchers.EDT)", ReplaceWith("launch(Dispatchers.EDT, start, action)", "kotlinx.coroutines.launch", "kotlinx.coroutines.Dispatchers",
                                                      "com.intellij.openapi.application.EDT"))
fun CoroutineScope.launchChildOnUi(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(Dispatchers.EDT, start, action)

@ApiStatus.ScheduledForRemoval
@Deprecated("Do not use this method")
fun CoroutineScope.launchChildOnUiAllowInlining(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(uiDispatcherWithInlining, start, action)

@ApiStatus.ScheduledForRemoval
@Deprecated("Use async with a specific IO dispatcher for your purposes.")
fun CoroutineScope.launchChildSyncIOBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: () -> Unit
): Job = launch(processIODispatcher, start) { action() }

@Deprecated("For CPU-bound tasks, use launch(Dispatchers.Default, action). For IO-bound tasks, use launch(Dispatchers.IO, action)")
fun CoroutineScope.launchChildBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(applicationThreadPool, start, action)

@ApiStatus.ScheduledForRemoval
@Deprecated("Use launch with a specific dispatcher for your purposes")
fun CoroutineScope.launchChildNonUrgentBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(nonUrgentDispatcher, start, action)

@ApiStatus.ScheduledForRemoval
@Deprecated("Use async(Dispatchers.EDT)", ReplaceWith("async(Dispatchers.EDT, start, action)", "kotlinx.coroutines.async", "kotlinx.coroutines.Dispatchers",
                                                      "com.intellij.openapi.application.EDT"))
fun <T> CoroutineScope.startChildOnUiAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = async(uiDispatcher, start, action)

@ApiStatus.ScheduledForRemoval
@Deprecated("Do not use this method")
fun <T> CoroutineScope.startChildOnUiAllowInliningAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = async(uiDispatcherWithInlining, start, action)

@ApiStatus.ScheduledForRemoval
@Deprecated("Use async with a specific IO dispatcher for your purposes.")
fun <T> CoroutineScope.startChildSyncIOBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: () -> T
): Deferred<T> = async(processIODispatcher, start) { action() }

@Deprecated("For CPU-bound tasks, use async(Dispatchers.Default, action). For IO-bound tasks, use async(Dispatchers.IO, action)")
fun <T> CoroutineScope.startChildBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
) = async(applicationThreadPool, start, action)

@ApiStatus.ScheduledForRemoval
@Deprecated("Use async with a specific dispatcher for your purposes")
fun <T> CoroutineScope.startChildNonUrgentBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = async(nonUrgentDispatcher, start, action)

@Deprecated("Use withContext(Dispatchers.EDT)", ReplaceWith("withContext(Dispatchers.EDT, action)", "kotlinx.coroutines.withContext", "kotlinx.coroutines.Dispatchers",
                                                            "com.intellij.openapi.application.EDT"))
suspend fun <T> withUiContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, uiDispatcher, action)

@ApiStatus.ScheduledForRemoval
@Deprecated("Do not use this method")
suspend fun <T> withUiAllowInliningContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, uiDispatcherWithInlining, action)

@Deprecated("Please use withContext with modality specified explicitly")
suspend fun <T> withUiAnyModalityContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, uiDispatcherAnyModality, action)

@Deprecated("Use withSyncIOBackgroundContext or withBackgroundContext")
suspend fun <T> withIOBackgroundContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, processIODispatcher, action)

@Deprecated("Use withContext with a specific IO dispatcher for your purposes")
suspend fun <T> withSyncIOBackgroundContext(lifetime: Lifetime = Lifetime.Eternal, action: () -> T): T =
  withContext(lifetime, processIODispatcher) { action() }

@Deprecated("Use withBackgroundContext", ReplaceWith("withBackgroundContext(action)"))
suspend fun <T> withLongBackgroundContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withBackgroundContext(lifetime, action)

@Deprecated("For CPU-bound tasks, use withContext(Dispatchers.Default, action). For IO-bound tasks, use withContext(Dispatchers.IO, action)")
suspend fun <T> withBackgroundContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, applicationThreadPool, action)

@ApiStatus.ScheduledForRemoval
@Deprecated("Use withContext with a specific dispatcher for your purposes")
suspend fun <T> withNonUrgentBackgroundContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, nonUrgentDispatcher, action)

@Deprecated("Api moved to Rd")
suspend fun <T> lifetimedCoroutineScope(lifetime: Lifetime, action: suspend CoroutineScope.() -> T): T =
  com.jetbrains.rd.util.threading.coroutines.lifetimedCoroutineScope(lifetime, action)


// See IJPL-157034
@ExperimentalCoroutinesApi
fun <T> Deferred<T>.toPromise(): Promise<T> = AsyncPromiseWithoutLogError<T>().also { promise ->
  invokeOnCompletion { throwable ->
    if (throwable != null) {
      promise.setError(throwable)
    }
    else {
      promise.setResult(getCompleted())
    }
  }
}

// See IJPL-157034
fun <T> CompletableFuture<T>.toPromise(): Promise<T> = AsyncPromiseWithoutLogError<T>().also { promise ->
  whenComplete { result, throwable ->
    if (throwable != null) {
      promise.setError(throwable)
    }
    else {
      promise.setResult(result)
    }
  }
}

