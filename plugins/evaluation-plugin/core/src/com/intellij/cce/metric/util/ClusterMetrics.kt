// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.metric.util

import kotlin.math.ln

/**
 * Clustering metrics similar to scikit-learn (homogeneity, completeness, V-measure) with optional weighting.
 *
 * Definitions (C = ground truth classes, K = predicted clusters):
 *  - Homogeneity = 1 - H(C|K) / H(C). If H(C) = 0 (all samples are in the same true class), homogeneity = 1.
 *  - Completeness = 1 - H(K|C) / H(K). If H(K) = 0 (all samples are in the same predicted cluster), completeness = 1.
 *  - V-measure(beta) = harmonic mean of homogeneity and completeness weighted by beta:
 *      (1 + beta) * homogeneity * completeness / (beta * homogeneity + completeness).
 *
 * Weighting support:
 *  - sampleWeight lets you emphasize or de-emphasize individual samples;
 *  - trueLabelWeights lets you re-weight true classes when computing entropy H(C|K) and derived scores that use it.
 *
 * Edge cases and conventions (aligned with scikit-learn):
 *  - Empty input (n = 0) -> homogeneity = completeness = v-measure = 1.0; precision/recall = 1.0.
 *  - Negative labels are ignored. Zero or positive labels are supported and automatically compacted via max label index.
 *  - Zero weights are allowed and effectively skip samples/classes; all weights must be non-negative.
 *
 * Generic overloads are provided for List<T> labels; they are mapped to Int indices internally.
 */
fun <T> homogeneityScore(
  labelsTrue: List<T>,
  labelsPred: List<T>,
  sampleWeight: List<Double>? = null,
  trueLabelWeights: Map<T, Double>? = null,
): Double {
  val (trueIdx, predIdx, sw, classWeights) = mapParamsToIndices(labelsTrue, labelsPred, sampleWeight, trueLabelWeights)
  val cm = ContingencyMatrixWeighted.from(trueIdx, predIdx, sw, classWeights)
  if (cm.n == 0.0) return 1.0
  val hC = cm.entropyTrue()
  if (hC == 0.0) return 1.0
  val hCgivenK = cm.conditionalEntropyTrueGivenPred()
  return 1.0 - hCgivenK / hC
}

fun <T> completenessScore(
  labelsTrue: List<T>,
  labelsPred: List<T>,
  sampleWeight: List<Double>? = null,
  trueLabelWeights: Map<T, Double>? = null,
): Double {
  val (trueIdx, predIdx, sw, classWeights) = mapParamsToIndices(labelsTrue, labelsPred, sampleWeight, trueLabelWeights)
  val cm = ContingencyMatrixWeighted.from(trueIdx, predIdx, sw, classWeights)
  if (cm.n == 0.0) return 1.0
  val hK = cm.entropyPred()
  if (hK == 0.0) return 1.0
  val hKgivenC = cm.conditionalEntropyPredGivenTrue()
  return 1.0 - hKgivenC / hK
}

fun <T> vMeasureScore(
  labelsTrue: List<T>,
  labelsPred: List<T>,
  sampleWeight: List<Double>? = null,
  beta: Double = 1.0,
  trueLabelWeights: Map<T, Double>? = null,
): Double {
  val h = homogeneityScore(labelsTrue, labelsPred, sampleWeight, trueLabelWeights)
  val c = completenessScore(labelsTrue, labelsPred, sampleWeight, trueLabelWeights)
  if (h == 0.0 && c == 0.0) return 0.0
  val numerator = (1.0 + beta) * h * c
  val denominator = beta * h + c
  // When both entropies are 0 in sklearn, homogeneity=completeness=1 => v-measure=1
  return if (denominator == 0.0) 0.0 else numerator / denominator
}


/**
 * Shannon entropy of true (gold) labels conditioned on predicted clusters: H(C|K).
 */
fun <T> shannonEntropyTrueGivenPredicted(
  labelsTrue: List<T>,
  labelsPred: List<T>,
  sampleWeight: List<Double>? = null,
  trueLabelWeights: Map<T, Double>? = null,
): Double {
  val (trueIdx, predIdx, sw, classWeights) = mapParamsToIndices(labelsTrue, labelsPred, sampleWeight, trueLabelWeights)
  val cm = ContingencyMatrixWeighted.from(trueIdx, predIdx, sw, classWeights)
  return cm.conditionalEntropyTrueGivenPred()
}

/**
 * Cluster precision: for each predicted cluster, take the maximum class purity and average by cluster size.
 * Class weights (trueLabelWeights) re-weight contributions when computing purities inside predicted clusters.
 */
