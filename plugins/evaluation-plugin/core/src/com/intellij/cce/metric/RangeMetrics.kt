package com.intellij.cce.metric

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.REFERENCE_CODE_COMMENT_RANGE_PROPERTY
import com.intellij.cce.evaluable.PREDICTED_CODE_COMMENT_RANGE_PROPERTY
import com.intellij.cce.evaluation.data.CodeCommentRange
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


abstract class RangeMetricBase(protected val category: String?) : ConfidenceIntervalMetric<Double>(), RangeFilter {
  override val showByDefault: Boolean = true
  override val valueType = MetricValueType.DOUBLE
  override val value: Double
    get() = compute(sample)

  override fun evaluate(sessions: List<Session>): Double {
    // this metric is used with an assumption that the session contains only one lookup and all data is stored there
    val lookups = sessions.flatMap { session -> session.lookups }
    val fileSample = Sample()
    lookups.forEach { lookup ->
      val referenceRanges = filter(getFromProperty(lookup, REFERENCE_CODE_COMMENT_RANGE_PROPERTY), category)
      val predictedRanges = filter(getFromProperty(lookup, PREDICTED_CODE_COMMENT_RANGE_PROPERTY), category)

      calculateMetric(getMatchedRanges(referenceRanges, predictedRanges), predictedRanges.size, referenceRanges.size, fileSample)
    }
    return aggregateFileSample(fileSample)
  }

  fun getFromProperty(lookup: Lookup, propertyKey: String): List<CodeCommentRange> {
    val gson = Gson()
    val namedRanges = lookup.additionalInfo[propertyKey] ?: return emptyList()
    val ranges = namedRanges as? JsonElement ?: gson.toJsonTree(namedRanges)
    return gson.fromJson(ranges, Array<CodeCommentRange>::class.java).toList()
  }

  open fun getMatchedRanges(predictedRanges: List<CodeCommentRange>, referenceRanges: List<CodeCommentRange>): Map<CodeCommentRange, CodeCommentRange> {
    return matchRanges(referenceRanges, predictedRanges)
  }

  abstract fun aggregateFileSample(fileSample: Sample): Double

  abstract fun calculateMetric(matchedRanges: Map<CodeCommentRange, CodeCommentRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample)
}

abstract class MeanRangeMetricBase(category: String?) : RangeMetricBase(category) {
  override fun aggregateFileSample(fileSample: Sample): Double = fileSample.mean()

  override fun compute(sample: List<Double>): Double = sample.average()
}

abstract class SumRangeMetricBase(category: String?) : RangeMetricBase(category) {
  override fun aggregateFileSample(fileSample: Sample): Double = fileSample.sum()

  override fun compute(sample: List<Double>): Double = sample.sum()
}

interface RangeFilter {
  fun filter(ranges: List<CodeCommentRange>, category: String?): List<CodeCommentRange> {
    if (category == null) return ranges
    return ranges.filter { it.category == category }
  }
}

interface PositiveOrUnknownExamplesRangeFilter : RangeFilter {
  override fun filter(ranges: List<CodeCommentRange>, category: String?): List<CodeCommentRange> {
    return super.filter(ranges, category).filter { !(it.negativeExample ?: false) }
  }
}

interface NegativeOrUnknownExamplesRangeFilter : RangeFilter {
  override fun filter(ranges: List<CodeCommentRange>, category: String?): List<CodeCommentRange> {
    return super.filter(ranges, category).filter { it.negativeExample ?: true }
  }
}

interface Scorer {
  fun computeScore(predictedRange: CodeCommentRange, referenceRange: CodeCommentRange): Double
}

interface IOUScorer : Scorer {
  override fun computeScore(predictedRange: CodeCommentRange, referenceRange: CodeCommentRange): Double {
    return computeIOU(predictedRange, referenceRange)
  }
}

interface PerfectOverlapScorer : Scorer {
  override fun computeScore(predictedRange: CodeCommentRange, referenceRange: CodeCommentRange): Double {
    val iou = computeIOU(predictedRange, referenceRange)
    return if (iou == 1.0) 1.0 else 0.0
  }
}

abstract class PrecisionRangeMetricBase(category: String?) : MeanRangeMetricBase(category), Scorer {
  override fun calculateMetric(matchedRanges: Map<CodeCommentRange, CodeCommentRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample) {
    val bestOverlaps = matchedRanges.map { computeScore(it.key, it.value) }
    (bestOverlaps + List(predictedSize - matchedRanges.size) { 0.0 }).forEach {
      fileSample.add(it)
      coreSample.add(it)
    }
  }
}

abstract class RecallRangeMetricBase(category: String?) : PrecisionRangeMetricBase(category), Scorer {
  override fun calculateMetric(matchedRanges: Map<CodeCommentRange, CodeCommentRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample) {
    super.calculateMetric(matchedRanges, referenceSize, predictedSize, fileSample)
  }
}


class PositivePerfectOverlapRecallMetric(category: String?) : RecallRangeMetricBase(category), PerfectOverlapScorer, PositiveOrUnknownExamplesRangeFilter {
  override val showByDefault: Boolean = false
  override val name = "Positive Perfect Overlap Recall" + ("".takeIf { category == null } ?: " ($category)")
  override val description: String = "Ratio of positive reference ranges that perfectly overlap with predicted ranges"
}

class PositiveIoURecallMetric(category: String?) : RecallRangeMetricBase(category), IOUScorer, PositiveOrUnknownExamplesRangeFilter {
  override val showByDefault: Boolean = category == null
  override val name = "Positive IoU Recall" + ("".takeIf { category == null } ?: " ($category)")
  override val description: String = "Sum of IoU between matched predicted & positive reference range divided by total number of positive reference ranges"
}

