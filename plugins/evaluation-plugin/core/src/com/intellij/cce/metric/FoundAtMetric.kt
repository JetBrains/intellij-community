package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample
import java.util.stream.Collectors

class FoundAtMetric(private val n: Int) : Metric {
  private val sample = Sample()

  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator): Double {
    val listOfCompletions = sessions.stream()
      .flatMap { session -> session.lookups.map { lookup -> Pair(lookup.suggestions, session.expectedText) }.stream() }
      .collect(Collectors.toList())

    val fileSample = Sample()
    for (completion in listOfCompletions) {
      val indexOfNecessaryCompletion = completion.first.indexOfFirst { comparator.accept(it, completion.second) }
      if (indexOfNecessaryCompletion in 0 until n) {
        fileSample.add(1.0)
        sample.add(1.0)
      }
      else if (completion.first.isNotEmpty()) {
        fileSample.add(0.0)
        sample.add(0.0)
      }
    }

    return fileSample.mean()
  }

  override val name: String = "Found@$n"

  override val valueType = MetricValueType.DOUBLE
}