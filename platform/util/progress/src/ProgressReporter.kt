// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental

package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.ProgressText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.*
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Represents an entity used to send progress updates to.
 * The client code is not supposed to read the progress updates, thus there are no getters.
 *
 * Example usage:
 * ```
 * withBackgroundProgress(title = "Top Level", ...) {
 *   indeterminateStep("Indeterminate Stage") { ... }
 *   progressStep(endFraction = 0.3, text = "0.3 Part") { ... }
 *   progressStep(endFraction = 1.0, text = "0.7 Part") {
 *     progressStep(endFraction = 0.4) { ... }
 *     progressStep(endFraction = 1.0) {
 *       items.mapWithProgress(concurrent = true) { item ->
 *         progressStep(text = "Processing '${item.presentableText}'") {
 *           ...
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 * yields:
 * ```
 * |                     Top Level                           |
 * |  Indeterminate Stage  | 0.3 Part |       0.7 Part       |
 *                                    | 0.4   | 10 items     |
 * ```
 *
 * ### Legend
 * A step is called "indeterminate" if its duration in the parent reporter is zero.
 *
 * ### Lifecycle
 * A reporter starts in indeterminate state (internal fraction is -1.0).
 * The start of the first determinate child step (i.e. a child step with end fraction >= 0.0)
 * triggers the transition of the current reporter to the determinate state (internal fraction is 0.0).
 * The start of an indeterminate child step does not affect the fraction of the current reporter.
 *
 * Indeterminate and determinate child steps can go in any order.
 *
 * Finally, [close] transitions the reporter to the final state (internal fraction is 1.0).
 *
 * ### Fraction Scaling
 *
 * Each reporter fraction spans from 0.0 to 1.0.
 * Each child reporter fraction also spans from 0.0 to 1.0,
 * and it's scaled to the parent using the end fraction passed to create a given child step.
 * ```
 * | 0.0                    0.4                                     1.0 |
 * | 0.0    child 1      1.0 | 0.0               child 2            1.0 |
 * ```
 *
 * ### Concurrency
 *
 * Child steps of the current reporter are allowed to exist concurrently.
 * The text of the current reporter is the last reported text of any the child steps.
 * The fraction of the current reporter is a sum of scaled child fractions.
 *
 * To reason about the growth of end fraction, each end fraction is expected to be greater than the previous one.
 * For example, the following might throw depending on execution order:
 * ```
 * fun CoroutineScope.run(topLevelStep: ProgressReporter) {
 *   launch {
 *     // will throw if executed after creating a child step with endFraction = 1.0
 *     topLevelStep.progressStep(endFraction = 0.5) { ... }
 *   }
 *   launch {
 *     topLevelStep.progressStep(endFraction = 1.0) { ... }
 *   }
 * }
 * ```
 * Instead, concurrent child steps should be created by specifying the duration:
 * ```
 * fun CoroutineScope.run(topLevelStep: ProgressReporter) {
 *   launch {
 *     // note duration parameter
 *     topLevelStep.durationStep(duration = 0.5) { ... }
 *   }
 *   launch {
 *     topLevelStep.durationStep(duration = 0.5) { ... }
 *   }
 * }
 * ```
 *
 * ### Examples
 *
 * #### How to process a list sequentially
 * ```
 * val items: List<X> = ...
 * withBackgroundProgress(...) {
 *   items.mapWithProgress {
 *     // will show the item string as progress text in the UI
 *     progressStep(endFraction = 1.0, text = item.presentableString()) {
 *       ...
 *     }
 *   }
 *   // or
 *   progressStep(endFraction = 1.0, text = "Processing items") {
 *     items.mapWithProgress {
 *       // will show the item string as progress details in the UI
 *       progressStep(endFraction = 1.0, text = item.presentableString()) {
 *         ...
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * #### How to process a list concurrently
 *
 * The parallelism if controlled by the context coroutine dispatcher.
 * ```
 * val items: List<X> = ...
 * withBackgroundProgress(...) {
 *   items.mapWithProgress(concurrent = true) {
 *     // will show the item string as progress text in the UI
 *     progressStep(text = item.presentableString()) {
 *       ...
 *     }
 *   }
 *   // or
 *   progressStep(text = "Processing items", endFraction = ...) {
 *     items.mapWithProgress(concurrent = true) {
 *       // will show the item string as progress details in the UI
 *       progressStep(text = item.presentableString()) {
 *         ...
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * @see com.intellij.platform.util.progress.impl.TextDetailsProgressReporter
 */
@Experimental
@NonExtendable
interface ProgressReporter : AutoCloseable {

  /**
   * Starts a child step.
   *
   * @param text text of the current step.
   * If the text is `null`, then the returned child step text will be used as text of this reporter.
   * If the text is not `null`, then the text will be used as text of this reporter,
   * while the text of the returned child step will be used as details of this reporter.
   *
   * @param endFraction value greater than 0.0 and less or equal to 1.0,
   * which is used to advance the fraction of the current step after the returned child step is [closed][close].
   * The duration of the returned step would be the difference between the previous [endFraction] and the currently requested one,
   * which means that each subsequent call should request [endFraction] greater than the previously requested [endFraction].
   *
   * @see close
   */
  fun step(endFraction: Double, text: ProgressText?): ProgressReporter

  /**
   * Starts a child step.
   *
   * @param duration duration of the step relative to this reporter.
   * It's used to advance the fraction of the current step after the returned child step is [closed][close].
   * The sum of durations of all child steps cannot exceed 1.0.
   * If the requested value is 0.0 the returned step is indeterminate,
   * the fraction advancements inside the returned step will be ignored in this reporter.
   *
   * @param text text of the current step.
   * If the text is `null`, then the returned child step text will be used as text of this reporter.
   * If the text is not `null`, then the text will be used as text of this reporter,
   * while the text of the returned child step will be used as details of this reporter.
   */
  fun durationStep(duration: Double, text: ProgressText?): ProgressReporter

  /**
   * Marks current step as completed.
   * This usually causes the progress fraction to advance in the UI.
   *
   * **It's mandatory to call this method**. Internally, this method unsubscribes from [child step][step] updates.
   *
   * Example:
   * ```
   * runUnderProgress { topLevelStep ->
   *   val childStep = topLevelStep.step("Part 1", 0.5)
   *   try {
   *     // do stuff
   *   }
   *   finally {
   *     childStep.close() // will advance the fraction to 0.5
   *   }
   * }
   * ```
   */
  override fun close()

  /**
   * Makes this reporter raw.
   * A raw reporter cannot start child steps.
   * A reporter which has child steps cannot be made raw.
   * This function cannot be called twice.
   *
   * @return a handle to feed the progress updates
   */
  fun rawReporter(): RawProgressReporter
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
  concurrent: Boolean,
  transform: suspend TransformCollector<R>.(value: T) -> Unit,
): List<R> {
  return channelFlow {
    val items = this@transformWithProgress
    val duration = 1.0 / items.size
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
suspend fun <T, R> Collection<T>.mapWithProgress(concurrent: Boolean, mapper: suspend (value: T) -> R): List<R> {
  return transformWithProgress(concurrent) { item ->
    out(mapper(item))
  }
}

/**
 * @see transformWithProgress
 */
suspend fun <T> Collection<T>.filterWithProgress(concurrent: Boolean, predicate: suspend (value: T) -> Boolean): List<T> {
  return transformWithProgress(concurrent) { item ->
    if (predicate(item)) {
      out(item)
    }
  }
}

/**
 * @see transformWithProgress
 */
suspend fun <T> Collection<T>.forEachWithProgress(concurrent: Boolean, action: suspend (value: T) -> Unit) {
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

@Internal // clients are not supposed to put reporter into context
fun ProgressReporter.asContextElement(): CoroutineContext.Element = ProgressReporterElement.Step(this)
val CoroutineContext.progressReporter: ProgressReporter? get() = (this[ProgressReporterElement] as? ProgressReporterElement.Step)?.reporter
val CoroutineScope.progressReporter: ProgressReporter? get() = coroutineContext.progressReporter

@Internal // clients are not supposed to put reporter into context
fun RawProgressReporter.asContextElement(): CoroutineContext.Element = ProgressReporterElement.Raw(this)
val CoroutineContext.rawProgressReporter: RawProgressReporter? get() = (this[ProgressReporterElement] as? ProgressReporterElement.Raw)?.reporter
val CoroutineScope.rawProgressReporter: RawProgressReporter? get() = coroutineContext.rawProgressReporter

private sealed class ProgressReporterElement : AbstractCoroutineContextElement(ProgressReporterElement) {
  companion object : CoroutineContext.Key<ProgressReporterElement>
  class Step(val reporter: ProgressReporter) : ProgressReporterElement()
  class Raw(val reporter: RawProgressReporter) : ProgressReporterElement()
}
