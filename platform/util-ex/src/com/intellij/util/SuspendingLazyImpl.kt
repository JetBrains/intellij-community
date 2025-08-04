// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

internal class SuspendingLazyImpl<out T>(
  initCs: CoroutineScope,
  initCtx: CoroutineContext,
  initializer: suspend CoroutineScope.() -> T,
  private val checkRecursion: Boolean,
) : SuspendingLazy<T> {

  companion object {

    private val stateHandle: VarHandle = MethodHandles
      .privateLookupIn(SuspendingLazyImpl::class.java, MethodHandles.lookup())
      .findVarHandle(SuspendingLazyImpl::class.java, "_state", Any::class.java)
  }

  private val _name: String? = initCtx[CoroutineName]?.name

  /**
   * Possible states = [InitialState] | [InProgress] | [Result].
   *
   * Transitions:
   * - [tryStart]: [InitialState] -> [InProgress] with waiter
   * - [SuspendingLazyImpl.launch]: [InProgress] -> final
   * - [initializationJobCompleted]: [InProgress] -> final
   * - [tryAwait]: [InProgress] with waiter(s) -> [InProgress] with waiter(s)
   * - [removeWaiterAndCancel]: [InProgress] with waiter(s) -> [InProgress] without waiters
   * - [removeWaiterAndCancel]: [InProgress] without waiters -> [InitialState]
   */
  private var _state: Any = InitialState(initCs, initCtx, initializer)

  private inline fun loopState(action: (state: Any) -> Unit): Nothing {
    while (true) {
      action(stateHandle.getVolatile(this))
    }
  }

  override fun toString(): String {
    val stateString = when (val state = _state) {
      is InitialState -> "Uninitialized"
      is InProgress -> "Initializing, waiters=${state.waiters.size}"
      is Result<*> -> state
      else -> error("Unexpected state: $state")
    }
    return "SuspendingLazy(${_name ?: "unnamed"}){${stateString}}"
  }

  override fun isInitialized(): Boolean = _state is Result<*>

  override fun getInitialized(): T {
    return when (val state = _state) {
      is Result<*> -> {
        @Suppress("UNCHECKED_CAST")
        state.getOrThrow() as T
      }
      else -> error(
        "SuspendingLazy(${_name ?: "unnamed"}) is not initialized yet. " +
        "Check result of `isInitialized()` before calling this method"
      )
    }
  }

  override suspend fun getValue(): T {
    (_state as? Result<*>)?.let {
      @Suppress("UNCHECKED_CAST")
      return it.getOrThrow() as T
    }
    loopState { state ->
      val result = when (state) {
        is InitialState -> tryStart(state)
        is InProgress -> tryAwait(state)
        is Result<*> -> state
        else -> error("Unexpected state $state")
      }
      if (result != null) {
        @Suppress("UNCHECKED_CAST")
        return result.getOrThrow() as T
      }
    }
  }

  private suspend fun tryStart(state: InitialState): Result<Any?>? {
    val initCs: CoroutineScope = state.initCs
    val initJob: Job = launch(state)
    return suspendCancellableCoroutine { waiter: CancellableContinuation<Result<Any?>?> ->
      val newState = InProgress(state, initJob, setOf(waiter))
      if (!stateHandle.compareAndSet(this, state, newState)) {
        initJob.cancel() // another thread started the job
        waiter.resume(null) // loop again
        return@suspendCancellableCoroutine
      }
      if (!checkRecursion()) {
        initJob.cancel()
        return@suspendCancellableCoroutine
      }
      @OptIn(InternalCoroutinesApi::class)
      initJob.invokeOnCompletion(true) { throwable ->
        initializationJobCompleted(throwable, initCs)
      }
      if (!initJob.start()) {
        check(initJob.isCancelled)
      }
      waiter.invokeOnCancellation {
        removeWaiterAndCancel(waiter)
      }
    }
  }

  private fun launch(state: InitialState): Job {
    val initializer = state.initializer
    val recursionTracker = LazyRecursionTrackerElement(this)
    return state.initCs.launch(start = CoroutineStart.LAZY, context = state.initCtx + recursionTracker) {
      try {
        complete(initializer())
      }
      catch (e: CancellationException) {
        if (coroutineContext.isActive) {
          // this coroutine is active even after getting CE
          // => CE must've been thrown from the initializer explicitly
          // => complete this lazy with explicit CE
          completeWithException(e)
        }
        else {
          throw e
        }
      }
      catch (e: Throwable) {
        completeWithException(e)
      }
    }
  }

  private fun initializationJobCompleted(throwable: Throwable?, initCs: CoroutineScope) {
    if (throwable == null) {
      // normal completion => final state must've been published
      return
    }
    if (initCs.isActive) {
      // all waiters were cancelled
      check(throwable is CancellationException)
      return
    }
    // cancelled by init scope
    @OptIn(InternalCoroutinesApi::class)
    completeWithException(initCs.coroutineContext.job.getCancellationException())
  }

  private suspend fun tryAwait(state: InProgress): Result<Any?>? {
    return suspendCancellableCoroutine { waiter: CancellableContinuation<Result<Any?>?> ->
      val newState = state.copy(waiters = state.waiters + waiter)
      if (!stateHandle.compareAndSet(this, state, newState)) {
        waiter.resume(null) // loop again
        return@suspendCancellableCoroutine
      }
      if (!checkRecursion()) {
        return@suspendCancellableCoroutine
      }
      waiter.invokeOnCancellation {
        removeWaiterAndCancel(waiter)
      }
    }
  }

  private fun removeWaiterAndCancel(waiter: CancellableContinuation<Result<Any?>>) {
    val state: InProgress? = removeWaiter(waiter)
    if (state == null) {
      // initialization job must've completed this lazy concurrently
      return
    }
    if (!state.waiters.isEmpty()) {
      return // nothing else to do
    }
    if (!stateHandle.compareAndSet(this, state, state.initialState)) {
      // some thread must've added a waiter again
      return // nothing else to do
    }
    state.initJob.cancel()
  }

  private fun removeWaiter(waiter: CancellableContinuation<Result<Any?>>): InProgress? {
    loopState { state ->
      when (state) {
        is InProgress -> {
          val newWaiters = state.waiters - waiter
          check(newWaiters !== state.waiters)
          val newState = state.copy(waiters = newWaiters)
          if (!stateHandle.compareAndSet(this, state, newState)) {
            return@loopState // loop again
          }
          return newState
        }
        is Result<*> -> {
          // initialization job must've completed this lazy concurrently
          return null
        }
        else -> {
          error("Unexpected state $state")
        }
      }
    }
  }

  private fun checkRecursion(): Boolean {
    if (!checkRecursion) {
      return true
    }

    val cycle = findCycle<SuspendingLazyImpl<*>>(this) { callingLazy ->
      val state = stateHandle.getVolatile(callingLazy)
      if (state is InProgress) {
        state.waiters.mapNotNull {
          it.context[LazyRecursionTrackerElement.Key]?.currentLazy
        }
      }
      else {
        emptyList()
      }
    }
    if (cycle != null) {
      completeWithException(LazyRecursionPreventedException(cycle.toString()))
      return false
    }
    return true
  }

  private fun complete(value: Any?) {
    completeWith(Result.success(value))
  }

  private fun completeWithException(throwable: Throwable) {
    completeWith(Result.failure(throwable))
  }

  private fun completeWith(result: Result<Any?>) {
    loopState { state ->
      when (state) {
        is InitialState -> {
          if (!stateHandle.compareAndSet(this, state, result)) {
            return@loopState // loop again
          }
          return
        }
        is InProgress -> {
          if (!stateHandle.compareAndSet(this, state, result)) {
            return@loopState // loop again
          }
          state.resumeWaiters(result)
          return
        }
        is Result<*> -> {
          // initialization job might've published the result while it was in Cancelling state
          return
        }
        else -> {
          error("Unexpected state $state")
        }
      }
    }
  }

  private class InitialState(
    val initCs: CoroutineScope,
    val initCtx: CoroutineContext,
    val initializer: suspend CoroutineScope.() -> Any?,
  )

  /**
   * @param initialState stored for resetting the lazy to initial state
   * @param initJob stored for cancelling after restoring the initial state
   */
  private data class InProgress(
    val initialState: InitialState,
    val initJob: Job,
    val waiters: Set<CancellableContinuation<Result<Any?>>>,
  ) {

    fun resumeWaiters(result: Result<Any?>) {
      for (waiter in waiters) {
        waiter.resume(result)
      }
    }
  }
}

@Internal
class LazyRecursionPreventedException(pathString: String) : Throwable("Recursion prevented: ${pathString}")

private typealias SLazy = SuspendingLazyImpl<*>

/**
 * @param currentLazy lazy instance which is being currently initialized
 */
private class LazyRecursionTrackerElement(val currentLazy: SLazy) : AbstractCoroutineContextElement(Key) {
  object Key : CoroutineContext.Key<LazyRecursionTrackerElement>
}
