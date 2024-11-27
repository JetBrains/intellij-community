package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_TEST_LINE_COVERAGE
import com.intellij.cce.metric.util.Sample

class LineCoverageMetric : Metric {
  private val sample = Sample()
  override val name: String = "Line Coverage"
  override val description: String = "Percentage of lines covered by generated tests in unit under test "
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>): Double {
    if (sessions.isEmpty()) return Double.NaN

    val fileSample = Sample()

    sessions
      .flatMap { session -> session.lookups }
      .forEach {
        val lineCoverage = it.additionalInfo.getOrDefault(AIA_TEST_LINE_COVERAGE, 0.0) as? Double ?: return Double.NaN

        if (lineCoverage < 0) return Double.NaN

        sample.add(lineCoverage)
        fileSample.add(lineCoverage)
      }
    return fileSample.mean()
  }

}