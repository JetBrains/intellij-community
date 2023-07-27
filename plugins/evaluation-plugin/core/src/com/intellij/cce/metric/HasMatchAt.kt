package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.metric.util.Sample

class HasMatchAt (private val n: Int) : Metric {
  private val sample = Sample()
  override val name = NAME_PREFIX + n
  override val description: String = "Ratio of invocations with matching proposal in top-$n"
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>, comparator: SuggestionsComparator): Double {
    val listOfCompletions = sessions
      .flatMap { session -> session.lookups.map { lookup -> Pair(lookup, session.expectedText) } }

    val fileSample = Sample()
    for (completion in listOfCompletions) {
      val lookup = completion.first
      val indexOfNecessaryCompletion = lookup.suggestions.indexOfFirst {
        matches(it.text.drop(lookup.prefix.length), completion.second.drop(lookup.offset))
      }
      if (indexOfNecessaryCompletion in 0 until n) {
        fileSample.add(1.0)
        sample.add(1.0)
      }
      else {
        fileSample.add(0.0)
        sample.add(0.0)
      }
    }

    return fileSample.mean()
  }

  private fun matches(suggestionTextNormalized: String, expectedTextNormalized: String): Boolean {
    return !(suggestionTextNormalized.isEmpty() || !expectedTextNormalized.startsWith(suggestionTextNormalized))
  }
  companion object {
    const val NAME_PREFIX = "HasMatchAt"
  }
}
