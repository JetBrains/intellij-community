// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.ProgressText
import com.intellij.platform.util.progress.impl.ScopedLambda

/**
 * This reporter can be used concurrently.
 *
 * #### Example usage
 * ```
 * reportProgress { reporter ->
 *   launch {
 *     reporter.sizedStep(60) {
 *       doX()
 *     }
 *   }
 *   async {
 *     reporter.indeterminateStep {
 *       preparingY()
 *     }
 *   }
 *   reporter.sizedStep(40, "Doing Z") {
 *     doZ()
 *   }
 * }
 * ```
 *
 * #### Example usage
 * ```
 * val ints: List<Int> = ...
 * reportProgress(ints.size) { reporter ->
 *   for (value in ints) {
 *     reporter.itemStep("Processing $value") {
 *       handle(value)
 *     }
 *   }
 *   // or
 *   ints.forEachConcurrent { value ->
 *     reporter.itemStep("Processing $value") {
 *       handle(value)
 *     }
 *   }
 * }
 * ```
 */
sealed interface ConcurrentProgressReporter {

  /**
   * A shortcut for [sizedStep] with size = 0,
   * which means that the fraction of the current reporter is not advanced after the new step is ended,
   * which means that fraction changes in the new step are effectively ignored.
   */
  suspend fun <T> indeterminateStep(text: ProgressText? = null, action: ScopedLambda<T>): T {
    return sizedStep(workSize = 0, text = text, action)
  }

  /**
   * A shortcut for [sizedStep] with size = 1.
   *
   * This function is useful to advance the progress by "1 of collection size", for example:
   * ```
   * reportProgress(items.size) { reporter ->
   *   for (item in items) {
   *     // to advance fraction without changing text
   *     reporter.itemStep {
   *       // fresh step in context
   *     }
   *     // or to advance fraction and change text
   *     reporter.itemStep("Processing $item") {
   *       // fresh step in context
   *     }
   *   }
   * }
   * ```
   */
  suspend fun <T> itemStep(text: ProgressText? = null, action: ScopedLambda<T>): T {
    return sizedStep(workSize = 1, text = text, action)
  }

  /**
   * Runs [action] with a fresh [currentProgressStep] in its context.
   *
   * Once [action] returns, the fraction of the current is advanced by [workSize] / size of this reporter.
   *
   * @param workSize value in [0, size of the current reporter], which is used to advance the fraction after the step is ended
   * @param text text of the current reporter:
   * - If the text is `null`, then the child step text will be used as text of this reporter.
   * - If the text is not `null`, then it will be used as text of this reporter,
   * while the text of the child step will be used as details of this reporter.
   *
   * @return whatever [action] returns
   */
  suspend fun <T> sizedStep(workSize: Int, text: ProgressText? = null, action: ScopedLambda<T>): T
}
