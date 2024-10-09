// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental

package com.intellij.platform.util.coroutines

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.experimental.ExperimentalTypeInference

/**
 * An arbitrary number.
 * CPU count is chosen because it's the default parallelism of [Dispatchers.Default].
 *
 * #### Concurrency vs parallelism
 *
 * Concurrency may be greater than parallelism of the underlying dispatcher.
 * For example, in the following snippet
 * ```
 * launch(Dispatchers.Default.limitedParallelism(1)) {
 *   urls.forEachConcurrent(concurrency = 5) {
 *     val page = withContext(Dispatchers.IO) { requestPage(it) }
 *     val data = compute(page)
 *     withContext(Dispatchers.IO) { storeDataInDb(data) }
 *   }
 * }
 * ```
 * there are at most 5 requests or DB connections, but at most 1 `compute` is executed simultaneously.
 */
internal val DEFAULT_CONCURRENCY: Int = Runtime.getRuntime().availableProcessors()

/**
 * Runs [action] on each item of [this] collection concurrently with specified [concurrency].
 *
 * @param concurrency maximal amount of [action] lambdas run at the same time.
 * When [concurrency] is set to 1, the function effectively behaves like [forEach],
 * i.e. [action] is run sequentially for each item.
 *
 * Default value: [CPU count][Runtime.availableProcessors].
 *
 * @throws IllegalArgumentException if [concurrency] is less or equal to zero
 */
suspend fun <X> Collection<X>.forEachConcurrent(
  concurrency: Int = DEFAULT_CONCURRENCY,
  action: suspend (X) -> Unit,
) {
  require(concurrency > 0)
  val items = this@forEachConcurrent
  when {
    concurrency == 1 -> {
      // sequential
      for (item in items) {
        yield()
        action(item)
      }
    }
    items.size <= concurrency -> {
      // a coroutine per item
      coroutineScope {
        for (item in items) {
          yield()
          launch {
            action(item)
          }
        }
      }
    }
    else -> {
      // X=concurrency coroutines processing items
      coroutineScope {
        val ch = items.asChannelIn(this)
        repeat(concurrency) {
          launch {
            for (item in ch) {
              yield()
              action(item)
            }
          }
        }
      }
    }
  }
}

/**
 * Runs [action] on each item of [this] collection concurrently with specified [concurrency].
 * The [action] is run against [TransformCollector], which allows to yield result items,
 * which are collected into another collection and returned to the caller.
 *
 * See [mapConcurrent] and [filterConcurrent] for example usages, both are implemented via [transformConcurrent].
 *
 * @param concurrency maximal amount of [action] lambdas run at the same time.
 * When [concurrency] is set to 1, [action] is run sequentially for each item.
 *
 * Default value: [CPU count][Runtime.availableProcessors].
 *
 * @return collection of the results of applying the given [action] function to each value of the original list.
 * The order of items in the returned collection in unspecified
 *
 * @throws IllegalArgumentException if [concurrency] is less or equal to zero
 * @see kotlinx.coroutines.flow.transform
 * @see mapConcurrent
 * @see filterConcurrent
 */
@OptIn(ExperimentalTypeInference::class)
suspend fun <T, R> Collection<T>.transformConcurrent(
  concurrency: Int = DEFAULT_CONCURRENCY,
  @BuilderInference action: suspend TransformCollector<R>.(T) -> Unit,
): Collection<R> {
  require(concurrency > 0)
  return channelFlow {
    val collector = ChannelTransformCollector(channel)
    forEachConcurrent(concurrency) {
      collector.action(it)
    }
  }.toList()
}

/**
 * Maps each item of [this] collection to another item with [action] concurrently using [transformConcurrent].
 */
suspend fun <T, R> Collection<T>.mapConcurrent(
  concurrency: Int = DEFAULT_CONCURRENCY,
  action: suspend (T) -> R,
): Collection<R> {
  return transformConcurrent(concurrency) {
    out(action(it))
  }
}

/**
 * Maps each item of [this] collection to another item with [action] concurrently using [transformConcurrent] and collects non-null results.
 */
suspend fun <T, R> Collection<T>.mapNotNullConcurrent(
  concurrency: Int = DEFAULT_CONCURRENCY,
  action: suspend (T) -> R?
): Collection<R> {
  return transformConcurrent(concurrency) { v ->
    action(v)?.let { mv -> out(mv) }
  }
}

/**
 * Filters items of [this] collection according to [action] concurrently using [transformConcurrent].
 */
suspend fun <T> Collection<T>.filterConcurrent(
  concurrency: Int = DEFAULT_CONCURRENCY,
  action: suspend (T) -> Boolean,
): Collection<T> {
  return transformConcurrent(concurrency) {
    if (action(it)) {
      out(it)
    }
  }
}

@Internal
fun <T> Collection<T>.asChannelIn(cs: CoroutineScope): ReceiveChannel<T> {
  @OptIn(ExperimentalCoroutinesApi::class)
  return cs.produce {
    for (item in this@asChannelIn) {
      yield()
      send(item)
    }
  }
}
