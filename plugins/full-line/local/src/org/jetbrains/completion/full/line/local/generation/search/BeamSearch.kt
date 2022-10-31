package org.jetbrains.completion.full.line.local.generation.search

import org.jetbrains.completion.full.line.local.generation.sliceArray
import org.jetbrains.completion.full.line.local.generation.topk1d
import java.lang.Math.floorDiv
import java.lang.Math.floorMod
import kotlin.math.exp

class BeamSearch(
  vocabSize: Int,
  searchSize: Int,
  private val repetitionPenalty: Double = 1.0
) : BaseSearch(vocabSize, searchSize) {

  override fun step(stepLogProbs: Array<DoubleArray>, context: IntArray?): Search.StepResult {
    modifyScore(stepLogProbs, context)

    val stepLogProbsLinearSize = stepLogProbs.sumOf { it.size }
    val logProbs = DoubleArray(stepLogProbsLinearSize)
    val expStepLogProbs = DoubleArray(stepLogProbsLinearSize)
    var offset = 0
    for (i in stepLogProbs.indices) {
      val probs = stepLogProbs[i]
      val score = hypothesesScores[i]
      for (value in probs) {
        val currentVal = value + score
        logProbs[offset] = currentVal
        expStepLogProbs[offset++] = exp(currentVal)
      }
    }

    var samples = topk1d(logProbs, searchSize)
    val sampleScores = logProbs.sliceArray(samples)

    val stepSortMask = IntArray(samples.size) { floorDiv(samples[it], vocabSize) }
    samples = IntArray(samples.size) { floorMod(samples[it], vocabSize) }

    sortMask = IntArray(batchSize) { it }
    updateState(samples, sampleScores, stepSortMask)

    return Search.StepResult(sortMask, samples)
  }

  private fun modifyScore(scores: Array<DoubleArray>, context: IntArray?) {
    if (repetitionPenalty != 1.0 && context != null) {
      val uniqueTokens = context.toSet()
      for (i in scores.indices) {
        pessimizeScore(scores, i, uniqueTokens)
      }

      for (i in hypothesesTokens.indices) {
        pessimizeScore(scores, i, hypothesesTokens[i].toSet())
      }
    }
  }

  private fun pessimizeScore(scores: Array<DoubleArray>, ind: Int, uniqueTokens: Set<Int>) {
    for (previousToken in uniqueTokens) {
      val score = scores[ind][previousToken]
      scores[ind][previousToken] = score * if (score < 0.0) repetitionPenalty else 1.0 / repetitionPenalty
    }
  }
}
