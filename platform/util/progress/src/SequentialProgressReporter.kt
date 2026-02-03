// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress

import com.intellij.platform.util.progress.impl.ProgressText
import com.intellij.platform.util.progress.impl.ScopedLambda

/**
 * Sequential progress reporter.
 *
 * This reporter is not supposed to be used concurrently
 * because subsequent [nextStep] step ends previous [nextStep],
 * which means it depends on the state after the previous step.
 *
 * #### Example usage
 * ```
 * reportSequentialProgress { reporter ->
 *   val rawData = reporter.indeterminateStep("Requesting data from server") {
 *     requestData()
 *   }
 *   val parsedData = reporter.nextStep(endFraction = 40, "Parsing data") {
 *     parseData(rawData)
 *   }
 *   reporter.nextStep(endFraction = 100) {
 *     processData(parsedData)
 *   }
 * }
 * ```
 */
sealed interface SequentialProgressReporter {

  /**
   * A shortcut for [sizedStep] with size = 0,
   * which means that the fraction of the current reporter is not advanced after the new step is ended,
   * which means that fraction changes in the new step are effectively ignored.
   */
  fun indeterminateStep(text: ProgressText? = null) {
    sizedStep(workSize = 0, text)
  }

  /**
   * A shortcut for [sizedStep] with size = 1.
   *
   * This function is useful to advance the progress by "1 of collection size", for example:
   * ```
   * reportSequentialProgress(items.size) { reporter ->
   *   for (item in items) {
   *     // to advance fraction without changing text
   *     reporter.itemStep()
   *     // or to advance fraction and change text
   *     reporter.itemStep("Processing $item")
   *     // or to pass child via context
   *     reporter.itemStep("Processing $item") {
   *       // fresh
   *     }
   *   }
   * }
   * ```
   */
  fun itemStep(text: ProgressText? = null) {
    sizedStep(workSize = 1, text)
  }

  /**
   * Starts a new step with size = [workSize].
   * Ends previous step if any.
   *
   * Once the new step is ended by starting the next step, the fraction of this reporter is advanced by [workSize].
   * The relative duration of the new step is [workSize].
   *
   * @param workSize value in [0, size of the current reporter], which is used to advance the fraction after the step is ended
   */
  fun sizedStep(workSize: Int, text: ProgressText? = null)

  /**
   * Starts a new step.
   * Ends previous step if any.
   *
   * Once the new step is ended by starting the next step, the fraction of this reporter is set to [endFraction].
   * The relative duration of the new step is the difference between the [endFraction] and the fraction of this reporter.
   * In particular, this means that each subsequent call should request [endFraction]
   * greater than the previously requested [endFraction].
   *
   * @param endFraction value in (0, size of the current reporter], which is used to set the fraction after the step is ended
   */
  fun nextStep(endFraction: Int, text: ProgressText? = null)

  /**
   * [nextStep] which makes the newly started step available inside [action] as [currentProgressStep].
   * Use this to collect reporting from inside [action] and reflect it in the current step.
   */
  suspend fun <T> nextStep(endFraction: Int, text: ProgressText? = null, action: ScopedLambda<T>): T

  /**
   * [sizedStep] which makes the newly started step available inside [action] as [currentProgressStep].
   * Use this to collect reporting from inside [action] and reflect it in the current step.
   */
  suspend fun <T> sizedStep(workSize: Int, text: ProgressText? = null, action: ScopedLambda<T>): T

  /**
   * [indeterminateStep] which makes the newly started step available inside [action] as [currentProgressStep].
   * Use this to collect reporting from inside [action] and reflect it in the current step.
   */
  suspend fun <T> indeterminateStep(text: ProgressText? = null, action: ScopedLambda<T>): T {
    return sizedStep(workSize = 0, text, action)
  }

  /**
   * [itemStep] which makes the newly started step available inside [action] as [currentProgressStep].
   * Use this to collect reporting from inside [action] and reflect it in the current step.
   */
  suspend fun <T> itemStep(text: ProgressText? = null, action: ScopedLambda<T>): T {
    return sizedStep(workSize = 1, text, action)
  }
}