class PositivePerfectOverlapMatchedMetric(category: String?) : PrecisionRangeMetricBase(category), PerfectOverlapScorer, PositiveOrUnknownExamplesRangeFilter {
  override val showByDefault: Boolean = false
  override val name = "Positive Perfect Overlap Matched" + ("".takeIf { category == null } ?: " ($category)")
  override val description: String = "Number of positive reference ranges that perfectly overlap with predicted ranges divided by total number of matched ranges"

  override fun calculateMetric(matchedRanges: Map<CodeCommentRange, CodeCommentRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample) {
    super.calculateMetric(matchedRanges, matchedRanges.size, referenceSize, fileSample)
  }
}

class PositiveIOUMatchedMetric(category: String?) : PrecisionRangeMetricBase(category), IOUScorer, PositiveOrUnknownExamplesRangeFilter {
  override val showByDefault: Boolean = category == null
  override val name = "Positive IoU Matched" + ("".takeIf { category == null } ?: " ($category)")
  override val description: String = "Sum of IoU between matched predicted & positive reference range divided by total number of matched ranges"

  override fun calculateMetric(matchedRanges: Map<CodeCommentRange, CodeCommentRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample) {
    super.calculateMetric(matchedRanges, matchedRanges.size, referenceSize, fileSample)
  }
}

class NegativePerfectOverlapRecallMetric(category: String?) : RecallRangeMetricBase(category), PerfectOverlapScorer, NegativeOrUnknownExamplesRangeFilter {
  override val showByDefault: Boolean = false
  override val name = "Negative Perfect Overlap Recall" + ("".takeIf { category == null } ?: " ($category)")
  override val description: String = "Ratio of negative reference ranges that perfectly overlap with predicted ranges"
}

class NegativeIOURecallMetric(category: String?) : RecallRangeMetricBase(category), IOUScorer, NegativeOrUnknownExamplesRangeFilter {
  override val showByDefault: Boolean = category == null
  override val name = "Negative IoU Recall" + ("".takeIf { category == null } ?: " ($category)")
  override val description: String = "Sum of IoU between predicted & negative reference range divided by total number of negative reference ranges"
}

class PositiveMatchedNumWordsMetric(category: String?) : MeanRangeMetricBase(category), PositiveOrUnknownExamplesRangeFilter {
  override val showByDefault: Boolean = false
  override val name = "Positive Matched Num Words" + ("".takeIf { category == null } ?: " ($category)")
  override val description: String = "Number of words in predicted text within matched predicted & reference ranges"

  override fun calculateMetric(matchedRanges: Map<CodeCommentRange, CodeCommentRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample) {
    matchedRanges.forEach { (predicted, _) ->
      val score = predicted.text.split("\\s+".toRegex()).size.toDouble()
      fileSample.add(score)
      coreSample.add(score)
    }
  }
}

class PositiveCountMetric(category: String?) : SumRangeMetricBase(category), PositiveOrUnknownExamplesRangeFilter {
  override val showByDefault: Boolean = category == null
  override val name = "Number Of Positive Examples" + ("".takeIf { category == null } ?: " ($category)")
  override val description: String = "Number of positive examples"

  override fun calculateMetric(matchedRanges: Map<CodeCommentRange, CodeCommentRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample) {
    fileSample.add(referenceSize.toDouble())
    coreSample.add(referenceSize.toDouble())
  }
}

class NegativeCountMetric(category: String?) : SumRangeMetricBase(category), NegativeOrUnknownExamplesRangeFilter {
  override val showByDefault: Boolean = category == null
  override val name = "Number Of Negative Examples" + ("".takeIf { category == null } ?: " ($category)")
  override val description: String = "Number of negative examples"

  override fun calculateMetric(matchedRanges: Map<CodeCommentRange, CodeCommentRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample) {
    fileSample.add(referenceSize.toDouble())
    coreSample.add(referenceSize.toDouble())
  }
}

open class TextSimilarityRangeMetric(val cloudSemanticSimilarityCalculator: CloudSemanticSimilarityCalculator, category: String?) : MeanRangeMetricBase(category), PositiveOrUnknownExamplesRangeFilter {
  override val showByDefault: Boolean = category == null
  override val name = "Text Similarity" + ("".takeIf { category == null } ?: " ($category)")
  override val description: String = "Semantic Similarity between texts of best matched predicted & reference ranges"

  private val project: Project
    get() = ProjectManager.getInstance().defaultProject

  override fun calculateMetric(matchedRanges: Map<CodeCommentRange, CodeCommentRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample) {
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

class OverlapPredictionsTextSimilarityMetric(cloudSemanticSimilarityCalculator: CloudSemanticSimilarityCalculator, category: String?) : TextSimilarityRangeMetric(cloudSemanticSimilarityCalculator, category), PositiveOrUnknownExamplesRangeFilter {
  override val showByDefault: Boolean = false
  override val name = "Overlap Predictions Text Similarity" + ("".takeIf { category == null } ?: " ($category)")
  override val description: String = "Semantic Similarity between texts of pairs of overlapping predicted ranges"

  override fun calculateMetric(matchedRanges: Map<CodeCommentRange, CodeCommentRange>, predictedSize: Int, referenceSize: Int, fileSample: Sample) {
    if (matchedRanges.isEmpty()) {
      fileSample.add(0.0)
      coreSample.add(0.0)
    }
    super.calculateMetric(matchedRanges, predictedSize, referenceSize, fileSample)
  }

  override fun getMatchedRanges(predictedRanges: List<CodeCommentRange>, referenceRanges: List<CodeCommentRange>): Map<CodeCommentRange, CodeCommentRange> {
    return overlapWithinRanges(predictedRanges)
  }
}