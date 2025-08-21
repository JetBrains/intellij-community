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
  @JvmOverloads
  fun homogeneityScore(labelsTrue: IntArray, labelsPred: IntArray, sampleWeight: DoubleArray? = null): Double {
    val cm = ContingencyMatrixWeighted.from(labelsTrue, labelsPred, sampleWeight)
    if (cm.n == 0.0) return 1.0
    val hC = cm.entropyTrue()
    if (hC == 0.0) return 1.0
    val hCgivenK = cm.conditionalEntropyTrueGivenPred()
    return (1.0 - hCgivenK / hC).coerceIn(0.0, 1.0)
  }

  @JvmStatic
  @JvmOverloads
  fun completenessScore(labelsTrue: IntArray, labelsPred: IntArray, sampleWeight: DoubleArray? = null): Double {
    val cm = ContingencyMatrixWeighted.from(labelsTrue, labelsPred, sampleWeight)
    if (cm.n == 0.0) return 1.0
    val hK = cm.entropyPred()
    if (hK == 0.0) return 1.0
    val hKgivenC = cm.conditionalEntropyPredGivenTrue()
    return (1.0 - hKgivenC / hK).coerceIn(0.0, 1.0)
  }

  @JvmStatic
  @JvmOverloads
  fun vMeasureScore(labelsTrue: IntArray, labelsPred: IntArray, sampleWeight: DoubleArray? = null, beta: Double = 1.0): Double {
    val h = homogeneityScore(labelsTrue, labelsPred, sampleWeight)
    val c = completenessScore(labelsTrue, labelsPred, sampleWeight)
    if (h == 0.0 && c == 0.0) return 0.0
    val numerator = (1.0 + beta) * h * c
    val denominator = beta * h + c
    // When both entropies are 0 in sklearn, homogeneity=completeness=1 => v-measure=1
    return if (denominator == 0.0) 0.0 else numerator / denominator
  }

  // Public API: generic label lists (e.g., String)
  @JvmStatic
  @JvmOverloads
  fun <T> homogeneityScore(labelsTrue: List<T>, labelsPred: List<T>, sampleWeight: List<Double>? = null): Double =
    homogeneityScore(
      mapToIndices(labelsTrue),
      mapToIndices(labelsPred),
      sampleWeight?.let { mapToDoubleArray(it) }
    )

  @JvmStatic
  @JvmOverloads
  fun <T> completenessScore(labelsTrue: List<T>, labelsPred: List<T>, sampleWeight: List<Double>? = null): Double =
    completenessScore(
      mapToIndices(labelsTrue),
      mapToIndices(labelsPred),
      sampleWeight?.let { mapToDoubleArray(it) }
    )

  @JvmStatic
  @JvmOverloads
  fun <T> vMeasureScore(labelsTrue: List<T>, labelsPred: List<T>, sampleWeight: List<Double>? = null, beta: Double = 1.0): Double =
    vMeasureScore(
      mapToIndices(labelsTrue),
      mapToIndices(labelsPred),
      sampleWeight?.let { mapToDoubleArray(it) },
      beta
    )

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

  private fun mapToDoubleArray(weights: List<Double>): DoubleArray {
    val result = DoubleArray(weights.size)
    for (i in weights.indices) {
      result[i] = weights[i]
    }
    return result
  }


  // Internal: weighted contingency matrix for sample weights
  private class ContingencyMatrixWeighted(
    val counts: Array<DoubleArray>,
    val trueSums: DoubleArray,
    val predSums: DoubleArray,
    val n: Double,
  ) {
    fun entropyTrue(): Double = entropyFromCounts(trueSums, n)
    fun entropyPred(): Double = entropyFromCounts(predSums, n)

    fun conditionalEntropyTrueGivenPred(): Double {
      if (n == 0.0) return 0.0
      var result = 0.0
      val predSize = if (counts.isNotEmpty()) counts[0].size else 0
      for (k in 0 until predSize) {
        val nk = predSums[k]
        if (nk == 0.0) continue
        var h = 0.0
        for (c in counts.indices) {
          val nck = counts[c][k]
          if (nck == 0.0) continue
          val p = nck / nk
          h -= p * ln(p)
        }
        result += (nk / n) * h
      }
      return result
    }

    fun conditionalEntropyPredGivenTrue(): Double {
      if (n == 0.0) return 0.0
      var result = 0.0
      for (c in counts.indices) {
        val nc = trueSums[c]
        if (nc == 0.0) continue
        var h = 0.0
        for (k in counts[c].indices) {
          val nck = counts[c][k]
          if (nck == 0.0) continue
          val p = nck / nc
          h -= p * ln(p)
        }
        result += (nc / n) * h
      }
      return result
    }

    companion object {
      fun from(labelsTrue: IntArray, labelsPred: IntArray, sampleWeight: DoubleArray? = null): ContingencyMatrixWeighted {
        require(labelsTrue.size == labelsPred.size) { "labelsTrue and labelsPred must have the same size" }
        if (sampleWeight != null) {
          require(labelsTrue.size == sampleWeight.size) { "sampleWeight must have the same size as labels" }
        }
        val n = labelsTrue.size
        if (n == 0) return ContingencyMatrixWeighted(arrayOf(DoubleArray(0)), DoubleArray(0), DoubleArray(0), 0.0)

        val trueMax = (labelsTrue.maxOrNull() ?: -1) + 1
        val predMax = (labelsPred.maxOrNull() ?: -1) + 1
        val counts = Array(trueMax) { DoubleArray(predMax) }
        val trueSums = DoubleArray(trueMax)
        val predSums = DoubleArray(predMax)
        for (i in 0 until n) {
          val c = labelsTrue[i]
          val k = labelsPred[i]
          if (c < 0 || k < 0) continue // ignore negative labels
          val w = sampleWeight?.get(i) ?: 1.0
          if (sampleWeight != null) {
            require(w >= 0.0) { "sampleWeight must be non-negative" }
            if (w == 0.0) continue
          }
          counts[c][k] += w
          trueSums[c] += w
          predSums[k] += w
        }
        val effectiveN = trueSums.sum()
        return ContingencyMatrixWeighted(counts, trueSums, predSums, effectiveN)
      }
    }
  }
}


private fun entropyFromCounts(counts: DoubleArray, n: Double): Double {
  if (n == 0.0) return 0.0
  var h = 0.0
  for (cnt in counts) {
    if (cnt == 0.0) continue
    val p = cnt / n
    h -= p * ln(p)
  }
  return h
}
