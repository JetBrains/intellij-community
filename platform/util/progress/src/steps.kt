// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental
@file:Suppress("DeprecatedCallableAddReplaceWith", "UNUSED_PARAMETER")

package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.EmptyProgressStep
import com.intellij.platform.util.progress.impl.ProgressStep
import com.intellij.platform.util.progress.impl.ProgressText
import com.intellij.platform.util.progress.impl.ScopedLambda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Experimental

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

// <editor-fold desc="Deprecated stuff">
@Deprecated("Use `SequentialProgressReporter.indeterminateStep`")
suspend fun <T> indeterminateStep(
  text: ProgressText? = null,
  action: suspend CoroutineScope.() -> T,
): T {
  return coroutineScope(action)
}

@Deprecated("Use `SequentialProgressReporter.sizedStep`")
suspend fun <T> progressStep(
  endFraction: Double,
  text: ProgressText? = null,
  action: suspend CoroutineScope.() -> T,
): T {
  return coroutineScope(action)
}

@Deprecated("Pass `Collection.size` as argument to `reportProgress` or `reportSequentialProgress`")
fun Collection<*>.itemDuration(): Double = this.size.itemDuration()

@Deprecated("Pass `Collection.size` as argument to `reportProgress` or `reportSequentialProgress`")
fun Int.itemDuration(): Double = 1.0 / this

@Deprecated("Use `ConcurrentProgressReporter.sizedStep`")
suspend fun <T> durationStep(duration: Double, text: ProgressText? = null, action: suspend CoroutineScope.() -> T): T {
  return coroutineScope(action)
}

@Deprecated(
  "Use `mapConcurrent` with `reportProgress`," +
  " or `map` with `reportProgress` or `reportSequentialProgress`",
)
suspend fun <T, R> Collection<T>.mapWithProgress(concurrent: Boolean = false, mapper: suspend (value: T) -> R): List<R> {
  return map {
    mapper(it)
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

@Deprecated(
  "Use `forEachConcurrent` with `reportProgress`," +
  " or `forEach` with `reportProgress` or `reportSequentialProgress`",
)
suspend fun <T> Collection<T>.forEachWithProgress(concurrent: Boolean = false, action: suspend (value: T) -> Unit) {
  forEach {
    action(it)
  }
}

@Deprecated("Use `reportRawProgress`")
suspend fun <X> withRawProgressReporter(action: suspend CoroutineScope.() -> X): X {
  return reportRawProgress { reporter ->
    withContext(reporter.asContextElement(), action)
  }
}

// </editor-fold>
