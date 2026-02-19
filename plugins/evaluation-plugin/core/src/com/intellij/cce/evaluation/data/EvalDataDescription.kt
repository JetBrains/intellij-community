package com.intellij.cce.evaluation.data

/**
 * Represents exhaustive description for evaluation data, collecting its metadata and presentation information in one place.
 *
 * @param name The name of the evaluation data.
 * @param description Optional detailed description of the evaluation data.
 * @param placement Defines how the data is dumped and restored during evaluation.
 * @param presentation Optional presentation details for how the data should be displayed.
 * @param problemIndicators List of indicators used for identifying issues within the evaluation output.
 */
data class EvalDataDescription<In, Out>(
  val name: String,
  val description: String?,
  override val placement: DataPlacement<In, Out>,
  val presentation: EvalDataPresentation<Out>? = null,
  val problemIndicators: List<ProblemIndicator<Out>> = emptyList(),
) : Bindable<In> {
  val data: EvalData<Out> = EvalData(name, placement)

  val presenter: EvalDataPresenter<Out>? =
    if (presentation == null) null else EvalDataPresenter(EvalData(name, placement), presentation)

  fun problemIndices(props: DataProps): List<Int> =
    placement.restore(props).mapIndexedNotNull { index, value -> if (problemIndicators.any { it.check(props, value) }) index else null }
}

typealias TrivialEvalData<T> = EvalDataDescription<T, T>

sealed interface ProblemIndicator<in T> {
  fun check(props: DataProps, value: T?): Boolean

  class FromMetric(val metricBuilder: () -> EvalMetric) : ProblemIndicator<Any> {
    // we can't pass metric directly because of cycles in initialization
    private val metric by lazy { metricBuilder() }

    override fun check(props: DataProps, value: Any?): Boolean =
      metric.hasProblem(metric.calculateScore(props))
  }

  class FromValue<T>(val predicate: (T) -> Boolean) : ProblemIndicator<T> {
    override fun check(props: DataProps, value: T?): Boolean = value != null && predicate(value)
  }
}

