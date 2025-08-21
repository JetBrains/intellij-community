// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.coroutines.flow

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * Returns a cold flow, which emits values of [this] flow not more often than the given [timeout][timeMs].
 *
 * Example:
 * ```kotlin
 * flow {
 *   delay(40)
 *   emit(1) // first value is always emitted
 *   delay(40)
 *   emit(2) // skipped because the emission happened within the timeout from the first emission
 *   delay(40)
 *   emit(3) // emitted as the latest value after the timeout from the first emission
 *   delay(40)
 *   emit(4) // last value is emitted after the given timeout
 * }.throttle(100)
 * ```
 * produces the following emissions
 * ```text
 * 1, 3, 4
 * ```
 */
fun <X> Flow<X>.throttle(timeMs: Long): Flow<X> {
  if (timeMs <= 0) {
    return this
  }
  return channelFlow {
    val latch = Channel<Unit>()
    val latchJob = launch(start = CoroutineStart.UNDISPATCHED) {
      while (isActive) {
        latch.send(Unit)
        delay(timeMs)
      }
    }
    try {
      collectLatest {
        latch.receive()
        @Suppress("EXPERIMENTAL_API_USAGE")
        send(it)
      }
    }
    finally {
      latchJob.cancel()
      latch.close()
    }
  }
}

/**
 * If the flow has an element available, then the element is passed into [action]
 * without dispatching as per [CoroutineStart.UNDISPATCHED].
 * When the next element arrives, the [action] is cancelled as in [collectLatest].
 * After that the function delegates to [collectLatest] as is.
 */
suspend fun <X> SharedFlow<X>.collectLatestUndispatched(action: suspend (value: X) -> Unit) {
  coroutineScope {
    val firstJob = launch(start = CoroutineStart.UNDISPATCHED) {
      action(first())
    }
    drop(1).first() // wait for next element to arrive
    firstJob.cancel()
    collectLatest(action)
  }
}

private object UNINITIALIZED

/**
 * Returns a cold flow containing the results of applying the given transform function
 * to each pair of two adjacent elements in this flow.
 *
 * Example:
 * ```kotlin
 * flow {
 *   emit(1)
 *   emit(2)
 *   emit(3)
 *   emit(4)
 * }.zipWithNext { a, b ->
 *   println("($a, $b)")
 * }.collect()
 * ```
 * produces the following output
 * ```
 * (1,2)
 * (2,3)
 * (3,4)
 * ```
 *
 * The returned flow is empty if [this] flow is empty or emits only a single element.
 *
 * See also: [Sequence.zipWithNext]
 */
fun <T, R> Flow<T>.zipWithNext(transform: suspend (a: T, b: T) -> R): Flow<R> {
  return flow {
    var current: Any? = UNINITIALIZED
    collect { value ->
      if (current !== UNINITIALIZED) {
        @Suppress("UNCHECKED_CAST")
        emit(transform(current as T, value))
      }
      current = value
    }
  }
}

/**
 * See: [zipWithNext]
 */
fun <T> Flow<T>.zipWithNext(): Flow<Pair<T, T>> = zipWithNext { a, b -> a to b }

/**
 * Returns a cold flow, which emits values of [this] flow in chunks no often than the given [duration][duration].
 *
 * Example:
 * ```kotlin
 * flow {
 *   delay(40)
 *   emit(1)
 *   delay(40)
 *   emit(2)
 *   delay(120)
 *   emit(3)
 *   delay(120)
 *   emit(4)
 * }.chunked(100.milliseconds)
 * ```
 * produces the following emissions
 * ```text
 * [1, 2], [3], [4]
 * ```
 */
fun <T> Flow<T>.debounceBatch(duration: Duration): Flow<List<T>> = channelFlow {
  val mutex = Mutex()
  val buffer = mutableListOf<T>()
  var job: Job? = null
  collect {
    mutex.withLock {
      buffer.add(it)
      job?.cancel()
      job = launch {
        delay(duration)
        mutex.withLock {
          send(buffer.toList())
          buffer.clear()
        }
      }
    }
  }
}


/**
 * Returns a state flow started in the given coroutine scope and containing the result of applying
 * the given [transform] function to the initial value of the original state flow and each its update.
 */
@ApiStatus.Experimental
fun <T, M> StateFlow<T>.mapStateIn(
  coroutineScope: CoroutineScope,
  started: SharingStarted = SharingStarted.Eagerly,
  transform: (value: T) -> M
): StateFlow<M> {
  return map(transform).stateIn(coroutineScope, started = started, initialValue = transform(value))
}

/**
 * For each distinct value of type [K], launches a coroutine on [scope] and executes [action] on it.
 * The previous action for the same [K] gets canceled when the new one arrives.
 * With each [K] there can be passed a value of type [V] which does not affect cancellation of previous computations,
 * and acts as additional payload.
 *
 * Example:
 * ```kotlin
 * val flow: Flow<Pair<Int, String>>
 *
 * flow.collectLatestCoalescedIn(scope) { key, value ->
 *   println("Started for: $key and $value")
 *   doProcessingOfValue(key, value)
 *   println("Finished for: $key and $value")
 * }
 *
 * flow.tryEmit(1 to 42)
 * flow.tryEmit(2 to 43)
 * flow.tryEmit(1 to 44)
 * ```
 *
 * Will print:
 * ```text
 * Started for: 1 and 42
 * Started for: 2 and 43
 * Started for: 1 and 44
 * Finished for: 2 and 43
 * Finished for: 1 and 44
 * ```
 */
@ApiStatus.Experimental
fun <K : Any, V> Flow<Pair<K, V>>.collectLatestCoalescedIn(scope: CoroutineScope, action: suspend CoroutineScope.(key: K, value: V) -> Unit) {
  val map = ConcurrentHashMap<K, Job>()
  scope.launch(start = CoroutineStart.UNDISPATCHED) {
    collect { (key, value) ->
      val newJob = scope.launch(start = CoroutineStart.LAZY) {
        action(key, value)
      }
      val existingJob = map.put(key, newJob)
      newJob.invokeOnCompletion {
        map.remove(key, newJob)
      }
      existingJob?.cancel()
      newJob.start()
    }
  }
}

/**
 * For each distinct value of type [K], launches a coroutine on [scope] and executes [action] on it.
 * The previous action for the same [K] gets canceled when the new one arrives.
 *
 * Example:
 * ```kotlin
 * val flow: Flow<Int>
 *
 * flow.collectLatestCoalescedIn(scope) { key ->
 *   println("Started for: $key")
 *   doProcessingOfValue(key)
 *   println("Finished for: $key")
 * }
 *
 * flow.tryEmit(1)
 * flow.tryEmit(2)
 * flow.tryEmit(1)
 * ```
 *
 * Will print:
 * ```text
 * Started for: 1
 * Started for: 2
 * Started for: 1
 * Finished for: 2
 * Finished for: 1
 * ```
 */
@ApiStatus.Experimental
fun <K : Any> Flow<K>.collectLatestCoalescedIn(scope: CoroutineScope, action: suspend CoroutineScope.(key: K) -> Unit) {
  return map { it to Unit }.collectLatestCoalescedIn(scope) { key, _ -> action(key) }
}
