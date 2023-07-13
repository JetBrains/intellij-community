// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import com.intellij.cce.core.Session

class MetricsEvaluator private constructor(private val evaluationType: String) {
  companion object {
    fun withDefaultMetrics(evaluationType: String): MetricsEvaluator {
      val evaluator = MetricsEvaluator(evaluationType)
      evaluator.registerDefaultMetrics()
      return evaluator
    }

    fun withMetrics(evaluationType: String, metrics: List<Metric>): MetricsEvaluator {
      val evaluator = MetricsEvaluator(evaluationType)
      evaluator.registerMetrics(metrics)
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

  private fun registerMetric(metric: Metric) = metrics.add(metric)

  private fun registerMetrics(metrics: Collection<Metric>) = this.metrics.addAll(metrics)

  fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator = SuggestionsComparator.DEFAULT): List<MetricInfo> {
    val result = metrics.map {
      MetricInfo(
        name = it.name,
        description = it.description,
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
    return metrics.map {
      MetricInfo(it.name, it.description, it.value, it.confidenceInterval(), evaluationType, it.valueType, it.showByDefault)
    }
  }
}
