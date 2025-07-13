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
import com.intellij.cce.metric.util.overlapWithinRanges
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.async
import kotlin.collections.forEach


abstract class RangeMetricBase : ConfidenceIntervalMetric<Double>(), RangeFilter {
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
      val referenceRanges = filter(getFromProperty(lookup, REFERENCE_NAMED_RANGE_PROPERTY))
      val predictedRanges = filter(getFromProperty(lookup, PREDICTED_NAMED_RANGE_PROPERTY))

      calculateMetric(getMatchedRanges(referenceRanges, predictedRanges), predictedRanges.size, referenceRanges.size, fileSample)
    }
    return fileSample.mean()
  }

  fun getFromProperty(lookup: Lookup, propertyKey: String): List<NamedRange> {
    val gson = Gson()
    val namedRanges = lookup.additionalInfo[propertyKey] ?: return emptyList()
    val ranges = namedRanges as? JsonElement ?: gson.toJsonTree(namedRanges)
    return gson.fromJson(ranges, Array<NamedRange>::class.java).toList()
  }

  open fun getMatchedRanges(predictedRanges: List<NamedRange>, referenceRanges: List<NamedRange>): Map<NamedRange, NamedRange> {
    return matchRanges(referenceRanges, predictedRanges)
  }

  abstract fun calculateMetric(matchedRanges: Map<NamedRange, NamedRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample)
}

interface RangeFilter {
  fun filter(ranges: List<NamedRange>): List<NamedRange>
}

interface PositiveExamplesRangeFilter : RangeFilter {
  override fun filter(ranges: List<NamedRange>): List<NamedRange> {
    return ranges.filter { !it.negativeExample }
  }
}

interface NegativeExamplesRangeFilter : RangeFilter {
  override fun filter(ranges: List<NamedRange>): List<NamedRange> {
    return ranges.filter { it.negativeExample }
  }
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
  override fun calculateMetric(matchedRanges: Map<NamedRange, NamedRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample) {
    val bestOverlaps = matchedRanges.map { computeScore(it.key, it.value) }
    (bestOverlaps + List(predictedSize - matchedRanges.size) { 0.0 }).forEach {
      fileSample.add(it)
      coreSample.add(it)
    }
  }
}

abstract class RecallRangeMetricBase : PrecisionRangeMetricBase(), Scorer {
  override fun calculateMetric(matchedRanges: Map<NamedRange, NamedRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample) {
    super.calculateMetric(matchedRanges, referenceSize, predictedSize, fileSample)
  }
}


class PositivePerfectOverlapRecallMetric : RecallRangeMetricBase(), PerfectOverlapScorer, PositiveExamplesRangeFilter {
  override val name = "Positive Perfect Overlap Recall"
  override val description: String = "Ratio of positive reference ranges that perfectly overlap with predicted ranges"
}

class PositiveIoURecallMetric : RecallRangeMetricBase(), IOUScorer, PositiveExamplesRangeFilter {
  override val name = "Positive IoU Recall"
  override val description: String = "Sum of IoU between matched predicted & positive reference range divided by total number of positive reference ranges"
}

class PositivePerfectOverlapMatchedMetric : PrecisionRangeMetricBase(), PerfectOverlapScorer, PositiveExamplesRangeFilter {
  override val name = "Positive Perfect Overlap Matched"
  override val description: String = "Number of positive reference ranges that perfectly overlap with predicted ranges divided by total number of matched ranges"

  override fun calculateMetric(matchedRanges: Map<NamedRange, NamedRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample) {
    super.calculateMetric(matchedRanges, matchedRanges.size, referenceSize, fileSample)
  }
}

class PositiveIOUMatchedMetric : PrecisionRangeMetricBase(), IOUScorer, PositiveExamplesRangeFilter {
  override val name = "Positive IoU Matched"
  override val description: String = "Sum of IoU between matched predicted & positive reference range divided by total number of matched ranges"

  override fun calculateMetric(matchedRanges: Map<NamedRange, NamedRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample) {
    super.calculateMetric(matchedRanges, matchedRanges.size, referenceSize, fileSample)
  }
}

class NegativePerfectOverlapRecallMetric : RecallRangeMetricBase(), PerfectOverlapScorer, NegativeExamplesRangeFilter {
  override val name = "Negative Perfect Overlap Recall"
  override val description: String = "Ratio of negative reference ranges that perfectly overlap with predicted ranges"
}

class NegativeIOURecallMetric : RecallRangeMetricBase(), IOUScorer, NegativeExamplesRangeFilter {
  override val name = "Negative IoU Recall"
  override val description: String = "Sum of IoU between predicted & negative reference range divided by total number of negative reference ranges"
}

class PositiveMatchedNumWordsMetric : RangeMetricBase(), PositiveExamplesRangeFilter {
  override val name = "Positive Matched Num Words"
  override val description: String = "Number of words in predicted text within matched predicted & reference ranges"

  override fun calculateMetric(matchedRanges: Map<NamedRange, NamedRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample) {
    matchedRanges.forEach { (predicted, _) ->
      val score = predicted.text.split("\\s+".toRegex()).size.toDouble()
      fileSample.add(score)
      coreSample.add(score)
    }
  }
}

open class TextSimilarityRangeMetric(val cloudSemanticSimilarityCalculator: CloudSemanticSimilarityCalculator) : RangeMetricBase(), PositiveExamplesRangeFilter {
  override val name = "Text Similarity for Range"
  override val description: String = "Semantic Similarity between texts of best matched predicted & reference ranges"

  private val project: Project
    get() = ProjectManager.getInstance().defaultProject

  override fun calculateMetric(matchedRanges: Map<NamedRange, NamedRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample) {
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

class OverlapPredictionsTextSimilarityMetric(cloudSemanticSimilarityCalculator: CloudSemanticSimilarityCalculator) : TextSimilarityRangeMetric(cloudSemanticSimilarityCalculator), PositiveExamplesRangeFilter {
  override val name = "Overlap Predictions Text Similarity"
  override val description: String = "Semantic Similarity between texts of pairs of overlapping predicted ranges"

  override fun calculateMetric(matchedRanges: Map<NamedRange, NamedRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample) {
    if (matchedRanges.isEmpty()) {
      fileSample.add(0.0)
      coreSample.add(0.0)
    }
    super.calculateMetric(matchedRanges, predictedSize, referenceSize, fileSample)
  }

  override fun getMatchedRanges(predictedRanges: List<NamedRange>, referenceRanges: List<NamedRange>): Map<NamedRange, NamedRange> {
    return overlapWithinRanges(predictedRanges)
  }
}