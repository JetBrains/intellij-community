// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress.impl

import com.intellij.platform.util.progress.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Represents an entity used to send progress updates to.
 * The client code is not supposed to read the progress updates, thus there are no getters.
 *
 * The current step is usually obtained in the beginning of a function,
 * which wants to report progress, using [currentProgressStep].
 *
 * ### Lifecycle
 *
 * A step starts in fresh state (internal fraction is -1.0).
 * The client code can choose how it wants to use the step by invoking one of the functions of this interface.
 * Once chosen, subsequent invocations of functions of this interface have no effect on this step, and return no-op reporters.
 *
 * The start of the first determinate child step (e.g. [SequentialProgressReporter.sizedStep] or [ConcurrentProgressReporter.itemStep])
 * triggers the transition of the current step to the determinate state (internal fraction is 0.0).
 * The start of an indeterminate child step does not affect the fraction of the current step.
 *
 * Indeterminate and determinate child steps can go in any order.
 *
 * Finally, when related action finishes, the step transitions to the final state (internal fraction is 1.0).
 *
 * ### Fraction Scaling
 *
 * Each step fraction spans from 0.0 to 1.0.
 * Each child step fraction also spans from 0.0 to 1.0,
 * and it's scaled to the parent using the end fraction passed to create a given child step.
 * ```
 * | 0.0                    0.4                                     1.0 |
 * | 0.0    child 1      1.0 | 0.0               child 2            1.0 |
 * ```
 *
 * ### Text levels composition
 *
 * When a text is set in the current step, the text of child step becomes details of the current step.
 *
 * Let's implement a function, which processes a collection:
 * ```
 * suspend fun processItems(items: Collection<Int>) {
 *   reportProgress(items.size) { reporter ->
 *     items.forEach { item ->
 *       reporter.nextStep("Processing $item") {
 *         handle(item)
 *       }
 *     }
 *   }
 * }
 * ```
 * `processItems` function reports 1 level of text.
 * If it's called on the top-level, the text reported by the function (`"Processing $item"`) will be displayed in the UI as text:
 * ```
 * withBackgroundProgress(title = "Top Level", ...) {
 *   processItems(listOf(1,2,3))
 * }
 * ```
 * It's also possible to push the text to details level (or ProgressIndicator.text2 in previous terms)
 * by wrapping the call into a step with text:
 * ```
 * withBackgroundProgress(title = "Top Level", ...) {
 *   reportSequentialProgress { reporter ->
 *      reporter.nextStep(endFraction = 10, "Processing items") {
 *        // text from within becomes details in the UI
 *        processItems(listOf(1,2,3))
 *      }
 *   }
 *   // or
 *   withProgressText("Processing items") {
 *     // text from within becomes details in the UI
 *     processItems(listOf(1,2,3))
 *   }
 * }
 * ```
 *
 * ### Concurrency
 *
 * Child steps of the current step are allowed to exist concurrently.
 * The text of the current step is the last reported text of any the child steps.
 * The fraction of the current step is a sum of scaled child fractions.
 *
 * To reason about the growth of end fraction, each end fraction is expected to be greater than the previous one.
 * For example, the following might throw depending on execution order:
 * ```
 * suspend fun run() = reportSequentialProgress { reporter ->
 *   launch {
 *     // will log an error if executed after creating a child step with endFraction = 1.0
 *     reporter.progressStep(endFraction = 50) { ... }
 *   }
 *   launch {
 *     reporter.progressStep(endFraction = 100) { ... }
 *   }
 * }
 * ```
 * Instead, concurrent child steps should be created by specifying the duration:
 * ```
 * suspend fun run() = reportProgress { reporter ->
 *   launch {
 *     // note workSize parameter
 *     reporter.sizedStep(workSize = 0.5) { ... }
 *   }
 *   launch {
 *     reporter.sizedStep(workSize = 0.5) { ... }
 *   }
 * }
 * ```
 *
 * ### Example usage
 * ```
 * withBackgroundProgress(title = "Top Level", ...) {
 *   reportSequentialProgress { reporter ->
 *     reporter.indeterminateStep("Indeterminate Stage")
 *
 *     reporter.nextStep(endFraction = 70, text = "0.7 Part") { innerReporter ->
 *       innerReporter.nextStep(endFraction = 0.4)
 *       innerReporter.nextStep(endFraction = 1.0) {
 *         reportProgress(items.size) { innerMostReporter ->
 *           items.map { item ->
 *             innerMostReporter.itemStep(text = "Processing '${item.presentableText}'") {
 *               ...
 *             }
 *           }
 *         }
 *       }
 *     }
 *
 *     reporter.nextStep(endFraction = 100) {
 *       withProgressText(text = "0.3 Part") {
 *         ...
 *       }
 *     }
 *   }
 * }
 * ```
 * yields:
 * ```
 * |                     Top Level                                       |
 * |  Indeterminate Stage  |       0.7 Part                  |  0.3 Part |
 *                         | 0.4   | Split into `size` items |
 * ```
 */
internal sealed interface ProgressStep {

  fun progressUpdates(): Flow<StepState>

  suspend fun <X> withText(text: ProgressText, action: suspend CoroutineScope.() -> X): X

  fun asConcurrent(size: Int): ConcurrentProgressReporterHandle?

  fun asSequential(size: Int): SequentialProgressReporterHandle?

  fun asRaw(): RawProgressReporterHandle?
}
