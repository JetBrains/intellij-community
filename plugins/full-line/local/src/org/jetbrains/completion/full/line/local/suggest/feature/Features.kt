package org.jetbrains.completion.full.line.local.suggest.feature

import org.jetbrains.completion.full.line.local.generation.generation.GenerationInfo

@Suppress("unused")
internal object Features {
  /** Probability of the whole completion */
  fun prob(generationInfo: GenerationInfo): Double {
    return generationInfo.probs.reduce(Double::times)
  }

  /** Average probability of BPE tokens inside completion */
  fun meanProb(generationInfo: GenerationInfo): Double {
    val probsSum = generationInfo.probs.reduce(Double::plus)
    return probsSum / generationInfo.probs.size
  }

  /**
   * Overall "usefulness" of completion by BPE tokens.
   *
   * For example, for completion with 4 bpe tokens it would be:
   * `1 * prob[0] + 2 * (prob[0] * prob[1]) + 3 * (prob[0] * prob[1] * prob[2]) + 1 * (prob[0] * prob[1] * prob[2] * prob[3])`
   *
   * And the multiplication in this formula is the number of words in sequence
   */
  fun stepProfit(generationInfo: GenerationInfo): Double {
    if (generationInfo.probs.isEmpty()) return 0.0

    var total = 0.0
    var cur = 1.0
    for ((i, prob) in generationInfo.probs.withIndex()) {
      cur *= prob
      total += cur * (i + 1)
    }

    return total
  }

  /** Length of maximum prefix common between [completion] and [prefix] */
  fun prefixMatchedCount(prefix: String, completion: String): Int {
    for (i in prefix.indices) {
      if (i == completion.length || prefix[i] != completion[i]) {
        return i
      }
    }
    return prefix.length
  }

  /** Total number of words */
  fun wordsCount(completion: String): Int {
    return completion.split(" ").map { it.trim() }.filter { it.isNotBlank() }.size
  }
}
