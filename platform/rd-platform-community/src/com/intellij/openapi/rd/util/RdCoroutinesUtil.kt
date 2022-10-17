// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.rd.util

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.jetbrains.rd.framework.util.*
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isEternal
import kotlinx.coroutines.*
import org.jetbrains.concurrency.Promise
import java.util.concurrent.CompletableFuture

private val applicationThreadPool get() = RdCoroutineHost.applicationThreadPool
private val processIODispatcher get() = RdCoroutineHost.processIODispatcher
private val nonUrgentDispatcher get() = RdCoroutineHost.nonUrgentDispatcher
private val uiDispatcher get() = RdCoroutineHost.instance.uiDispatcher
private val uiDispatcherWithInlining get() = RdCoroutineHost.instance.uiDispatcherWithInlining
private val uiDispatcherAnyModality get() = RdCoroutineHost.instance.uiDispatcherAnyModality

fun Lifetime.launchOnUi(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job {
  return launch(uiDispatcher + ModalityState.defaultModalityState().asContextElement(), start, action)
}

fun Lifetime.launchOnUiNonModal(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(uiDispatcher, start, action)

fun Lifetime.launchOnUiAllowInlining(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(uiDispatcherWithInlining, start, action)

fun Lifetime.launchOnUiAnyModality(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(uiDispatcherAnyModality, start, action)

@Deprecated("Use launchSyncIOBackground or launchBackground")
fun Lifetime.launchIOBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(processIODispatcher, start, action)

fun Lifetime.launchBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(applicationThreadPool, start, action)

fun Lifetime.launchSyncIOBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: () -> Unit
): Job = launch(processIODispatcher, start) { action() }

@Deprecated("Use launchBackground", ReplaceWith("launchBackground(start, action)"))
fun Lifetime.launchLongBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
) = launchBackground(start, action)

fun Lifetime.launchNonUrgentBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(nonUrgentDispatcher, start, action)

fun <T> Lifetime.startOnUiAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startAsync(uiDispatcher + ModalityState.defaultModalityState().asContextElement(), start, action)

fun <T> Lifetime.startOnUiNonModalAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startAsync(uiDispatcher, start, action)

fun <T> Lifetime.startOnUiAllowInliningAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startAsync(uiDispatcherWithInlining, start, action)

fun <T> Lifetime.startOnUiAnyModalityAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startAsync(uiDispatcherAnyModality, start, action)

@Deprecated("Use startSyncIOBackgroundAsync or startBackgroundAsync")
fun <T> Lifetime.startIOBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startAsync(processIODispatcher, start, action)

@Deprecated("Use startBackgroundAsync", ReplaceWith("startBackgroundAsync(start, action)"))
fun <T> Lifetime.startLongBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
) = startBackgroundAsync(start, action)

fun <T> Lifetime.startBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startAsync(applicationThreadPool, start, action)

fun <T> Lifetime.startSyncIOBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: () -> T
): Deferred<T> = startAsync(processIODispatcher, start) { action() }

fun <T> Lifetime.startNonUrgentBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startAsync(nonUrgentDispatcher, start, action)

@Deprecated("Use launchChildOnUi without lifetime or use lifetimedCoroutineScope", ReplaceWith("launchChildOnUi(start, action)"))
fun CoroutineScope.launchChildOnUi(
  lifetime: Lifetime,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launchChild(lifetime, uiDispatcher, start, action)

fun CoroutineScope.launchChildOnUi(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(uiDispatcher, start, action)

fun CoroutineScope.launchChildOnUiAllowInlining(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(uiDispatcherWithInlining, start, action)

@Deprecated("Use launchChildSyncIOBackground or launchChildBackground or use lifetimedCoroutineScope")
fun CoroutineScope.launchChildIOBackground(
  lifetime: Lifetime = Lifetime.Eternal,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launchChild(lifetime, processIODispatcher, start, action)

fun CoroutineScope.launchChildSyncIOBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: () -> Unit
): Job = launch(processIODispatcher, start) { action() }

fun CoroutineScope.launchChildBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(applicationThreadPool, start, action)

@Deprecated("Use launchChildBackground or use lifetimedCoroutineScope", ReplaceWith("launchChildBackground(start, action)"))
fun CoroutineScope.launchChildLongBackground(
  lifetime: Lifetime = Lifetime.Eternal,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launchChild(lifetime, applicationThreadPool, start, action)

@Deprecated("Use launchChildNonUrgentBackground without lifetime or use lifetimedCoroutineScope", ReplaceWith("launchChildNonUrgentBackground(start, action)"))
fun CoroutineScope.launchChildNonUrgentBackground(
  lifetime: Lifetime,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launchChild(lifetime, nonUrgentDispatcher, start, action)

fun CoroutineScope.launchChildNonUrgentBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(nonUrgentDispatcher, start, action)

@Deprecated("Use startChildOnUiAsync without lifetime or use lifetimedCoroutineScope", ReplaceWith("startChildOnUiAsync(start, action)"))
fun <T> CoroutineScope.startChildOnUiAsync(
  lifetime: Lifetime,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startChildAsync(lifetime, uiDispatcher, start, action)

fun <T> CoroutineScope.startChildOnUiAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = async(uiDispatcher, start, action)

fun <T> CoroutineScope.startChildOnUiAllowInliningAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = async(uiDispatcherWithInlining, start, action)

@Deprecated("Use startChildSyncIOBackgroundAsync or startChildBackgroundAsync or use lifetimedCoroutineScope")
fun <T> CoroutineScope.startChildIOBackgroundAsync(
  lifetime: Lifetime = Lifetime.Eternal,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startChildAsync(lifetime, processIODispatcher, start, action)

fun <T> CoroutineScope.startChildSyncIOBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: () -> T
): Deferred<T> = async(processIODispatcher, start) { action() }

@Deprecated("Use startChildBackgroundAsync without lifetime or use lifetimedCoroutineScope", ReplaceWith("startChildBackgroundAsync(start, action)"))
fun <T> CoroutineScope.startChildLongBackgroundAsync(
  lifetime: Lifetime = Lifetime.Eternal,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
) = startChildAsync(lifetime, applicationThreadPool, start, action)

fun <T> CoroutineScope.startChildBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
) = async(applicationThreadPool, start, action)

@Deprecated("Use startChildNonUrgentBackgroundAsync without lifetime or use lifetimedCoroutineScope", ReplaceWith("startChildNonUrgentBackgroundAsync(start, action)"))
fun <T> CoroutineScope.startChildNonUrgentBackgroundAsync(
  lifetime: Lifetime,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startChildAsync(lifetime, nonUrgentDispatcher, start, action)

fun <T> CoroutineScope.startChildNonUrgentBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = async(nonUrgentDispatcher, start, action)

suspend fun <T> withUiContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, uiDispatcher, action)

suspend fun <T> withUiAllowInliningContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, uiDispatcherWithInlining, action)

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

