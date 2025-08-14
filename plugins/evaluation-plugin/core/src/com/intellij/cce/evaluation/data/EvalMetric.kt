package com.intellij.cce.evaluation.data

import com.intellij.cce.metric.DataMetric
import com.intellij.cce.metric.Metric

data class EvalMetric(
  val threshold: Double? = null,
  val dependencies: MetricDependencies<*> = MetricDependencies.Empty,
  val showInCard: Boolean = true,
  val metricBuilder: () -> Metric, // we need to create a new instance each time to overpass stateful nature of metric
) {
  val name: String = buildMetric().name

  fun buildMetric(): Metric = metricBuilder()

  fun hasProblem(score: Double): Boolean = threshold?.let { score < it } == true

  fun calculateScore(props: DataProps): Double {
    val session = props.session.copy().also {
      it.addLookup(props.lookup)
    }
    return buildMetric().evaluate(listOf(session)).toDouble()
  }

  companion object {
    fun <T> fromIndicators(name: String, data: EvalDataDescription<*, T>): EvalMetric {
      fun value(t: T, props: DataProps): Double = if (data.problemIndicators.any { it.check(props, t) }) 0.0 else 1.0
      return EvalMetric(1.0) { DataMetric(data, ::value, name) }
    }

    fun <T> fromIndicators(data: EvalDataDescription<*, T>): EvalMetric {
      return fromIndicators(data.name, data)
    }

    fun fromDoubleIndicators(data: EvalDataDescription<*, Double>): EvalMetric {
      val name = data.name
      return EvalMetric(0.0) { DataMetric(data, { value, _ -> value }, name) }
    }
  }
}

sealed interface MetricDependencies<T> {
  val requiredData: List<EvalData<*>>

  val renderer: DataRenderer<T>?
  fun renderableValue(props: DataProps): T?

  fun failedRequirements(data: List<EvalData<*>>): List<EvalData<*>> = requiredData.filter { !data.contains(it) }

  data object Empty : MetricDependencies<Unit> {
    override val requiredData: List<EvalData<*>> = emptyList()

    override val renderer: DataRenderer<Unit>? = null
    override fun renderableValue(props: DataProps): Unit? = null
  }

  class Single<T>(
    private val desc: EvalDataDescription<*, T>
  ) : MetricDependencies<T> {
    override val requiredData: List<EvalData<*>> = listOf(desc.data)

    override val renderer: DataRenderer<T>? = desc.presentation?.renderer
    override fun renderableValue(props: DataProps): T? = desc.placement.restore(props).firstOrNull()
  }

  class Couple<T, U, R>(
    private val first: EvalData<T>,
    private val second: EvalData<U>,
    override val renderer: DataRenderer<R>?,
    private val transform: (T, U) -> R?
  ) : MetricDependencies<R> {
    override val requiredData: List<EvalData<*>> = listOf(first, second)

    override fun renderableValue(props: DataProps): R? {
      val firstValue = first.placement.restore(props).firstOrNull() ?: return null
      val secondValue = second.placement.restore(props).firstOrNull() ?: return null
      return transform(firstValue, secondValue)
    }
  }

  companion object {
    operator fun invoke(desc: EvalDataDescription<*, *>): Single<*> = Single(desc)

    operator fun <T, U, R> invoke(
      first: EvalDataDescription<*, T>,
      second: EvalDataDescription<*, U>,
      renderer: DataRenderer<R>,
      transform: (T, U) -> R?
    ): MetricDependencies<R> = Couple(first.data, second.data, renderer, transform)
  }
}

