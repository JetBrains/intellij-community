package com.intellij.cce.metric

import com.intellij.cce.actions.CompletionStrategy
import com.intellij.cce.core.Session

class MetricsEvaluator private constructor(private val evaluationType: String) {
  companion object {
    fun withDefaultMetrics(evaluationType: String, strategy: CompletionStrategy): MetricsEvaluator {
      val evaluator = MetricsEvaluator(evaluationType)

      if (strategy.completionGolf != null) {
        evaluator.registerCompletionGolfMetrics()
      }
      else {
        evaluator.registerDefaultMetrics()
      }
      return evaluator
    }
  }

  private val metrics = mutableListOf<Metric>()

  fun registerDefaultMetrics() {
    registerMetric(RecallAtMetric(1))
    registerMetric(RecallAtMetric(5))
    registerMetric(RecallMetric())
    registerMetric(MeanLatencyMetric())
    registerMetric(MaxLatencyMetric())
    registerMetric(MeanRankMetric())
    registerMetric(SessionsCountMetric())
  }

  fun registerCompletionGolfMetrics() {
    registerMetrics(createCompletionGolfMetrics())
    registerMetric(MeanLatencyMetric(true))
    registerMetric(MaxLatencyMetric())
    registerMetric(TotalLatencyMetric())
    registerMetric(SessionsCountMetric())
    registerMetric(SuggestionsCountMetric())
  }

  private fun registerMetric(metric: Metric) = metrics.add(metric)

  private fun registerMetrics(metrics: Collection<Metric>) = this.metrics.addAll(metrics)

  fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator = SuggestionsComparator.DEFAULT): List<MetricInfo> {
    val result =  metrics.map {
      MetricInfo(
        name = it.name,
        value = it.evaluate(sessions, comparator).toDouble(),
        confidenceInterval = null,
        evaluationType = evaluationType,
        valueType = it.valueType,
        showByDefault = it.showByDefault
      )
    }
    return result
  }

  fun result(): List<MetricInfo> {
    return metrics.map { MetricInfo(it.name, it.value, it.confidenceInterval(), evaluationType, it.valueType, it.showByDefault) }
  }
}
