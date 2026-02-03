package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_CONTEXT_COLLECTION_DURATION_MS

abstract class ContextCollectionDuration : ConfidenceIntervalMetric<Double>() {
  override val value: Double
    get() = compute(sample)

  override fun evaluate(sessions: List<Session>): Double {
    val fileSample = mutableListOf<Double>()
    sessions
      .flatMap { session -> session.lookups }
      .filter(::shouldInclude)
      .forEach {
        val duration = it.contextCollectionDuration()
        if (duration != null) {
          this.coreSample.add(duration)
          fileSample.add(duration)
        }
      }
    return compute(fileSample)
  }

  abstract override fun compute(sample: List<Double>): Double

  open fun shouldInclude(lookup: Lookup) = true
}

class ContextCollectionMeanDuration(private val filterZeroes: Boolean = false) : ContextCollectionDuration() {
  override val name: String = "Context Collection Mean Duration"
  override val valueType = MetricValueType.DOUBLE
  override val description: String = "Average duration of context collection"
  override val showByDefault: Boolean = true

  override fun compute(sample: List<Double>): Double = sample.average()

  override fun shouldInclude(lookup: Lookup) = !filterZeroes || lookup.contextCollectionDuration()?.let { it > 0 } == true
}

private fun Lookup.contextCollectionDuration(): Double? = additionalInfo[AIA_CONTEXT_COLLECTION_DURATION_MS]?.toString()?.toDoubleOrNull()
