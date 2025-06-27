package com.intellij.cce.metric

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.REFERENCE_START_LINE_PROPERTY
import com.intellij.cce.evaluable.REFERENCE_END_LINE_PROPERTY
import com.intellij.cce.evaluable.START_LINES_PROPERTY
import com.intellij.cce.evaluable.END_LINES_PROPERTY
import com.intellij.cce.metric.util.Sample
import com.intellij.cce.metric.util.computeIOU
import kotlin.collections.forEach


abstract class RangeMetricBase : ConfidenceIntervalMetric<Double>() {
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = compute(sample)

  override fun compute(sample: List<Double>): Double = sample.average()

  override fun evaluate(sessions: List<Session>): Double {
    // this metric is used with an assumption that the session contains only one lookup and all data is stored there
    val lookups = sessions.flatMap { session -> session.lookups }
    val fileSample = Sample()
    lookups.forEach { lookup ->
      val referenceRange = Pair((lookup.additionalInfo[REFERENCE_START_LINE_PROPERTY] as? Double ?: -1).toInt(),
                                (lookup.additionalInfo[REFERENCE_END_LINE_PROPERTY] as? Double ?: -1).toInt())
      val predictedRanges = (lookup.additionalInfo[START_LINES_PROPERTY] as? List<Double> ?: emptyList())
        .zip(lookup.additionalInfo[END_LINES_PROPERTY] as? List<Double> ?: emptyList()).map {
          Pair(it.first.toInt(), it.second.toInt())
        }

      calculateMetric(predictedRanges, referenceRange, fileSample)
    }
    return fileSample.mean()
  }

  abstract fun calculateMetric(predictedRanges: List<Pair<Int, Int>>, referenceRange: Pair<Int, Int>, fileSample: Sample)
}

interface Scorer {
  fun computeScore(predictedRange: Pair<Int, Int>, referenceRange: Pair<Int, Int>): Double
}

interface IOUScorer : Scorer {
  override fun computeScore(predictedRange: Pair<Int, Int>, referenceRange: Pair<Int, Int>): Double {
    return computeIOU(predictedRange, referenceRange)
  }
}

interface PerfectOverlapScorer : Scorer {
  override fun computeScore(predictedRange: Pair<Int, Int>, referenceRange: Pair<Int, Int>): Double {
    val iou = computeIOU(predictedRange, referenceRange)
    return if (iou == 1.0) 1.0 else 0.0
  }
}

abstract class PrecisionRangeMetricBase : RangeMetricBase(), Scorer {
  override fun calculateMetric(predictedRanges: List<Pair<Int, Int>>, referenceRange: Pair<Int, Int>, fileSample: Sample) {
    if (predictedRanges.isEmpty()) return
    val bestOverlap = predictedRanges.maxOfOrNull { computeScore(it, referenceRange) } ?: 0.0
    (listOf(bestOverlap) + List(predictedRanges.size - 1) { 0.0 }).forEach {
      fileSample.add(it)
      coreSample.add(it)
    }
  }
}

abstract class RecallRangeMetricBase : RangeMetricBase(), Scorer {
  override fun calculateMetric(predictedRanges: List<Pair<Int, Int>>, referenceRange: Pair<Int, Int>, fileSample: Sample) {
    if (referenceRange.first == -1 || referenceRange.second == -1) return
    val bestOverlap = predictedRanges.maxOfOrNull { computeScore(it, referenceRange) } ?: 0.0
    fileSample.add(bestOverlap)
    coreSample.add(bestOverlap)
  }
}


class PerfectOverlapPrecisionMetric : PrecisionRangeMetricBase(), PerfectOverlapScorer {
  override val name = "Perfect Overlap Precision"
  override val description: String = "Ratio of predicted ranges that overlap with reference ranges"
}

class PerfectOverlapRecallMetric : RecallRangeMetricBase(), PerfectOverlapScorer {
  override val name = "Perfect Overlap Recall"
  override val description: String = "Ratio of reference ranges that overlap with predicted ranges"
}

class IOUPrecisionMetric : PrecisionRangeMetricBase(), IOUScorer {
  override val name = "IoU Precision"
  override val description: String = "Sum of IoU between predicted & reference range divided by total number of predicted ranges"
}

class IOURecallMetric : RecallRangeMetricBase(), IOUScorer {
  override val name = "IoU Recall"
  override val description: String = "Sum of IoU between predicted & reference range divided by total number of reference ranges"
}