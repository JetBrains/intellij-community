package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_TEST_BRANCH_COVERAGE
import com.intellij.cce.metric.util.Sample

class BranchCoverageMetric : Metric {
  private val sample = Sample()
  override val name: String = "Branch Coverage"
  override val description: String = "Percentage of branches covered by generated tests in unit under test "
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = sample.mean()

  override fun evaluate(sessions: List<Session>): Double {
    val fileSample = Sample()

    sessions
      .flatMap { session -> session.lookups }
      .forEach {
        val branchCoverage =
          it.additionalInfo.getOrDefault(AIA_TEST_BRANCH_COVERAGE, 0.0) as Double

        if (branchCoverage < 0) return Double.NaN

        sample.add(branchCoverage)
        fileSample.add(branchCoverage)
      }
    return fileSample.mean()
  }

}