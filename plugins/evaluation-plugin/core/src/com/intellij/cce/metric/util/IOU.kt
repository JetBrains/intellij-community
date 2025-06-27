package com.intellij.cce.metric.util

/**
 * Computes the Intersection over Union (IOU) between two ranges.
 *
 * @param range1 Pair of (start, end) positions for the first range
 * @param range2 Pair of (start, end) positions for the second range
 * @return IOU value between 0.0 (no overlap) and 1.0 (perfect overlap)
 */
fun computeIOU(range1: Pair<Int, Int>, range2: Pair<Int, Int>): Double {
  val (start1, end1) = range1
  val (start2, end2) = range2

  require(start1 <= end1) { "First range start must be less than or equal to end" }
  require(start2 <= end2) { "Second range start must be less than or equal to end" }

  // Calculate intersection
  val intersectionStart = maxOf(start1, start2)
  val intersectionEnd = minOf(end1, end2)

  // If there's no intersection, return 0
  if (intersectionEnd < intersectionStart) {
    return 0.0
  }

  val intersectionArea = intersectionEnd - intersectionStart

  // Calculate union
  val range1Size = end1 - start1
  val range2Size = end2 - start2
  val unionArea = range1Size + range2Size - intersectionArea

  // Return IOU
  return if (unionArea > 0) intersectionArea.toDouble() / unionArea else 0.0
}