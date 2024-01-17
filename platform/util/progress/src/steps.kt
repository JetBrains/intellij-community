// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental
@file:Suppress("DeprecatedCallableAddReplaceWith")

package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.EmptyProgressStep
import com.intellij.platform.util.progress.impl.ProgressStep
import com.intellij.platform.util.progress.impl.ProgressText
import com.intellij.platform.util.progress.impl.ScopedLambda
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.*
import kotlin.coroutines.coroutineContext

/**
 * Takes ownership of the [current step][currentProgressStep],
 * splits the current step into [size] parts, runs [action] with a reporter,
 * which is used to advance fraction in parts of [size].
 *
 * If the current step is already not fresh, the passed reporter is no-op.
 *
 * The reporter **NOT** is allowed to be used concurrently from several coroutines.
 *
 * @param size amount of work. Default: 100 (as in 100%)
 * @return whatever [action] returns
 * @see ProgressStep
 */
suspend inline fun <T> reportSequentialProgress(size: Int = 100, action: (reporter: SequentialProgressReporter) -> T): T {
  return internalCurrentStepAsSequential(size).use {
    action(it.reporter)
  }
}

/**
 * Takes ownership of the [current step][currentProgressStep],
 * splits the current step into [size] parts, runs [action] with a reporter,
 * which is used to advance fraction in parts of [size].
 *
 * If the current step is already not fresh, the passed reporter is no-op.
 *
 * The reporter is allowed to be used concurrently from several coroutines.
 *
 * @param size amount of work. Default: 100 (as in 100%)
 * @return reporter, which is supposed to be used for reporting duration-based progress updates,
 * or no-op reporter if this step is already not fresh
 * @see ProgressStep
 */
suspend fun <T> reportProgress(size: Int = 100, action: suspend (reporter: ConcurrentProgressReporter) -> T): T {
  // TODO consider inline here
  return internalCurrentStepAsConcurrent(size).use { handle ->
    action(handle.reporter)
  }
}

/**
 * A shortcut for [reportProgress] with [coroutineScope] inside for cases when child coroutines are started manually.
 * ```
 * reportProgress { reporter ->
 *   coroutineScope {
 *     launch {
 *       reporter.sizedStep(40) { ... }
 *     }
 *     launch {
 *       reporter.sizedStep(60) { ... }
 *     }
 *   }
 * }
 * // becomes
 * reportProgressScope { reporter ->
 *   launch {
 *     reporter.sizedStep(40) { ... }
 *   }
 *   launch {
 *     reporter.sizedStep(60) { ... }
 *   }
 * }
 * ````
 *
 * This is not needed when the reporter is used for concurrent collections processing:
 * ```
 * reportProgress { reporter ->
 *   items.forEachConcurrent {
 *     // <- coroutine scope is not necessary here
 *     reporter.itemStep { ... }
 *   }
 * }
 * ```
 */
suspend fun <T> reportProgressScope(size: Int = 100, action: suspend CoroutineScope.(reporter: ConcurrentProgressReporter) -> T): T {
  return reportProgress(size) {
    ignoreProgressReportingIn {
      action(it)
    }
  }
}

/**
 * Takes ownership of the [current step][currentProgressStep], runs [action] with a raw reporter.
 *
 * If the current step is already not fresh, the passed reporter is no-op.
 *
 * The reporter is technically allowed to be used concurrently from several coroutines,
 * but the caller is responsible to report concurrent progress updates,
 * and to ensure they don't interfere with each other.
 *
 * ##### Design notes
 *
 * This function accepts [action] because it needs to clean up after whatever was reported from inside [action].
 * The [action] has [CoroutineScope] receiver for consistency with [reportProgress].
 *
 * @return reporter, which is supposed to be used for reporting raw progress updates,
 * or no-op reporter if this step is already not fresh
 */
suspend inline fun <T> reportRawProgress(action: (RawProgressReporter) -> T): T {
  return internalCurrentStepAsRaw().use { handle ->
    action(handle.reporter)
  }
}

/**
 * Sets the text of the [current step][currentProgressStep] to [text], and run [action].
 * [Text][ProgressState.text], which is reported within [action],
 * becomes [details][ProgressState.details] in the current step.
 *
 * @return reporter, which is supposed to be used for reporting duration-based progress updates,
 * or no-op reporter if this step is already not fresh
 */
suspend fun <T> withProgressText(text: ProgressText?, action: ScopedLambda<T>): T {
  if (text == null) {
    return coroutineScope(action)
  }
  return currentProgressStep().withText(text, action)
}

suspend fun <T> ignoreProgressReportingIn(action: suspend CoroutineScope.() -> T): T {
  // It's not possible to throw away a context element
  // => we replace the element with a null-object instead
  return withContext(EmptyProgressStep.asContextElement(), action)
}

/**
 * [forEach] which splits [currentProgressStep] into [Collection.size] steps,
 * and runs [action] with a fresh child [currentProgressStep] in its context.
 */
suspend fun <T> Collection<T>.forEachWithProgress(action: suspend (value: T) -> Unit) {
  reportSequentialProgress(size) { reporter ->
    forEach { item ->
      reporter.itemStep {
        action(item)
      }
    }
  }
}

/**
 * [map] which splits [currentProgressStep] into [Collection.size] steps,
 * and runs [mapper] with a fresh child [currentProgressStep] in its context.
 */
suspend fun <T, R> Collection<T>.mapWithProgress(mapper: suspend (value: T) -> R): List<R> {
  return reportSequentialProgress(size) { reporter ->
    map { item ->
      reporter.itemStep {
        mapper(item)
      }
    }
  }
}

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
 * ```
 * val filtered = items.filterWithProgress(predicate)
 * filtered.forEachWithProgress(action)
 * // becomes
 * items.forEachWithProgress {
 *   if (predicate(it)) {
 *     action(it)
 *   }
 * }
 * ```
 */
@Deprecated("Inline filter step into nearby `mapWithProgress`/`forEachWithProgress`", level = DeprecationLevel.ERROR)
suspend fun <T> Collection<T>.filterWithProgress(concurrent: Boolean = false, predicate: suspend (value: T) -> Boolean): List<T> {
  return filter {
    predicate(it)
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