fun <T> precisionScore(
  labelsTrue: List<T>,
  labelsPred: List<T>,
  sampleWeight: List<Double>? = null,
  trueLabelWeights: Map<T, Double>? = null,
): Double {
  val (trueIdx, predIdx, sw, classWeights) = mapParamsToIndices(labelsTrue, labelsPred, sampleWeight, trueLabelWeights)
  val cm = ContingencyMatrixWeighted.from(trueIdx, predIdx, sw, classWeights)
  if (cm.n == 0.0) return 1.0

  var score = 0.0
  for (predCluster in cm.predSums.indices) {
    // calculating weighted predClusterSize using gold class weights
    var denom = 0.0
    for (goldCluster in cm.trueSums.indices) {
      val numPredWithinGold = cm.counts[goldCluster][predCluster]
      if (numPredWithinGold == 0.0) continue
      denom += numPredWithinGold * weightAt(classWeights, goldCluster)
    }
    if (denom == 0.0) continue

    var maxP = 0.0
    for (goldCluster in cm.trueSums.indices) {
      val numPredWithinGold = cm.counts[goldCluster][predCluster]
      if (numPredWithinGold == 0.0) continue
      // use gold class weights
      val p = (numPredWithinGold * weightAt(classWeights, goldCluster)) / denom
      if (p > maxP) maxP = p
    }
    score += maxP
  }
  return score / cm.predSums.size
}

/**
 * Cluster recall: for each true class, take the maximum cluster capture rate and average weighted by class size.
 * Class weights (trueLabelWeights) scale each true class contribution to the final average.
 */
fun <T> recallScore(
  labelsTrue: List<T>,
  labelsPred: List<T>,
  sampleWeight: List<Double>? = null,
  trueLabelWeights: Map<T, Double>? = null,
): Double {
  val (trueIdx, predIdx, sw, classWeights) = mapParamsToIndices(labelsTrue, labelsPred, sampleWeight, trueLabelWeights)
  val cm = ContingencyMatrixWeighted.from(trueIdx, predIdx, sw, classWeights)
  if (cm.n == 0.0) return 1.0

  var weightedSum = 0.0
  var totalWeight = 0.0
  for (goldCluster in cm.trueSums.indices) {
    val goldClusterSize = cm.trueSums[goldCluster]
    if (goldClusterSize == 0.0) continue
    var maxR = 0.0
    for (predCluster in cm.counts[goldCluster].indices) {
      val numPredWithinGold = cm.counts[goldCluster][predCluster]
      if (numPredWithinGold == 0.0) continue
      val r = numPredWithinGold / goldClusterSize
      if (r > maxR) maxR = r
    }
    val weight = weightAt(classWeights, goldCluster)
    totalWeight += weight
    weightedSum += weight * maxR
  }
  if (totalWeight == 0.0) return 1.0
  return weightedSum / totalWeight
}

data class MetricParams(
  val labelsTrue: IntArray,
  val labelsPred: IntArray,
  val sampleWeight: DoubleArray? = null,
  val trueLabelWeights: DoubleArray? = null,
)

private fun <T> mapParamsToIndices(
  labelsTrue: List<T>,
  labelsPred: List<T>,
  sampleWeight: List<Double>?,
  trueLabelWeights: Map<T, Double>? = null,
): MetricParams {
  val trueIdx = mapToIndices(labelsTrue)
  val predIdx = mapToIndices(labelsPred)
  val sw = sampleWeight?.let { mapToDoubleArray(it) }
  val classWeights = mapTrueLabelWeights(labelsTrue, trueLabelWeights)
  return MetricParams(trueIdx, predIdx, sw, classWeights)
}


private fun weightAt(trueLabelWeights: DoubleArray?, index: Int): Double =
  if (trueLabelWeights != null && index < trueLabelWeights.size) trueLabelWeights[index] else 1.0


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

private fun <T> mapTrueLabelWeights(labelsTrue: List<T>, weightsByLabel: Map<T, Double>?): DoubleArray? {
  if (weightsByLabel == null) return null
  val indexMap = LinkedHashMap<T, Int>()
  var idx = 0
  // establish indices consistent with mapToIndices(labelsTrue)
  for (v in labelsTrue) {
    if (!indexMap.containsKey(v)) indexMap[v] = idx++
  }
  val result = DoubleArray(indexMap.size) { 1.0 }
  for ((label, index) in indexMap) {
    val w = weightsByLabel[label]
    if (w != null) {
      require(w >= 0.0) { "trueLabelWeights must be non-negative" }
      result[index] = w
    }
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
    fun from(labelsTrue: IntArray, labelsPred: IntArray, sampleWeight: DoubleArray? = null, trueLabelWeights: DoubleArray? = null): ContingencyMatrixWeighted {
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
        // apply class weighting additionally to sample weights
        val w = (sampleWeight?.get(i) ?: 1.0) * weightAt(trueLabelWeights, c)
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