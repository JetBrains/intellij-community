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
    registerMetric(SuggestionsCountMetric())
  }

  fun registerCompletionGolfMetrics() {
    registerMetrics(createCompletionGolfMetrics())
    registerMetric(MeanLatencyMetric(true))
    registerMetric(MaxLatencyMetric())
    registerMetric(TotalLatencyMetric())
    registerMetric(SessionsCountMetric())
  }

  private fun registerMetric(metric: Metric) = metrics.add(metric)

  private fun registerMetrics(metrics: Collection<Metric>) = this.metrics.addAll(metrics)

  fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator = SuggestionsComparator.DEFAULT): List<MetricInfo> {
    return metrics.map { MetricInfo(it.name, it.evaluate(sessions, comparator).toDouble(), evaluationType, it.valueType, it.showByDefault) }
  }

  fun result(): List<MetricInfo> {
    return metrics.map { MetricInfo(it.name, it.value, evaluationType, it.valueType, it.showByDefault) }
  }
}
