// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental

package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.ProgressText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.*
import kotlin.coroutines.coroutineContext

suspend fun <T> indeterminateStep(
  text: ProgressText? = null,
  action: suspend CoroutineScope.() -> T,
): T {
  return durationStep(duration = 0.0, text, action)
}

suspend fun <T> progressStep(
  endFraction: Double,
  text: ProgressText? = null,
  action: suspend CoroutineScope.() -> T,
): T {
  val reporter = coroutineContext.progressReporter
                 ?: return coroutineScope(action)
  return progressStep(reporter, endFraction, text, action)
}

private suspend fun <T> progressStep(
  parent: ProgressReporter,
  endFraction: Double,
  text: ProgressText?,
  action: suspend CoroutineScope.() -> T,
): T {
  return parent.step(endFraction, text).use { step: ProgressReporter ->
    withContext(step.asContextElement(), action)
  }
}

/**
 * Example usage:
 *
 * ```
 * val duration = items.itemDuration()
 * for (item in items) {
 *   durationStep(duration, "Processing $item") {
 *     ...
 *   }
 * }
 * ```
 *
 * @return a fraction duration which should be advanced once the processing of item in [this] is finished
 */
fun Collection<*>.itemDuration(): Double = this.size.itemDuration()

fun Int.itemDuration(): Double = 1.0 / this

/**
 * @see itemDuration
 */
suspend fun <T> durationStep(duration: Double, text: ProgressText? = null, action: suspend CoroutineScope.() -> T): T {
  val reporter = coroutineContext.progressReporter
                 ?: return coroutineScope(action)
  return reporter.durationStep(duration, text).use { step ->
    withContext(step.asContextElement(), action)
  }
}

/**
 * @see FlowCollector
 */
@NonExtendable // instances are provided by the platform
interface TransformCollector<R> {

  /**
   * Send a value to the output of the current transformation.
   * This method is thread-safe and can be invoked concurrently.
   */
  suspend fun out(value: R)
}

/**
 * Splits context progress reporter into N steps, where N = size of [this] collection,
 * each [transform] invocation happens in a context of a separate progress step.
 *
 * Returns a list containing the results of applying the given [transform] function to each value of the original list.
 *
 * [mapWithProgress], [filterWithProgress], [forEachWithProgress] are implemented via this function.
 *
 * ### Example usage
 *
 * #### `transform`
 *
 * ```
 * items.transformWithProgress(concurrent = true) { item ->
 *   when {
 *     condition0 -> {
 *       // transformed into nothing
 *       return@transformWithProgress
 *     }
 *     condition1 -> {
 *       progressStep(endFraction = 1.0, text = "Transforming $item into a single value") {
 *         out(handleItem(item))
 *       }
 *     }
 *     else -> {
 *       indeterminateStep(text = "Transforming $item into multiple values") {
 *         val (a, b) = handlePair(item)
 *         out(a)
 *         out(b)
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * #### `forEach`
 *
 * ```
 * progressStep(endFraction = 0.7, text = "Processing files") {
 *   files.forEachWithProgress(concurrent = false) { file ->
 *     val data = progressStep(endFraction = 0.2, text = "Preprocessing file $file") {
 *       withContext(Dispatchers.IO) {
 *         preprocess(file)
 *       }
 *     }
 *     progressStep(endFraction = 1.0, text = "Processing data $file") {
 *       applyData(data)
 *     }
 *   }
 * }
 * ```
 *
 * @param concurrent `true` if items should be transformed in concurrent, `false` to transform items sequentially
 *
 * @see transform
 */
suspend fun <T, R> Collection<T>.transformWithProgress(
  concurrent: Boolean = false,
  transform: suspend TransformCollector<R>.(value: T) -> Unit,
): List<R> {
  return channelFlow {
    val items = this@transformWithProgress
    val duration = itemDuration()
    val collector = object : TransformCollector<R> {
      override suspend fun out(value: R) {
        send(value)
      }
    }

    suspend fun step(item: T) {
      durationStep(duration, text = null) {
        collector.transform(item)
      }
    }

    if (concurrent) {
      for (item in items) {
        launch {
          step(item)
        }
      }
    }
    else {
      for (item in items) {
        step(item)
      }
    }
  }.toList()
}

/**
 * @see transformWithProgress
 */
suspend fun <T, R> Collection<T>.mapWithProgress(concurrent: Boolean = false, mapper: suspend (value: T) -> R): List<R> {
  return transformWithProgress(concurrent) { item ->
    out(mapper(item))
  }
}

/**
 * @see transformWithProgress
 */
suspend fun <T> Collection<T>.filterWithProgress(concurrent: Boolean = false, predicate: suspend (value: T) -> Boolean): List<T> {
  return transformWithProgress(concurrent) { item ->
    if (predicate(item)) {
      out(item)
    }
  }
}

/**
 * @see transformWithProgress
 */
suspend fun <T> Collection<T>.forEachWithProgress(concurrent: Boolean = false, action: suspend (value: T) -> Unit) {
  transformWithProgress<_, Nothing?>(concurrent) { item ->
    action(item)
  }
}

/**
 * Switches from context [ProgressReporter] to [RawProgressReporter] via [ProgressReporter.rawReporter].
 * This means, that the context [ProgressReporter] is marked raw.
 * If the context [ProgressReporter] already has children, then the caller should wrap this call
 * into [progressStep] or [indeterminateStep] to start a new child progress step, which then can be marked raw.
 *
 * The [action] loses [progressReporter] and receives [rawProgressReporter] in its context instead.
 */
suspend fun <X> withRawProgressReporter(action: suspend CoroutineScope.() -> X): X {
  val progressReporter = coroutineContext.progressReporter
                         ?: return coroutineScope(action)
  return withContext(progressReporter.rawReporter().asContextElement(), action)
}
