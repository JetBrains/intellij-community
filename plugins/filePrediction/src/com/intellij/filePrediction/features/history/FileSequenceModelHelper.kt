// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history

import kotlin.math.max

private val EMPTY_NEXT_FILE_PROBABILITY = NextFileProbability(0.0, 0.0, 0.0, 0.0, 0.0)

class FileSequenceModelHelper {
  fun onFileOpened(root: NGramMapNode, code: Int, previous1: Int?) {
    root.count++
    root.addOrIncrement(code)

    if (previous1 != null) {
      val biGramRoot = root.getOrCreate(previous1)
      biGramRoot.addOrIncrement(code)
    }
  }

  fun remove(root: NGramMapNode, code: Int) {
    if (root.usages.containsKey(code)) {
      root.count = max(root.count - root.usages.get(code).count, 0)
      root.usages.remove(code)
    }

    for (secondNode in root.usages.values) {
      val node = secondNode as NGramListNode
      val current = node.getNode(code)
      if (current != null) {
        node.count = max(node.count - current, 0)
        node.usages.remove(code)
      }
    }
  }

  fun calculateUniGramProb(root: NGramMapNode, code: Int?): NextFileProbability {
    val count = code?.let { root.getNode(code)?.count } ?: 0
    val (min, max) = root.findMinMax()
    return calculateNextFileProbabilityFeatures(root, count, min, max, false)
  }

  fun calculateBiGramProb(root: NGramMapNode, code: Int?, previous: Int?, isIncomplete: Boolean = true): NextFileProbability {
    val prevNode = previous?.let { root.getNode(it) } ?: return EMPTY_NEXT_FILE_PROBABILITY
    val count = code?.let { prevNode.getNode(code) } ?: 0

    val (min, max) = prevNode.findMinMax()
    return calculateNextFileProbabilityFeatures(prevNode, count, min, max, isIncomplete)
  }

  private fun <T> calculateNextFileProbabilityFeatures(root: NGramModelNode<T>, count: Int, min: Int, max: Int, isIncomplete: Boolean): NextFileProbability {
    val mle = calculateProbability(root, count, isIncomplete)
    val minMle = calculateProbability(root, min, isIncomplete)
    val maxMle = calculateProbability(root, max, isIncomplete)
    val mleToMin = if (minMle != 0.0) mle / minMle else 0.0
    val mleToMax = if (maxMle != 0.0) mle / maxMle else 0.0
    return NextFileProbability(mle, minMle, maxMle, mleToMin, mleToMax)
  }

  private fun <T> calculateProbability(node: NGramModelNode<T>, count: Int, isIncomplete: Boolean): Double {
    val contextCount = if (isIncomplete) node.count - 1 else node.count
    if (contextCount <= 0 || count <= 0) return 0.0
    return count / contextCount.toDouble()
  }
}