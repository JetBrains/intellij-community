package com.intellij.cce.metric

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.REFERENCE_NAMED_RANGE_PROPERTY
import com.intellij.cce.evaluable.PREDICTED_NAMED_RANGE_PROPERTY
import com.intellij.cce.evaluation.data.NamedRange
import com.intellij.cce.metric.util.CloudSemanticSimilarityCalculator
import com.intellij.cce.metric.util.Sample
import com.intellij.cce.metric.util.computeIOU
import com.intellij.cce.metric.util.matchRanges
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.async
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
      val referenceRanges = getFromProperty(lookup, REFERENCE_NAMED_RANGE_PROPERTY)
      val predictedRanges = getFromProperty(lookup, PREDICTED_NAMED_RANGE_PROPERTY)

      calculateMetric(getMatchedRanges(predictedRanges, referenceRanges), predictedRanges, referenceRanges, fileSample)
    }
    return fileSample.mean()
  }

  fun getFromProperty(lookup: Lookup, propertyKey: String): List<NamedRange> {
    val gson = Gson()
    val namedRanges = lookup.additionalInfo[propertyKey] ?: return emptyList()
    val ranges = namedRanges as? JsonElement ?: gson.toJsonTree(namedRanges)
    return gson.fromJson(ranges, Array<NamedRange>::class.java).toList()
  }

  fun getMatchedRanges(predictedRanges: List<NamedRange>, referenceRanges: List<NamedRange>): Map<NamedRange, NamedRange> {
    return matchRanges(referenceRanges, predictedRanges)
  }

  abstract fun calculateMetric(matchedRanges: Map<NamedRange, NamedRange>, predictedRanges: List<NamedRange>, referenceRanges: List<NamedRange>, fileSample: Sample)
}

interface Scorer {
  fun computeScore(predictedRange: NamedRange, referenceRange: NamedRange): Double
}

interface IOUScorer : Scorer {
  override fun computeScore(predictedRange: NamedRange, referenceRange: NamedRange): Double {
    return computeIOU(predictedRange, referenceRange)
  }
}

interface PerfectOverlapScorer : Scorer {
  override fun computeScore(predictedRange: NamedRange, referenceRange: NamedRange): Double {
    val iou = computeIOU(predictedRange, referenceRange)
    return if (iou == 1.0) 1.0 else 0.0
  }
}

abstract class PrecisionRangeMetricBase : RangeMetricBase(), Scorer {
  override fun calculateMetric(matchedRanges: Map<NamedRange, NamedRange>, predictedRanges: List<NamedRange>, referenceRanges: List<NamedRange>, fileSample: Sample) {
    if (predictedRanges.isEmpty()) {
      val value = if (referenceRanges.isEmpty()) 1.0 else 0.0
      fileSample.add(value)
      coreSample.add(value)
      return
    }

    val bestOverlaps = matchedRanges.map { computeScore(it.key, it.value) }
    (bestOverlaps + List(predictedRanges.size - matchedRanges.size) { 0.0 }).forEach {
      fileSample.add(it)
      coreSample.add(it)
    }
  }
}

abstract class RecallRangeMetricBase : PrecisionRangeMetricBase(), Scorer {
  override fun calculateMetric(matchedRanges: Map<NamedRange, NamedRange>, predictedRanges: List<NamedRange>, referenceRanges: List<NamedRange>, fileSample: Sample) {
    super.calculateMetric(matchedRanges, referenceRanges, predictedRanges, fileSample)
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

class SemanticSimilarityRangeMetric(val cloudSemanticSimilarityCalculator: CloudSemanticSimilarityCalculator) : RangeMetricBase() {
  override val name = "Semantic Similarity"
  override val description: String = "Semantic Similarity between texts of best matched predicted & reference ranges"

  private val project: Project
    get() = ProjectManager.getInstance().defaultProject

  override fun calculateMetric(matchedRanges: Map<NamedRange, NamedRange>, predictedRanges: List<NamedRange>, referenceRanges: List<NamedRange>, fileSample: Sample) {
    if (matchedRanges.isEmpty()) {
      val value = if (referenceRanges.isEmpty()) 1.0 else 0.0
      fileSample.add(value)
      coreSample.add(value)
      return
    }
    matchedRanges.forEach { (predicted, reference) ->
      val score = runBlockingCancellable {
        async {
          cloudSemanticSimilarityCalculator.calculateCosineSimilarity(
            project,
            predicted.text,
            reference.text
          )
        }.await()
      }
      fileSample.add(score)
      coreSample.add(score)
    }
  }
}