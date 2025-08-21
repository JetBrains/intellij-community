// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric

import kotlin.math.ln

/**
 * Sklearn-like clustering metrics: homogeneity, completeness and V-measure.
 *
 * The implementation follows scikit-learn definitions:
 *  - homogeneity = 1 - H(C|K) / H(C), with homogeneity = 1.0 when H(C) = 0
 *  - completeness = 1 - H(K|C) / H(K), with completeness = 1.0 when H(K) = 0
 *  - v-measure(beta) = (1 + beta) * (homogeneity * completeness) / (beta * homogeneity + completeness)
 */
object ClusterMetrics {
  // Public API: Int labels
  @JvmStatic
  fun homogeneityScore(labelsTrue: IntArray, labelsPred: IntArray): Double {
    val cm = ContingencyMatrix.from(labelsTrue, labelsPred)
    if (cm.n == 0) return 1.0
    val hC = cm.entropyTrue()
    if (hC == 0.0) return 1.0
    val hCgivenK = cm.conditionalEntropyTrueGivenPred()
    return (1.0 - hCgivenK / hC).coerceIn(0.0, 1.0)
  }

  @JvmStatic
  fun completenessScore(labelsTrue: IntArray, labelsPred: IntArray): Double {
    val cm = ContingencyMatrix.from(labelsTrue, labelsPred)
    if (cm.n == 0) return 1.0
    val hK = cm.entropyPred()
    if (hK == 0.0) return 1.0
    val hKgivenC = cm.conditionalEntropyPredGivenTrue()
    return (1.0 - hKgivenC / hK).coerceIn(0.0, 1.0)
  }

  @JvmStatic
  fun vMeasureScore(labelsTrue: IntArray, labelsPred: IntArray, beta: Double = 1.0): Double {
    val h = homogeneityScore(labelsTrue, labelsPred)
    val c = completenessScore(labelsTrue, labelsPred)
    if (h == 0.0 && c == 0.0) return 0.0
    val numerator = (1.0 + beta) * h * c
    val denominator = beta * h + c
    // When both entropies are 0 in sklearn, homogeneity=completeness=1 => v-measure=1
    return if (denominator == 0.0) 0.0 else numerator / denominator
  }

  // Public API: generic label lists (e.g., String)
  @JvmStatic
  fun <T> homogeneityScore(labelsTrue: List<T>, labelsPred: List<T>): Double =
    homogeneityScore(mapToIndices(labelsTrue), mapToIndices(labelsPred))

  @JvmStatic
  fun <T> completenessScore(labelsTrue: List<T>, labelsPred: List<T>): Double =
    completenessScore(mapToIndices(labelsTrue), mapToIndices(labelsPred))

  @JvmStatic
  fun <T> vMeasureScore(labelsTrue: List<T>, labelsPred: List<T>, beta: Double = 1.0): Double =
    vMeasureScore(mapToIndices(labelsTrue), mapToIndices(labelsPred), beta)

  private fun <T> mapToIndices(labels: List<T>): IntArray {
    val map = LinkedHashMap<T, Int>()
    var idx = 0
    val result = IntArray(labels.size)
    for (i in labels.indices) {
      val v = labels[i]
      val id = map.getOrPut(v) { idx++ }
      result[i] = id
    }
    return result
  }

  // Internal: contingency matrix and entropy helpers
  private class ContingencyMatrix(
    val counts: Array<IntArray>,
    val trueSums: IntArray,
    val predSums: IntArray,
    val n: Int,
  ) {
    fun entropyTrue(): Double = entropyFromCounts(trueSums, n)
    fun entropyPred(): Double = entropyFromCounts(predSums, n)

    fun conditionalEntropyTrueGivenPred(): Double {
      if (n == 0) return 0.0
      var result = 0.0
      for (k in counts[0].indices) {
        val nk = predSums[k]
        if (nk == 0) continue
        var h = 0.0
        for (c in counts.indices) {
          val nck = counts[c][k]
          if (nck == 0) continue
          val p = nck.toDouble() / nk
          h -= p * ln(p)
        }
        result += (nk.toDouble() / n) * h
      }
      return result
    }

    fun conditionalEntropyPredGivenTrue(): Double {
      if (n == 0) return 0.0
      var result = 0.0
      for (c in counts.indices) {
        val nc = trueSums[c]
        if (nc == 0) continue
        var h = 0.0
        for (k in counts[c].indices) {
          val nck = counts[c][k]
          if (nck == 0) continue
          val p = nck.toDouble() / nc
          h -= p * ln(p)
        }
        result += (nc.toDouble() / n) * h
      }
      return result
    }

    companion object {
      fun from(labelsTrue: IntArray, labelsPred: IntArray): ContingencyMatrix {
        require(labelsTrue.size == labelsPred.size) { "labelsTrue and labelsPred must have the same size" }
        val n = labelsTrue.size
        if (n == 0) return ContingencyMatrix(arrayOf(IntArray(0)), IntArray(0), IntArray(0), 0)

        val trueMax = (labelsTrue.maxOrNull() ?: -1) + 1
        val predMax = (labelsPred.maxOrNull() ?: -1) + 1
        val counts = Array(trueMax) { IntArray(predMax) }
        val trueSums = IntArray(trueMax)
        val predSums = IntArray(predMax)
        for (i in 0 until n) {
          val c = labelsTrue[i]
          val k = labelsPred[i]
          if (c < 0 || k < 0) continue // ignore negative labels
          counts[c][k] += 1
          trueSums[c] += 1
          predSums[k] += 1
        }
        val effectiveN = trueSums.sum()
        return ContingencyMatrix(counts, trueSums, predSums, effectiveN)
      }
    }
  }
}

private fun entropyFromCounts(counts: IntArray, n: Int): Double {
  if (n == 0) return 0.0
  var h = 0.0
  for (cnt in counts) {
    if (cnt == 0) continue
    val p = cnt.toDouble() / n
    h -= p * ln(p)
  }
  return h
}
