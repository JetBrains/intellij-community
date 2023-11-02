// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental

package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.ProgressText
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable

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
