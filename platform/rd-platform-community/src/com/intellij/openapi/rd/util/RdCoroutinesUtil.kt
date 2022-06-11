// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.rd.util

import com.jetbrains.rd.framework.util.*
import com.jetbrains.rd.util.lifetime.Lifetime
import kotlinx.coroutines.*
import org.jetbrains.concurrency.Promise
import java.util.concurrent.CompletableFuture

private val applicationThreadPool get() = RdCoroutineHost.applicationThreadPool
private val processIODispatcher get() = RdCoroutineHost.processIODispatcher
private val nonUrgentDispatcher get() = RdCoroutineHost.nonUrgentDispatcher
private val uiDispatcher get() = RdCoroutineHost.instance.uiDispatcher
private val uiDispatcherAnyModality get() = RdCoroutineHost.instance.uiDispatcherAnyModality

fun Lifetime.launchOnUi(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(uiDispatcher, start, action)

fun Lifetime.launchOnUiAnyModality(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(uiDispatcherAnyModality, start, action)

fun Lifetime.launchIOBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(processIODispatcher, start, action)

fun Lifetime.launchLongBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
) = launch(applicationThreadPool, start, action)

fun Lifetime.launchNonUrgentBackground(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launch(nonUrgentDispatcher, start, action)

fun <T> Lifetime.startOnUiAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startAsync(uiDispatcher, start, action)

fun <T> Lifetime.startIOBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startAsync(processIODispatcher, start, action)

fun <T> Lifetime.startLongBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
) = startAsync(applicationThreadPool, start, action)

fun <T> Lifetime.startNonUrgentBackgroundAsync(
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startAsync(nonUrgentDispatcher, start, action)

fun CoroutineScope.launchChildOnUi(
  lifetime: Lifetime = Lifetime.Eternal,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launchChild(lifetime, uiDispatcher, start, action)

fun CoroutineScope.launchChildIOBackground(
  lifetime: Lifetime = Lifetime.Eternal,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launchChild(lifetime, processIODispatcher, start, action)

fun CoroutineScope.launchChildLongBackground(
  lifetime: Lifetime = Lifetime.Eternal,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launchChild(lifetime, applicationThreadPool, start, action)

fun CoroutineScope.launchChildNonUrgentBackground(
  lifetime: Lifetime = Lifetime.Eternal,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> Unit
): Job = launchChild(lifetime, nonUrgentDispatcher, start, action)

fun <T> CoroutineScope.startChildOnUiAsync(
  lifetime: Lifetime = Lifetime.Eternal,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startChildAsync(lifetime, uiDispatcher, start, action)

fun <T> CoroutineScope.startChildIOBackgroundAsync(
  lifetime: Lifetime = Lifetime.Eternal,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startChildAsync(lifetime, processIODispatcher, start, action)

fun <T> CoroutineScope.startChildLongBackgroundAsync(
  lifetime: Lifetime = Lifetime.Eternal,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
) = startChildAsync(lifetime, applicationThreadPool, start, action)

fun <T> CoroutineScope.startChildNonUrgentBackgroundAsync(
  lifetime: Lifetime = Lifetime.Eternal,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  action: suspend CoroutineScope.() -> T
): Deferred<T> = startChildAsync(lifetime, nonUrgentDispatcher, start, action)


suspend fun <T> withUiContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, uiDispatcher, action)

suspend fun <T> withUiAnyModalityContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, uiDispatcherAnyModality, action)

suspend fun <T> withIOBackgroundContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, processIODispatcher, action)

suspend fun <T> withLongBackgroundContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, applicationThreadPool, action)

suspend fun <T> withNonUrgentBackgroundContext(lifetime: Lifetime = Lifetime.Eternal, action: suspend CoroutineScope.() -> T): T =
  withContext(lifetime, nonUrgentDispatcher, action)


@ExperimentalCoroutinesApi
@Deprecated("Use the overload without `shouldLogErrors` argument", ReplaceWith("toPromise()"))
fun <T> Deferred<T>.toPromise(shouldLogErrors: Boolean): Promise<T> = toPromise()

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

