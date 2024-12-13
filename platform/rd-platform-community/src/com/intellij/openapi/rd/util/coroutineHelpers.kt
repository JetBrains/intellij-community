package com.intellij.openapi.rd.util

import com.intellij.codeWithMe.assertClientIdConsistency
import com.intellij.codeWithMe.currentThreadClientId
import com.jetbrains.rd.framework.IRdDynamic
import com.jetbrains.rd.framework.IRdEndpoint
import com.jetbrains.rd.framework.util.toRdTask
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IScheduler
import com.jetbrains.rd.util.reactive.ISource
import com.jetbrains.rd.util.threading.SynchronousScheduler
import com.jetbrains.rd.util.threading.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Sets suspend handler for the [IRdEndpoint].
 *
 * When a protocol call is occurred it starts a new coroutine passing [coroutineContext] and [coroutineStart] to it.
 * Coroutine dispatcher is being chosen in the following order: handlerScheduler (if not null) -> coroutineContext (if it has) -> this.protocol.scheduler (if not null) -> SynchronousScheduler
 */
@ApiStatus.Internal
fun <TReq, TRes> IRdEndpoint<TReq, TRes>.setSuspend(
  coroutineContext: CoroutineContext = EmptyCoroutineContext,
  coroutineStart: CoroutineStart = CoroutineStart.DEFAULT,
  handler: suspend (Lifetime, TReq) -> TRes
) {
  val beforeAdviseClientId = currentThreadClientId
  set(null, SynchronousScheduler) { lt, req ->
    // use ?. to skip cases when beforeAdviseClientId == null, it's likely valid
    beforeAdviseClientId?.assertClientIdConsistency("Inside set ($this)", fallbackToLocal = false)
    val inAdviseClientId = currentThreadClientId
    lt.coroutineScope.async(coroutineContext + getSuitableDispatcher(coroutineContext), coroutineStart) {
      inAdviseClientId.assertClientIdConsistency("Inside async ($this)", fallbackToLocal = false)
      handler(lt, req)
    }.toRdTask()
  }
}

/**
 * Shortcut for [setSuspend]
 */
@ApiStatus.Internal
@Deprecated("Use `setSuspend` instead`", ReplaceWith("setSuspend(coroutineContext, coroutineStart, handler)"))
fun <TReq, TRes> IRdEndpoint<TReq, TRes>.setSuspendPreserveClientId(
  coroutineContext: CoroutineContext = EmptyCoroutineContext,
  coroutineStart: CoroutineStart = CoroutineStart.DEFAULT,
  cancellationScheduler: IScheduler? = null,
  handlerScheduler: IScheduler? = null,
  handler: suspend (Lifetime, TReq) -> TRes) {
  @OptIn(DelicateCoroutinesApi::class)
  setSuspend(coroutineContext, coroutineStart, handler)
}

/**
 * Sets suspend handler for the [ISource].
 *
 * When a protocol call is occurred it starts a new coroutine passing [coroutineContext] and [coroutineStart] to it.
 *
 * Coroutine dispatcher is being chosen in the following order: handlerScheduler (if not null) -> coroutineContext (if it has) -> this.protocol.scheduler (if not null) -> SynchronousScheduler
 */
@ApiStatus.Internal
fun<T> ISource<T>.adviseSuspend(lifetime: Lifetime, coroutineContext: CoroutineContext = EmptyCoroutineContext, coroutineStart: CoroutineStart = CoroutineStart.DEFAULT, handler: suspend (T) -> Unit) {
  val beforeAdviseClientId = currentThreadClientId
  advise(lifetime) {
    // TODO: very very temporary hack to avoid assertions in some cases
    // 1) TODO Moklev IJPL-173291 Dependency between RdSettingsStorageService and
    //  settingsModel should be inversed to provide proper coroutine scopes where model operations are done
    // 2) TODO: Dubov IJPL-173492 Inconsistent ClientId in `adviseSuspend` on a signal in `ConnectionStateProperty`
    if (!this.toString().contains("ThinClient.SettingsModel.settingsChanges")
        && !this.toString().contains("RdOptionalProperty: `<<not bound>>`")) {
      // use ?. to skip cases when beforeAdviseClientId == null, it's likely valid
      beforeAdviseClientId?.assertClientIdConsistency("Inside advise ($this)", fallbackToLocal = false)
    }
    val inAdviseClientId = currentThreadClientId
    lifetime.coroutineScope.launch(coroutineContext + getSuitableDispatcher(coroutineContext), coroutineStart) {
      inAdviseClientId.assertClientIdConsistency("Inside launch ($this)", fallbackToLocal = false)
      handler(it)
    }
  }
}

/**
 * Shortcut for [adviseSuspend]
 */
@ApiStatus.Internal
@Deprecated("Use `adviseSuspend` instead", ReplaceWith("adviseSuspend(lifetime, coroutineContext, coroutineStart, handler)"))
fun<T> ISource<T>.adviseSuspendPreserveClientId(lifetime: Lifetime, coroutineContext: CoroutineContext = EmptyCoroutineContext, coroutineStart: CoroutineStart = CoroutineStart.DEFAULT, handler: suspend (T) -> Unit) {
  @OptIn(DelicateCoroutinesApi::class)
  adviseSuspend(lifetime, coroutineContext, coroutineStart, handler)
}

private fun Any.getSuitableDispatcher(context: CoroutineContext): CoroutineDispatcher {
  @OptIn(ExperimentalStdlibApi::class)
  val contextDispatcher = context[CoroutineDispatcher.Key]
  if (contextDispatcher != null)
    return contextDispatcher
  return ((this as IRdDynamic).protocol?.scheduler ?: SynchronousScheduler).asCoroutineDispatcher(allowInlining = true)
}