package com.intellij.cce.metric.util

import com.intellij.cce.evaluation.data.Range


/**
 * Computes the Intersection over Union (IOU) between two ranges.
 *
 * @param range1 First range
 * @param range2 Second range
 * @return IOU value between 0.0 (no overlap) and 1.0 (perfect overlap)
 */
fun <T : Range> computeIOU(range1: T, range2: T): Double {
  // Calculate intersection
  val intersectionStart = maxOf(range1.start, range2.start)
  val intersectionEnd = minOf(range1.end, range2.end)

  // If there's no intersection, return 0
  if (intersectionEnd < intersectionStart) {
    return 0.0
  }

  val intersectionArea = intersectionEnd - intersectionStart

  // Calculate union
  val range1Size = range1.end - range1.start
  val range2Size = range2.end - range2.start
  val unionArea = range1Size + range2Size - intersectionArea

  // Return IOU
  return if (unionArea > 0) intersectionArea.toDouble() / unionArea else 0.0
}


data class Match<T>(val reference: T, val prediction: T, val iou: Double)

/**
 * Matches reference ranges with predicted ranges based on maximum IOU overlap.
 * Each predicted range can only be matched once.
 *
 * @param references List of reference ranges
 * @param predictions List of predicted ranges
 * @return Map of reference ranges to their best matching predicted ranges
 */
fun <T : Range> matchRanges(references: List<T>, predictions: List<T>): Map<T, T> {
  val result = mutableMapOf<T, T>()
  val usedReferences = mutableSetOf<T>()
  val usedPredictions = mutableSetOf<T>()

  // Calculate all possible matches and their IOU scores
  val allMatches = references.flatMap { reference ->
    predictions.map { prediction ->
      Match(reference, prediction, computeIOU(reference, prediction))
    }
  }.filter { it.iou > 0.0 }
    .sortedByDescending { it.iou }

  // Select matches in order of decreasing IOU score
  for (match in allMatches) {
    val (reference, prediction, _) = match
    if (reference !in usedReferences && prediction !in usedPredictions) {
      result[reference] = prediction
      usedReferences.add(reference)
      usedPredictions.add(prediction)
    }
  }

  return result
}

/**
 * Computes the overlap between all pairs of ranges in a list.
 * Each range can only be paired once.
 * The resulting map contains the maximum overlap for each pair of ranges.
 *
 * @param ranges List of ranges
 * @return Map overlapped ranges
 */
fun <T : Range> overlapWithinRanges(ranges: List<T>): Map<T, T> {
  return ranges.flatMapIndexed { i, range1 ->
    ranges.subList(i + 1, ranges.size).map { range2 ->
      Match(range1, range2, computeIOU(range1, range2))
    }
  }.filter { it.iou > 0.0 }.associateBy({ it.reference }, { it.prediction })
}