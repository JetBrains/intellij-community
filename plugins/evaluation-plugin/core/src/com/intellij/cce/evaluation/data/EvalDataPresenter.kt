package com.intellij.cce.evaluation.data

/**
 * Represents coupling of evaluation data with its presentation details.
 *
 * @property data Evaluation data to be presented.
 */
data class EvalDataPresenter<T>(
  val data: EvalData<T>,
  val presentation: EvalDataPresentation<T>,
)

/**
 * Presentation details such as category, renderer, and dynamic naming.
 */
data class EvalDataPresentation<in T>(
  val category: PresentationCategory,
  val renderer: DataRenderer<T>? = null,
  val dynamicName: DynamicName<T>? = null,
  val ignoreMissingData: Boolean = false,
)

/**
 * Represents the category of evaluation data for cases when we need to present data in groups.
 */
enum class PresentationCategory(val priority: Int, val displayName: String) {
  RESULT(0, "Results"),
  METRIC(1, "Metrics"),
  EXECUTION(2, "Invocation"),
  ANALYSIS(3, "Analysis"),
}
