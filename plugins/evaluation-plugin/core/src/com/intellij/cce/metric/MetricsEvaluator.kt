package com.intellij.cce.metric

import com.intellij.cce.actions.CompletionStrategy
import com.intellij.cce.core.Session

class MetricsEvaluator private constructor(private val evaluationType: String) {
  companion object {
    fun withDefaultMetrics(evaluationType: String, strategy: CompletionStrategy): MetricsEvaluator {
      val evaluator = MetricsEvaluator(evaluationType)
      evaluator.registerDefaultMetrics(strategy)
      return evaluator
    }
  }

  private val metrics = mutableListOf<Metric>()

  fun registerDefaultMetrics(strategy: CompletionStrategy) {
    if (strategy.completionGolf) {
      registerMetric(CompletionGolfMovesSumMetric())
      registerMetric(CompletionGolfMovesCountNormalised())
      registerMetric(CompletionGolfPerfectLine())
      registerMetric(MeanLatencyMetric())
      registerMetric(MaxLatencyMetric())
      registerMetric(SessionsCountMetric())

      return
    }

    registerMetric(FoundAtMetric(1))
    registerMetric(FoundAtMetric(5))
    registerMetric(RecallMetric())
    registerMetric(MeanLatencyMetric())
    registerMetric(MaxLatencyMetric())
    registerMetric(MeanRankMetric())
    registerMetric(SessionsCountMetric())
  }

  private fun registerMetric(metric: Metric) {
    metrics.add(metric)
  }

  fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator = SuggestionsComparator.DEFAULT): List<MetricInfo> {
    return metrics.map { MetricInfo(it.name, it.evaluate(sessions, comparator).toDouble(), evaluationType, it.valueType) }
  }

  fun result(): List<MetricInfo> {
    return metrics.map { MetricInfo(it.name, it.value, evaluationType, it.valueType) }
  }
}
