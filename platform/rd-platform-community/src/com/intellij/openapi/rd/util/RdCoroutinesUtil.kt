// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.openapi.rd.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.jetbrains.rd.framework.util.launch
import com.jetbrains.rd.framework.util.startAsync
import com.jetbrains.rd.framework.util.synchronizeWith
import com.jetbrains.rd.framework.util.withContext
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isEternal
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

fun Lifetime.launchOnUi(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job {
  return launch(uiDispatcher + ModalityState.defaultModalityState().asContextElement() + context, start, action)
}

fun Lifetime.launchOnUiNonModal(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(uiDispatcher + context, start, action)

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

fun Lifetime.launchBackground(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(applicationThreadPool + ModalityState.defaultModalityState().asContextElement() + context, start, action)

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

fun Lifetime.launchNonUrgentBackground(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(nonUrgentDispatcher + context, start, action)

fun <T> Lifetime.startOnUiAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startAsync(uiDispatcher + ModalityState.defaultModalityState().asContextElement() + context, start, action)

fun <T> Lifetime.startOnUiNonModalAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startAsync(uiDispatcher + context, start, action)

fun <T> Lifetime.startOnUiAllowInliningAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startAsync(uiDispatcherWithInlining + context, start, action)

@Deprecated("Please use async with modality specified explicitly")
fun <T> Lifetime.startOnUiAnyModalityAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> {
  if (ApplicationManager.getApplication().isDispatchThread && start == CoroutineStart.DEFAULT)
    // for backward compatibility
    return startAsync(uiDispatcherAnyModality + context, CoroutineStart.UNDISPATCHED, action)

  return startAsync(uiDispatcherAnyModality + context, start, action)
}

@Deprecated("Use startSyncIOBackgroundAsync or startBackgroundAsync")
fun <T> Lifetime.startIOBackgroundAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startAsync(processIODispatcher + context, start, action)

@ApiStatus.ScheduledForRemoval
@Deprecated("Use startBackgroundAsync", ReplaceWith("startBackgroundAsync(start, action)"))
fun <T> Lifetime.startLongBackgroundAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
) = startBackgroundAsync(context, start, action)

fun <T> Lifetime.startBackgroundAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startAsync(applicationThreadPool + ModalityState.defaultModalityState().asContextElement() + context, start, action)

fun <T> Lifetime.startSyncIOBackgroundAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: () -> T
): Deferred<T> = startAsync(processIODispatcher + context, start) { action() }

fun <T> Lifetime.startNonUrgentBackgroundAsync(
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startAsync(nonUrgentDispatcher + context, start, action)

fun CoroutineScope.launchChildOnUi(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(uiDispatcher, start, action)

fun CoroutineScope.launchChildOnUiAllowInlining(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(uiDispatcherWithInlining, start, action)

fun CoroutineScope.launchChildSyncIOBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: () -> Unit
): Job = launch(processIODispatcher, start) { action() }

fun CoroutineScope.launchChildBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(applicationThreadPool, start, action)

fun CoroutineScope.launchChildNonUrgentBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(nonUrgentDispatcher, start, action)

fun <T> CoroutineScope.startChildOnUiAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = async(uiDispatcher, start, action)

fun <T> CoroutineScope.startChildOnUiAllowInliningAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = async(uiDispatcherWithInlining, start, action)

fun <T> CoroutineScope.startChildSyncIOBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: () -> T
): Deferred<T> = async(processIODispatcher, start) { action() }

fun <T> CoroutineScope.startChildBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
) = async(applicationThreadPool, start, action)

fun <T> CoroutineScope.startChildNonUrgentBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = async(nonUrgentDispatcher, start, action)

suspend fun <T> withUiContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, uiDispatcher, action)

suspend fun <T> withUiAllowInliningContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, uiDispatcherWithInlining, action)

@Deprecated("Please use withContext with modality specified explicitly")
suspend fun <T> withUiAnyModalityContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, uiDispatcherAnyModality, action)

@Deprecated("Use withSyncIOBackgroundContext or withBackgroundContext")
suspend fun <T> withIOBackgroundContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, processIODispatcher, action)

suspend fun <T> withSyncIOBackgroundContext(lifetime: Lifetime = Lifetime.Eternal, action: () -> T): T =
  withContext(lifetime, processIODispatcher) { action() }

@Deprecated("Use withBackgroundContext", ReplaceWith("withBackgroundContext(action)"))
suspend fun <T> withLongBackgroundContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withBackgroundContext(lifetime, action)

suspend fun <T> withBackgroundContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, applicationThreadPool, action)

suspend fun <T> withNonUrgentBackgroundContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, nonUrgentDispatcher, action)

suspend fun <T> lifetimedCoroutineScope(lifetime: Lifetime, action: suspend CoroutineScope.() -> T) = coroutineScope {
  if (!lifetime.isEternal)
    lifetime.createNested().synchronizeWith(coroutineContext[Job]!!)

  action()
}

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

