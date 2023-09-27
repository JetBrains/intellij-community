package com.intellij.openapi.rd.util

import com.intellij.codeWithMe.ClientId
import com.jetbrains.rd.framework.IRdDynamic
import com.jetbrains.rd.framework.IRdEndpoint
import com.jetbrains.rd.framework.util.*
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IScheduler
import com.jetbrains.rd.util.reactive.ISource
import com.jetbrains.rd.util.threading.SynchronousScheduler
import com.jetbrains.rd.util.threading.coroutines.asCoroutineDispatcher
import com.jetbrains.rd.util.threading.coroutines.async
import com.jetbrains.rd.util.threading.coroutines.launch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// TODO: remove when the same is merged in rd
/**
 * Consider using [setSuspendPreserveClientId] to propagate [ClientId] into [handler]
 *
 * Sets suspend handler for the [IRdEndpoint].
 *
 * When a protocol call is occurred it starts a new coroutine passing [coroutineContext] and [coroutineStart] to it.
 * [cancellationScheduler] and [handlerScheduler] are passed to [IRdEndpoint.set]
 * Coroutine dispatcher is being chosen in the following order: handlerScheduler (if not null) -> coroutineContext (if it has) -> this.protocol.scheduler (if not null) -> SynchronousScheduler
 */
@DelicateCoroutinesApi
fun <TReq, TRes> IRdEndpoint<TReq, TRes>.setSuspend(
  coroutineContext: CoroutineContext = EmptyCoroutineContext,
  coroutineStart: CoroutineStart = CoroutineStart.DEFAULT,
  cancellationScheduler: IScheduler? = null,
  handlerScheduler: IScheduler? = null,
  handler: suspend (Lifetime, TReq) -> TRes
) {
  set(cancellationScheduler, handlerScheduler) { lt, req ->
    lt.async(coroutineContext + getSuitableDispatcher(handlerScheduler, coroutineContext), coroutineStart) {
      handler(lt, req)
    }.toRdTask()
  }
}

/**
 * The same as [setSuspend] but add [ClientId] into [coroutineContext] to restore it in [handler]
 */
fun <TReq, TRes> IRdEndpoint<TReq, TRes>.setSuspendPreserveClientId(
  coroutineContext: CoroutineContext = EmptyCoroutineContext,
  coroutineStart: CoroutineStart = CoroutineStart.DEFAULT,
  cancellationScheduler: IScheduler? = null,
  handlerScheduler: IScheduler? = null,
  handler: suspend (Lifetime, TReq) -> TRes) {
  @OptIn(DelicateCoroutinesApi::class)
  setSuspend( coroutineContext + ClientId.coroutineContext(), coroutineStart, cancellationScheduler, handlerScheduler, handler)
}

// TODO: remove when the same is merged in rd
/**
 * Consider using [adviseSuspendPreserveClientId] to propagate [ClientId] into [handler]
 *
 * Sets suspend handler for the [ISource].
 *
 * When a protocol call is occurred it starts a new coroutine passing [coroutineContext] and [coroutineStart] to it.
 *
 * Coroutine dispatcher is being chosen in the following order: handlerScheduler (if not null) -> coroutineContext (if it has) -> this.protocol.scheduler (if not null) -> SynchronousScheduler
 */
@DelicateCoroutinesApi
fun<T> ISource<T>.adviseSuspend(lifetime: Lifetime, coroutineContext: CoroutineContext = EmptyCoroutineContext, coroutineStart: CoroutineStart = CoroutineStart.DEFAULT, handler: suspend (T) -> Unit) {
  advise(lifetime) {
    lifetime.launch(coroutineContext + getSuitableDispatcher(null, coroutineContext), coroutineStart) {
      handler(it)
    }
  }
}

/**
 * The same as [adviseSuspend] but adds ClientId into [coroutineContext] to restore it in [handler]
 */
fun<T> ISource<T>.adviseSuspendPreserveClientId(lifetime: Lifetime, coroutineContext: CoroutineContext = EmptyCoroutineContext, coroutineStart: CoroutineStart = CoroutineStart.DEFAULT, handler: suspend (T) -> Unit) {
  advise(lifetime) {
    lifetime.launch(coroutineContext + ClientId.coroutineContext() + getSuitableDispatcher(null, coroutineContext), coroutineStart) {
      handler(it)
    }
  }
}

private fun Any.getSuitableDispatcher(handlerScheduler: IScheduler?, context: CoroutineContext): CoroutineDispatcher {
  if (handlerScheduler != null) {
    return handlerScheduler.asCoroutineDispatcher(allowInlining = true)
  }
  @OptIn(ExperimentalStdlibApi::class)
  val contextDispatcher = context[CoroutineDispatcher.Key]
  if (contextDispatcher != null)
    return contextDispatcher
  return ((this as IRdDynamic).protocol?.scheduler ?: SynchronousScheduler).asCoroutineDispatcher(allowInlining = true)
}