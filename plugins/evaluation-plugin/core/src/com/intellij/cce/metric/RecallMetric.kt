package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample
import java.util.stream.Collectors

class RecallMetric : Metric {
  private val sample = Sample()

  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator): Double {
    val listOfCompletions = sessions.stream()
      .flatMap { session -> session.lookups.map { lookup -> Pair(lookup.suggestions, session.expectedText) }.stream() }
      .collect(Collectors.toList())

    val fileSample = Sample()
    listOfCompletions.stream()
      .forEach { (suggests, expectedText) ->
        val value = if (suggests.any { comparator.accept(it, expectedText) }) 1.0 else 0.0
        fileSample.add(value)
        sample.add(value)
      }

    return fileSample.mean()
  }

  override val name: String = "Recall"

  override val valueType = MetricValueType.DOUBLE
}