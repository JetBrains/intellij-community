package com.intellij.filePrediction.history

import kotlin.math.max

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

  fun calculateUniGramProb(root: NGramMapNode, code: Int): Double {
    val count = root.getNode(code)?.count ?: 0
    return calculateProbability(root, count, false)
  }

  fun calculateBiGramProb(root: NGramMapNode, code: Int, previous: Int?, isIncomplete: Boolean = true): Double {
    val prevNode = previous?.let { root.getNode(it) } ?: return 0.0
    val count = prevNode.getNode(code) ?: 0
    return calculateProbability(prevNode, count, isIncomplete)
  }

  private fun <T> calculateProbability(node: NGramModelNode<T>, count: Int, isIncomplete: Boolean): Double {
    val contextCount = if (isIncomplete) max(node.count - 1, 0) else node.count
    if (contextCount == 0) return 0.0
    return count / contextCount.toDouble()
  }
}